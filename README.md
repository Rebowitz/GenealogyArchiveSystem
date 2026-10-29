# Genealogy Archive System

A lightweight, durable archive for genealogical research and the historical evidence that supports it.
One application codebase runs as independent Archive Instances on **Windows** and **Linux** hosts, with
**PostgreSQL** as the authoritative database and an ordinary **web browser** as the client.

> **Status: Phase 1 - Archive Identity / Core, ACCEPTED 2026-10-28 at `1dff0fa` (see [`docs/PHASE-1-ACCEPTANCE.md`](docs/PHASE-1-ACCEPTANCE.md)).** Phase 0 (Foundation) was
> accepted 2026-10-28 at `f326366` ([`docs/PHASE-0-ACCEPTANCE.md`](docs/PHASE-0-ACCEPTANCE.md)). Phase 1 adds
> the permanent Archive ID, global and friendly identifier infrastructure, archive configuration, change
> history, local accounts with sign-in, Storage Volume registration and the background-job foundation.
> **No genealogy or historical research features exist yet**; they arrive in later phases (see
> [`docs/BUILD-SPEC.md`](docs/BUILD-SPEC.md) s.62).

Governing documents, in authority order: archival invariants -> [`docs/ACCEPTANCE-SPEC.md`](docs/ACCEPTANCE-SPEC.md)
-> [`docs/BUILD-SPEC.md`](docs/BUILD-SPEC.md) -> current phase brief [`docs/PHASE-1.md`](docs/PHASE-1.md)
(owner decisions: [`docs/PHASE-1-DECISIONS.md`](docs/PHASE-1-DECISIONS.md)).
Agent/developer operating rules: [`CLAUDE.md`](CLAUDE.md). Architecture and decisions: [`docs/architecture/`](docs/architecture/).

---

