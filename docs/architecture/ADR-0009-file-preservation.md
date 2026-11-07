# ADR-0009: File preservation — ingestion, operations, recovery, integrity and repair

Status: Implemented for Phase 2 review (owner decisions P2-A … P2-K, B1 … B5), revised for the independent
Phase 2 audit (GAS-P2-001 … GAS-P2-011), its re-audit (GAS-P2-R01, GAS-P2-R02) final verification
(GAS-P2-FV01 … FV03) and IV01 … IV03; see
`docs/PHASE-2-AUDIT-CORRECTIONS.md`. Not yet accepted.

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
- Physical-copy accounting (GAS-P2-FV01, IV01): every copy is accounted at every moment by a record that
  says it is there now - a `CURRENT` or `QUARANTINE_PENDING` generation (its storage path), a `QUARANTINED`
  generation or ledger entry (its quarantine path), or the operation that created it (`target_created`, set
  in the transaction that creates it exclusively). Settling a quarantine move retires its source in the
  same transaction: every operation's ownership of that location ends and a superseded generation stored
  there becomes `QUARANTINED` (or `MISSING`). Locations bytes were moved from, never reached, or already
  removed are historical: destruction never deletes there, and reports any object it finds there as left
  untouched. The database keeps generation state and operation ownership from contradicting one another
  (V0015): retiring a generation (QUARANTINED, MISSING, DESTROYED) or recording content as MISSING ends every
  operation's ownership of that location in the same transaction, and no operation can take ownership of a
  retired generation's location. A current location recorded as missing is removed only if the object
  there is verifiably the content (authoritative size and SHA-256); otherwise it is left and reported. Interrupted moves are settled before anything is deleted; if a move's source and destination
  both exist, nothing is deleted and the operation stays `CLEANUP_INCOMPLETE`. Generation, ledger and
  content states are retired in the completion transaction.
- Destruction intent and repairs (GAS-P2-IV03): inside the intent transaction, on its own connection, the
  intent first takes every unfinished repair's lock for the transaction (`pg_try_advisory_xact_lock`, the
  same key a worker holds as a session lock for as long as it works on the operation), waiting at most two
  seconds and otherwise failing visibly ("being written right now"); so any number of repairs costs one
  pooled connection. Removal holds all other writers' locks together on one connection; in its transaction it locks the content row,
  refuses if an unheld repair has started, and cancels unfinished repairs. A repair creates its copy and
  records ownership in one transaction holding the content row in share mode after re-reading its state, so
  no repair byte is created after the intent commits.
- **Single owner and physical evidence (V0016, structural):** each location has exactly one owner at a time.
  An operation owns only a copy it created and has not committed; the commit records the copy as a
  generation and ends the operation's claim in the same transaction. The database checks at commit, for
  every INSERT and UPDATE of operations and generations, that no operation claims a location any generation
  records. Generations carry their own physical evidence: `vacated` is set in the same statement that
  records content as MISSING (the finding is written in the same transaction) and cleared only when the
  bytes are verified again; content cannot go from MISSING to DAMAGED, and a vacated generation can never
  be QUARANTINE_PENDING. An object that appears at a vacated location is reported as unexpected and never
  quarantined, adopted or deleted; destruction removes a vacated location only if the object there is
  verifiably the content. Quarantine intents record the moved object's size and last-modified time; an
  interrupted move is finished only for that object, otherwise nothing is moved and the operation reports
  it. Recovery reconciles an interrupted or legacy missing recording (open MISSING finding, content still
  ACTIVE) before settling moves or processing operations; destruction does the same for its content.
- **Evidence for every destructive step (V0017):**
  - Ownership claims are rows of `physical_location_owner` (primary key volume + path), maintained by
    triggers on operations and generations, so PostgreSQL itself serialises concurrent claimants: a second
    claim of a location waits for the first transaction and is then refused (READ COMMITTED, the
    application's level) or fails to serialise (REPEATABLE READ). Ownership passes from an operation to its
    generation by releasing the claim and inserting the generation in one transaction.
  - Content whose bytes leave is recorded MISSING from ACTIVE or DAMAGED alike (vacating the location);
    recovery reconciles either state when an open MISSING finding names the current location.
  - Quarantine intents record the object's SHA-256; recovery moves or confirms only an object with those
    bytes, re-checks what arrived after the rename and returns anything else to where it was. Intents without
    that evidence (older ones) are unverified and block visibly instead of finishing.
  - A repair quarantines a damaged generation's copy only if it still has the size and SHA-256 recorded when
    the damage was found (scans now record the hash for size mismatches too); otherwise the generation is
    retired as MISSING and the object is left untouched and reported.
  - Destruction deletes a generation's object only if its SHA-256 is vouched for (authoritative hash, the
    recorded damaged-copy hash, or the hash recorded when it was quarantined); an operation's own uncommitted
    copy is vouched for by its claim. Anything else is left and reported.
  - A MISSING original is restored to ACTIVE only when its bytes verify and it is already protected
    read-only; scans never change protection. Worker re-verification checks the bytes before it protects.
