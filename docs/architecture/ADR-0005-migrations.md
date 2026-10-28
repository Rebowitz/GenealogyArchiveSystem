# ADR-0005: Database migrations

- **Status:** Accepted (Phase 0)
- **Governs:** BUILD-SPEC s.56, s.65; ACCEPTANCE UP-004, UP-005, UP-006

## Decision
- **Flyway**, plain SQL files `src/main/resources/db/migration/V####__description.sql`, applied strictly
  in version order (no out-of-order, no baseline-on-migrate, `clean` disabled).
- All application objects live in PostgreSQL schema **`gas`**; history table `gas.flyway_schema_history`.
- **Explicit command only.** `genealogy-archive migrate` applies migrations; the web application and worker
  never migrate on startup. Until the schema matches the build, the web application reports not-ready and
  the worker refuses to start.
- **Each migration is its own PostgreSQL transaction.** A failing migration leaves the schema exactly at the
  previous version (`MigrationFailureIT`).
- **Checksums are validated.** An edited, missing, failed or unknown ("future") migration produces a
  non-CURRENT `SchemaStatus` (INVALID / FAILED / AHEAD) and `migrate` refuses to proceed. An older build
  never runs against a database upgraded by a newer build.
- **Post-migration validation** (`PostMigrationValidator`) runs after every `migrate`; failures exit with code 4.
  Each later phase adds read-only checks for its own invariants.
- Flyway takes a PostgreSQL advisory lock, so concurrent `migrate` invocations serialise.

## Pre-upgrade recovery
BUILD-SPEC s.56 requires a recoverable checkpoint before consequential or destructive migrations, and allows
recovery by restoring that backup instead of reverse migrations. Phase 0 migrations are additive and
technical. Until the Complete Archival Backup exists (Phase 8), the documented checkpoint for a
consequential migration is a PostgreSQL `pg_dump -Fc` of the archive database plus a copy of the Storage
Volumes, taken by the administrator before running `migrate`. A later phase should make `migrate` require
or create that checkpoint automatically for migrations marked consequential.

## Rules for future migrations
1. Never edit an applied migration; add a new one.
2. Never silently reinterpret historical data (UP-006). A migration that changes the representation of
   historical information must preserve meaning exactly and add post-migration checks proving it.
3. Do not encode historically false constraints (BUILD-SPEC s.65).
4. Keep within the PostgreSQL 16 feature set (ADR-0003).
