# Phase 4 owner-decision register (People, Dates and Places)

**Status: PROPOSED. No decision below is approved.** Each entry gives the question, the alternatives, their
tradeoffs and a recommendation, citing its governing text. Implementation waits until the owner approves this
register (with any changes) and separately authorizes Phase 4 coding. See [`PHASE-4-PLAN.md`](PHASE-4-PLAN.md).

The **⚠** mark flags a choice that affects historical semantics, identity, provenance, uncertainty or security
(CLAUDE.md, "stop and report the design question").

---

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
