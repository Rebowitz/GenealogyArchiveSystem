# Genealogy Archive System
## Coding-Agent Build Specification v1.1

### 1. Purpose
> **Project Focus:** This is the **Genealogy Archive System**. Genealogy is its primary purpose and organizing domain. Historical records, properties, places, organizations, DNA evidence, and other historical materials are modeled because they support genealogical research; their inclusion shall not broaden or rename the product as a generic historical-research archive.

Build a lightweight, durable genealogy archive system for durable genealogical research and preservation of supporting historical evidence.

The Genealogy Archive System is designed first for a closely held personal genealogy archive shared by a limited number of trusted users. Its architecture shall permit independent deployment by other researchers, families, historical organizations, or small institutions without introducing enterprise, public-social, subscription, cloud-scale, or speculative complexity into v1.

The system shall preserve historical source material, evidence, interpretations, relationships, uncertainty, provenance, and research conclusions over long periods of time.

The archive is the trusted system of record.

### 2. Governing Principles
The following principles are mandatory.

> Complexity required for archival integrity belongs primarily beneath the interface. Routine research and data entry should remain simple.

> The system should assist organization rather than require the researcher to completely organize material before it can be archived.

> Capture first. Organize progressively. Never require interpretation before preservation.

> Information shall be stored according to what it represents, not according to where the researcher happened to encounter it.

> The system shall permit information to be recorded before its identity or interpretation has been resolved.

> Archival processing shall be progressive.

> Search assists discovery; it does not establish historical truth.

> Automation should remove typing, not become the genealogist.

> When archival integrity is uncertain, fail visibly rather than silently guessing.

> Future capability shall be enabled by extensible architecture, not by prematurely exposing complexity in the initial implementation.

### 3. Lightweight-First Requirement
Implement the minimum structure necessary to preserve identity, provenance, historical relationships, source information, evidence, uncertainty, research conclusions, archival Files, integrity, recoverability, and portability.

Do not implement speculative functionality merely because the model could support it. A new infrastructure service, abstraction, workflow, field, entity, dependency, or user-facing requirement shall require a demonstrated v1 need.

Prefer mature operating-system, PostgreSQL, filesystem, framework, and standard-library capabilities over additional infrastructure.

### 4. Authority Hierarchy
When requirements appear to conflict, use this priority:
1. Archival invariants
2. Acceptance Specification v1.1
3. Finalized domain specifications
4. Technical Architecture v1.1
5. UI requirements
6. Implementation convenience

Implementation convenience shall never override archival correctness.

If genuine ambiguity remains and choosing an interpretation could change historical semantics, identity, provenance, preservation behavior, or evidence meaning, do not invent a solution. Surface the ambiguity for a design decision.

### 5. Architecture
Implement a modular monolith.

Primary runtime architecture:
Browser -> Web Application -> Application/Domain Services -> PostgreSQL and File Service -> Configured Storage Volumes.

A persistent background worker shall perform appropriate long-running operations.

PostgreSQL is the sole authoritative relational database for v1. Ordinary archival Files shall be stored in filesystem-backed managed storage rather than exclusively as database blobs.

The system shall operate without Internet access. A normal deployment shall not require cloud services.

### 6. Target Deployment
Supported v1 server hosts:
- Windows and Linux, using one portable application codebase
- PostgreSQL
- browser-based client
- local filesystem and/or NAS storage
- mirrored server drives where provided by deployment
- RAID-backed NAS where provided
- detachable USB or other filesystem-backed storage for backups, exports, or transfer packages

Windows-hosted and Linux-hosted Archive Instances are first-class independent peers. Historical domain behavior, database schema, object identity, archival semantics, interchange formats, backup semantics, preservation rules, and browser behavior shall remain platform-independent.

Platform-specific behavior shall be isolated to installation, service management, filesystem configuration, path handling, permissions, PostgreSQL host integration, and other operating-system integration concerns.

Use ordinary platform service management: Windows service mechanisms on Windows and normally systemd on Linux. The application shall not require Docker. Docker may be supported later but shall not be required. Kubernetes shall not be required.

The same application shall support differing storage topology between installations.

Do not encode assumptions about Windows drive letters, POSIX mount paths, path separators, case behavior, specific RAID levels, NAS vendors, disk technologies, or removable-media technologies into archival identity.

Storage references persisted as archival/application data shall use Storage Volume identity plus portable relative paths. Machine-specific absolute paths belong only to installation configuration.

Windows and Linux Archive Instances shall exchange Transfer Packages and Complete Archive Exports without conversion of historical semantics or object identity.

No live synchronization, database replication between Archive Instances, distributed locking, or automatic bidirectional site synchronization is required in v1. Exchange between independent Archive Instances is deliberate and package-based.

