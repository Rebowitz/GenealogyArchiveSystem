# Phase 0 Completion Report

Branch `phase-0`. Verified by CI run **40632551836** (commit `82072b1`) on GitHub Actions: native
`windows-2025` and `ubuntu-24.04` runners x PostgreSQL 16 and 18. All four jobs passed.
Phase 1 has **not** been started.

## 1. What was implemented
- **Repository and build:** single-module Maven project (`pom.xml`), Maven Wrapper (Maven 3.9.11; generated
  by real Maven in CI), Spring Boot 4.1.1 BOM pinning, Java 21, reproducible jar timestamp, Maven Enforcer
  rules (Java >= 21, Maven >= 3.9, banned ORM/prohibited-infrastructure dependencies), `.gitattributes`
  line-ending policy, `.gitignore` keeping installation files and secrets out of Git.
- **Modular monolith skeleton** with enforced boundaries (`ArchitectureTest`): `domain` (empty), `files`,
  `platform`, `config`, `data`, `migration`, `service`, `web`, `ops`, `worker`, `boot`, `cli`.
- **One executable** `genealogy-archive.jar` with commands `serve`, `worker`, `migrate`, `status`,
  `check-config`, `version`, `help` and documented exit codes.
- **Browser entry point:** server-rendered status page (Thymeleaf, local assets only).
- **PostgreSQL connectivity:** HikariCP pool from installation configuration; all tables in schema `gas`;
  minimum-version gate (16).
- **Migrations:** Flyway SQL migrations, explicit `migrate` command only, per-migration transactions,
  checksum validation, refusal on FAILED/AHEAD/INVALID states, post-migration validation.
- **Configuration:** strict installation configuration (unknown keys rejected, every problem reported,
  host-native absolute path validation with cross-host hints, BOM-tolerant); secrets only from
  environment variable or protected file, masked everywhere. Archive configuration boundary defined and
  documented (implemented in Phase 1).
- **Platform adapter:** `HostPlatform`, `NativePaths`, `FilesystemCapabilities` (probed case sensitivity and
  atomic rename) - the only OS-aware code.
- **Portable storage references:** `PortableRelativePath`, `StorageLocation` (Storage Volume UUID + portable
  path), `VolumeRoots` (single native-resolution point).
- **Health/readiness:** `/actuator/health/liveness`, `/actuator/health/readiness` (database + `schema`
  contributor), status page and `status` command; only the health endpoint is exposed.
- **Structured logging:** ECS JSON (default) or text; console plus optional rolling files; Hikari at WARN;
  no secrets in logs (tested).
- **Worker process:** starts only when the foundation is ready, heartbeat checks, graceful stop.
  No job processing (Phase 1).
- **Service baselines:** systemd units + `install.sh`/`uninstall.sh` (Linux); WinSW v2 XML +
  `install-services.ps1`/`uninstall-services.ps1` (Windows).
- **Developer scripts** (`.sh` and `.ps1`): `setup-dev-db`, `run`, `test`, `smoke-test`.
- **CI:** `.github/workflows/ci.yml` (Windows + Linux x PostgreSQL 16 + 18) running the documented test
  procedure, packaged smoke test and real service-manager tests; failure annotations; zero-test and
  masked-failure guards. `maven-wrapper.yml` maintenance workflow.
- **Documentation:** `README.md`, `docs/architecture/overview.md`, ADR-0001..0005, this report.

## 2. Schema/migration changes
- `V0001__foundation.sql`: schema `gas` (created by Flyway), table `gas.schema_identity` (single row,
  `application = 'genealogy-archive-system'`) marking the database as a Genealogy Archive System schema.
  Not the Archive ID. Flyway history table `gas.flyway_schema_history`.
- No historical/domain tables (asserted by `MigrationIT.phaseZeroCreatesNoHistoricalDomainTables`).

## 3. File/storage behaviour affected
No archival File storage exists yet (Phase 2). Phase 0 provides only the reference shape and rules:
persisted locations are Storage Volume UUID + portable relative path; native roots exist only in
installation configuration (`storageRoots`); overlapping roots are rejected. Nothing writes to Storage
Volumes except the temporary filesystem probe used in tests.

## 4. Tests implemented and run
106 tests per job (Fast suite + Phase-Gate suite), identical on all four jobs, plus scripted CI checks.

