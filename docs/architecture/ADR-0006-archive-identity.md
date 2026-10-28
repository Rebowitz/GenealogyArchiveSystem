# ADR-0006: Archive identity, global identifiers and the archive/installation boundary

Status: Accepted for Phase 1 implementation (owner decisions P1-B, P1-D). Phase 1.

## Context
Every Archive Instance needs a permanent identity that survives backup/restore and host moves, distinct from
the Phase 0 schema-identity marker, the application version and anything host-specific. Historical objects
in later phases need globally unique identifiers that can travel between independent instances, plus
short human-friendly identifiers (P00127) that are never reused.

## Decision
- **Archive ID** is a UUIDv7 stored in `gas.archive_instance` (one row, enforced by a singleton key).
  It is created only by the explicit `init-archive` command (SYSTEM actor "command line"); no migration
  and no browser action creates it. Triggers make `archive_id` and `created_at` immutable and the row
  undeletable/untruncatable, even to direct SQL. The archive name is Correctable and history-recorded.
- The Archive ID is **never derived** from hostname, path, database name, repository name, network address
  or configuration; it is random/time-ordered. It lives in the database, so `pg_dump`/restore preserves it.
  Copying an initialized database to found an unrelated archive is unsupported (it would duplicate the ID).
- Readiness requires exactly one archive row. More than one row is reported as inconsistent identity by
  status and by post-migration validation; nothing repairs it automatically.
- **Global object IDs** are UUIDv7 (`GlobalIds.newId()`, library `uuid-creator` 6.1.1), generated without
  coordination. Identity is the UUID, never a friendly ID, name, path or row position.
- **Friendly IDs** come from `gas.friendly_id_series` (object type, prefix, minimum digits, dedicated
  PostgreSQL sequence, `NO CYCLE`). Sequence numbers are never reused, including after rollback or
  deletion. Series rows are immutable. Phase 1 registers **no** series because no historical object types
  exist yet; tests use a test-only migration location.
- **Archive configuration** (`ArchiveConfiguration`: Archive ID, name, Storage Volume identities and
  descriptions, identifier series) is archive data in PostgreSQL. **Installation configuration**
  (`installation.yml`: database connection, bind address, log paths, volume root paths) stays host-local
  and is never stored in or exported with archive configuration. `ArchitectureTest` forbids the
  `archive` and `identity` packages from depending on host/platform/installation code.
- **Storage Volumes** have a permanent UUIDv7 identity, an open descriptive purpose code
  (`^[a-z][a-z0-9-]{0,63}$`), required/optional, and are retired rather than deleted. Their native root
  is an installation mapping (`storageRoots`). An unavailable required volume is a prominent problem but
  does not make the instance unready.

## Consequences
- A database upgraded from Phase 0 is visibly *not ready* until `init-archive` runs.
- Later phases attach Person/File/Record identities to `GlobalIds` and register friendly-ID series by
  migration.