### 7. Archive Instance
Each installation represents an Archive Instance with a persistent globally unique Archive ID.

An Archive Instance contains application, PostgreSQL database, archive configuration, managed File storage, Archive ID, users and permissions, background jobs, and archival/research information.

Restoring an archive onto different hardware does not create a new Archive ID. Independent archives must have independent Archive IDs.

### 8. Object Identity
Every durable archival/domain object shall receive an immutable globally unique machine identity.

Use UUIDv7 where adequately supported by the selected production stack; otherwise use a mature standards-compliant UUID implementation with equivalent uniqueness guarantees.

Important objects may additionally receive human-friendly archive-local identifiers such as P00127, F03711, and R01842.

Friendly identifiers shall be generated using PostgreSQL-backed sequences or an equivalently safe database mechanism. Identifiers shall never be reused. Deletion, retirement, consolidation, or merge permanently retires the identifier. Gaps in friendly numbering are valid and shall never be renumbered merely for appearance.

Filenames, names, database row positions, paths, classifications, and current relationships shall never constitute object identity.

### 9. Information Mutability Classes
Respect three categories.

Immutable: permanent global identity, original preservation File bytes, authoritative ingestion hash, original filename as captured provenance, Archive ID. These shall never be silently changed.

Correctable: classification, descriptions, repository information, book/volume/page, identified subjects, managed filename, Record associations, tags. Corrections are expected. Significant corrections shall retain appropriate history.

Interpretive: Claims, identity resolutions, relationship conclusions, hypotheses, evidence assessments, Conclusions. Interpretive information is expected to evolve and shall retain sufficient provenance/history to understand significant prior states.

### 10. Common Domain Framework
Major domain objects shall share common infrastructure where appropriate for identity, description, dates, places, connections, sources/evidence, Files/media, notes, tags, collections, history, creator/modifier metadata, and progressive completeness.

Do not implement a generic entity-attribute-value database in place of explicit domain models. Materially different historical concepts require explicit domain tables/models.

### 11. Core Domain Models
Implement explicit models for at least:
- Archive Instance
- User
- Person
- Person Name
- Person Relationship
- Collective Person Reference
- Historical Place
- Place Name
- Jurisdiction relationship/history
- Property
- Structure
- Event
- Organization
- Repository
- Source
- Archival Record
- Citation
- Source Statement
- Evidence
- Claim
- Conclusion
- Mention
- Candidate Resolution
- File
- File Relationship
- Storage Volume
- Capture Session
- DNA Test
- Research Question
- Hypothesis
- Research Action
- Research Note
- Collection
- Tag
- Classification/configuration vocabulary
- background operation/job
- change/activity history
- retired-object/tombstone information where required

Use normalized relationship tables where relationships are materially many-to-many.

### 12. Historical Date Model
Do not reduce historical dates to ordinary exact SQL dates.

Support exact, partial, approximate, before, after, ranges, uncertain, unknown, Julian, Gregorian, and dual-dated expressions.

Preserve original source expression independently from normalized interpretation. Normalization shall never introduce greater precision than supplied.

Example: March 1847 must not become 1847-03-01.

Structured temporal information shall support interval-aware search. Conflicting dates may coexist.

### 13. Historical Place Model
Separate physical Place, Place Name, and Jurisdiction.

Place identity shall not depend on current name, current jurisdiction, coordinates, or address.

Support historical names, alternate spellings, approximate location, unresolved Place Mentions, time-dependent jurisdiction relationships, source wording, and uncertain/correctable place conclusions.

Property and Structure shall remain distinct from Place.

### 14. Person Model
A Person represents a historical individual reconstructed progressively.

Person identity shall not depend on name, dates, relationships, surname, household, or family grouping.

Support unlimited Names with provenance/context. Preferred display Name shall not replace other documented Names.

Do not create fictional Persons to fill unknown genealogical positions.

### 15. Person Relationships
Parentage shall be direct Person-to-Person information. It shall not depend on Marriage, household, surname, or family grouping.

Support at least Parent/Child, Spouse, Guardian/Ward, Sibling, and extensible historical/social relationships.

Parentage qualifiers shall include at least Unspecified, Biological, Adoptive, Step, and Foster.

Competing parentage may coexist. Do not enforce exactly one mother or father.

Marriage and parentage are independent. A shared child shall never automatically create Marriage Event, spouse relationship, household, or couple entity. A child born outside known marriage dates is not inherently erroneous.

Guardian is not Parent. Apprentice Master is not Parent. Household Member is not Parent.

Direct historical relationships such as grandparent may be recorded when supported without manufacturing intermediate Persons.

### 16. Candidate Resolution
Use the common state progression Unresolved -> Proposed -> Resolved.