## Contents
1. [Requirements](#1-requirements)
2. [Get the source](#2-get-the-source)
3. [Set up PostgreSQL](#3-set-up-postgresql)
4. [Create an installation configuration](#4-create-an-installation-configuration)
5. [Build, migrate and run](#5-build-migrate-and-run)
6. [Running the tests](#6-running-the-tests)
7. [Installing as a service](#7-installing-as-a-service)
8. [Command reference](#8-command-reference)
9. [Repository layout](#9-repository-layout)
10. [Continuing development](#10-continuing-development)

---

## 1. Requirements

| | Version | Notes |
|---|---|---|
| Java | **21** (LTS) or newer | Eclipse Temurin recommended. `java -version` must report 21+. |
| PostgreSQL | **16** or newer | 16 is the minimum supported baseline; CI also tests 18. See ADR-0003. |
| Maven | none to install | The Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9.11 on first use. |
| Docker | **not required** | Never needed to build, test or run. |
| Internet | build time only | Needed to download dependencies when building. Not needed at runtime. |

Both supported hosts are first-class. Everything below is given for **Windows (PowerShell)** and **Linux (bash)**.

## 2. Get the source

```
git clone https://github.com/Rebowitz/GenealogyArchiveSystem.git genealogy-archive
cd genealogy-archive
```

Keep the source checkout separate from archive Storage Volumes. Its location has no meaning to the application.

## 3. Set up PostgreSQL

Install PostgreSQL 16+ (Windows: the EDB installer from postgresql.org; Linux: your distribution's package
or the PGDG repository). Then create a role and database for the archive.

**Linux**
```bash
sudo -u postgres scripts/setup-dev-db.sh            # prompts for the new role's password
```

**Windows**
```powershell
.\scripts\setup-dev-db.ps1 -PsqlPath 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
```

Both create role `genealogy_archive` and database `genealogy_archive` (override with `GAS_DEV_ROLE` /
`GAS_DEV_DB` on Linux, `-Role` / `-Database` on Windows).

## 4. Create an installation configuration

The **installation configuration** is a YAML file holding everything specific to *this host*: database
connection, HTTP binding, log location, and the native root folder of each Storage Volume. It is never
part of the archive. (Archive configuration - Archive ID, vocabularies, classifications - lives in the
database and travels with the archive; see ADR-0006.)

```
cp config/installation.example.yml installation.yml          # Linux
Copy-Item config\installation.example.yml installation.yml   # Windows
```

Edit it. Rules (all enforced by `check-config`):
- **No secrets in the file.** The database password comes from the `GAS_DB_PASSWORD` environment variable
  (or another variable named by `database.passwordEnv`), or from a protected one-line file named by
  `database.passwordFile`. A `password:` key is rejected.
- **Unknown keys are errors**, so a typo never silently falls back to a default.
- **Paths are absolute for this host.** Windows: `'D:\GenealogyArchive\primary'` (single quotes keep
  backslashes literal). Linux: `/srv/genealogy-archive/primary`. A path in the other host's style is
  rejected with an explanation.
- `server.bindAddress` defaults to `127.0.0.1` (this computer only). Password sign-in over plain HTTP on a
  network is **refused**: with any non-loopback address (including `0.0.0.0` or `::`) `check-config`
  and `serve` fail, unless a TLS reverse proxy is declared. Built-in TLS arrives in a later phase. To
  reach the archive from other computers now, put a TLS reverse proxy in front and declare its exact IP
  address(es); the archive then accepts sign-in only through that proxy over HTTPS (ADR-0007):
  ```yaml
  server:
    bindAddress: 127.0.0.1        # proxy on this computer; use a LAN address only if the proxy is elsewhere
    tlsReverseProxy:
      addresses: ['127.0.0.1']    # exact IP literals; the proxy must send X-Forwarded-Proto: https
  ```

`installation.yml` is git-ignored. Validate it:

**Linux**
```bash
export GAS_DB_PASSWORD='...'
scripts/run.sh check-config --config=installation.yml
```
**Windows**
```powershell
$env:GAS_DB_PASSWORD = '...'
.\scripts\run.ps1 check-config --config=installation.yml
```

## 5. Build, migrate and run

`scripts/run.*` builds the jar when sources changed, then runs a command.

| Step | Linux | Windows |
|---|---|---|
| Build only | `./mvnw package -DskipTests` | `.\mvnw.cmd package -DskipTests` |
| Apply migrations | `scripts/run.sh migrate --config=installation.yml` | `.\scripts\run.ps1 migrate --config=installation.yml` |
| Check status | `scripts/run.sh status --config=installation.yml` | `.\scripts\run.ps1 status --config=installation.yml` |
| Run web application | `scripts/run.sh serve --config=installation.yml` | `.\scripts\run.ps1 serve --config=installation.yml` |
| Run worker | `scripts/run.sh worker --config=installation.yml` | `.\scripts\run.ps1 worker --config=installation.yml` |

### First-time setup of an archive

After the first `migrate`, create the archive and its first administrator account (once per archive):

| Step | Linux | Windows |
|---|---|---|
| Create the archive (assigns the permanent Archive ID) | `scripts/run.sh init-archive --name="Smith Family Archive" --config=installation.yml` | `.\scripts\run.ps1 init-archive '--name=Smith Family Archive' --config=installation.yml` |
| Create the first administrator | `scripts/run.sh create-admin --username=alice --config=installation.yml` | `.\scripts\run.ps1 create-admin --username=alice --config=installation.yml` |

`create-admin` prompts for the password (twice), or reads it from `GAS_NEW_ADMIN_PASSWORD` for
unattended setup; it is never accepted as a command-line argument. Passwords need at least 12 characters.
Further accounts (Administrator, Editor, Viewer) are created by an administrator in the browser.

- **The Archive ID is permanent.** It is stored in the database, so restoring a backup of this archive keeps
  it. **Copying an initialized database to start a different, unrelated archive is unsupported**: both
  copies would carry the same Archive ID. Start a new archive from an empty database instead.
- `init-archive` refuses to run twice, and no migration ever creates an archive.

Then open `http://127.0.0.1:8080/` (or your configured address/port) and sign in.

- **Migrations never run automatically.** Until `migrate` has been run, the web application starts but
  reports itself *not ready* (home page, and HTTP 503 from `/actuator/health/readiness`), and the worker
  refuses to start. This is deliberate: an upgrade is an explicit administrative act (ADR-0005).
- Health endpoints: `/actuator/health/liveness` (process alive) and `/actuator/health/readiness`
  (database reachable, supported version, schema current, archive initialized).
- A database migrated from Phase 0 (or freshly migrated) reports *not ready - archive not initialized*
  until `init-archive` has run; the worker refuses to start until then.
- An unavailable *required* Storage Volume is shown prominently on the Operations page and in `status`,
  but does not make the instance unready (an offline backup volume is normal).

## 6. Running the tests

Two suites (BUILD-SPEC s.63):

| Suite | What | Linux | Windows |
|---|---|---|---|
| Fast Development | Unit + architecture tests, no database | `./mvnw test` | `.\mvnw.cmd test` |
| Phase-Gate | Fast suite + PostgreSQL integration tests | `scripts/test.sh` | `.\scripts\test.ps1` |

The Phase-Gate suite needs a PostgreSQL server on which it may **create and drop throwaway databases**
named `gas_test_<random>`. It never touches any other database and never reads your installation file.
Point it at the server with test-only variables:

**Linux**
```bash
export GAS_TEST_DB_URL=jdbc:postgresql://localhost:5432/postgres
export GAS_TEST_DB_USER=genealogy_archive         # needs CREATEDB: ALTER ROLE genealogy_archive CREATEDB
export GAS_TEST_DB_PASSWORD='...'
scripts/test.sh
```
**Windows**
```powershell
$env:GAS_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/postgres'
$env:GAS_TEST_DB_USER = 'genealogy_archive'
$env:GAS_TEST_DB_PASSWORD = '...'
.\scripts\test.ps1
```

If these variables are missing, the integration tests **fail** with an explanation; they are never skipped.

`scripts/smoke-test.sh` / `scripts/smoke-test.ps1` exercise the packaged jar end to end (check-config,
status, migrate twice, serve, worker, log checks) against an empty database named by `GAS_SMOKE_DB_URL`.

**Continuous integration** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the same
procedures on native `windows-2025` and `ubuntu-24.04` runners against PostgreSQL 16 and 18: Phase-Gate
suite, packaged smoke test, and a real service-manager test (systemd units on Linux, WinSW Windows
services on Windows).

## 7. Installing as a service

The same jar runs two long-lived processes: `serve` (web) and `worker` (background work).

**Linux (systemd)** - run as root:
```bash
./mvnw package -DskipTests
sudo deploy/linux/install.sh target/genealogy-archive.jar "$(command -v java)"
```
Creates the `genealogy-archive` system account, `/opt/genealogy-archive`, `/etc/genealogy-archive`
(0750, root:genealogy-archive), `/var/log/genealogy-archive`, and the units
`genealogy-archive-web.service` / `genealogy-archive-worker.service`. Follow the printed next steps:
edit `/etc/genealogy-archive/installation.yml`, create the password file, run `migrate` as the service
account, then (first install only) `init-archive` and `create-admin` as the service account, then
`systemctl enable --now genealogy-archive-web genealogy-archive-worker`.
Remove with `sudo deploy/linux/uninstall.sh` (configuration, logs, database and volumes are left untouched).

**Windows (Windows services via WinSW)** - elevated PowerShell:
1. Download `WinSW-x64.exe` **v2.12.0** from https://github.com/winsw/winsw/releases (one-time, at install).
2. Install:
   ```powershell
   .\mvnw.cmd package -DskipTests
   .\deploy\windows\install-services.ps1 -JarPath .\target\genealogy-archive.jar -WinSWPath C:\Path\To\WinSW-x64.exe
   ```
   Creates `C:\Program Files\GenealogyArchive` (jar + service wrappers), `C:\ProgramData\GenealogyArchive`
   (`installation.yml`, `logs\`, `secrets\` restricted to SYSTEM and Administrators) and the services
   `GenealogyArchiveWeb` / `GenealogyArchiveWorker` (automatic, delayed start, restart on failure).
3. Follow the printed next steps (edit config, put the password in `secrets\db-password`, `migrate`,
   and on first install `init-archive` and `create-admin`),
   then `Start-Service GenealogyArchiveWeb, GenealogyArchiveWorker`.
Remove with `.\deploy\windows\uninstall-services.ps1`.

Services run as LocalSystem by default on Windows; to use a dedicated account, add a `<serviceaccount>`
element to the WinSW XML (see WinSW documentation) and grant it read access to `installation.yml` and
`secrets\`.

**Logs.** Each process logs to its console (captured by journald on Linux, by WinSW's
`logs\service` files on Windows) and, when `logging.directory` is set, to rolling files
`genealogy-archive-web.log` / `genealogy-archive-worker.log` (20 MB, 30 files). Default format is one
JSON object per line. Diagnostic logs are operational material, never historical evidence, and never
contain the database password.

## 8. Command reference

```
java -jar genealogy-archive.jar <command> [--config=<installation.yml>]
```

| Command | Purpose | Exit codes |
|---|---|---|
| `serve` | Web application (browser entry point) | runs until stopped |
| `worker` | Background worker (refuses to start unless the archive is ready) | runs until stopped; 1 if not ready |
| `migrate` | Apply pending migrations, then post-migration validation | 0 ok, 3 database problem, 4 migration failed/refused |
| `init-archive --name=<name>` | Create the archive and its permanent Archive ID (once) | 0 ok, 3 schema not current, 5 already initialized, 64 no name |
| `create-admin --username=<u> [--display-name=<d>]` | Create an administrator account (password from prompt or `GAS_NEW_ADMIN_PASSWORD`) | 0 ok, 3 archive not ready, 5 refused (e.g. weak password, name taken), 64 no password source |
| `status` | Database, schema, archive, worker, job and Storage Volume status | 0 ready, 3 not ready |
| `check-config` | Validate installation configuration (password shown masked) | 0 valid, 2 invalid |
| `version`, `help` | | 0 |

`--config` may be replaced by the `GAS_CONFIG` environment variable. Exit code 64 = usage error;
5 = refused by an archive rule.

## 9. Repository layout

```
pom.xml                         Single Maven module (modular monolith); versions pinned via Spring Boot BOM
mvnw, mvnw.cmd, .mvn/           Maven Wrapper (generated by CI workflow "Update Maven Wrapper")
config/installation.example.yml Example installation configuration (no secrets)
src/main/java/org/genealogyarchive/
  GenealogyArchive.java         Executable entry point
  cli/        Command-line front end (commands, exit codes)
  boot/       Process assembly: web and worker Spring applications, explicit core wiring
  config/     Installation configuration: strict loading, validation, secret handling
  platform/   The only OS-aware code: host detection, native path rules, filesystem probes
  files/      Portable storage references: PortableRelativePath, StorageLocation, VolumeRoots
  data/       All SQL/JDBC: connection pool, repositories, catalog inspection
  migration/  Flyway migration runner, schema status, post-migration validation
  service/    Application services (transactions and orchestration live here), system/operational status
  identity/   UUIDv7 global identifiers, friendly identifier format (plain Java)
  access/     Roles, permissions, Actor, AccessPolicy (plain Java)
  archive/    Archive ID, archive name, archive configuration (ArchiveService)
  security/   Local accounts, password policy/hashing (UserService, Spring Security adapters)
  history/    Field classes and append-only change history (ChangeRecorder, HistoryService)
  storage/    Storage Volume registration and availability (no file writes in Phase 1)
  jobs/       PostgreSQL-backed background job queue and runner
  domain/     Historical domain model - intentionally empty until Phase 2
  web/        Browser controllers + Thymeleaf templates (talk to services only)
  ops/        Health/readiness contributors
  worker/     Worker process lifecycle
src/main/resources/db/migration/ Versioned SQL migrations V####__description.sql
src/test/...                     *Test = Fast suite, *IT = PostgreSQL integration suite
scripts/                         setup-dev-db, run, test, smoke-test (.sh and .ps1); ci/ = CI-only helpers
deploy/linux/, deploy/windows/   systemd units / WinSW service definitions and install scripts
docs/                            Governing specifications and architecture/ADRs
```

## 10. Continuing development

Read [`docs/architecture/overview.md`](docs/architecture/overview.md) first, then the ADRs. Key conventions:
- **Implement only the approved phase.** Phase 1 (Archive Identity / Core) is accepted; Phase 2 has not
  been started and begins only on the owner's separate authorization.
- **Every service operation takes an explicit `Actor`** and checks `AccessPolicy`; record changes with
  `ChangeRecorder` in the same transaction; use `row_version` optimistic concurrency for mutable rows.
- **New schema = new migration file** `V000N__description.sql`; never edit an applied migration
  (checksums are validated). Add post-migration checks to `PostMigrationValidator`.
- **SQL lives in `data/`** (enforced by `ArchitectureTest`). Short statements as text blocks in the
  repository class; longer ones may go under `src/main/resources/sql/<area>/`.
- **Persist file locations only as `StorageLocation`** (Storage Volume ID + `PortableRelativePath`).
  Never persist a native path. Never build paths with string separators.
- **Do not branch on operating system** outside `platform/`.
- Name tests after Acceptance Specification IDs where practical; never weaken a test to make code pass.

## Phase 0 audit corrections

See [correction notes](docs/PHASE-0-CORRECTIONS.md) for the URL option/secret boundary,
configuration-time lexical and physical-snapshot checks, Phase 2 containment invariant,
WinSW XML rendering/integrity verification, runtime identity readiness, and CI evidence limits.
Phase-Gate scripts now run clean verify and require both report suites.
WinSW 2.12.0 x64 is digest-verified by the installer before execution.
Phase 0 was accepted on 2026-10-28 at `f326366` (tag `phase-0-accepted`).
