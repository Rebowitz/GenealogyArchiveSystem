# Phase 2 Acceptance Record and Operations Notes

**Phase 2 (File Preservation) of the Genealogy Archive System was formally ACCEPTED by the project owner on
2026-11-08**, following the independent Codex verdict **ACCEPT WITH NONBLOCKING FINDINGS** on the accepted
implementation below.

| Item | Value |
|---|---|
| Accepted implementation | `464d2d0ac971b020679fa5d408e9490f0b89f0ee` (tree `ef185fdacc331cdd2bfa771780727fe52974e2a7`) |
| Acceptance date | 2026-11-08 |
| Independent verdict | Codex: ACCEPT WITH NONBLOCKING FINDINGS |
| Starting baseline | `phase-1-accepted` = `1dff0fa63672ac9af162d283816207cb0d78c357`; branched from `main` at `26b8df8` (PR #4 merge) |
| Branch / pull request | `phase-2` / PR #5, merged into `main` with a regular merge commit (no squash, no rebase) on the owner's authorization |
| Verifying CI (pull-request run, accepted commit) | GitHub Actions run 42464866249 |
| Push run (accepted commit) | GitHub Actions run 42464862992 (documented exception below) |
| Later commits on the branch | this acceptance record only (documentation) |
| Recovery tag | none created; a tag is made only on the owner's separate authorization |

## Verified at acceptance

Pull-request run 42464866249 on the accepted commit, all four combinations passing:

| Job | Tests (unit + integration) | Failures | Errors | Skipped | Smoke | Service manager |
|---|---|---|---|---|---|---|
| windows-2025 / PostgreSQL 16 | 616 (308 + 308) | 0 | 0 | 0 | passed | WinSW passed |
| windows-2025 / PostgreSQL 18 | 616 (308 + 308) | 0 | 0 | 0 | passed | WinSW passed |
| ubuntu-24.04 / PostgreSQL 16 | 616 (308 + 308) | 0 | 0 | 0 | passed | systemd passed |
| ubuntu-24.04 / PostgreSQL 18 | 616 (308 + 308) | 0 | 0 | 0 | passed | systemd passed |

Each job included the migration-upgrade tests (`Phase2SchemaIT`, 7), the real worker JVM kill/restart test
(`WorkerProcessKillIT`, 1), the Phase 2 audit regressions (`Phase2AuditCorrectionsIT`, 129) and
`ObjectIdentityTest` (4). Migrations V0001–V0008 are unchanged from Phase 1; Phase 2 adds V0009–V0020.
`git merge-tree --write-tree origin/main 464d2d0` equals the accepted tree `ef185fd`.

### Nonblocking CI exceptions (accepted by the owner)

1. **WinSW HTTP 504 (push run 42464862992, windows-2025 / PostgreSQL 16).** On four attempts the native-service
   step failed before the service test ran, because downloading WinSW from `github.com` returned *504 Gateway
   Time-out*. In every attempt the job's 616 tests and its smoke test passed. The corresponding pull-request job
   (same commit) passed the WinSW service test. The other three push jobs passed in full. No code or
   configuration changed between attempts. Not an application defect; a cached WinSW copy in CI would remove the
   dependency (not done in Phase 2).
2. **Upload-timing assertion.** In the push run's first attempt, ubuntu-24.04 / PostgreSQL 16 failed
   `Phase2AuditCorrectionsIT.gasP2_001_aLiveReceiverBlockedLongerThanTheTimeoutKeepsItsUpload` once: it expected
   the upload `STAGED` but recovery, running concurrently in the test, had already begun `PROMOTING` it. This is
   a race in the test's own assertion (it reads the state after a concurrent recovery loop may have advanced the
   operation), not in the code under test; it passed on rerun and in the other seven jobs. The test is unchanged.

## Operational limitations

The limitations below are accepted for Phase 2. **They are designated for inclusion in the final Operations
Manual and the User Instructions** (neither document exists yet in this repository). Each item says which audience it
is for.

### V0020 object-identity evidence (Operations Manual; User Instructions for Administrators)
- Every unresolved-object record stores the evidence observed for the object: size, SHA-256, creation and
  modification times (to the microsecond) and the platform file key where there is one. Administrator
  attestations and releases re-examine the object and are bound to that evidence.
- This is **evidence, not proof** of physical identity. An object given the same bytes, the same times and (where
  one exists) the same file key cannot be told apart from the original.
- Matching is exact. A file key present on only one side counts as a difference.