Support multiple candidates. A Proposed identification shall remain interpretive and shall never silently become Resolved. Original source wording shall remain preserved. Rejected and superseded resolutions shall be representable.

Apply the same framework to appropriate Persons, Places, Properties, Structures, Organizations, Events, Records, and similar historical entities.

### 17. Source and Evidence Architecture
Use the conceptual chain:
Repository -> Source -> Archival Record -> Citation -> Source Statement -> Evidence -> Claim -> Conclusion.

Not every research situation requires every layer.

Source Statements represent what sources say. Claims represent propositions being evaluated. Evidence associates Source Statements or other appropriate evidence with Claims. Conclusions represent current researcher conclusions.

Do not collapse these layers merely to simplify implementation.

Evidence effects shall include SUPPORTS, CONTRADICTS, EXPECTED_ABSENT, and INDETERMINATE.

Historical contradictions shall be preserved. A Conclusion shall not overwrite conflicting Evidence.

### 18. Expected/Absent Evidence
Expected/Absent Evidence may exist only when information was reasonably expected within a defined and sufficiently examined source or record set.

It shall support recording expected information, reason for expectation, examined scope, result, and relevant completeness information.

Absence from the archive alone shall never constitute Expected/Absent Evidence. A failed or unsuccessful search alone shall not automatically create Expected/Absent Evidence.

### 19. Archival Record Model
Archival Record is the principal logical documentary object.

For courthouse work: Repository -> Source/Record Book -> Archival Record -> File representations.

One Record may use multiple Files. One File may represent portions of multiple Records. Therefore File-to-Archival Record shall be many-to-many.

Court/source page number is distinct from image sequence.

Record Roles shall support values such as grantor, grantee, testator, executor, witness, beneficiary, adjoining landholder, prior owner, surveyor, and other configurable roles.

Record Role shall remain distinct from Person Relationship and Event Role.

### 20. Property and Structure
Property and Structure are separate entities.

Property identity shall not depend on current owner, name, acreage, address, or jurisdiction.

Ownership, residence, occupancy, tenancy, and operation are distinct relationships. Do not infer one from another.

Conflicting acreage and boundary descriptions may coexist. Do not manufacture remainder Properties using arithmetic.

Subdivision, inheritance, recombination, and boundary change may be represented through explicit relationships.

Detailed GIS and metes-and-bounds reconstruction are optional future capabilities and shall not burden v1.

A deed may be indexed without requiring creation of a Property.

### 21. Event Model
Event represents a historical occurrence. Record represents documentary evidence. Do not equate them.

Records and Events may be many-to-many where appropriate. Do not automatically create Events from Records. Do not manufacture transition Events merely because two states differ.

Event date, filing date, Record date, and Capture date shall remain semantically distinct.

Event participation does not automatically establish residence, ownership, or family relationship.

### 22. Organization and Repository
Organization represents historical institutions/groups. Repository represents custody/stewardship. They shall remain distinct.

Also distinguish historical creator, current Repository, Access Provider, Jurisdiction, and physical Structure.

Unknown Repository shall never prevent archival entry.

### 23. DNA
DNA functionality shall remain lightweight. DNA analysis occurs externally.

DNA Test shall identify at minimum Test ID, tested Person when known, DNA type, optional provider/lab, optional external test/kit ID, optional test date, notes, and restriction/privacy status.

The Test belongs to the actual tested Person, not a historical ancestor under investigation.

DNA-derived material participates in the common Source Statement -> Evidence -> Claim -> Conclusion framework.

Do not implement genomic analysis. Raw DNA material may be archived as Restricted source material.

DNA Evidence shall not automatically invalidate adoptive, step, foster, guardian, or other social relationships.

### 24. Research Model
The archive preserves durable research knowledge. Do not build project-management software.

Implement only lightweight Research Question, Hypothesis, Research Action, Conclusion, and Research Note.

Research Action outcomes shall include Found, No Relevant Result, Partial, and Unable to Examine.

No Project object is required. No Task object is required. Do not require due dates, assignments, sprints, or workflow states.

Research history shall remain distinct from technical audit/change history.

### 25. Collections, Tags and Classification
Collections group. Tags label. Classifications identify type. None determines storage or ownership.

Collections are optional and many-to-many. Deleting a Collection shall not delete its members. No Collection hierarchy is required in v1.

Tags require no ontology.

Classification is controlled/configurable and correctable. Changing classification shall not change permanent identity or physically reorganize preservation originals.

### 26. File Model
At minimum preserve immutable File global ID, friendly File ID, original filename, managed/display/export filename, byte size, SHA-256, MIME/media type where useful, ingestion datetime, provenance, Storage Volume, relative physical storage reference, File state, restriction status, and derivative relationships where applicable.

Physical storage path and filename shall never constitute File identity.

### 27. Physical File Storage
Store archival Files as ordinary files in managed filesystem storage.

