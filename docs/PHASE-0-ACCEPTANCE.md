# Phase 0 Acceptance Record

**Phase 0 (Foundation) of the Genealogy Archive System was formally ACCEPTED by the project owner on
2026-10-28.**

| Item | Value |
|---|---|
| Accepted baseline commit | `f326366172759d0b9e94c87db51434237834d550` |
| Merged to `main` by | `38e61b870f5792ff8480a514dca14f80eff9e795` (PR #1, merge commit; tree identical to the accepted commit: `f67bf140d75e595ae3b80da854f14e65c536b8d5`) |
| Verifying CI run | GitHub Actions run 40707199883 |
| Recovery tag | `phase-0-accepted` -> `f326366` (see "Recovering the baseline") |

## Verified at acceptance
- Independent review findings GAS-P0-001 through GAS-P0-007 resolved; GAS-P0-R01 (JDBC option
  spellings vs PgJDBC interpretation) resolved; no GAS-P0-001 regression; no remaining Phase 0 blocker.
- Native CI, all four combinations passing:

  | Job | Tests | Failures | Errors | Skipped | Smoke | Service manager |
  |---|---|---|---|---|---|---|
  | windows-2025 / PostgreSQL 16 | 171 | 0 | 0 | 0 | passed | WinSW passed |
  | windows-2025 / PostgreSQL 18 | 171 | 0 | 0 | 0 | passed | WinSW passed |
  | ubuntu-24.04 / PostgreSQL 16 | 171 | 0 | 0 | 0 | passed | systemd passed |
  | ubuntu-24.04 / PostgreSQL 18 | 171 | 0 | 0 | 0 | passed | systemd passed |

## Governing records
- `docs/PHASE-0-COMPLETION.md` - original completion report.
- `docs/PHASE-0-CORRECTIONS.md` - review findings and corrections (GAS-P0-001..007, GAS-P0-R01) and the
  mandatory Phase 2 containment/race invariant.
- `docs/PHASE-0-DECISIONS.md` - owner decisions D-0001 (manual pre-upgrade checkpoint until Phase 8) and
  D-0002 (LocalSystem is a Phase 0 limitation; approved low-privilege Windows service account required
  before Phase 2 storage is operational).

## Recovering the baseline
```
git checkout f326366172759d0b9e94c87db51434237834d550     # always works (in main's history)
git checkout phase-0-accepted                             # once the tag exists
```
Any later change to Phase 0 behaviour is a new change on top of this baseline, never a rewrite of it.

## Next
Phase 1 (Archive Identity and Core Infrastructure) has **not** been started. It begins only on the
owner's separate Phase 1 authorization and instructions.