| PHASE-0 required test | Evidence |
|---|---|
| Starts against supported PostgreSQL on Windows and Linux | `WebApplicationIT`, `WorkerProcessIT`, `MigrationIT.serverMeetsSupportedBaseline`; smoke test; systemd and WinSW service tests |
| Migrations apply to a clean database on both | `MigrationIT.migrationsApplyToCleanDatabaseAndPassPostMigrationValidation`, `CliDatabaseIT` |
| Repeated migration reaches same schema | `MigrationIT.repeatedMigrationIsANoOpWithIdenticalSchema`, `independentCleanDatabasesReachTheSameSchema`; smoke test migrates twice |
| Config validation detects missing required installation configuration | `InstallationConfigValidationTest` (20 cases), `CliTest` |
| Basic health/readiness | `WebApplicationIT` (ready, unmigrated, unreachable database), smoke and service tests |
| No normal-runtime Internet/cloud dependency | `WebApplicationIT` (no non-loopback connection attempts), `ArchitectureTest.noOutboundNetworkClients`, `noProhibitedInfrastructure`, Enforcer bans |
| Path/config abstractions not POSIX- or Windows-only | `PortableRelativePathTest`, `VolumeRootsTest`, `InstallationConfigValidationTest.foreignHostPathStyleIsRejectedWithHint`, `HostFilesystemBehaviourTest` |
| Test isolation independent of production data | `TestDatabase` (throwaway `gas_test_*` databases only), `TestDatabaseGuardTest` |
| Documented install/test procedure works on both | CI runs `scripts/test.(sh\|ps1)`, `scripts/smoke-test.(sh\|ps1)`, `deploy/linux/install.sh`, `deploy/windows/install-services.ps1` |

Acceptance Specification items with **foundation coverage only** (not complete; their phases finish them):
UP-004/UP-005 (versioned migration, failed migration rolls back - `MigrationFailureIT`), UP-001/UP-002
(offline startup, browser client), UP-007 (installation vs archive configuration), UP-008/UP-009 (same
codebase on both hosts), UP-010 (portable storage references), SEC-010 (no secrets in diagnostic logs),
NF-001 (no prohibited infrastructure).

## 5. Test results
CI run 40632551836, commit 82072b1:

| Job | Tests | Failures | Errors | Skipped | Smoke | Service manager |
|---|---|---|---|---|---|---|
| ubuntu-24.04 / PostgreSQL 16 | 106 | 0 | 0 | 0 | passed | systemd passed |
| ubuntu-24.04 / PostgreSQL 18 (18.6) | 106 | 0 | 0 | 0 | passed | systemd passed |
| windows-2025 / PostgreSQL 16 | 106 | 0 | 0 | 0 | passed | WinSW passed |
| windows-2025 / PostgreSQL 18 | 106 | 0 | 0 | 0 | passed | WinSW passed |

Defects found and fixed during the CI loop (none by weakening a test): an ambiguous Flyway overload call
(compile error); a missing `::text` cast in the catalog query (caught locally before CI); readiness-group
configuration leaking into the worker process; a test assuming flat ECS log keys; a CI shell without
`pipefail` that had produced a false green on the first run (now guarded by zero-test and success-marker checks).

## 6. Known limitations
- **No authentication yet** (Phase 1). The web process binds to `127.0.0.1` by default; do not expose it
  to a LAN until Phase 1 authentication exists.
- Offline-operation evidence is a JVM proxy-selector recorder plus static rules, not a network-isolated run;
  raw sockets opened with an explicit no-proxy would bypass the recorder.
- The pre-upgrade checkpoint required by BUILD-SPEC s.56 is documented as a manual `pg_dump` + volume copy
  until the Complete Archival Backup exists (Phase 8). See question 1.
- Windows services run as LocalSystem by default; a dedicated account is documented but not automated.
- `setup-dev-db.ps1` is interactive and not exercised by CI (`setup-dev-db.sh` was verified manually on
  PostgreSQL 16).
- The Windows smoke test stops processes forcibly; graceful stop on Windows is verified by the WinSW test.
- CI on Windows installs PostgreSQL via Chocolatey (EDB builds, ~10 minutes per job); CI-only dependency.
- WinSW and the Maven distribution are downloaded without checksum pinning (`distributionSha256Sum` not set).
- Filesystem characterisation tests encode the CI hosts' default filesystems (NTFS, ext4).
- Real Windows/Linux server deployment testing is deferred to the deployment/acceptance activity (as agreed).

## 7. Unresolved specification questions
1. **Pre-upgrade checkpoint before consequential migrations (BUILD-SPEC s.56).** Until Phase 8, is a
   documented manual `pg_dump` + Storage Volume copy acceptable, or should Phase 1 add a minimal
   `pg_dump`-based checkpoint that `migrate` requires for migrations marked consequential?
2. **Windows service account policy.** Default LocalSystem (current) vs. a dedicated low-privilege account
   created by the installer. Affects permission handling for Storage Volumes from Phase 2.

## 8. Deviations from specification
- **Spring Boot 4.1 instead of the originally proposed 3.5** (3.5 OSS support ended 2026-06-30). Approved by
  the project owner. Not a deviation from the specifications, which name no framework.
- **Additions not explicitly listed in PHASE-0** (all technical, disclosed here): the `schema_identity`
  marker table; a `status` command; `storageRoots` in installation configuration keyed by Storage Volume
  UUID before the Storage Volume model exists (needed to prove the path abstraction).
- No deviations from archival invariants. No later-phase historical semantics were implemented.

## Subsequent audit corrections

This report above records the original implementation run, not acceptance of the corrected head.
See PHASE-0-CORRECTIONS.md. The two former owner questions are resolved: manual dump plus
appropriate Storage Volume copy is acceptable until Phase 8; LocalSystem is a Phase 0 dev/CI
limitation and low-privilege execution must be resolved before Phase 2. The previous no-checksum
limitation is corrected. Current test names and narrower guarantees are described in the correction notes.