Use stable identity-derived physical names and scalable directory sharding.

Conceptual layout may include originals/, derivatives/, thumbnails/, working/, staging/, and quarantine/. Exact directory names are implementation choices, but semantic distinctions shall remain.

Do not physically organize originals by Person, county, classification, or other changing research interpretation.

Managed human-readable filenames are presentation/export metadata and may change without moving or renaming immutable preservation objects.

Use relative references associated with Storage Volumes rather than machine-specific absolute paths as archival identity.

### 28. Storage Volumes
Implement a lightweight Storage Volume abstraction.

A Volume requires approximately immutable Volume ID, name, purpose/role, filesystem-backed type, configured installation root, and online/offline status.

Initial storage is filesystem-backed. Local disks, mirrored drives, NAS, and removable drives are all filesystem volumes from the application perspective.

Do not implement enterprise storage tiering or mandatory object storage in v1.

An offline backup Volume is normal. An unavailable required primary archival Volume is an operational problem.

### 29. Preservation Originals
Preservation originals become immutable after verified ingestion.

Never rotate them in place, crop them in place, enhance them in place, overwrite them with OCR, rewrite metadata into them, or silently replace them.

Display-only rotation may be metadata. Material transformations produce derivatives.

### 30. Derivatives
A derivative shall have its own File identity, hash, provenance, and parent relationship.

Support lightweight common archival operations such as display rotation, crop, materialized rotation, basic brightness/contrast if implemented, grayscale if implemented, and simple deskew if justified.

Complex restoration and processing remain external-tool responsibilities. Externally processed Files can be imported as derivatives and linked to their source Files.

Do not build a general image editor.

### 31. File Ingestion Protocol
File ingestion shall use a staged, verifiable protocol:
1. Receive/read source.
2. Place/process in controlled staging.
3. Determine byte size.
4. Calculate SHA-256.
5. Check indexed duplicate candidates.
6. Assign File identity as appropriate.
7. Copy into managed preservation storage.
8. Close/flush appropriately.
9. Verify destination content.
10. Commit authoritative active File state.
11. Report successful archival ingestion.

Do not delete the user's source material automatically.

An archival File shall not become Active until its managed copy is verified.

### 32. Exact Duplicate Detection
Use byte size plus SHA-256 for efficient duplicate candidate detection.

When incoming content would otherwise be permanently omitted or destroyed solely because it is believed identical to existing content, perform byte-for-byte verification.

Same size + same SHA-256 + successful byte comparison = exact duplicate.

Exact duplicate physical bytes need not be stored twice, but distinct provenance and relationships must remain representable.

Never falsify provenance through deduplication.

### 33. Redundant Captures
Different byte content depicting the same source subject is not an exact duplicate.

Do not automatically consolidate based on visual similarity.

Provide controlled researcher-driven consolidation: select candidate Files; compare them; designate preferred representation; preview relationships/provenance; preserve appropriate relationships; move redundant Files through recoverable deletion; permit deliberate permanent destruction later.

A redundant independent capture shall not falsely become a derivative of the preferred capture.

Retired File IDs remain permanently retired.

### 34. Tombstones
Where permanent deletion, merge, or consolidation would otherwise make historical references ambiguous, retain lightweight tombstone information.

A File tombstone may include former File identity, status, original filename, SHA-256, provenance summary where appropriate, retirement/removal reason, removal date, and preferred replacement where applicable.

Do not retain unnecessary content merely to create tombstones.

### 35. Filesystem Transactions
PostgreSQL ACID does not make filesystem operations transactional.

Implement an application-level recoverable File-operation protocol using persistent operation IDs, explicit operation states, staging, verification, idempotent retry, safe promotion, quarantine, and durable completion state.

Do not implement distributed transactions.

Prefer same-filesystem atomic rename when appropriate and guaranteed by the filesystem. Across Storage Volumes, use copy -> verify -> commit/promote behavior.

### 36. Failure Recovery
Multi-step archival operations shall be idempotent wherever practical.

After restart, the system must distinguish completed, incomplete, retryable, failed, quarantined, and cleanup-required operations.

Do not infer transaction success merely from filenames.

If state cannot safely be determined: fail visibly and quarantine rather than guess.

NAS/storage interruption shall not create active File records for content that was not successfully preserved. Safe database-only research may continue when appropriate.

### 37. Permanent Deletion
Use recoverable deletion before physical destruction.

Permanent destruction shall be explicit and appropriately authorized.

For destructive operations, preserve/commit necessary retirement and tombstone information before removing physical bytes.

If physical destruction fails, report incomplete cleanup rather than pretending deletion succeeded.

### 38. Capture Sessions and Bulk Import
Capture Sessions shall permit deterministic context to be entered once and inherited by many Files.

