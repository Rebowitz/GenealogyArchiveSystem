# Architecture overview (Phase 1)

Read with [`../BUILD-SPEC.md`](../BUILD-SPEC.md) s.5 and the ADRs in this folder.

## Runtime shape

```
Browser ──HTTP──> [serve]  Web application ─┐
                                             ├─> Application services ─> data/ (JDBC) ─> PostgreSQL (schema "gas")
                  [worker] Background worker ┘                        └─> files/ ─> VolumeRoots ─> Storage Volume roots (native paths)
Administrator ──> [migrate | init-archive | create-admin | status | check-config]   (same jar, short-lived commands)
```

One jar, one codebase, two long-lived processes per Archive Instance, run by the host's own service
manager (systemd or Windows services). No broker, cache, search server, container runtime or cloud
service. Instances are independent; nothing synchronises between them.

## Modules (Java packages) and dependency rules

| Package | Responsibility | May depend on |
|---|---|---|
| `domain` | Historical domain model (empty until Phase 2) | nothing application-level, no frameworks |
| `identity` | UUIDv7 `GlobalIds`, `FriendlyId` format | JDK + uuid-creator only |
| `access` | `Role`, `Permission`, `Actor`, `AccessPolicy` | JDK only |
| `history` | `FieldClass`, `FieldChange`, `ChangeRecorder`, `HistoryService` | `data`, `access` |
| `archive` | `ArchiveService`, `ArchiveConfiguration` (archive-level, never installation) | `data`, `access`, `history`, `identity` (no host/platform/config) |
| `security` | Local accounts (`UserService`), `PasswordPolicy`, `PasswordHasher`, Spring Security adapters | `data`, `access`, `history` |
| `storage` | Storage Volume registration/availability (read-only checks) | `data`, `files`, `access`, `history` |
| `jobs` | `JobQueue`, `JobRunner` (PostgreSQL `SKIP LOCKED`) | `data`, `access` |
| `files` | Portable storage references (`PortableRelativePath`, `StorageLocation`, `VolumeRoots`) | JDK; platform snapshot check in VolumeRoots |
| `platform` | The only OS-aware code: `HostPlatform`, `NativePaths`, `FilesystemCapabilities` | JDK only |
| `config` | Installation configuration loading/validation, `Secret` | `platform`, SnakeYAML |
| `data` | All SQL/JDBC: `DataSources`, `PostgresServer`, repositories, `SchemaCatalog` | Spring JDBC, driver |
| `migration` | Flyway runner, `SchemaStatus`, `PostMigrationValidator` | `data`, Flyway |
| `service` | Application services, transactions, `SystemStatusService` | `data`, `migration`, `platform` |
| `web` | Controllers + templates, `WebSecurityConfiguration` | services only (never `data`/`migration`/JDBC) |
| `ops` | Health/readiness contributors | `service` |
| `worker` | Worker lifecycle | `service` |
| `boot` | Spring process assembly (`CoreConfiguration`, `WebApplication`, `WorkerApplication`, `ProcessLauncher`) | everything |
| `cli` | Command front end, exit codes | everything |

Enforced by `src/test/java/org/genealogyarchive/ArchitectureTest.java`, which also bans: JDBC outside
`data`/`migration`, `File.separator`, host inspection outside `platform`, outbound HTTP clients, Spring
Security outside `security`/`web`/`boot`, host/installation dependencies in `archive`/`identity`, and the
infrastructure prohibited by BUILD-SPEC s.60 (also blocked at build time by the Maven Enforcer).

Wiring of core beans is explicit (`CoreConfiguration`), not component-scanned, so it is obvious what exists.

## Readiness model
`SystemStatusService.check()` -> `SystemStatus.ready()` = database reachable AND PostgreSQL >= 16 AND
schema exactly current AND foundation identity marker present AND archive initialized (exactly one
`archive_instance` row). Storage Volume availability, worker presence and failed jobs are reported by
`OperationalStatusService` but do not affect readiness (owner decision P1-D). Exposed as:
- web home page (researcher/administrator readable, never a stack trace),
- `/actuator/health/readiness` (HTTP 503 with `OUT_OF_SERVICE` when not ready; liveness stays UP),
- `status` command (exit code 3 when not ready),
- worker start gate (refuses to start when not ready) and heartbeat log.

Problems are reported, never hidden or auto-repaired (BUILD-SPEC s.2 "fail visibly").

## Test suites
| Suite | Command | Contents |
|---|---|---|
| Fast Development | `mvnw test` | `*Test`: config validation, portable paths, filesystem characterisation, CLI, architecture rules |
| Phase-Gate | `scripts/test.(sh\|ps1)` = `mvnw verify` | adds `*IT` against real PostgreSQL in throwaway `gas_test_*` databases |
| CI extras | `.github/workflows/ci.yml` | packaged-jar smoke test; real systemd and WinSW service tests; Windows + Linux x PostgreSQL 16 + 18 |

## Phase 1 core (see ADR-0006, ADR-0007, ADR-0008)
- Archive ID (UUIDv7) in `archive_instance`, created only by `init-archive`; immutable by trigger.
- Archive configuration (`ArchiveConfiguration`) in PostgreSQL; `InstallationConfig` stays host-local.
- Local accounts and form sign-in; authorization enforced in services with an explicit `Actor`.
- Append-only `change_history`, written in the same transaction as the change; `row_version`
  optimistic concurrency.
- `storage_volume` registry with open purpose codes; roots mapped by installation configuration.
- `background_job` + `worker_presence`; the worker runs the job loop. Not Archival Operation History.
- `friendly_id_series` infrastructure (no series registered until historical object types exist).
- `PostMigrationValidator` additionally reports duplicate archive rows and unsafe friendly-ID sequences.

## Correction boundary

Readiness additionally requires the expected foundation identity marker. The files/platform
boundary now includes a physical containment snapshot; it does not authorize later file access.
See ../PHASE-0-CORRECTIONS.md for the required Phase 2 race/containment work and evidence limits.
