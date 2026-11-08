# Phase 3 owner decisions (Repository, Source and Archival Records)

On 2026-11-08 the project owner authorized Phase 3 and approved every recommendation in
[`PHASE-3-PLAN.md`](PHASE-3-PLAN.md) §9 ("Adopt all"). These decisions bind Phase 3; later phases may revisit
them only with owner approval. The design is in [ADR-0011](architecture/ADR-0011-records.md).

| ID | Decision |
|---|---|
| D3-01 | **Record Roles before Persons.** A Role is a role code, the party *as written* in the source (optional, because a party may be unreadable) and a note. Phase 4 adds an optional link to a Person or an unresolved Mention. A Role never creates a Person Relationship or Event Role. |
| D3-02 | **Courthouse grouping** is entered in a browser worksheet on the Capture Session page. Each row has a Start New Record marker, an optional `record_group`, a page designation and a classification. These are the rules a Phase 7 CSV import will apply. |
| D3-03 | **Relationship-aware consolidation** of redundant captures (F-006/F-007) is included in Phase 3. P2-G deferred it here. |
| D3-04 | **Records carry NORMAL/RESTRICTED.** A restricted Record is visible only to Administrators, like a restricted File. Records and Files do not propagate restriction to each other, but a restricted File is never exposed through a Record. Comprehensive restriction management stays in Phase 9 (P2-H). |
| D3-05 | **Record date and filing date** are stored as the source expression only (A-004). Phase 4 adds a structured Historical Date interpretation alongside. The capture date is an ordinary calendar date. |
| D3-06 | **Historical creator, Access Provider and Jurisdiction** are separate descriptive text fields on a Source, never stored in the Repository. Phases 4 and 6 add links to Organization, Place/Jurisdiction and Structure objects. |
| D3-07 | **Friendly-ID prefixes:** Record `R` (spec), Repository `RP`, Source `S`, Capture Session `CS`, Citation `CT`. |
| D3-08 | **Seed vocabularies** contain only values the specifications name. Classifications: `DEED`, `DTRUST`. Roles: grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor. Administrators add, relabel and retire values; codes are never deleted. |
| D3-09 | **Managed filenames are not tied to classification** in Phase 3. They stay as entered. |
| D3-10 | **A Record has one transcription/abstract text**, labelled as researcher-entered (not OCR). Structured Source Statements arrive in Phase 5. |
| D3-11 | **Bulk reclassification** of selected Records is included. It is transactional, with Change History for each Record. |
| D3-12 | **Record lifecycle** is ACTIVE and DELETED (recoverable) only. There is no purge or merge of Records in Phase 3, and identifiers are retired permanently. |

Phase 0, 1 and 2 decisions remain in force, including P2-H (comprehensive restriction management in Phase 9),
P2-I (attestation before quarantine) and V0020.