- **Deletion and quarantine authorization (V0019, replacing the V0018 table).** Bytes are moved or deleted only
  on evidence the archive established before the object could have been replaced. An observed hash - including
  the one the first damage scan records (`damaged_sha256`, now diagnostic only) - and a claim alone are never
  evidence. For every physical copy:

  | Copy | Record claiming it | Evidence identifying the object | Established | Move / delete allowed when |
  |---|---|---|---|---|
  | Current generation (content ACTIVE, DAMAGED, or MISSING/vacated) | generation (CURRENT) | authoritative size + SHA-256 | at ingestion (never changes) | object absent, or it has the authoritative SHA-256; otherwise recorded unresolved, untouched |
  | Uncommitted copy of an operation | operation claim (`physical_location_owner`) | the receiver's size + SHA-256 (complete copy), or byte prefix of the staged upload after the staged upload re-verifies against them; for a copy the worker just wrote, the size + SHA-256 measured while writing | at receipt / while writing | promotion, cancellation, recovery and destruction move or delete it only when verified; otherwise the claim becomes an unresolved record, the object is untouched |
  | Superseded generation whose location is occupied (UNRESOLVED) | generation + `unresolved_object` | none until an Administrator attests the recorded observation | at attestation | attested and still matching: quarantined (`ADMINISTRATOR_ATTESTED`) or removed by destruction; otherwise untouched |
  | Superseded generation, nothing at its location | none (MISSING) | - | - | nothing to act on |
  | Quarantined object | ledger entry (QUARANTINED) | the move's size + SHA-256 when its `evidence_basis` is `OPERATION_COPY_VERIFIED` or `ADMINISTRATOR_ATTESTED` | at the move's intent | object absent or matches; `UNTRUSTED` (all moves before V0019) authorises nothing - recorded unresolved |
  | Unresolved object (any of the above) | `unresolved_object` (open) | an Administrator's attestation of its observed SHA-256 | at attestation | as above; released objects are never touched; verified absence closes the record |
  | Anything at a former location (historical generation path, operation target, ledger source) | none - a hash match is not a claim (third V0019 audit) | an Administrator's attestation only | at attestation | attested and still matching: removed by destruction; released: left and reported; otherwise left, unresolved (keeps destruction incomplete) |

  Destruction records `BYTES_REMOVED` only when no open unresolved record remains for the content or its
  operations; an unidentified object keeps it `CLEANUP_INCOMPLETE`, naming the path and why, on every retry,
  until the object is verifiably gone or an Administrator resolves it (Preservation page: attest or release).
  The database enforces that an UNRESOLVED generation always has its open record, that records are never
  deleted and a resolution never changes, and that a move's `evidence_basis` is never rewritten.
- Unfinished repairs of the content are part of that accounting (GAS-P2-R01). Removal first takes the lock
  of every unfinished operation on the content and of every owner of an unsettled quarantine move of it
  (if one is busy, removal waits and is retried), then stops each unfinished repair (`CANCELLED`, with the
  reason) so none can write, commit or move bytes afterwards. A copy a repair created itself
  (`target_created`, not since moved through the ledger) is removed with the content's other copies; an
  object it did not create is left untouched, as everywhere else. Stopping is committed before removal, so
  an interruption at any point is finished by the next attempt.

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
  *Amended by V0019 (V0018 audit; recorded for owner review in PHASE-2-AUDIT-CORRECTIONS.md):* a scan's
  observation of a mismatch cannot identify which object is the damaged copy, so the repair keeps the
  superseded copy accountable where it is - generation `UNRESOLVED`, durable `unresolved_object` record,
  untouched and recoverable - and moves it to quarantine only after an Administrator attests the recorded
  object (the move verifies it still has the attested SHA-256). An empty former location is retired MISSING.

### Restriction (P2-H, B2)
Restricted Files are visible only to Administrators. For everyone else every path (page, friendly-ID
lookup, download, thumbnail, lists, duplicate listings, derivation links, upload status, Change History)
answers exactly as for a nonexistent File.

- **Change History (GAS-P2-003)** is filtered when read, following the current restriction (so it also
  covers entries written before a File was restricted): entries about a restricted File are removed, and in
  the remaining entries a field or reason naming a restricted File (UUID or friendly ID) or content it
  uses is withheld - absent, exactly as if it had never been recorded. References are recognised by the
  same rules that resolve File identifiers (`FileIdentifiers`, GAS-P2-R02, GAS-P2-FV03), which are also the
  web layer's UUID conversion: friendly IDs in any case and with any number of leading zeros (`F00001`,
  `f00001`, `F1`); UUIDs in every spelling `UUID.fromString` accepts (any case, abbreviated or over-long
  groups, any Unicode decimal digit, full-width letters), alone or embedded in text.
- **No existence oracle (GAS-P2-FV02, IV02):** one visibility policy for whole and embedded identifiers.
  Which identifiers a value refers to is read from the text alone: friendly IDs as delimited words; UUIDs
  from every five consecutive hyphen-separated groups of UUID characters, read as the JDK reads them
  (canonical-shaped groups take precedence over overlapping windows). The value is shown only if every
  identifier it refers to is visible to the reader - an unrestricted File, content used only by
  unrestricted Files, or another object with history (archive, volume, user). A restricted File and a
  nonexistent one are never distinguished, whatever the spelling or embedding.
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
