# Phase 2 completion report — File Preservation

Prepared by the implementation agent for independent review. **Phase 2 is not accepted.** Formal
acceptance requires independent review, correction of confirmed findings and owner approval. The Phase 2
PR has not been merged.

> **Audit corrections.** The independent audit of `438649f` found 9 MAJOR and 2 MINOR defects
> (GAS-P2-001 … GAS-P2-011). They are corrected in `83031267c8c37394462c4cf8346cc5a1d8108cde`; the
> correction matrix, replaced tests and reconciliation are in
> [`PHASE-2-AUDIT-CORRECTIONS.md`](PHASE-2-AUDIT-CORRECTIONS.md), which takes precedence over this report
> where they differ. The design is in the revised ADR-0009.

## Baseline and head
| | |
|---|---|
| Starting baseline | `phase-1-accepted` = `1dff0fa63672ac9af162d283816207cb0d78c357`; branched from `main` at `26b8df8` (PR #4 merge) |
| Branch / PR | `phase-2` / PR (open; not merged) |
| Implementation commit (audited) | `438649f2d0a88cf861675c5cb8234f46ca607eaa` |
| Corrected implementation commit | `83031267c8c37394462c4cf8346cc5a1d8108cde` (audit corrections) |
| Later commits | documentation only; CI runs on the PR head and the run IDs are in the PR description |

## 1. What was implemented
Owner decisions P2-A … P2-K and B1 … B5 ([`PHASE-2-DECISIONS.md`](PHASE-2-DECISIONS.md)); design in
[ADR-0009](architecture/ADR-0009-file-preservation.md) and [ADR-0010](architecture/ADR-0010-service-identities.md).

| WP | Delivered |
|---|---|
| WP1 | `SafeFiles` (exclusive create, no-follow open, per-component link/junction/reparse refusal, verified read-only protection, Windows sharing-violation retry), volume marker, managed layout, protected staging area |
| WP2 | Migrations V0009–V0013 (below) |
| WP3 | Browser ingestion: upload batches, raw `PUT` per file streamed into staging with size/SHA-256, limit enforced while streaming, 1 s progress heartbeat, idempotent per batch item |
| WP4 | Worker promotion: plan → exclusive create → copy → flush → verify → **read-only before PROMOTED** → re-verify → commit (content, File, derivation, Change History in one transaction) → clean staging; per-operation advisory lock; compare-and-set state machine with append-only events |
| WP5 | Recovery on worker start and every 2 min: active vs abandoned uploads, partial copies to quarantine, volume waits, collisions blocked, nothing guessed |
| WP6 | Files pages: list with thumbnails/rotation, details with provenance, derivation, duplicates, history; correct (display name, rotation, restriction); recoverable delete/restore; Administrator destruction with tombstones and transactional shared-content checks |
| WP7 | Exact-duplicate sharing with full byte comparison (never with content a restricted File uses); derivative ingestion; restriction inheritance |
| WP8 | Integrity scans (quick/full), findings, worker re-verification, verified repair with new generations and retained, quarantined damaged copies |
| WP9 | Preservation page, operation detail with retry/cancel (executed by the worker), Operations page status and web write probe; CLI `register-volume`, `init-volume`, `set-ingestion-volume`, `status` lines |
| WP10 | Windows virtual service accounts with SID-based permissions and `grant-volume-access.ps1`; Linux web unit `ProtectSystem=strict`; staging/cache directories; smoke and native service tests extended |
| WP11 | ADR-0009, ADR-0010, decisions record, README, example configuration, this report |

## 2. Schema / migration changes (V0001–V0008 untouched)
| Migration | Adds |
|---|---|
| `V0009__file_operation.sql` | `file_upload_batch`; `file_operation` (kinds INGEST/DESTROY/REPAIR; 13 states; actor, volume, batch item, staging name, planned target, provenance captured at upload, expected size/SHA-256, progress, outcome, blocked reason, quarantine path; unique batch item; immutable identity columns; never deleted); append-only `file_operation_event` |
| `V0010__file_content.sql` | `file_content` (size and SHA-256 immutable; ACTIVE/MISSING/DAMAGED/DESTROY_PENDING/DESTROYED) and `file_content_generation` (one CURRENT per content; QUARANTINED requires its quarantine path; unique storage path per volume) |
| `V0011__archival_file.sql` | Friendly series `F` (min. 5 digits); `archival_file` (identity, provenance, lifecycle ACTIVE/DELETED/DESTROYED, restriction, display rotation, deletion/destruction fields, replacement; immutable provenance; destruction final); `file_derivation`; foreign keys from operations (the File reference is deferred to the committing transaction) |
| `V0012__integrity.sql` | `integrity_scan`; `integrity_finding` (11 kinds; only resolution may change; never deleted) |
| `V0013__volume_preservation_settings.sql` | `storage_volume.marker_initialized_at` (set once); `archive_instance.ingestion_volume_id`; guard refusing to retire a volume that holds current or quarantine-pending generations or is the ingestion destination |
| `V0014__preservation_audit_corrections.sql` | Audit corrections: `quarantine_object` ledger; `file_operation.target_created` and `receiver_alive_at`; `integrity_finding_observation`; destroyed generations keep their quarantine path; restriction-inheritance triggers (and consistency fix for existing data) |

## 3. File / storage behaviour
- Originals: `<volume root>/originals/<hex2>/<hex2>/<content-id>[.gN]`, never named after uploads;
  quarantine: `<volume root>/quarantine/<operation-id>-<reason>`. Locations persist as Volume ID + portable
  relative path.
- The web process writes only the installation-local staging directory; the worker is the only writer to
  volumes. Protection: POSIX 0440, Windows read-only attribute — applied and verified before success.
- A missing, foreign or mismatched volume marker makes operations wait; nothing is written.
- Nothing found in a volume is adopted or deleted automatically.

## 4. Acceptance tests implemented and run
New test classes (IDs from the acceptance specification and the authorization in names/comments):

| Class | Covers |
|---|---|
| `SafeFilesTest`, `ManagedVolumeTest`, `StagingAreaTest` | containment, exclusive create, protection, marker parsing/mismatch, staging isolation (WP1) |
| `Phase2SchemaIT` | V0008 → V0013 upgrade; table set; File series |
| `IngestionIT` | F-001, success only after verified/protected/committed, P2-B two Files one content, F-004 byte comparison, BI-008 resend, B5 streamed and declared limits, broken/short uploads, F-009 derivatives and restriction inheritance, readiness, permissions |
| `RecoveryIT` | F-002 crash at every worker fault point → exactly one verified File and a clean full scan; TX-003 partial copy quarantined; I/O failure retried; TX-004 volume wait/resume; P2-D foreign marker; TX-005 collision blocked/untouched then retried; cancellation by the worker; **requirement 1**: stale RECEIVING abandoned only after the timeout, slow-but-active upload never abandoned, stalled upload abandoned and cannot be archived |
| `DestructionIT` | F-006 recoverable delete/restore; F-007 tombstone, bytes removed, IDs not reused; **requirement 4** shared content kept while referenced, pending content never reused; TX-007 incomplete removal (Windows open handle, Linux read-only directory) visible and retried |
| `IntegrityRepairIT` | IV-001 clean scans; IV-002 missing → repaired (new generation); IV-003 corruption found by full scan, wrong repair rejected, damaged copy quarantined; **requirement 4** crash before quarantine move keeps the generation accounted for; IV-004 lost protection restored by worker; IV-005 unexpected objects/staging leftovers reported, not touched; IV-006 link (Linux) / junction (Windows) reported; IV-007 unavailable volume ≠ missing content; size-mismatch download refused |
| `RestrictionIT` | B2: Administrator-only; identical not-found for page, friendly ID, download, thumbnail, lists; duplicate uploads reveal nothing; editors cannot change/delete/destroy |
| `FileWebIT` | P2-A 12 MB browser upload through the web process, "Safely archived" only after the worker; download headers; B5 413 for declared and streamed bodies; CSRF; restricted parity over HTTP; Administrator-only preservation pages; probe reports write access in a test process |
| `PreservationWorkerIT` | B3 launched worker archives and generates thumbnails; crash inside a worker job recovered |
| `CliPhase2IT`, `FileSettingsConfigTest`, `MediaTypesTest`, `FileControllerJsonTest`, `AccessPolicyTest` (Phase 2 additions) | CLI volume commands and exit codes; `files` settings validation; media sniffing; JSON escaping; Phase 2 permissions |

Packaged smoke tests (both OSes): register/init (second init refused)/set ingestion volume, browser
upload archived by the worker, downloaded byte for byte, stored original read-only and not named after the
upload, staging cleaned.
Native service tests: **Windows** — each service runs as its own virtual account; volume granted by
`grant-volume-access.ps1`; the web service's own probe reports denial; upload/archive/download through the
real services. **Linux** — web unit runs as the service account under `ProtectSystem=strict`; the probe
reports denial; upload/archive/download through the real units; original is `0440`.

## 5. Test results
See the PR description for the run IDs. On the verified head, every job of the Windows/Linux ×
PostgreSQL 16/18 matrix runs the Phase-Gate suite (unit + PostgreSQL integration), the packaged smoke
test and the native service-manager test; no test is skipped on any platform.

## 6. Known limitations
- **Linux separation is per unit, not per account (B1):** both units share `genealogy-archive`; the web
  service's read-only access comes from `ProtectSystem=strict`. Running `serve` by hand outside systemd
  as that account would have write access (the Operations page would show it).
- **Residual containment race (P2-E):** a local attacker able to write inside a volume could race a
  directory swap between the per-component check and use; no native code was added.
- **Workgroup NAS (B4):** documented only, not CI-tested.
- **Thumbnails:** JPEG, PNG, GIF, BMP and TIFF via the JDK only; PDFs and other formats get none.
- **Uploads are not resumable:** an interrupted file is sent again in full. A resent item returns the
  earlier attempt (never a second File); when that attempt ended without archiving, the browser uploader
  retries the file under a fresh item key.
- **Integrity scans are started by an Administrator**; no scheduled scans yet.
- **Ingestion volume** is archive configuration but is not yet part of the archive configuration export
  (Phase 8).
- **Crash testing:** protocol-point crashes are simulated in-process, and a real worker JVM is forcibly
  terminated mid-copy and recovered by a fresh JVM on every CI host (`WorkerProcessKillIT`, GAS-P2-002).
- **Change History redaction** covers structured references (File UUIDs, friendly IDs, content IDs);
  free-text that describes a restricted File in other words cannot be recognised.
- `DestructionIT`'s Linux obstruction uses a read-only directory, which a superuser ignores; that case
  would be skipped (and the CI evidence guard would then fail the job) if tests were run as root.

## 7. Unresolved specification questions
None blocking. For owner awareness:
- Should scheduled integrity scans (and their frequency) be part of a later phase's brief?
- Should restricted Files be excluded from exact-duplicate sharing permanently (current behaviour, to avoid
  an existence oracle), or should sharing be allowed among Administrators' uploads?

## 8. Deviations from the approved plan
1. **Phase 1 schema test scoping:** `Phase1SchemaIT`'s "current schema" checks (migration count, table set)
   are pinned to V0008 with `SchemaMigrator.upTo`, as the Phase 0 check was pinned to V0001. Assertions
   unchanged; Phase 2 equivalents are in `Phase2SchemaIT`.
2. **Phase 1 permission test scoping:** `AccessPolicyTest`'s Editor/Viewer Phase 1 loop now iterates the
   explicit Phase 1 permission list (Editors gained `INGEST_FILES`/`EDIT_FILES`); a new test proves the list
   plus the Phase 2 list cover every permission, and Phase 2 grants are tested separately.
3. **WinSW XML regression test:** expected arguments updated for the per-service `-Djava.io.tmpdir`.
4. **Re-verification and cancellation run in the worker** (the browser records the request) because they
   change managed storage, which the web service may not do.
5. **No duplicate sharing with content a restricted File uses** — refinement of P2-B ("may share") to keep
   restricted Files from being revealed by an upload.
6. **ArchUnit:** `preservation` added to the inward-layering rule (a strengthening).
