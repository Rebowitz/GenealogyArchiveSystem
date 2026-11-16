# Phase 4 owner-decision register (People, Dates and Places)

**Status: APPROVED by the owner on 2026-11-15**, for all 30 decisions and open items U-1…U-3, with the choices below. Architecture: [ADR-0012](architecture/ADR-0012-people-dates-places.md), prepared for final independent review. The approval includes
the applicable architectural refinements in [`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md).
**D4-28 was approved as modified** and **D4-30 as B (deferral)**.

Approval of the decisions does **not** authorize implementation. Phase 4 coding still requires a separate owner
instruction. See [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md).

**How to read this register:**
- *Approved decisions* (immediately below) is the governing record.
- The original entries (D4-01…D4-24) and Revisions 1–3 that follow are the **decision trail**, preserved
  unchanged. Where a trail entry says "PROPOSED", "Recommended" or offers alternatives, the approved record
  governs.
- The **⚠** mark flags a choice that affects historical semantics, identity, provenance, uncertainty or security.

---

## Approved decisions (owner, 2026-11-15)

| ID | Chosen | Approved content (including refinements) | Superseded or not chosen |
|---|---|---|---|
| D4-01 | A | The gate is the plan §9.1/§9.1a matrix. Full items pass in Phase 4; Partial and Foundation items pass their Phase 4 part and are re-tested in the named phase. | B (all P, D, L and C tests in full) |
| D4-02 | A | Friendly IDs `P` (5 digits), `PL` (5), `M` (5), `CP` (4). `PR` stays reserved for Property. | B (Person and Place only) |
| D4-03 | A | Person, Place, Mention and Collective Reference are ACTIVE or DELETED (recoverable, restore only; D3-12). **No MERGED_INTO**, because D4-09 is A. | B (purge) |
| D4-04 ⚠ | B + refinements | Historical Date is: `expression` (correctable with history); `interpreted_expression`; `form` (POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN, NOT_INTERPRETED); per-point year/month/day, `modifier` (EXACT, ABOUT, ESTIMATED, CALCULATED), `uncertain` and `dual_year`; `calendar` (UNSPECIFIED, GREGORIAN, JULIAN); `year_start` (UNSPECIFIED, JANUARY_1, MARCH_25). Validation is per clarifications §2.2. A dual year gives the MARCH_25 reading, and an explicit JANUARY_1 with a dual year is refused. An explicit pre-1582 GREGORIAN is proleptic and noted. Unsupported year-start styles are never mapped. | A (minimal set); the original single date-level `qualifier` column; immutable expression |
| D4-05 ⚠ | A + refinements | An owned `historical_date` table and nullable `archival_record.record_date_id` and `filing_date_id`; the text stays authoritative. Stored bounds are the **calendar-unit envelope** in JDN (`earliest_jdn`, `latest_jdn`; NULL = open). It is computed per date with the Julian and Gregorian algorithms (never a constant offset) and is never moved by modifiers or "?". Phase 7 tolerance is query-time only (clarifications §2.3, §2.6). | B (inline columns); C (defer); the original `earliest_day`/`latest_day`; the withdrawn "at most 13 days" statement |
| D4-06 ⚠ | A + refinements | A deterministic parser only proposes (grammar and refusals per clarifications §2.5). Confirmation records origin, actor, time and `interpreted_expression`. No background, import or migration parsing. The parser always proposes `year_start` UNSPECIFIED. | B (parse on save); C (no parser) |
| D4-07 ⚠ | A + refinements | No vital-event fields. Relationship periods are labelled "period as entered" and never synchronised with Phase 6 Events. Test status is per clarifications §6.2. | B (interim vital dates); C |
| D4-08 ⚠ | A | Phase 4 relationships are researcher-entered and interpretive, ACTIVE or WITHDRAWN. Competing relationships coexist. Phase 5 attaches Evidence and never rewrites them. | B; C |
| D4-09 ⚠ | A + refinements | Persons always coexist. Person-identity proposals are PROPOSED, RESOLVED or REJECTED (**no SUPERSEDED**). RESOLVED changes no data; there is no merge and no stored or inferred transitivity; contradictions are flagged, never blocked (clarifications §3.5). | B (controlled merge); C; the original "SUPERSEDED" state for identity proposals |
| D4-10 | A | `name_as_written` is required and is the authority; optional parts; type; valid-from/to dates; provenance (Record, Citation, Mention, note). | B; C |
| D4-11 | A (+ U-1, U-2) | Seeds are the spec-named values only. Relationship types: the seven listed. Parentage qualifiers are fixed by the specification. Person name types: BIRTH, MARRIED, NICKNAME, VARIANT. **Place types: COUNTY and STATE (JURISDICTIONAL), type optional (U-1). Place-name types: HISTORICAL, CURRENT, VARIANT, as a vocabulary (U-2).** | broader genealogical seed set; no place-type seeds; mandatory place type |
| D4-12 ⚠ | B | A Role-backed Mention (`mention.record_role_id`, wording read from `party_as_written`); free Mentions carry their own wording. Identity flows only through Candidate Resolution. `record_role` is unchanged. No Mentions are created by migration. **Several Mentions per Role are allowed** (D4-16 refinement). | A (`person_id` on Role); C (copied wording) |
| D4-13 ⚠ | A + refinements | Typed nullable candidate FKs, exactly one set. At most one RESOLVED per Mention (partial unique index), and one non-terminal row per (Mention, candidate). States and transitions as in clarifications §3.2–§3.3, enforced by trigger; attribution is set once. Resolving does not close other proposals. SUPERSEDED happens only as part of Resolve to another candidate. Every candidate write bumps the Mention's `row_version`. | B (polymorphic target) |
| D4-14 | A + refinements | `collective_reference` (Role-backed or wording-backed, anchor Mention and phrase, `stated_count` with `count_as_written`), `collective_named_mention`, and `collective_member` (PROPOSED, RESOLVED or REJECTED; several RESOLVED; no SUPERSEDED). The count is not a limit; membership creates nothing else (clarifications §4). | B (wording only) |
| D4-15 | A | Reuse EDIT_RECORDS and MANAGE_VOCABULARIES. Add VIEW_RESTRICTED_PEOPLE (Administrator only). | B (EDIT_PEOPLE_PLACES) |
| D4-16 ⚠ | A + refinements | **Policy S (complete suppression).** NORMAL or RESTRICTED on Person, Mention, Collective Reference and person-identity proposal; Places carry no restriction. The invariant (no NORMAL object targets a RESTRICTED Person, nor a NORMAL collective a RESTRICTED anchor or named Mention) is enforced by service and by deferred commit-time triggers. Hidden equals nonexistent on every path (clarifications §5.2–§5.4). The original text's "no propagation" means **no automatic propagation**; the only cascade is the explicit D4-27 operation. Lifting a restriction never cascades. | B (none until Phase 9); C (per Name and relationship); policy R (redacted placeholders) |
| D4-17 | A | Explicit `living_status` UNKNOWN, LIVING or DECEASED, never inferred. | B |
| D4-18 ⚠ | A + refinements | Jurisdictional units are Places. `place_type` carries a category (LOCALITY, JURISDICTIONAL, OTHER). `jurisdiction_link` is time-bounded, with `jurisdiction_kind` (CIVIL, ECCLESIASTICAL, JUDICIAL, MILITARY, OTHER), `basis` (RECOGNISED, CLAIMED, DISPUTED, UNKNOWN) and provenance. For a jurisdictional Place, "physical Place" means the unit. **No formation link in Phase 4** (D4-30). | B (separate Jurisdiction entity); the Revision 1 "optional `place_derivation_link`" |
| D4-19 | A | No Source-to-jurisdiction link in Phase 4; `jurisdiction_text` stays as written. | B |
| D4-20 | A | `approximate_location` text plus optional latitude and longitude with a precision note. No GIS. | B |
| D4-21 | A | A plain case-insensitive contains lookup over documented Names and Place Names. Alphabetical, no ranking; restricted Persons are excluded before limits. | B |
| D4-22 | A | No sex or gender field in Phase 4. | B |
| D4-23 | A | SIBLING carries no qualifier. | B |
| D4-24 ⚠ | A | "Create Person or Place from this Mention" is a separate, explicit action that records the new object, an initial Name and an attributed RESOLVED decision. | B |
| D4-25 ⚠ | A | An UNSPECIFIED calendar is never assumed or narrowed by era. Both readings are kept, and the difference is computed for the actual date. | — |
| D4-26 ⚠ | A | "YYYY-YYYY" is proposed as BETWEEN, with FROM_TO offered at confirmation. Never decided silently. | — |
| D4-27 ⚠ | A | The restriction protocol of clarifications §5.5: persisted preview; one-transaction confirmation with revalidation (any difference means STALE, nothing applied); global lock order; deferred integrity triggers; idempotent, retryable confirmation; a single-transaction job path above the interactive limit; an absolute ceiling; never chunked. It is distinct from prohibited automatic propagation. The numeric values are configurable installation settings with the U-3 defaults (ADR-0012 §8). | fixed constants |
| D4-28 ⚠ | **Modified** | **No universal cut-off.** Explicitly recorded year-start conventions are respected in any year. An UNSPECIFIED year start keeps both the 1 January and 25 March readings **in every period**. No convention is inferred from jurisdiction, period, Record, Source or Place. Dual-year semantics are retained (a dual year gives the MARCH_25 reading). Jurisdiction-specific, sourced calendar conventions are a possible **later-phase enhancement**, not Phase 4 functionality (clarifications §2.3). | The proposed `MARCH_25_LAST_YEAR = 1750` (Revision 2) |
| D4-29 | A | Re-proposing a rejected candidate (Mention, identity pair or collective member) creates a new attributed row and requires a rationale. The earlier rejection is shown and never blocks. | — |
| D4-30 ⚠ | **B** | Structured county-formation and boundary-change relationships are **deferred to Phase 6**. Phase 4 keeps dated jurisdiction-containment relationships and sourced historical notes. **Notes are never automatically converted into structured assertions.** | A (`place_derivation_link` in Phase 4) |
| GAS-P4-PLAN-001 ⚠ | **C** (owner, 2026-11-15) | **Record Role withdrawal replaces physical deletion** once Phase 4 is installed. Identity, wording, references and history are preserved. "Remove role" becomes "Withdraw role"; "Restore role" returns the same Role. The migration is additive (`lifecycle` defaults to ACTIVE, with a `BEFORE DELETE` trigger). Withdrawal never reads or changes Mentions or collectives, so nothing hidden can be disclosed. A withdrawn Role's Mentions and collectives stay visible (subject to policy S), are labelled, and are frozen for writes until the Role is restored. Correcting an ACTIVE Role's wording changes no Phase 4 row. Design: clarifications §11; ADR-0012 §5a. | A (block removal); B (allow removal and copy the wording) |

### Open items from the consistency review

**U-1…U-3 were resolved by the owner on 2026-11-15** and are part of the approved record. U-4 and U-5 are notes, not
decisions.

| ID | Item | Status |
|---|---|---|
| U-1 | Place-type seed set, and whether a type is mandatory | **Resolved:** seed **COUNTY and STATE** (category JURISDICTIONAL; L-002). **`place_type` is optional**, so a Place can be saved without a type (A-003) and no administrator setup is needed first. |
| U-2 | Place-name types | **Resolved:** an administrator-extensible vocabulary `place_name_type`, seeded **HISTORICAL, CURRENT and VARIANT** (VARIANT = alternate spelling; BUILD-SPEC §13, L-001). The type is optional on a name. |
| U-3 | D4-27 numeric values | **Resolved:** configurable **installation configuration**, with defaults preview expiry `PT30M`, lock timeout `PT5S`, interactive statement timeout `PT30S`, interactive limit 1,000 items, job timeout `PT10M`, ceiling 50,000. Validated by `check-config` (ADR-0004). **50,000 is also the maximum permitted ceiling:** it may be lowered by configuration, and raising it requires a separately approved architectural revision (review correction PR4-R1). Operational only; no archival meaning; never exported. Keys are in ADR-0012 §8. |
| U-4 | Search usability of approved D4-28 (envelopes about one year wider when the convention is unrecorded) | Note: approved behaviour; the Phase 7 search design must expect it. |
| U-5 | Phase 6 must carry formation and boundary-change relationships (not in BUILD-SPEC §62's Phase 6 list) | Note: a Phase 6 planning obligation. |

The architecture is recorded in [ADR-0012](architecture/ADR-0012-people-dates-places.md), prepared for the final
independent architectural review.

---

## Decision trail (preserved; superseded where the approved record differs)


*Original register (2026-11-15), as written for owner review:*

### D4-01 — Phase 4 gate and partial coverage
**Question.** Several People, Date and Place acceptance tests depend on later layers: P-003 needs dates of birth
and marriage, P-005 needs Claims, D-005 needs a round trip, EX-007 is Phase 8 and S-002/S-005/S-008 are Phase 7.
What exactly must pass for Phase 4?
- **A. Recommended.** The gate is the plan §9.1 matrix. Items marked Full must pass. Partial and Foundation items
  pass their Phase 4 part and are re-tested in the named phase.
- **B.** All P, D, L and C tests in full. That pulls Events, Claims and export forward and breaks the §62 phase
  order.

**Recommendation:** A.

### D4-02 — Friendly-ID prefixes
**Question.** Which objects get friendly IDs, and with which prefixes?

| Object | Proposal | Notes |
|---|---|---|
| Person | `P`, 5 digits | The spec example is `P00127`. |
| Place | `PL`, 5 digits | Alternative `L`, matching the L-tests. `PL` reads more clearly. |
| Mention | `M`, 5 digits | Optional. Useful in UI and history ("M00012 unresolved"). |
| Collective Reference | `CP`, 4 digits | Optional. |

**Alternatives.**
- **A. Recommended.** All four.
- **B.** Only Person and Place. Mentions and collective references are identified by Record and position, which
  is weaker for reference and for history.

**Compatibility.** Same checks as D3-07: 1–4 letters, unique, greedy parse. No collision with F, R, RP, S, CS or CT.
`PR` stays reserved for Property (spec `PR0041`). **Recommendation:** A.

### D4-03 — Person and Place lifecycle
**Question.** Lifecycle for Person, Place, Mention and Collective Reference.
- **A. Recommended.** ACTIVE or DELETED (recoverable), following D3-12. A deleted object stays reachable, marked
  deleted, and can only be restored. No purge. Persons may additionally be MERGED_INTO if D4-09 B is chosen.
- **B.** Also allow permanent purge. That is unnecessary and risks identity loss.

**Recommendation:** A.

### D4-04 ⚠ — Historical Date qualifiers and expression correction
**Question.** Which qualifiers exist, and can the source expression be corrected?

**Qualifiers proposed:**
- EXACT, ABOUT, ESTIMATED, CALCULATED, BEFORE, AFTER, BETWEEN, FROM_TO, UNKNOWN, NOT_INTERPRETED;
- plus an independent `uncertain` flag ("1847?").

**Alternatives.**
- **A.** Minimal set: EXACT, ABOUT, BEFORE, AFTER, BETWEEN, UNKNOWN. ESTIMATED and CALCULATED collapse into ABOUT,
  which loses a distinction genealogists use.
- **B. Recommended.** The full set above. BETWEEN (a range) and FROM_TO (a period) stay distinct.
- **Expression correction.** Correctable, with history: it fixes a typo in the transcription and the history keeps
  the original (A-004). Alternatively, immutable, with a correction meaning a new date. That is safer but harder
  for researchers.

**Recommendation:** B; the expression is correctable with history.

### D4-05 ⚠ — Historical Date storage and Phase 3 date fields
**Question.** How are dates stored, and do Phase 3 Record dates get an interpretation now?
- **A. Recommended.** A separate owned `historical_date` table, one row per dated field and never shared. Owners
  reference it by FK. `archival_record` gains `record_date_id` and `filing_date_id` beside the untouched text
  columns. The text stays authoritative; the interpretation is optional.
- **B.** Inline columns on every owner table. Simpler joins, but about 15 columns repeated on many tables, with
  validation duplicated.
- **C.** Defer Record date interpretation to Phase 5. That leaves D3-05's Phase 4 promise unmet.

Derived interval bounds (`earliest_day`, `latest_day`) are stored for Phase 7 search and are non-authoritative.
**Recommendation:** A.

### D4-06 ⚠ — Automatic date parsing
**Question.** May software turn "abt Mar 1847" into an interpretation?
- **A. Recommended.** A deterministic parser **proposes** an interpretation for common forms. Nothing is saved until
  the researcher confirms or edits it, and the row records `interpretation_origin`. An unparseable expression stays
  NOT_INTERPRETED, which is valid. **Existing Phase 3 date text is never parsed by migration or job** (UP-006).
- **B.** Parse automatically on save. Faster, but software normalisation becomes stored interpretation without a
  decision (A-006 risk).
- **C.** No parser. Every interpretation is entered by hand, which causes friction (UI-005).

**Recommendation:** A.

### D4-07 ⚠ — Birth, marriage and death dates before the Event model
**Question.** P-003 needs "married 1852" and "born 1849", but Events (birth, marriage) are Phase 6 (§21, §62).
- **A. Recommended.**
  - Phase 4 adds **no** vital-event fields.
  - SPOUSE relationships may carry optional start and end Historical Dates, which describe the relationship, not a
    Marriage Event.
  - P-003 is tested in Phase 4 as "no chronology validation exists between any dates or relationships", and fully
    in Phase 6 with Events.
- **B.** Interim "birth" and "death" summary dates on Person. Researchers get them sooner, but there would be two
  sources of truth once Events exist, and Phase 6 must reconcile them without reinterpretation (UP-006).
- **C.** Also defer relationship dates to Phase 6.

**Recommendation:** A.

### D4-08 ⚠ — What a Phase 4 relationship is
**Question.** Before Claims and Conclusions exist (Phase 5), what does a stored Person Relationship mean?
- **A. Recommended.**
  - A researcher-entered **interpretive** relationship (§9), with attribution, optional provenance (Record,
    Citation, note) and history.
  - Lifecycle ACTIVE or WITHDRAWN: withdrawal is kept, not deleted.
  - Competing relationships coexist (P-005).
  - Phase 5 attaches Evidence and Claims to these rows and never rewrites them.
- **B.** Store relationships only as Phase 5 Claims. Phase 4 would then have no relationships, which defeats the
  gate.
- **C.** Mark each relationship PROPOSED or ACCEPTED. That duplicates Candidate Resolution and Conclusion semantics
  early.

**Recommendation:** A.

### D4-09 ⚠ — Duplicate Persons (P-010)
**Question.** "Controlled merge **or** resolution preserves identity and history." Which does Phase 4 provide?
- **A. Recommended.**
  - Persons always coexist.
  - A **Person-identity candidate**: "P00127 may be P00133", with PROPOSED, RESOLVED, REJECTED or SUPERSEDED,
    using the same framework as Mentions.
  - No merge in Phase 4; RESOLVED records the decision only.
  - Physical merge, if wanted, comes later with its own design.
- **B.** Controlled merge now. P00133 becomes MERGED_INTO P00127; Names, relationships and Mentions are re-pointed
  with history; P00133's ID is retired and resolves to the survivor. P-010 is then complete, but this is the most
  complex and riskiest feature in the phase (identity, history, restriction, Phase 5 evidence attachment).
- **C.** Coexistence only, with P-010's "controlled merge or resolution" deferred.

**Recommendation:** A. It meets "resolution" with low risk and leaves merge to a separately designed later step.

### D4-10 — Person Name structure and provenance
**Question.** How detailed is a Name, and how is its provenance recorded?
- **A. Recommended.**
  - `name_as_written` is required and is the authority.
  - Optional parts: given, surname, prefix, suffix.
  - Type (D4-11) and optional valid-from/to dates.
  - Provenance: an optional Record, Citation or Mention reference plus a note.
  - Phase 5 adds Source Statement links.
- **B.** Structured parts only. That loses as-written forms, contrary to A-004.
- **C.** Free text only. Sorting and lookup by surname are weak.

**Recommendation:** A.

### D4-11 — Vocabularies and seeds
**Question.** Which vocabularies exist and what do they hold?

Following D3-08, seeds are only the values the specifications name. Administrators add, relabel and retire; codes
are never deleted or reused.

| Vocabulary | Seeds |
|---|---|
| Relationship types (§15; P-006, P-007, P-009) | PARENT_CHILD (parentage), SPOUSE, GUARDIAN_WARD, SIBLING, GRANDPARENT_GRANDCHILD (direct), MASTER_APPRENTICE, HOUSEHOLD_MEMBER |
| Parentage qualifiers (§15) | UNSPECIFIED, BIOLOGICAL, ADOPTIVE, STEP, FOSTER. **Fixed by the specification, not editable.** |
| Name types (P-001) | BIRTH, MARRIED, NICKNAME, VARIANT. Others are added by the Administrator. |
| Place types | None seeded, or COUNTY and STATE only, as L-002 names them. |

**Alternative.** Seed a broader genealogical set (ALIAS, RELIGIOUS, GODPARENT, TOWNSHIP, …). Convenient, but beyond
the specification.

**Recommendation:** spec-named seeds only.

### D4-12 ⚠ — Mentions: anchor and Record Role linkage (D3-01)
**Question.** Where is a Mention anchored, and how does a Role refer to a Person?

**Anchor.** A Mention belongs to an Archival Record (required in Phase 4). Optional Citation and File page
anchors come later.

**Role linkage.**
- **A.** Role → Person directly (a nullable `person_id` on `record_role`). Simplest, but it bypasses
  Proposed/Resolved: a link is an unrecorded resolution (C-003, C-004).
- **B. Recommended.**
  - A Mention may be **Role-backed**: `mention.record_role_id`, with its wording read from the Role's
    `party_as_written`, so the text is never duplicated.
  - Identity flows only through Candidate Resolution.
  - "Link this grantor to P00127" creates or uses the Role's Mention and records an explicit RESOLVED decision in
    one deliberate action.
  - Free Mentions (C-001) carry their own `wording_as_written`.
  - The accepted `record_role` table is unchanged.
- **C.** A Mention always copies the wording at creation. There would be two copies of the as-written text, which
  can diverge.

**Also.** Existing Roles get no Mentions by migration. A Mention is created on first use (UP-006).
**Recommendation:** B.

### D4-13 ⚠ — Candidate Resolution: targets and the one-RESOLVED rule
**Question.** How are candidates stored, and how many can be RESOLVED?

**Target typing.**
- **A. Recommended.** Typed nullable FKs (`candidate_person_id`, `candidate_place_id`, later others) with CHECK
  exactly one, matching the Mention kind. Referential integrity is kept, and §60 bans a generic target.
- **B.** Polymorphic `(target_type, target_id)`. More extensible, but no FK integrity and EAV-like.

**Cardinality.**
- At most one RESOLVED per Mention (partial unique index).
- A resolution change supersedes the previous one (kept, linked with `superseded_by`).
- A Mention naming several people is a collective reference (D4-14), not multiple resolutions.

**Phase 5 overlap.** §62 places "Candidate Resolution history" in Phase 5. Phase 4 keeps the full state trail as
rows and Change History; Phase 5 links Evidence to it.

**Recommendation:** A with these rules.

### D4-14 — Collective Person Reference (P-008)
**Question.** How is a group of people preserved without manufacturing individuals?
- **A. Recommended.**
  - A collective reference on a Record: wording as written, an optional anchor (a Person or Mention: "my deceased
    son John"), an optional count when the source states one, and a note.
  - Members are added only explicitly by the researcher, each with its own Candidate Resolution state.
  - No implied relationships between members, or between members and the anchor, unless entered.
- **B.** Wording only, with members deferred to a later phase. Simpler, but later identification has nowhere to go.

**Recommendation:** A.

### D4-15 — Permissions
**Question.** Which permission governs editing the new objects?
- **A. Recommended.** Reuse EDIT_RECORDS (Editor) for people, places, mentions and resolutions, and
  MANAGE_VOCABULARIES (Administrator) for vocabularies. Add VIEW_RESTRICTED_PEOPLE (Administrator) if D4-16 is
  approved.
- **B.** A new EDIT_PEOPLE_PLACES permission. Finer control, but no role in the specification needs the
  distinction.

**Recommendation:** A.

### D4-16 ⚠ — Restriction of Persons (and Places)
**Question.** Can a Person, and therefore its private details, be restricted?

Living-person privacy is a real genealogical need. SEC-004 and §43 require object-level Restricted, and full policy
and propagation are Phase 9 (P2-H, D3-04).
- **A. Recommended.**
  - Person has NORMAL or RESTRICTED, Administrator-only, with no propagation.
  - Its Names, relationships, collective memberships and resolutions are hidden whenever any Person they name is
    hidden.
  - Hidden and nonexistent IDs give identical answers on every path.
  - Places carry no restriction in Phase 4 (as Sources in D3-04, choice 7).
- **B.** No Person restriction until Phase 9. Simpler, but no way to protect living people until then.
- **C.** Restrict Names and relationships individually. Fine-grained, and much more leak surface.

**Recommendation:** A.

### D4-17 — Living status (SEC-006)
**Question.** How is living status recorded?
- **A. Recommended.** An explicit `living_status` of UNKNOWN (default), LIVING or DECEASED, set only by a person.
  Nothing is ever derived from a missing death date. Phase 9 may use it for privacy policy.
- **B.** No field. SEC-006 is satisfied trivially, but a researcher cannot record a known status.

**Recommendation:** A.

### D4-18 ⚠ — Jurisdiction model (L-002, O-005)
**Question.** Is a jurisdiction a Place or a separate entity? §11 says "Jurisdiction relationship/history" and §13
says "Separate physical Place, Place Name, and Jurisdiction".
- **A. Recommended.**
  - A jurisdictional unit (county, state, parish) is a **Place** with a type.
  - Jurisdiction is a **time-bounded relationship**, `jurisdiction_link` (Place within jurisdiction Place, kind,
    valid-from/to, provenance).
  - Place, Place Name and Jurisdiction stay separate concepts.
  - A county's government is an Organization (Phase 6) and its courthouse a Structure (Phase 6), both distinct
    (O-005).
- **B.** A separate Jurisdiction entity with its own names and hierarchy, plus Place-in-Jurisdiction links. Spatial
  and administrative identities are kept fully apart, at the cost of a fourth model and duplicated naming and
  history machinery.

**Recommendation:** A.

### D4-19 — Source `jurisdiction_text` (D3-06)
**Question.** Should Phase 4 link a Source to a jurisdiction Place?
- **A. Recommended.** Not in Phase 4. The text stays as written; the link comes with Organization (Phase 6), so
  creator and jurisdiction are linked together.
- **B.** Add an optional `jurisdiction_place_id` now, beside the text.

**Recommendation:** A, which keeps the scope tight.

### D4-20 — Place location
**Question.** How is a Place's location recorded?
- **A. Recommended.** `approximate_location` text plus optional decimal latitude and longitude with a
  free-text precision note. No GIS, no maps, no spatial index (§60).
- **B.** Text only.

**Recommendation:** A.

### D4-21 — Finding candidates in Phase 4
**Question.** How does a researcher find candidate Persons?

C-002 needs a way to find "three plausible Robert Andrews" to propose; global and fuzzy search are Phase 7.
- **A. Recommended.** A plain case-insensitive *contains* lookup over every documented Name (and Place Name),
  ordered alphabetically, with no scoring, ranking or suggestion. Results are for selection only.
- **B.** Lookup by `P` ID only.

**Recommendation:** A.

### D4-22 — Sex or gender on Person
**Question.** The specifications never mention it. Should Phase 4 add it?
- **A. Recommended.** Not in Phase 4. It is not specified; it can be considered later with its own semantics
  (as recorded vs. as concluded).
- **B.** An optional "sex as recorded" field.

**Recommendation:** A. **This is an owner call:** genealogists commonly expect it.

### D4-23 — Sibling and half-sibling
**Question.** SIBLING is a seeded type. Does Phase 4 qualify it as full, half or step?
- **A. Recommended.** SIBLING carries no qualifier in Phase 4. A sibling link is only what a source states directly.
  Shared parents never create one (A-009). A qualifier vocabulary can be added later.
- **B.** Reuse the parentage qualifiers for siblings. That conflates two different meanings.

**Recommendation:** A.

### D4-24 ⚠ — Creating a Person or Place from a Mention
**Question.** May a researcher create a new Person or Place directly from a Mention?

C-001 and BI-006 forbid creating a Person merely to save, or silently.
- **A. Recommended.** "Create new Person/Place from this Mention" is a separate, explicit action. It records the new
  object, an initial Name taken from the wording (the wording itself stays on the Mention or Role), and an explicit
  RESOLVED decision attributed to the researcher. It is never a default or automatic step.
- **B.** No shortcut. The researcher creates the Person first, then resolves the Mention. More friction (UI-005).

**Recommendation:** A.

---

## Revisions from the architectural clarification (PROPOSED, 2026-11-15)

**Trail only; superseded by the approved record above.** At the time of writing: not approved. These revise the recommendations above; the original options stay recorded for comparison. Full
analysis, examples and alternatives: [`PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md`](PHASE-4-ARCHITECTURAL-CLARIFICATIONS.md).

| Decision | Revised recommendation (PROPOSED) | Clarification § |
|---|---|---|
| D4-04 ⚠ | `form` (POINT, BEFORE, AFTER, BETWEEN, FROM_TO, FROM, TO, UNKNOWN, NOT_INTERPRETED) plus per-point `modifier` (EXACT, ABOUT, ESTIMATED, CALCULATED), per-point `uncertain`, `dual_year`, `calendar` and new `year_start`. `interpreted_expression` exposes stale interpretations after an expression correction. | §2.1–2.2 |
| D4-05 ⚠ | Search envelope stored as Julian Day Numbers (`earliest_jdn`, `latest_jdn`, NULL = open), derived and non-authoritative. Conservative widening for partial dates, unspecified calendar and unspecified Julian year start; approximation never widened in storage. | §2.3–2.4 |
| D4-06 ⚠ | Deterministic English grammar; refuses ambiguous numeric dates, regnal years and seasons. Proposals saved only on explicit confirmation, which records origin, actor, time and `interpreted_expression`. No background, import or migration parsing. | §2.5 |
| D4-07 ⚠ | Unchanged (A). Relationship periods labelled "period as entered", never synchronised with Phase 6 Events. Test status split Complete / Partial with the completing phase named. | §6 |
| D4-09 ⚠ | Person-identity proposals: PROPOSED, RESOLVED, REJECTED (no SUPERSEDED). RESOLVED changes no data; no merge; no stored or inferred transitivity; contradictions flagged, never blocked. | §3.5 |
| D4-13 ⚠ | Fixed state meanings and transitions (P→R, P→X, R→X, R→S only as part of Resolve), trigger-enforced; attribution set once; resolving does not close other proposals; deleted candidates shown, not cleared. | §3.2–3.4 |
| D4-14 | `collective_reference` (Role-backed or wording-backed, anchor Mention, `stated_count` with `count_as_written`), `collective_named_mention`, `collective_member` (PROPOSED/RESOLVED/REJECTED, several RESOLVED). Count is not a limit. Membership creates nothing else. | §4 |
| D4-15 | Unchanged; adds VIEW_RESTRICTED_PEOPLE (Administrator only), required by D4-16. | §5 |
| D4-16 ⚠ | Policy S (complete suppression). Restriction on Person, Mention, Collective Reference and identity proposal; invariant that NORMAL objects never target a RESTRICTED Person; restricting a linked Person is a previewed, explicit, single-transaction action; lifting never cascades; several Mentions per Role. | §5.2–5.4 |
| D4-18 ⚠ | Model A refined: `place_type` category (LOCALITY, JURISDICTIONAL, OTHER); `jurisdiction_link.jurisdiction_kind` and `basis` (RECOGNISED, CLAIMED, DISPUTED, UNKNOWN); optional `place_derivation_link` (owner may defer); documented reading of "physical Place" for jurisdictional units. | §1 |
| **D4-25 ⚠ (new)** | Calendar UNSPECIFIED is never assumed Gregorian or Julian; the envelope covers both readings. | §7 C-4 |
| **D4-26 ⚠ (new)** | "YYYY-YYYY" is proposed as BETWEEN; FROM_TO offered at confirmation; never decided silently. | §7 C-5 |
| **D4-27 ⚠ (new)** | Sanction the explicit, previewed restriction of linked Mentions, collectives and identity proposals when a Person is restricted, as distinct from D3-04's prohibited automatic propagation. | §7 C-1, C-2 |

### Revision 2: final architectural corrections (PROPOSED, 2026-11-15)

**Trail only; superseded by the approved record above** (D4-28 was approved as modified, not as written here). At the time of writing: not approved. These supersede the matching rows of the table above. See clarifications §2.2–§2.6, §5.5 and §10.

| Decision | Revised recommendation (PROPOSED) |
|---|---|
| D4-04 ⚠ | Adds: a dual year implies a MARCH_25 year start, and an explicit JANUARY_1 with a dual year is refused; any calendar may carry a dual year or MARCH_25; explicit GREGORIAN before 15 Oct 1582 is proleptic and noted; unsupported year-start styles are never mapped. |
| D4-05 ⚠ | The stored bounds are the **calendar-unit envelope**: the convex hull of every reading left open by precision, calendar and year start. Julian/Gregorian differences are computed per date by the two calendar algorithms, never as a constant; the earlier "at most 13 days" is withdrawn. ABOUT, ESTIMATED, CALCULATED and "?" never move the bounds. Phase 7 applies a versioned, query-time tolerance per modifier, labels approximate matches and never writes back. |
| D4-16 ⚠ | The invariant also covers collective anchors and named Mentions, and is enforced by deferred commit-time constraint triggers. |
| D4-25 ⚠ | An UNSPECIFIED calendar keeps both readings and is not narrowed by era. |
| **D4-27 ⚠** | The restriction protocol of clarifications §5.5: persisted preview with item `row_version`s; one-transaction confirmation; recompute and compare, where any difference means STALE and nothing is applied; global lock order (Person before Mention before collective before identity proposal, UUID order within each); deferred integrity triggers; idempotent, retryable confirmation; large operations run as one job transaction; an absolute ceiling; never chunked. |
| **D4-28 ⚠ (new)** | `MARCH_25_LAST_YEAR`, proposed 1750, bounds year-start widening for an UNSPECIFIED year start. This is a regional fact; the owner chooses the value. |
| **D4-29 (new)** | Re-proposing a rejected candidate creates a new attributed row and requires a rationale. The earlier rejection is shown and never blocks. |

### Revision 3: supplement (PROPOSED, 2026-11-15)

**Trail only; superseded by the approved record above** (D4-30 approved as B, Phase 6). At the time of writing: not approved. See clarifications §1.5 and §10.6.

| Decision | Recommendation (PROPOSED) |
|---|---|
| D4-18 ⚠ | As in Revision 1, with formation links removed from Phase 4 (see D4-30) |
| **D4-30 ⚠ (new): county-formation relationships** | **Options:** **A**, include a minimal dated, sourced `place_derivation_link` (WHOLLY, PARTLY or UNKNOWN) in Phase 4; **B**, defer it and keep jurisdiction change as dated containment links plus sourced notes. **Recommendation: B.** No specification requires it in Phase 4; containment meets L-002 accurately; and modelling formation alone, before Evidence and Events exist, risks a competing second representation of boundary changes later. **Cost of B:** the parent-county question for areas without locality Places is answered only by notes until the later phase. **Owner question:** which phase receives formation and boundary-change relationships (Phase 6 is the natural candidate). |
