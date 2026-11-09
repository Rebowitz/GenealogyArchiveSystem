# Phase 3 decisions and technical clarifications (WP0)

**Status:** APPROVED by owner 2026-11-08. Owner explicitly authorized Phase 3 implementation, subject to the safeguards below. Phase 3 merge, formal acceptance, and acceptance tag remain prohibited without separate owner authorization.

The owner decisions are those of plan §9 (approved 2026-11-08). They are restated here only so the ADR can cite
them; if this table and plan §9 ever differ, plan §9 governs.

| ID | Decision (plan §9) |
|---|---|
| D3-01 | A Record Role is a configurable role code, the party **as written** and a note. Person or unresolved-Mention links come in Phase 4. A Role is not a Person Relationship or Event Role. |
| D3-02 | Browser grouping worksheet on the Capture Session: Start New Record and explicit `record_group`; the explicit group wins. No CSV import (Phase 7). |
| D3-03 | **Deferred:** relationship-aware File consolidation. P2-G, F-006 and F-007 stay outstanding. |
| D3-04 | NORMAL/RESTRICTED Records; restricted Records are Administrator-only. Checks apply on every Record, Citation, link, thumbnail, count, history and guessed-ID path. A NORMAL Record never discloses its RESTRICTED Files. No automatic propagation. Full policy management is Phase 9. |
| D3-05 | Record date and filing date are stored as written, in separate fields. The capture date is a calendar date. Structured interpretation (Phase 4) never overwrites the expression. |
| D3-06 | Historical creator, Access Provider and jurisdiction as written are separate Source text fields, distinct from Repository custody. No Organization, Jurisdiction or Structure objects yet. |
| D3-07 | Prefixes R, RP, S, CS, CT, subject to the compatibility check below. |
| D3-08 | Seeds: classifications `DEED`, `DTRUST`; roles grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor. Administrators add, relabel and retire; codes are never deleted or reused. |
| D3-09 | No automatic managed-filename changes. Classification never renames or moves originals. |
| D3-10 | Separate, clearly labelled **verbatim transcription** and **researcher-written abstract** fields. Neither is OCR or a Source Statement. Original text and attribution are preserved. |
| D3-11 | **Deferred:** bulk reclassification (Phase 7 or later). Individual classification correction and R-006 only. |
| D3-12 | ACTIVE/DELETED (recoverable) only. No purge or merge. IDs are never reused. Deletion preserves history and associations. Restore and visibility semantics are defined in ADR-0011 §7. |

## D3-07 compatibility check — result: compatible

Checked against the existing allocator and naming rules before any migration:

| Rule (source) | Proposed prefixes |
|---|---|
| Prefix is 1–4 upper-case letters (`FriendlyId.PREFIX`; V0007 `CHECK (prefix ~ '^[A-Z]{1,4}$')`) | R, RP, S, CS, CT all conform. |
| Prefix unique per series (V0007 `prefix ... UNIQUE`) | Only `F` (V0011) is registered today; no collision. |
| Display/parse form `([A-Z]{1,4})([0-9]{1,19})` (`FriendlyId.parse`) | Letters and digits never overlap, so parsing is unambiguous: `RP0001` is prefix `RP`, never `R`; `CS0001` is never `S`. |
| BUILD-SPEC §8 examples `P00127`, `F03711`, `R01842` | `R` with 5 digits matches the spec's own Record example. `P` stays free for Persons (Phase 4). |
| Never reused (BUILD-SPEC §8) | Each series gets its own `NO CYCLE` sequence, like `F`. |

No conflict was found, so the prefixes are used as approved. A unit test will pin the parse cases above
(`RP0001`→RP, `R00001`→R, `CS0001`→CS, `CT00001`→CT, `S00001`→S). Phase 3 also applies the history filter's
existing File-ID tokenization (`FileIdentifiers.referencesIn`) to `R`: that test also confirms that the filter
never reads `RP…` as an `R` reference.

## Technical clarifications (plan §10)

Plan §10 requires these to be defined before coding. They are specified in full in ADR-0011:

| §10 item | ADR-0011 |
|---|---|
| 10.1 Citation identity and provenance | §6 |
| 10.2 Capture Session override tri-state | §3 |
| 10.3 Restriction traversal | §8 |
| 10.4 Grouping idempotency and restart | §4 |
| 10.5 Record lifecycle | §7 |
| 10.6 Scope control | §9 |
| 10.7 Compatibility and gates | §10 |

### Owner-approved technical choices

The owner approved all seven choices on 2026-11-08, subject to the two mandatory safeguards stated below.

1. **Original locator (§10.1).** A Citation keeps an immutable `original_locator`, captured on creation, beside
   a correctable `locator`. Change History also records every correction.
2. **Record-only Citations (§10.1).** These store no Source. A Record's Source is shown as "via Record R…",
   read live and never copied into the Citation, so no Source metadata is fabricated.
3. **Deleted Records (§10.5).** Same as the Phase 2 rule for deleted Files: a deleted Record can still be
   reached by its ID, marked deleted. It is excluded from default lists and from grouping, and the only change
   allowed is restore.
4. **Grouping by a non-Administrator (§10.3, §10.4).** A grouping run uses only the rows the person applying
   it can see. Restricted rows stay unapplied until an Administrator applies the session. A run started by an
   Administrator is shown only to Administrators, because its counts could reveal restricted rows.
5. **Raw sequence numbers (§10.3).** Image positions are shown as the position among the Files the reader can
   see, never as the stored sequence number, because gaps would reveal restricted Files. Stored sequence numbers
   are also kept out of Change History.
6. **Group size (§10.4).** A single transaction processes at most 1,000 rows. Larger archival groups must remain representable and must be processed in bounded, resumable operations with no duplicate Records or links. The transaction limit is not a Record-size limit.
7. **Catalogue objects.** Repositories, Sources and Capture Sessions carry no restriction in Phase 3; a session
   whose Files are all restricted shows as an empty session to a non-Administrator. Restricting these objects
   is Phase 9 policy (P2-H).

Phase 0, 1 and 2 decisions remain in force, including P2-G (consolidation deferred, now beyond Phase 3), P2-H,
P2-I and V0020.

## Mandatory safeguards and implementation authorization (owner approved 2026-11-08)

1. **Restricted-row grouping safety.** Skipping a restricted File must never silently merge separate Records or alter the meaning of a Start New Record marker. If omitted rows make group boundaries ambiguous, stop or flag the affected group and leave it unapplied until an authorized Administrator can resolve it. Add explicit regression tests for hidden boundary rows and mixed-visibility groups.
2. **Unbounded logical Record size.** The 1,000-row limit is a per-transaction processing bound, **not** a maximum size of a logical Record. Design bounded, resumable continuation for larger groups, retaining stable Record identity, correct page ordering, durable progress and replay safety. Test interrupted and retried processing of a group exceeding 1,000 rows.

**Authorization:** Begin Phase 3 implementation under the finalized plan, these decisions and ADR-0011, incorporating both safeguards. Reuse `phase-3-wip` only after checking consistency with the approved design. Preserve Phase 0–2 functionality and the Windows/Linux × PostgreSQL 16/18 matrix. Produce a builder completion report and submit for independent Codex audit. **Do not merge Phase 3 to main, declare acceptance or create a Phase 3 tag without explicit owner authorization.** The Phase 2 acceptance tag push remains a separate outstanding task.