Typical inherited information includes Repository, Source/Book, capture date, researcher, equipment/method, and notes.

Inherited values may be overridden appropriately.

Bulk courthouse processing shall support hundreds of Files, thumbnail review, sequential grouping, nonsequential grouping, classification, people/roles, transcription, and partial completion.

Ungrouped Files remain valid archival material.

### 39. Import Templates
Generate import templates from the active data model/configuration. Do not maintain a single giant static CSV specification.

Templates shall distinguish where appropriate System Generated, Required, Optional, Conditional, Immutable, Correctable, and Interpretive.

Use normalized related templates rather than columns such as person_1 and person_2.

Import shall provide validation and preview before commit. Interpretive matching shall not silently resolve identity.

### 40. Multiple-Image Record Grouping
For sequential courthouse capture, support one row per File plus a Start New Record indicator.

Blank means continue preceding Record.

Support explicit record_group for nonsequential or complex grouping. If both are present, explicit record_group is authoritative.

One File may participate in multiple Records.

### 41. Search
Provide one prominent global search.

Search results shall be grouped by useful object type.

Support alternate documented Names, structured roles, historical dates, historical Places, OCR/transcription text, unresolved Mentions, related Records, and fuzzy discovery.

Search results and similarity rankings shall never modify historical information.

PostgreSQL search/index capabilities shall be used first. Do not require Elasticsearch/OpenSearch in v1.

Search indexes are rebuildable and non-authoritative.

### 42. User Interface
Use List-First design.

Major object types shall provide concise one-line summaries for scanning, sorting, filtering, and selection.

Additional metadata, provenance, notes, relationships, evidence, history, and technical identifiers shall be progressively disclosed.

Routine lists shall not resemble database dumps. Use useful historical information before UUIDs or technical jargon.

Minimal valid entry shall remain possible. Warnings shall be proportional. True archival-integrity failures shall be prominent. Optional/incomplete research information shall not be treated as system failure.

### 43. Authentication and Authorization
Use local authentication independent of Internet/cloud identity.

Roles: Administrator, Editor, Viewer.

Use mature password hashing and secure session practices.

Support object-level Normal and Restricted.

Permanent destruction of preservation originals shall be Administrator-controlled by default.

Restrictions must prevent unauthorized access through direct object navigation, File retrieval, search, relationship traversal, export, and guessed identifiers.

Do not infer living/deceased status merely from absence of death date.

### 44. Concurrency
Use optimistic concurrency.

Prevent stale saves from silently overwriting newer changes. Present understandable conflict handling.

Do not implement real-time Google-Docs-style collaborative editing.

### 45. Change and Activity History
Automatically capture significant modification attribution: user, time, changed object, and meaningful change.

Researcher-facing activity history shall remain distinct from low-level technical logs.

Do not require users to document trivial administrative actions. Interpretive rationale may be recorded when meaningful but shall not become friction for ordinary work.

### 46. Logging
Use ordinary structured application/service logs compatible with standard host-platform log rotation and service management on Windows and Linux.

Keep separate diagnostic logs, researcher Change History, and archival operation history.

Do not unnecessarily place sensitive archival content, raw DNA data, passwords, authentication secrets, or session credentials in diagnostic logs.

Logs are operational material, not historical evidence.

### 47. Background Jobs
Use a lightweight persistent PostgreSQL-backed job mechanism.

Do not require Redis, Kafka, RabbitMQ, or a separate message broker.

Appropriate jobs include thumbnails, large imports, hashing, integrity scans, exports, Transfer Packages, backup verification, and rebuildable indexing.

Jobs shall survive application/worker restart. Jobs shall be retryable/idempotent where practical. Failed jobs remain diagnosable.

### 48. Integrity Verification
Implement Quick Integrity Check and Full Integrity Scan.

Quick Integrity Check shall check archive-level database consistency, required Storage Volume availability, expected File presence, recorded File size, storage references, and relevant model invariants.

Full Integrity Scan shall additionally recalculate SHA-256 for protected archival Files and compare against authoritative hashes.

Never silently replace an authoritative stored hash because a current File produces a different hash.

Report missing Files, hash mismatches, unexpected Files, unavailable required storage, and applicable structural failures.

Maintain lightweight scan history.

Controlled repair from a verified copy shall reverify the authoritative hash before returning the File to verified state.

### 49. Complete Archival Backup
A Complete Archival Backup is for disaster recovery.

It shall contain a logically consistent recoverable state including PostgreSQL authoritative data, preservation originals, required non-rebuildable derivatives, Archive ID, archive configuration, relevant permissions/security state, tombstones/retired identities, and other non-rebuildable information.

Rebuildable thumbnails, caches, and search indexes need not be included.

