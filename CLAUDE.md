# CLAUDE.md
## Genealogy Archive System - Agent Operating Instructions v1.1

### Project identity
This project is the **Genealogy Archive System**. Genealogy is its primary purpose and organizing domain. Historical records and related materials are included because they support genealogical research. Do not rename, generalize, or reframe the product as a generic historical-research archive.

### Read this first
You are implementing the Genealogy Archive System. Work from the repository specifications; do not substitute generic genealogy-software assumptions.

### Authority order
1. Archival invariants.
2. `docs/ACCEPTANCE-SPEC.md`.
3. `docs/BUILD-SPEC.md`.
4. The currently approved phase brief, such as `docs/PHASE-0.md`.
5. Existing implementation details that do not conflict with the above.
6. Implementation convenience.

If genuine ambiguity could change historical semantics, identity, provenance, evidence meaning, preservation behavior, uncertainty, security, portability, or cross-platform behavior, stop and report the design question instead of inventing an answer.

### v1 hosting requirement
The same application codebase must support independent Archive Instances hosted on **Windows and Linux**. PostgreSQL is authoritative on both. The normal client is a web browser.

Platform-specific code may exist only where genuinely required for installation, service management, filesystem/path handling, permissions, PostgreSQL host integration, or equivalent operating-system concerns. Historical/domain logic and interchange semantics must not fork by operating system.

Persist archival File locations as Storage Volume identity plus portable relative paths. Windows drive letters and POSIX mount roots are installation configuration, never File identity.

Windows and Linux Archive Instances are independent peers. Exchange is deliberate through Transfer Packages and approved exports. **Do not implement live synchronization, cross-site database replication, distributed locking, or continuous bidirectional connectivity in v1.**

### Non-negotiable archival rules
- Permanent identifiers are never reused.
- Preservation originals are immutable after verified ingestion.
- Filename, path, classification, display name, and database row position never constitute historical object identity.
- Incomplete and unresolved historical information is valid.
- Proposed identity never silently becomes Resolved.
- Search, OCR, fuzzy matching, similarity, and automation assist discovery; they do not establish historical truth.
- Parentage is independent of marriage. A shared child does not create marriage or spouse relationships.
- Do not fabricate unknown Persons or intermediate relatives.
- Source Statement, Evidence, Claim, and Conclusion remain distinct.
- Contradictory evidence may coexist.
- Historical dates never gain false precision.
- Absence from the archive alone is not Expected/Absent Evidence.
- File-to-Archival Record is many-to-many.
- Restricted information must not leak through search, direct retrieval, relationship traversal, export, or guessed identifiers.
- When archival integrity is uncertain, fail visibly rather than silently guessing.

### v1 architecture boundary
Use a modular monolith, PostgreSQL, ordinary filesystem-backed managed Storage Volumes, a browser UI, and a lightweight persistent PostgreSQL-backed background-job mechanism.

Do not add microservices, Kubernetes, distributed databases, an authoritative graph database, generic EAV architecture, Elasticsearch/OpenSearch, Redis, Kafka, RabbitMQ, mandatory cloud services, mandatory object storage, event sourcing, blockchain, live site synchronization, real-time collaborative editing, enterprise workflow infrastructure, embedded AI, mandatory GIS, or genomic analysis unless explicitly approved.

### Working method
1. Read this file.
2. Read `docs/PHASE-0.md`.
3. Read referenced portions of `docs/BUILD-SPEC.md` and `docs/ACCEPTANCE-SPEC.md`.
4. Inspect the existing repository before choosing libraries or adding infrastructure.
5. Implement only the approved phase/task.
6. Add automated tests with implementation.
7. Use Acceptance Specification IDs in test names/comments where practical.
8. Run applicable tests on both supported host platforms when the phase reaches platform behavior.
9. Do not weaken a test to make implementation pass.
10. Do not silently broaden scope or perform unrelated refactoring.

### Required completion report
Report:
1. What was implemented.
2. Schema/migration changes.
3. File/storage behavior affected.
4. Acceptance tests implemented and run.
5. Test results, including Windows/Linux results where applicable.
6. Known limitations.
7. Unresolved specification questions.
8. Deviations from specification.

**Final rule:** Preserve the evidence, preserve the uncertainty, preserve the provenance, preserve the identity, and keep the researcher's ordinary work simple.
