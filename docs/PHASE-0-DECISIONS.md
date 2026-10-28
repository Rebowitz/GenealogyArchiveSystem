# Phase 0 - Owner Decisions Recorded After Completion

Documentation-only follow-up. It does **not** modify the frozen Phase 0 review baseline.

- **Review baseline:** commit `dee3c3ee7cad93346c0a721410abf0c2ce142b83` (branch `phase-0`, PR #1),
  verified by CI run 40634285066. The baseline is frozen; changes to it require approval as corrections
  arising from the independent Phase 0 review.
- **Status:** PR #1 is not to be merged and Phase 1 is not to begin until the independent review is
  complete and confirmed findings have been evaluated.

These decisions answer the open questions in `docs/PHASE-0-COMPLETION.md` section 7. Where they differ in
emphasis from that report or from ADR-0002 / ADR-0005, this file governs.

## D-0001 - Pre-upgrade checkpoint until Phase 8 (answers question 1)
- The documented manual checkpoint - a PostgreSQL `pg_dump -Fc` of the archive database plus a copy of the
  Storage Volumes, taken by the administrator before running `migrate` - is **accepted as the temporary
  pre-upgrade checkpoint mechanism** until the Complete Archival Backup/Restore work in Phase 8.
- **Do not** pull a partial backup subsystem into Phase 1 merely to automate this.
- **The requirement is preserved:** consequential or destructive migrations still require an appropriate
  recoverable checkpoint before they are applied (BUILD-SPEC s.56). Any phase that introduces such a
  migration must state that it is consequential and document the checkpoint step in its upgrade notes.
- Affects: ADR-0005 "Pre-upgrade recovery" (the "later phase should make migrate require or create that
  checkpoint" note is deferred to Phase 8, not Phase 1).

## D-0002 - Windows service account (answers question 2)
- **LocalSystem is accepted only as a Phase 0 development/CI limitation.** It is **not** approved as the
  eventual production default for Windows.
- **Gate for Phase 2:** before archival File storage becomes operational in Phase 2, Windows deployment must
  have an **approved low-privilege service-account strategy**, including appropriate PostgreSQL access and
  Storage Volume (NTFS) permissions. That strategy needs owner approval before Phase 2 file storage is
  considered complete.
- No implementation change now, unless one is required to correct a Phase 0 defect identified during review.
- Affects: ADR-0002 service-management table ("LocalSystem by default"), README section 7, and
  `deploy/windows/install-services.ps1`, all of which describe the Phase 0 state only.

## Carry-forward checklist
- [ ] Phase 2 plan includes the Windows low-privilege service-account strategy (D-0002) for approval.
- [ ] Any consequential migration before Phase 8 documents the manual checkpoint (D-0001).
- [ ] Phase 8 replaces the manual checkpoint with the Complete Archival Backup mechanism (D-0001).