Use established PostgreSQL backup mechanisms rather than inventing a database backup engine.

Application code coordinates archive consistency, manifests, and verification.

### 50. Backup Verification and Restore
Every Complete Archival Backup shall have a manifest.

A backup shall not be considered verified merely because file copying exited successfully.

Provide verification.

Restore must work on different hardware/storage paths.

Restoration shall preserve Archive ID, global IDs, friendly IDs, retired IDs, Files, provenance, relationships, evidence, restrictions, configuration, and research information.

Rebuild disposable artifacts after restoration.

### 51. Complete Archive Export
Complete Archive Export is for long-term application-independent preservation and reconstruction. It is distinct from operational backup.

Export shall contain ordinary preservation Files, important derivatives, complete structured archival information, identities, relationships, provenance, uncertainty, evidence states, research conclusions, configuration, controlled vocabularies, manifests and hashes, and sufficient documentation for independent reconstruction.

Do not require future software to reproduce the current PostgreSQL schema.

### 52. Self-Describing Export
Every Complete Archive Export shall include at minimum:
- plain-text START HERE/README documentation
- archive/export manifest
- export-format version
- archive/schema/model version information
- human-readable data dictionary
- relationship/cardinality documentation
- controlled-vocabulary definitions
- semantic/invariant documentation
- machine-readable schema definitions
- File manifest with relative paths, identities, sizes, and hashes
- reconstruction instructions

The export shall explain meaning, not merely field names.

### 53. Reconstruction Independence
An independent implementer with the Complete Archive Export and its included documentation, but without the original application's source code, internal PostgreSQL schema, or running application, must be able to reconstruct the exported historical information faithfully in another software solution.

Preserve distinctions such as Proposed vs Resolved; source statement vs Conclusion; approximate vs exact dates; parentage vs marriage; Record vs Event; File vs Record; historical creator vs Repository; unresolved Mentions; and retired identifiers.

### 54. Transfer Packages
Implement Transfer Packages for controlled exchange between independent Archive Instances.

Each package shall preserve origin identity and lineage.

Import sequence: Validate -> Analyze -> Preview -> Reconcile -> Commit.

Distinguish exact known identity, exact File content, and candidate historical similarity.

Never silently merge historical objects based on similarity.

Support local friendly-ID collisions without identity corruption.

Dependency closure shall include the minimum supporting information required to preserve selected material's meaning without recursively transferring unrelated archive contents.

Round-trip A -> B -> A and A -> B -> C lineage shall remain intelligible.

Do not implement live synchronization in v1.

### 55. Configuration
Separate archive configuration from installation configuration.

Archive configuration travels appropriately with archive: Archive ID, classifications, Record types, role definitions, relationship types, controlled vocabularies, and archive preferences.

Installation configuration remains local: database connection, secrets, actual filesystem mount paths, and server/network settings.

Do not embed machine-specific absolute paths into portable archival meaning.

### 56. Database Migrations
Use explicit sequential versioned migrations.

Before consequential or destructive migrations, provide or require a recoverable pre-upgrade checkpoint/backup.

Use PostgreSQL transactional migration behavior where appropriate. Run post-migration validation.

Software upgrade shall not silently reinterpret historical information.

Recovery from failed destructive/incompatible migration may use restoration from the pre-upgrade backup rather than requiring an elaborate reverse-migration framework.

### 57. API Boundary
Maintain clean internal service/domain boundaries.

Browser/UI code shall not become a collection of uncontrolled direct database mutations.

No unrestricted public API is required for v1. External API access shall remain disabled unless deliberately added later.

### 58. System Status
Provide a small Administrator system-status view.

At minimum expose useful status for database, required Storage Volumes, background worker, pending jobs, failed jobs, last verified backup, last integrity scan, and relevant available storage.

Do not require an enterprise monitoring platform.

### 59. AI Boundary
Do not implement embedded AI connectivity in v1.

The archive shall not require AI for operation, search, preservation, import, export, backup, restoration, evidence management, or research functionality.

No AI system shall have implicit continuous or unrestricted archive access.

Future external analysis may follow: selected material out -> external analysis -> proposed result back -> researcher review -> controlled import.

AI suggestions shall never silently become historical conclusions.

### 60. Explicitly Prohibited v1 Architecture
Do not introduce without a separately approved demonstrated requirement:
- microservices
- Kubernetes
- distributed database
- graph database as authoritative store
- generic EAV architecture
- Elasticsearch/OpenSearch
- Redis
- Kafka
- RabbitMQ
- cloud requirement
- cloud identity requirement
- mandatory S3/object storage
- event sourcing
- blockchain
- live site synchronization
- real-time collaborative editing
- enterprise permission engine
- enterprise workflow engine
- public social features
- project-management system
- mandatory GIS
- genomic-analysis platform
- embedded AI access

