# ADR-0009: File preservation — ingestion, operations, recovery, integrity and repair

Status: Implemented for Phase 2 review (owner decisions P2-A … P2-K, B1 … B5), revised for the independent
Phase 2 audit (GAS-P2-001 … GAS-P2-011; see `docs/PHASE-2-AUDIT-CORRECTIONS.md`). Not yet accepted.

## Context
Phase 2 stores the archive's first preservation originals. The archival invariants that govern it:
preservation originals are immutable after verified ingestion; filename, path and row position are never
identity; permanent identifiers are never reused; restricted information must not leak; when integrity
is uncertain the system fails visibly instead of guessing. The design must work identically on Windows
and Linux hosts, with PostgreSQL authoritative and ordinary filesystems as Storage Volumes.

## Decisions

### Processes and who writes what
- The **web process** streams each upload (a raw `PUT` body, P2-A; multipart is disabled) into an
  installation-local **staging area** outside every Storage Volume, measuring size and SHA-256 as bytes
  arrive and enforcing `files.maxUploadBytes` (default 4 GiB, B5) during streaming. It never writes an
  archival volume.
- The **worker** is the only writer to archival volumes (B3: ingestion needs the worker running). It
  revalidates the staged file (plain file inside the staging directory, still the recorded size/hash)
  before promotion. Re-verification and cancellation requested in the browser are executed by the worker
  for the same reason.
- The operating system enforces this (ADR-0010): the web service is denied write access to volume roots,
  and the web process checks it from the inside with a write probe shown on the Operations page.

### Identity and content
- Every deliberate ingestion creates a new **Archival File** (UUIDv7 + friendly `F00042`) with its own
  provenance: original filename as captured, uploader, time, batch, client last-modified time, source
  note, requested restriction, derivation (P2-B, P2-F).
- Bytes live in **file content** rows (size and SHA-256 immutable). Byte-for-byte identical uploads may
  share one content. Before sharing, the worker verifies the stored original against its authoritative
  size and SHA-256, compares it byte for byte with the upload (equal hashes are not enough), and makes
  sure it is protected read-only - re-applying lost protection (and recording a finding) or blocking with
  `PROTECTION` - so a shared upload carries the same guarantee as a new copy (GAS-P2-005). Content used by
  a restricted File is never shared, so an upload cannot reveal that a restricted File exists.
- Stored paths are `originals/<2 hex>/<2 hex>/<content-id>[.gN]` relative to the volume root (P2-C).
  Names never derive from uploaded filenames. Repairs write a new **generation**; old generations are
  kept as `QUARANTINE_PENDING` until the move into `quarantine/` is confirmed, then `QUARANTINED`.

### Volumes
- A volume is usable only if its root contains `.genealogy-archive-volume` naming this Archive ID and the
  Volume ID (P2-D). `init-volume` writes it once (exclusive create) and is refused on a second run, and
  also when the database records an earlier initialization (a missing marker means the wrong directory is
  mounted, not that it should be recreated). A missing, foreign or mismatched marker makes operations
  **wait**; nothing is written.
- The ingestion destination is chosen by Volume ID (`set-ingestion-volume`, or Preservation page).
- A volume holding current or quarantine-pending generations, or chosen as ingestion destination, cannot
  be retired (database trigger).

### Operation protocol (single writer, no overwrite)
Each ingest, repair and destruction is a persistent `file_operation` row with an append-only event history
(P2-J: separate from background jobs and Change History). Every state change is a compare-and-set
recorded with its event in one transaction. A PostgreSQL session advisory lock per operation keeps two
workers from driving the same operation.

`RECEIVING → STAGED → PROMOTING → PROMOTED → COMMITTED → COMPLETED`, with `WAITING_FOR_VOLUME`, `BLOCKED`,
`ABANDONED`, `CANCELLED`, `REJECTED` and, for destruction, `CLEANUP_PENDING`/`CLEANUP_INCOMPLETE`.

1. The target path is planned and recorded before anything is written.
2. The copy is created with exclusive create (`CREATE_NEW`, no-follow); nothing is ever overwritten or
   renamed into place. Right after the create succeeds - before any byte is written - the operation
   records that it owns the target (`target_created`). Only an owned object is ever measured, adopted,
   quarantined or cancelled as the operation's copy; any other object at a planned location blocks the
   operation (`COLLISION`), across retries and volume waits, and is left untouched (GAS-P2-009).
3. The copy is flushed (file and directory), verified against the staged size and hash, and **made
   read-only before the operation becomes `PROMOTED`**; if protection cannot be applied the operation is
   `BLOCKED (PROTECTION)` and is not reported as archived (requirement 3).
4. The stored copy is re-measured; only then are the content, File, derivation and Change History rows
   committed in one transaction. The browser reports "Safely archived" only from `COMMITTED`/`COMPLETED`.
5. Staging is cleaned and a thumbnail job is queued.

### Recovery
- On worker start and every `gas.worker.recovery-interval` (PT2M), recovery advances unfinished operations
  from their recorded state and a fresh look at the bytes — never from filenames.
