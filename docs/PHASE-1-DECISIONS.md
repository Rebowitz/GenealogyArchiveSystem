# Phase 1 owner decisions

Recorded from the project owner's response (2026-10-28) to the Phase 1 pre-coding report. The brief itself
is [`PHASE-1.md`](PHASE-1.md). These decisions bind Phase 1; later phases may revisit them only with owner
approval.

| ID | Question | Decision |
|---|---|---|
| P1-A | Include a minimal Storage Volume model, the PostgreSQL-backed background-job foundation and optimistic concurrency in Phase 1? | **Approved.** No archival file writes and no production job kinds in Phase 1. |
| P1-B | How is an Archive (and its Archive ID) created? | **Approved: explicit `init-archive` command**, never a migration side effect. A database upgraded from Phase 0 is not ready until initialized. Restoring a backup of the same archive preserves its Archive ID. Copying an initialized database to start an unrelated archive is **unsupported** and must be documented as such. |
| P1-C | Network exposure of password sign-in. | **Approved: keep the loopback-only default.** No ordinary LAN password login over unprotected HTTP. Built-in TLS is a later phase; an administrator may place a TLS reverse proxy in front independently. |
| P1-D | Effect of an unavailable Storage Volume; volume purpose vocabulary. | **Approved with refinement.** An unavailable *required* volume is reported prominently but does not make the instance unready; an offline backup volume is normal. Purpose is **not** a closed PRIMARY/BACKUP/EXCHANGE vocabulary: keep durable Volume identity, a descriptive open purpose code, required/optional, and an installation-local root mapping. |
| P1-E | Relationship of the background `job` table to Archival Operation History. | **Constraint:** the background-job table is not, and must not be treated as, the complete future Archival Operation History. Keep the concepts separate. |

Standing decisions carried from Phase 0 ([`PHASE-0-DECISIONS.md`](PHASE-0-DECISIONS.md)) still apply:
D-0001 (manual `pg_dump` + volume copy as the pre-upgrade checkpoint until Phase 8) and D-0002 (LocalSystem
is a Phase 0 dev/CI limitation; a low-privilege Windows service account is required before Phase 2 storage
is operational).