### Windows file keys and NTFS timestamps (Operations Manual)
- Through Java, Windows/NTFS provides **no file key**. On Windows, identity rests on size, SHA-256 and the two
  timestamps.
- Ordinary Windows tools can set both creation and modification times.
- NTFS *file-system tunnelling* can give a file created or renamed into a just-deleted or just-renamed name the
  old file's creation time (within about 15 seconds). A replacement made that way, with its modification time also
  matching, cannot be distinguished.

### Quarantine-copy identification (Operations Manual)
- A quarantined copy is identified by its quarantine move's evidence: size, SHA-256 and the modification time
  recorded when the move was intended. A move keeps the modification time; creation time and file key may change
  on relocation, so they are not used.
- A same-byte object given the same modification time at a quarantine path cannot be told apart from the moved copy.

### LEGACY and UNCONFIRMED authorization (Operations Manual; User Instructions for Administrators)
- Records made before V0020 are **LEGACY**. A LEGACY attestation authorises no move or deletion. A LEGACY release
  identifies no object. Nothing is upgraded automatically.
- A release made when the object present was not exactly the recorded one is **UNCONFIRMED** and identifies no
  object later. An attestation is refused in that situation.
- In either case the object is left in place, recorded as unresolved, and needs a new Administrator decision
  about the object actually present.
- A copy moved into quarantine under a LEGACY attestation stays recorded as quarantined. Destroying it requires the
  Administrator to attest the quarantine copy itself.

### P2-I: Administrator attestation before quarantine (Operations Manual; User Instructions for Administrators)
- Approved owner policy: a damaged or unidentified copy is moved to quarantine **only after an Administrator
  attests** that it is the archive's object, and the attestation is bound to the object examined.
- Until then the copy stays in place, accountable as `UNRESOLVED`. Repair, recovery and destruction all follow
  this rule.

### P2-E: filesystem check-to-action race (Operations Manual)
- Every check before a move or deletion (volume identity, presence, identity evidence, claims) and the filesystem
  action itself are separate calls. Java offers no portable "act only if still this object".
- A local process that swaps an object in between them can still have the swapped object moved or deleted.
  Repeated checks narrow this window; they do not close it.
- Mitigation is operational: only the archive's service accounts may write to managed volumes (ADR-0010).

### CLEANUP_INCOMPLETE and Administrator resolution (Operations Manual; User Instructions for Administrators)
- A destruction stays **CLEANUP_INCOMPLETE** (never `BYTES_REMOVED`) while any location it accounts for:
  - holds an unresolved object;
  - cannot be examined (access denied, or a volume that is unavailable or not confirmed as the configured one);
  - or has a pending attested quarantine move that can no longer be completed.

  The File is already retired; only its bytes remain.
- The operation's events list each reason. The operation's message keeps the first reason, and later passes add
  events.
- **To resolve** (Preservation page, unresolved objects):
  - **Attest** the object if it is the archive's. This is possible only while it is exactly the recorded object.
    The pending destruction then removes it, or the quarantine move proceeds.
  - **Release** it if it is not the archive's. It is left in place untouched.
  - **Or remove it on the volume by hand**, after which the next check closes the record as absent.

  A pending attested move whose source object changed cannot be released while the move is recorded. Removing the
  object at the source by hand settles the move as source-absent.
- Then retry the destruction from Operations (recovery also retries it). Access or volume problems are resolved by
  restoring access or the correct volume and retrying.
- A released object is never deleted. If a different object later appears where it was, that object is recorded
  as unresolved and does not inherit the release.

## Governing records
- `docs/PHASE-2-DECISIONS.md`: owner decisions P2-A … P2-K, B1 … B5, the P2-I finalization decision and V0020.
- `docs/PHASE-2-COMPLETION.md`: original completion report.
- `docs/PHASE-2-AUDIT-CORRECTIONS.md`: every audit round, its reproduction, design, corrections and tests. It
  takes precedence over the completion report where they differ.
- `docs/architecture/ADR-0009-file-preservation.md` and `ADR-0010-service-identities.md`.
- Standing Phase 0 decisions still apply (`docs/PHASE-0-DECISIONS.md`).

## Recovering the baseline
```
git checkout 464d2d0ac971b020679fa5d408e9490f0b89f0ee     # accepted implementation (in main's history after the merge)
```
Any later change to Phase 2 behaviour is a new change on top of this baseline, never a rewrite of it.

## Next
Phase 3 has **not** been started. It begins only on the owner's separate authorization.