Do not add infrastructure merely because it is fashionable or theoretically scalable.

### 61. Prohibited Historical Inference
The application shall never automatically:
- create Marriage because two Persons share a child
- infer parentage from Marriage
- infer ownership from residence
- infer residence from ownership
- infer relationship from co-occurrence
- manufacture missing ancestors
- manufacture intermediate relatives
- manufacture migration Events from changed locations
- resolve identity from fuzzy matching
- merge Persons from matching names/dates
- merge distinct Files from visual similarity
- convert approximate dates into exact dates
- convert a source statement into a Conclusion
- discard contradictory Evidence
- treat absence from the archive as Expected/Absent Evidence
- treat OCR as reviewed transcription
- treat Access Provider as historical Repository/creator
- infer living/deceased status solely from missing death date

### 62. Implementation Phases
Phase 0 - Foundation: source repository, development/test environments, Windows and Linux deployment baselines from one portable codebase, PostgreSQL, migration framework, modular-monolith structure, automated test framework, configuration framework, logging, and cross-platform path/service abstractions. Gate: foundation tests pass on both supported host platforms.

Phase 1 - Archive Identity and Core Infrastructure: Archive Instance, Archive ID, users, global/local IDs, configuration, common history, optimistic concurrency, Storage Volume model, background-job foundation. Gate: identity and infrastructure acceptance tests pass.

Phase 2 - File Preservation: File, staging, hashing, ingestion, storage, duplicates, derivatives, File states, transaction/recovery protocol, thumbnails, integrity checks. Gate: applicable File, transaction and integrity tests pass. Do not proceed with domain convenience features while File preservation is unreliable.

Phase 3 - Repository, Source and Archival Records: Repository, Source, Capture Session, Archival Record, Citation foundation, File-to-Record many-to-many, courthouse grouping, classifications, Record roles. Gate: courthouse workflow tests pass.

Phase 4 - People, Dates and Places: Person, Names, relationships, collective references, Historical Date, Place, Place Name, Jurisdiction, unresolved Mentions, Candidate Resolution foundation. Gate: Person/date/place tests pass, especially marriage-parentage invariants.

Phase 5 - Evidence Architecture: Source Statement, Evidence, Claim, Conclusion, Candidate Resolution history, Expected/Absent. Gate: evidence and Candidate Resolution tests pass.

Phase 6 - Remaining Historical Models: Property, Structure, Event, Organization, DNA Test, Research objects, Collections, Tags. Gate: applicable domain tests pass.

Phase 7 - Search and Bulk Workflows: global search, structured filters, OCR/transcription discovery, self-describing import templates, import preview, bulk operations, list-first UI refinement. Gate: search/import/UI tests pass.

Phase 8 - Preservation & Interchange: Complete Archival Backup, restore, Complete Archive Export, self-describing reconstruction documentation, Transfer Packages, package validation/reconciliation. Gate: backup/export/transfer tests pass.

Phase 9 - Security & Operational Completion: restrictions, permission traversal, system status, operational diagnostics, migration validation, deployment hardening. Gate: security/deployment tests pass.

Phase 10 - Full Acceptance: run the Reference Acceptance Archive and Clean-Room tests from Acceptance Specification v1.1. No v1 release until release-blocking tests pass.

### 63. Testing Strategy
Use Acceptance Specification v1.1 as the authoritative behavioral test catalog.

Implement tests at the lowest sensible level:
- unit tests for deterministic domain rules
- PostgreSQL integration tests for constraints and transactions
- filesystem integration tests
- service tests
- security tests
- import/export round-trip tests
- failure-injection tests
- browser/UI acceptance tests where required
- backup/restore tests
- Transfer Package tests
- clean-room reconstruction tests

Not every expensive preservation test must run on every commit.

Maintain a Fast Development Suite, Phase-Gate Suite, and Full Release Suite.

### 63A. Cross-Platform Test Matrix
The same core automated test suite shall run against supported Windows and Linux environments.

At minimum, platform acceptance shall exercise:
- clean installation and configuration
- PostgreSQL connection and migrations
- application and background-worker startup
- Storage Volume configuration using native host paths
- portable relative-path persistence
- filesystem staging/copy/verification behavior
- backup and restore behavior
- Complete Archive Export generation and validation
- Transfer Package generation, import, round trip, and origin-lineage preservation across Windows and Linux

Tests shall explicitly detect accidental assumptions about POSIX-only paths, Windows-only drive letters, path separators, filename case behavior, file locking, atomic rename behavior, and service management.

Cross-platform support shall not be implemented as separate historical/domain codebases. Platform adapters may differ; archival semantics shall not.

### 64. Reference Acceptance Archive
Maintain a version-controlled synthetic Reference Acceptance Archive containing historical edge cases required by Acceptance Specification v1.1.