- **Active vs abandoned uploads (requirement 1, GAS-P2-001):** the process receiving an upload renews a
  receiver lease (`receiver_alive_at`, database clock) on a timer, every quarter of
  `files.receivingTimeout` (between 200 ms and 5 s), independently of whether bytes arrive. Recovery
  abandons an upload only when neither its lease nor its byte progress was renewed for
  `files.receivingTimeout` (default PT15M) - that is, when its receiver has stopped. A live receiver blocked
  in a read or write keeps its upload. A client that stops sending is a separate, live-request matter:
  the server's read timeout (`server.tomcat.connection-timeout`) ends the request and the live receiver
  abandons the upload itself, with a message saying so.
- **Quarantine ledger (GAS-P2-008):** every move into `quarantine/` (partial copy, unverified copy,
  cancelled copy, damaged generation) is recorded in `quarantine_object` as `INTENDED` and committed
  before the move, and confirmed (`QUARANTINED`) after it. A move interrupted at any point is settled from
  what is on disk - finished, confirmed, or recorded as `SOURCE_ABSENT`; if both the object and its
  destination exist, nothing is moved and the operation blocks. Workers settle their operation's entries
  before deciding anything else, and recovery settles the rest; settling is idempotent.
- A partial copy from an interrupted attempt is moved to quarantine through the ledger and a new
  generation path is planned.
- An unavailable volume puts the operation in `WAITING_FOR_VOLUME`; it resumes when the marker verifies.
- Blocked operations wait for an Administrator (retry or cancel); nothing is guessed.

### Deletion and destruction (P2-G for relationships; requirement 4)
- Deletion is recoverable and touches only the database.
- Destruction is Administrator-only, only from recoverable deletion, with a reason. In one transaction,
  under row locks on the File and the content, the File becomes a permanent tombstone (identifiers stay
  retired) and the live-File count of the content is checked; only when no other File still uses it is
  the content marked `DESTROY_PENDING` and a removal operation created. Pending content is never reused by
  a new upload. Removal failures are visible (`CLEANUP_INCOMPLETE`) and retried.
- Physical removal covers every known copy (GAS-P2-006): each generation's storage location whatever its
  state, each quarantined generation's quarantine copy, and each quarantine-ledger entry of the content
  (interrupted moves are settled first). `BYTES_REMOVED` is recorded only after re-checking that none of
  those locations still holds bytes; otherwise the operation stays `CLEANUP_INCOMPLETE` and names what
  remains. Destroyed generations keep their quarantine path on record.

### Integrity and repair (P2-I)
- Quick scans check presence, size, protection, links/reparse points, unexpected objects in `originals/`
  and staging leftovers; full scans also re-hash. Scans cover every active required volume and the
  ingestion destination, even ones holding no content yet (GAS-P2-010). Findings are recorded once while
  open and are never deleted; nothing found is adopted or deleted automatically. Each scan records which
  findings it observed - new ones and unresolved ones found earlier - so a repeated scan never reports "No
  problems found" while damage remains (GAS-P2-011). A missing volume is reported as unavailable, not as
  missing content; on a volume whose marker verified, a missing `originals` or shard directory is missing
  content and can be repaired (GAS-P2-007).
- Repair accepts Administrator-supplied bytes only if size and SHA-256 equal the authoritative values
  recorded at ingestion (which never change), writes a new generation through the same protocol, and
  keeps the damaged copy in quarantine.

### Restriction (P2-H, B2)
Restricted Files are visible only to Administrators. For everyone else every path (page, friendly-ID
lookup, download, thumbnail, lists, duplicate listings, derivation links, upload status, Change History)
answers exactly as for a nonexistent File.

- **Change History (GAS-P2-003)** is filtered when read, following the current restriction (so it also
  covers entries written before a File was restricted): entries about a restricted File are removed, and in
  the remaining entries a field or reason naming a restricted File (UUID or friendly ID) or content it
  uses is withheld - absent, exactly as if it had never been recorded.
- **Inheritance (GAS-P2-004)** is a database invariant (V0014 triggers): restricting a File restricts every
  derivative at every depth in the same statement; a derivation recorded under a restricted parent
  (including one committed concurrently, ordered by a row lock) is restricted; a derivative of a
  restricted File cannot be lowered. The application records each inherited restriction, with its cause,
  in Change History. Lowering stays deliberate: lowering a parent leaves its derivatives restricted until
  each is lowered by an Administrator.

## Residual risks accepted (P2-E, requirement 7)
Containment uses portable Java checks: every directory component below a volume root is checked without
following links (symlinks, junctions, Windows reparse points refused), targets are created exclusively and
opened no-follow. A local attacker who can already write inside a volume could still race a directory
swap between check and use; closing that would need native `openat`-style code, which the owner decided
not to add. The OS permissions of ADR-0010 keep the web service out entirely.

## Consequences
- Upload size is bounded by the configured limit, not by framework limits; a reverse proxy in front must
  allow at least the same body size and should not buffer whole uploads (README §7).
- The ingestion volume setting is archive configuration but is not yet part of the archive configuration
  export (Phase 8).
