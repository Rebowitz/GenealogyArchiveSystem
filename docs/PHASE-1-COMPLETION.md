# Phase 1 completion report — Archive Identity / Core

Prepared by the implementation agent for independent review. **Phase 1 is not accepted** until the
project owner says so after review. The Phase 1 PR has not been merged.

## Baseline and head
| | |
|---|---|
| Starting baseline | `phase-0-accepted` = `f326366172759d0b9e94c87db51434237834d550`; branched from `main` at `24679aa` (PR #2 merge) |
| Branch / PR | `phase-1` / PR #3 (open, draft; not merged) |
| Implementation head verified by CI | `a18c9893eafbfb2d30c1b1c5c196c2dc6f5ecdc5` — run 40752459561 |
| Later commits | documentation only (this report); CI is re-run on the final head and reported in the PR |

## Files changed (`main`..`a18c989`)
125 files: 98 added, 27 modified (+6408 / −122).
- Migrations: 6 new SQL files. Java main: new packages `identity`, `access`, `archive`, `security`,
  `history`, `storage`, `jobs`; new repositories in `data`; `boot/CoreServices`; web controllers and
  security configuration; CLI commands. Templates: 11 new, `index.html` updated.
- Tests: 17 new test classes plus test support (`ArchiveFixtures`, `WebSession`, `TestDatabase.copy`) and
  a test-only migration location.
- Scripts: smoke tests and native service tests initialize the archive and create an administrator.
- Docs: `PHASE-1.md` (brief, verbatim), `PHASE-1-DECISIONS.md`, ADR-0006/0007/0008, README, overview.

## Migrations added (V0001 untouched)
| Migration | Adds |
|---|---|
| `V0002__archive_instance.sql` | Shared guards `reject_identity_change(...)`, `reject_removal()`; `archive_instance` (singleton row, UUID Archive ID, name, `created_at`, `row_version`); ID/creation time immutable; no delete/truncate |
| `V0003__app_user.sql` | `app_user` (UUID id, case-insensitively unique immutable username, display name, role, password hash, enabled, timestamps, `row_version`); no delete/truncate |
| `V0004__change_history.sql` | Append-only `change_history` (who/when/object type+id/change kind/field changes as JSON array/optional reason); USER rows must reference an account, SYSTEM rows must not |
| `V0005__storage_volume.sql` | `storage_volume` (permanent UUID, name, open purpose code, description, required, type `filesystem`, retired_at; a retired volume cannot be required); never deleted |
| `V0006__background_job.sql` | `background_job` (kind, state, JSON payload, attempts/max, run_after, lease, bounded last_error) and `worker_presence`; documented as **not** Archival Operation History |
| `V0007__friendly_id_series.sql` | `friendly_id_series` (object type, unique prefix, minimum digits, dedicated sequence); immutable |

Upgrade path verified: accepted Phase 0 database (V0001) → V0002–V0007 → same schema as a fresh
database; post-migration validation passes; the instance reports *not ready: archive not initialized*
until `init-archive` runs.

## Identity implementation (ADR-0006)
- Archive ID: UUIDv7 created only by `init-archive` (SYSTEM "command line" actor), stored in the database,
  immutable by trigger, refused a second time (exit 5). Never derived from host, path, DB name, repository
  or network; distinct from the Phase 0 schema-identity marker and the application version.
- Global object IDs: `GlobalIds.newId()` (UUIDv7, `com.github.f4b6a3:uuid-creator` 6.1.1).
- Friendly IDs: `FriendlyId` format (prefix + zero-padded number that widens, never wraps) and
  sequence-backed allocation; numbers are never reused after rollback or deletion. No series is
  registered in Phase 1 (no Person/File/Record/Property objects exist).

## Archive configuration implementation
`ArchiveService.configuration()` / `portableConfiguration()` returns `ArchiveConfiguration`: Archive ID,
name, Storage Volume identities/descriptions, identifier series. It contains nothing from
`installation.yml` (database connection, bind address, logging, volume root paths), which stays host-local.
An ArchUnit rule keeps `archive` and `identity` independent of host/platform/installation code.

## History implementation (ADR-0008)
Field classes Immutable/Correctable/Interpretive (`FieldClass`, `FieldChange`). `ChangeRecorder` writes
only changed fields in the same transaction as the change; reasons are optional; reads and no-op saves
record nothing; secrets are never recorded. `change_history` is append-only at the database level and is
separate from diagnostic logs, from background jobs and from future Archival Operation History. It is not
activity tracking. Optimistic concurrency via `row_version` (`ConflictException`, HTTP 409).

## Authentication / authorization implementation (ADR-0007)
Local accounts only; roles Administrator/Editor/Viewer; bcrypt (cost 12) through Spring Security's
delegating encoder; password policy 12 characters to 72 UTF-8 bytes, not the username. Form sign-in with
CSRF, session-ID renewal, 30-minute idle timeout, sign-out; the account is re-read on every request so
disabling or demoting takes effect immediately. Authorization is enforced inside services via an explicit
`Actor` and `AccessPolicy`; the last enabled Administrator cannot be disabled or demoted. Loopback default
kept; no LAN password sign-in over plain HTTP (decision P1-C). First administrator via `create-admin`
(password from prompt or `GAS_NEW_ADMIN_PASSWORD`, never argv).

## Storage Volumes and background jobs (decision P1-A, P1-D, P1-E)
Volumes can be registered, corrected and retired (never deleted); availability is a read-only directory
check against the installation root mapping; an unavailable required volume is reported prominently and
does not affect readiness. Jobs: `FOR UPDATE SKIP LOCKED` claiming, leases with expiry recovery,
exponential backoff capped at one hour, visible FAILED state, Administrator retry. No production job kinds
and no archival file writes.

## Tests
Phase 0 tests are all retained. Four Phase 0 integration tests needed an added setup step because
readiness now requires an initialized archive (`migrate` → `migrate` + `init-archive`); no Phase 0
assertion was removed or weakened. `MigrationIT.phaseZeroCreatesNoHistoricalDomainTables` is pinned to
V0001 so it still checks exactly the Phase 0 schema.

New unit tests (`*Test`): `GlobalIdsTest`, `FriendlyIdTest`, `AccessPolicyTest`, `FieldChangeTest`,
`PasswordPolicyTest`, `JobRunnerTest`, `ArchiveConfigurationTest`; three new `ArchitectureTest` rules.
New integration tests (`*IT`, real PostgreSQL): `ArchiveIdentityIT`, `Phase1SchemaIT`, `UserSecurityIT`,
`ChangeHistoryIT`, `StorageVolumeIT`, `FriendlyIdIT`, `BackgroundJobIT`, `WorkerJobsIT`, `WebSecurityIT`,
`CliPhase1IT`.

Brief s.15 mapping:
| Requirement | Evidence |
|---|---|
| Archive ID creation / persistence / immutability | `ArchiveIdentityIT` (init once, version 7, refused twice, immutable to direct SQL, persists across application restart) |
| Archive ID vs schema identity, app version, host | `ArchiveIdentityIT.archiveIdIsDistinctFromSchemaMarkerApplicationVersionAndHost` |
| Global object identity | `GlobalIdsTest` |
| Friendly IDs, non-reuse | `FriendlyIdTest`, `FriendlyIdIT` (rollback/deletion, validator detects CYCLE sequences) |
| Archive configuration persistence; machine/archive separation | `ArchiveConfigurationTest`, `StorageVolumeIT.archiveConfigurationCarriesVolumeIdentityButNeverInstallationConfiguration` |
| Change history | `ChangeHistoryIT`, `FieldChangeTest`, attribution assertions in `UserSecurityIT`/`WebSecurityIT` |
| Authentication / authorization | `UserSecurityIT`, `WebSecurityIT`, `AccessPolicyTest`, `PasswordPolicyTest`, `CliPhase1IT` |
| Migration correctness; upgrade from Phase 0 | `Phase1SchemaIT` (Phase 0 DB → Phase 1; upgraded = fresh; validator reports inconsistent identity without repair) |
| Restart behaviour | `ArchiveIdentityIT.archiveIdPersistsAcrossApplicationRestarts`, `BackgroundJobIT.jobsPersistAcrossServiceRestarts`, lease recovery test |
| Readiness implications | `WebSecurityIT.migratedButUninitializedInstanceIsVisiblyNotReadyUntilInitArchive`, `CliDatabaseIT`, `WorkerJobsIT.workerRefusesToStartBeforeTheArchiveIsInitialized`, `StorageVolumeIT` (unavailable volume does not make the instance unready) |
| PostgreSQL 16/18, Windows/Linux | four-way CI matrix below |

## Total test counts and four-way CI (run 40752459561, head `a18c989`)
| Job | Tests | Failures | Errors | Skipped | Smoke test | Native service test |
|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 | 268 | 0 | 0 | 0 | passed | systemd passed |
| ubuntu-24.04 / PostgreSQL 18 | 268 | 0 | 0 | 0 | passed | systemd passed |
| windows-2025 / PostgreSQL 16 | 268 | 0 | 0 | 0 | passed | WinSW passed |
| windows-2025 / PostgreSQL 18 | 268 | 0 | 0 | 0 | passed | WinSW passed |

Phase 0 baseline was 171 tests per job; Phase 1 adds 97. The CI evidence guard requires both unit and
integration reports and fails on zero tests or any skip.

Packaged smoke test now additionally checks: `status` exits 3 after migrate until `init-archive`;
`init-archive` succeeds once and is refused (exit 5) a second time; `create-admin` from the environment;
real form sign-in with CSRF token; archive page reachable only when signed in; administrator password
absent from logs. Native service tests run `init-archive` and `create-admin` as the service account
(Linux) before starting services and check the worker started for the archive.

## Acceptance-specification coverage (foundation level)
Tests cite A-001, A-002, SEC-001, SEC-007, SEC-008, SEC-009, OP-001, OP-002, OP-004, OP-005, F-010,
UP-007, BK-007, Q-007. Coverage is of the Phase 1 foundation for these IDs only: e.g. A-001/A-002 are
exercised on the Archive ID, Storage Volumes, accounts and friendly-ID sequences, not on historical
objects (none exist yet); BK-007 is approximated by a database copy (`CREATE DATABASE … TEMPLATE`), not
by `pg_dump`/restore, which is Phase 8.

## Known limitations
- No built-in TLS (decision P1-C); remote access requires an administrator-provided TLS reverse proxy.
- No sign-in rate limiting or account lockout yet.
- Sessions are held in memory: restarting the web service signs users out.
- bcrypt rather than Argon2 (no extra dependency); the `{bcrypt}` prefix allows a later switch without a
  schema change.
- Editor has the same Phase 1 permissions as Viewer, because no editable historical objects exist yet.
- Windows services still run as LocalSystem (D-0002: a low-privilege account is required before Phase 2
  storage is operational).
- CI job logs cannot be downloaded from this development environment; CI evidence was read through
  check-run annotations and the test-summary step.

## Explicitly deferred to Phase 2+
Persons, Files, Archival Records, Properties and any friendly-ID series for them; archival file
ingestion/writes and production job kinds; Archival Operation History; restricted-information rules;
exports/Transfer Packages; backup/restore tooling (Phase 8); TLS; research history.

## Deviations from the brief
None intended. Interpretations to note for review: (1) four Phase 0 integration tests gained an
`init-archive` setup step (assertions unchanged); (2) the public home page no longer shows the database
version to signed-out visitors (shown after sign-in); (3) `CoreConfiguration` no longer exposes the unused
`PostgresServer`/`SchemaMigrator` beans.

## Unresolved specification questions
None blocking. For a later phase: sign-in throttling policy; whether Editor gains any Phase 1-era
administrative abilities.

## Phase 2
**Phase 2 was not started.** No historical domain tables or features were added (`Phase1SchemaIT`
asserts this).
