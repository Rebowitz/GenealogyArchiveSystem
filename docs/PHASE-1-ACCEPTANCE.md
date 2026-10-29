# Phase 1 Acceptance Record

**Phase 1 (Archive Identity / Core) of the Genealogy Archive System was formally ACCEPTED by the project
owner on 2026-10-28.**

| Item | Value |
|---|---|
| Accepted commit | `1dff0fa63672ac9af162d283816207cb0d78c357` (tree `8dd2fc9ff11dd6f331d622ce3e6ae22534c81bfc`) |
| Starting baseline | `phase-0-accepted` = `f326366172759d0b9e94c87db51434237834d550`; branched from `main` at `24679aa` |
| Merged to `main` by | `77d6ee02a2e12dd4ccb88be83fde6c64788eb748` (PR #3, regular merge commit; parents `24679aa` and `1dff0fa`; tree identical to the accepted commit) |
| Verifying CI run (accepted commit) | GitHub Actions run 40780560051 |
| Post-merge `main` CI run (merge commit) | GitHub Actions run 40791489878 |
| Recovery tag | `phase-1-accepted` -> `1dff0fa` (to be created by the owner; see "Recovering the baseline") |

## Verified at acceptance
- Independent review findings resolved: GAS-P1-001 (live job lease ownership), GAS-P1-002 (enforced
  transport-security boundary for password sign-in), GAS-P1-R01 (claim result atomically bound to its
  attempt). Evidence strengthening accepted: concurrent Archive initialization, Change History
  transaction coupling.
- Native CI on the accepted commit, all four combinations passing:

  | Job | Tests | Failures | Errors | Skipped | Smoke | Service manager |
  |---|---|---|---|---|---|---|
  | windows-2025 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | WinSW passed |
  | windows-2025 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | WinSW passed |
  | ubuntu-24.04 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | systemd passed |
  | ubuntu-24.04 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | systemd passed |

- Post-merge `main` CI on merge commit `77d6ee0` (run 40791489878), all four combinations passing:

  | Job | Tests | Failures | Errors | Skipped | Smoke | Service manager |
  |---|---|---|---|---|---|---|
  | windows-2025 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | WinSW passed |
  | windows-2025 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | WinSW passed |
  | ubuntu-24.04 / PostgreSQL 16 | 339 | 0 | 0 | 0 | passed | systemd passed |
  | ubuntu-24.04 / PostgreSQL 18 | 339 | 0 | 0 | 0 | passed | systemd passed |

  Run history: attempt 1 did not start (GitHub account billing block, since cleared). In attempt 2 the
  Linux jobs passed, but both Windows jobs failed while installing PostgreSQL, because the Chocolatey
  community feed returned HTTP 504/406 (no tests ran). Attempt 3 re-ran only the two Windows jobs, and
  both passed. No code or configuration changed between attempts.
- Schema: migrations V0002–V0008 added on top of the accepted Phase 0 V0001 (unchanged).
- Phase 2 functionality: none.

## Governing records
- `docs/PHASE-1.md` — Phase 1 brief (verbatim).
- `docs/PHASE-1-DECISIONS.md` — owner decisions P1-A to P1-E.
- `docs/PHASE-1-COMPLETION.md` — original completion report (describes reviewed head `70c3b95`).
- `docs/PHASE-1-CORRECTIONS.md` — GAS-P1-001, GAS-P1-002, GAS-P1-R01 and evidence-strengthening
  corrections, with tests and CI evidence.
- `docs/architecture/ADR-0006` (archive identity), `ADR-0007` (local authentication and transport
  boundary), `ADR-0008` (change history and background jobs).
- Standing Phase 0 decisions still apply (`docs/PHASE-0-DECISIONS.md`): D-0001 manual pre-upgrade
  checkpoint until Phase 8; D-0002 an approved low-privilege Windows service account is required before
  Phase 2 storage is operational.

## Recovering the baseline
```
git checkout 1dff0fa63672ac9af162d283816207cb0d78c357     # always works (in main's history)
git checkout phase-1-accepted                             # once the tag exists
```
Any later change to Phase 1 behaviour is a new change on top of this baseline, never a rewrite of it.

## Next
Phase 2 has **not** been started. It begins only on the owner's separate Phase 2 authorization and
instructions.
