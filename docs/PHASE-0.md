# Phase 0 - Foundation
## Approved First Coding Assignment - Cross-Platform v1.1

### Objective
Establish a clean, maintainable, portable foundation for the Genealogy Archive System. Do not implement historical domain features yet.

The same codebase shall support independent Archive Instances hosted on Windows and Linux.

### Governing documents
Read in this order:
1. `../CLAUDE.md`
2. `BUILD-SPEC.md`
3. `ACCEPTANCE-SPEC.md`

### Required Phase 0 deliverables
Create or establish:
- source-controlled repository structure;
- modular-monolith application skeleton;
- browser/web application entry point;
- clean application/domain/service/data/file-service boundaries;
- PostgreSQL connectivity and configuration on Windows and Linux;
- sequential versioned database migrations;
- automated unit and integration testing;
- Windows and Linux development/deployment baselines from one codebase;
- platform adapter/integration boundary for service management, filesystem/path behavior, permissions and equivalent OS concerns;
- service-process definitions or documented service installation for the web application and future worker on both platforms;
- archive configuration separated from installation configuration;
- structured application logging compatible with both host platforms;
- secrets handling that keeps secrets out of source control;
- health/readiness checks;
- repeatable setup, migration, test and local-run commands;
- dependency/version management;
- technical documentation sufficient for another agent/developer to continue Phase 1.

### Cross-platform rules
- Do not fork historical/domain code by operating system.
- Do not persist Windows drive letters or POSIX mount roots as archival identity.
- Design persisted File storage references around Storage Volume identity plus portable relative path.
- Use path-handling libraries rather than string-concatenated separators.
- Isolate service-manager differences (Windows service vs systemd).
- Account for filesystem differences such as path separators, filename case behavior, locking and rename/copy semantics.
- Do not assume rename is atomic across filesystems.
- Do not require Docker on either platform.
- Do not require Internet/cloud services for normal runtime.
- Do not implement live synchronization or cross-site database replication.

### Technology-selection rule
The specification does not mandate a web language/framework. Before committing to a stack, inspect the repository. If no stack exists, choose a mature, actively maintained, widely deployed stack that:
- is well supported on both Windows and Linux;
- has first-class PostgreSQL support;
- supports straightforward browser UI development;
- supports migrations, testing, secure sessions and background-worker development without prohibited infrastructure;
- provides mature cross-platform filesystem/path APIs;
- can run as ordinary host services on Windows and Linux;
- remains understandable as a modular monolith;
- operates normally without Internet/cloud services.

Record the choice and rationale in an ADR. Do not introduce multiple competing frameworks.

### Phase 0 data scope
Phase 0 may create only technical data necessary to prove the foundation. Do not prematurely implement Person, Place, Property, Record, Evidence, DNA, Event, Source, File-ingestion, Transfer Package or other historical domain tables. Do not invent generic EAV.

### Required repository documentation
At minimum:
- `README.md` with Windows and Linux setup/run/test/migrate instructions;
- `docs/architecture/` with an ADR for stack choice and cross-platform approach;
- example configuration containing no real secrets;
- repeatable developer commands/scripts.

### Tests required in Phase 0
Automated tests shall prove, as applicable:
- application starts against the supported PostgreSQL version on Windows and Linux;
- migrations apply successfully to a clean database on both;
- repeated migration execution reaches the same expected schema state;
- configuration validation detects missing required installation configuration;
- basic health/readiness works;
- no normal runtime Internet/cloud dependency is required;
- path/configuration abstractions do not require POSIX-only or Windows-only absolute paths;
- test isolation does not depend on production data;
- documented installation/test procedure works on both supported platforms.

Use CI or equivalent repeatable environments for both Windows and Linux where practical. Do not create fake passing tests for later-phase features.

### Explicitly out of scope
Do not implement historical domain UI, genealogy, archival File ingestion, hashing/deduplication, OCR, image manipulation, search, Candidate Resolution, evidence architecture, backup/restore implementation, Complete Archive Export, Transfer Packages, DNA, AI, live synchronization, database replication, or cloud integration.

### Completion gate
Phase 0 is complete only when:
- a fresh checkout can be configured on Windows and Linux using documented steps;
- PostgreSQL can be initialized and migrations applied on both;
- the application starts successfully on both;
- automated foundation tests pass on both;
- the architecture remains within the v1 boundary;
- no later-phase historical semantics have been invented;
- the required completion report is supplied.

### First response before coding
Before substantial code changes, briefly state:
1. proposed implementation stack;
2. why it fits Windows, Linux and the governing specification;
3. initial repository/module layout;
4. how platform-specific integration will be isolated;
5. any genuine design question that must be resolved before coding.

Do not ask for approval of routine details already governed by the specifications.
