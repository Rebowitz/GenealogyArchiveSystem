# Architecture overview (Phase 0)

Read with [`../BUILD-SPEC.md`](../BUILD-SPEC.md) s.5 and the ADRs in this folder.

## Runtime shape

```
Browser ──HTTP──> [serve]  Web application ─┐
                                             ├─> Application services ─> data/ (JDBC) ─> PostgreSQL (schema "gas")
                  [worker] Background worker ┘                        └─> files/ ─> VolumeRoots ─> Storage Volume roots (native paths)
Administrator ──> [migrate | status | check-config]   (same jar, short-lived commands)
```

One jar, one codebase, two long-lived processes per Archive Instance, run by the host's own service
manager (systemd or Windows services). No broker, cache, search server, container runtime or cloud
service. Instances are independent; nothing synchronises between them.

## Modules (Java packages) and dependency rules

| Package | Responsibility | May depend on |
|---|---|---|
| `domain` | Historical domain model (empty in Phase 0) | nothing application-level, no frameworks |
| `files` | Portable storage references (`PortableRelativePath`, `StorageLocation`, `VolumeRoots`) | JDK; platform snapshot check in VolumeRoots |
| `platform` | The only OS-aware code: `HostPlatform`, `NativePaths`, `FilesystemCapabilities` | JDK only |
| `config` | Installation configuration loading/validation, `Secret` | `platform`, SnakeYAML |
| `data` | All SQL/JDBC: `DataSources`, `PostgresServer`, repositories, `SchemaCatalog` | Spring JDBC, driver |
| `migration` | Flyway runner, `SchemaStatus`, `PostMigrationValidator` | `data`, Flyway |
| `service` | Application services, transactions, `SystemStatusService` | `data`, `migration`, `platform` |
| `web` | Controllers + templates | `service` only (never `data`/`migration`/JDBC) |
| `ops` | Health/readiness contributors | `service` |
| `worker` | Worker lifecycle | `service` |
| `boot` | Spring process assembly (`CoreConfiguration`, `WebApplication`, `WorkerApplication`, `ProcessLauncher`) | everything |
| `cli` | Command front end, exit codes | everything |

Enforced by `src/test/java/org/genealogyarchive/ArchitectureTest.java`, which also bans: JDBC outside
`data`/`migration`, `File.separator`, host inspection outside `platform`, outbound HTTP clients, and the
infrastructure prohibited by BUILD-SPEC s.60 (also blocked at build time by the Maven Enforcer).

Wiring of core beans is explicit (`CoreConfiguration`), not component-scanned, so it is obvious what exists.

## Readiness model
`SystemStatusService.check()` -> `SystemStatus.ready()` = database reachable AND PostgreSQL >= 16 AND
schema exactly current. Exposed as:
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

## What Phase 1 builds on
- `InstallationConfig` stays host-local; Archive configuration (with Archive ID) goes into the database.
- `StorageLocation` is the persisted file-location shape for the Storage Volume model.
- `WorkerProcess` is where the PostgreSQL-backed job loop (`FOR UPDATE SKIP LOCKED`) attaches.
- `PostMigrationValidator` gains invariant checks for each new table.
- Local authentication (Spring Security, Argon2/bcrypt) is added within the existing stack.

## Correction boundary

Readiness additionally requires the expected foundation identity marker. The files/platform
boundary now includes a physical containment snapshot; it does not authorize later file access.
See ../PHASE-0-CORRECTIONS.md for the required Phase 2 race/containment work and evidence limits.