It shall not contain real private user data.

Use it repeatedly for migrations, imports, exports, backup/restore, Transfer Packages, search, permissions, and clean-room reconstruction.

The Reference Archive becomes a permanent regression fixture.

### 65. Database Constraints
Where an invariant can safely be enforced by PostgreSQL without preventing legitimate historical ambiguity, enforce it in PostgreSQL as well as application logic.

Examples include primary keys, unique global IDs, appropriate unique local IDs, foreign keys, required technical fields, valid enum/reference values, and appropriate check constraints.

Do not encode historically false assumptions as database constraints.

Do not create constraints requiring exactly two parents, exactly one mother, exactly one father, marriage between parents, one birth date, one Property acreage, or one historical name.

### 66. Performance Philosophy
Optimize for expected personal/small-institution archive scale.

The system shall comfortably handle realistic collections including thousands of courthouse images and substantially larger long-term File collections.

Large imports, scans, and exports shall run as background operations and shall not require one enormous transaction or keeping all content in browser memory.

Do not compromise archival correctness for performance. Add specialized infrastructure only after measured performance demonstrates need.

### 67. Coding Style and Maintainability
Prefer explicit models, understandable services, typed interfaces where supported, straightforward SQL/database mappings, small cohesive modules, documented archival invariants, deterministic operations, and reproducible tests.

Avoid excessive metaprogramming, clever generic abstractions hiding domain meaning, unnecessary framework-specific magic, undocumented side effects, and implicit data mutation.

A future developer should be able to understand why an archival rule exists from code and tests.

### 68. Dependency Policy
Use mature, actively maintained, widely deployed dependencies.

Do not add a dependency for functionality adequately supplied by PostgreSQL, operating system, filesystem, standard library, or existing application framework.

Pin/version dependencies appropriately for reproducible deployment. Security-critical dependencies shall be maintained and updateable.

Do not make archival readability depend solely upon an undocumented third-party library format.

### 69. Error Philosophy
Errors shall distinguish between normal incomplete research, validation problem, operational failure, and archival-integrity failure.

Do not treat unknown historical information as a software error.

Do not hide archival-integrity failures behind generic success/failure messages.

Prefer researcher-facing messages such as: "Import interrupted. 137 of 200 Files were safely archived. Resume import?"

Technical details belong in diagnostics.

### 70. Definition of Done
A feature is not complete because its database table exists, its API returns data, its screen renders, or its happy-path demo succeeds.

A feature is complete only when:
1. applicable archival invariants are preserved
2. applicable Acceptance Specification tests pass
3. failure behavior is defined and tested
4. security behavior is correct
5. significant operations are recoverable where required
6. export/backup implications have been considered
7. documentation reflects material behavior

### 71. Agent Behavior Rules
Coding agents shall:
- implement only the current approved phase unless explicitly instructed otherwise
- inspect existing architecture before adding infrastructure
- reuse existing domain/service patterns where they preserve semantics
- add tests with implementation
- run applicable tests before declaring work complete
- report failed acceptance tests
- identify migration implications
- preserve backward compatibility with stored archival information where required
- avoid unrelated refactoring during focused implementation
- avoid silently changing finalized domain semantics

Coding agents shall not:
- broaden scope because an enhancement seems useful
- substitute a generic model for explicit historical concepts
- add required metadata solely for developer convenience
- weaken acceptance tests to make implementation pass
- remove contradictory data because it looks inconsistent
- automatically repair historical ambiguity
- introduce a prohibited v1 dependency
- expose database complexity directly in routine UI
- modify preservation originals
- silently discard data during migrations/imports
- invent historical semantics when specification is ambiguous

### 72. Required Agent Completion Report
At the end of each implementation task or phase, the coding agent shall report:
1. what was implemented
2. schema/migration changes
3. Files/storage behavior affected
4. acceptance tests implemented/run
5. test results
6. known limitations
7. unresolved specification questions
8. any deviations from this specification

A deviation shall not be hidden merely because the implementation works.

### 73. Final v1 Release Gate
Version 1 may be considered acceptable only when:
- applicable Acceptance Specification v1.1 tests pass
- the Reference Acceptance Archive passes
- File preservation and recovery tests pass
- Quick and Full Integrity verification pass
- Complete Archival Backup can restore the archive onto a clean installation
- Complete Archive Export passes verification
- clean-room reconstruction demonstrates software-independent intelligibility
- Transfer Package A-to-B and round-trip tests pass
- restrictions prevent unauthorized access
- migrations have been tested
- no prohibited infrastructure has become required
- no archival invariant has been knowingly weakened

> Preserve the evidence, preserve the uncertainty, preserve the provenance, preserve the identity, and keep the researcher's ordinary work simple.
