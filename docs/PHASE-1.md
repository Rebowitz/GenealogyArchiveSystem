<!-- Phase 1 brief as issued by the project owner on 2026-10-28; reproduced verbatim. Owner decisions on the pre-coding report: PHASE-1-DECISIONS.md -->
Genealogy Archive System
Phase 1 — Archive Identity / Core
Phase 0 has been formally accepted.

Accepted Phase 0 baseline:

f326366172759d0b9e94c87db51434237834d550

Accepted tag:

phase-0-accepted

The repository is now named:

GenealogyArchiveSystem

You are authorized to begin Phase 1 only.

Do not begin Phase 2 or later functionality.

1. Governing authority
Continue applying requirements in this order:
1. Archival invariants and explicitly locked project requirements
2. Genealogy Archive System Acceptance Specification v1.1
3. Genealogy Archive System Coding-Agent Build Specification v1.1
4. This approved Phase 1 brief
5. Accepted Phase 0 architecture and implementation
6. Implementation convenience
Do not reinterpret previously accepted requirements merely because another implementation would be easier.

Do not weaken accepted Phase 0 behavior.

2. Project identity
This is the Genealogy Archive System.

Genealogy is its primary purpose and organizing domain.

Historical records, places, properties, organizations, DNA material and other historical information are modeled because they support genealogical research. Do not genericize the application into a general historical-research archive.

3. Phase 1 objective
Implement the minimum durable Archive Identity / Core foundation required for later genealogy-domain objects.

Phase 1 establishes:
- persistent Archive Instance identity;
- globally unambiguous underlying object identity conventions;
- archive-local friendly identifier infrastructure;
- archive configuration distinct from machine/installation configuration;
- core lifecycle/history infrastructure required by the Phase 1 specification;
- the minimum authentication/authorization foundation assigned to Phase 1 by the governing build specification;
- durable core invariants needed by subsequent phases.
Keep the implementation lightweight.

Do not introduce genealogy-domain objects merely to demonstrate the core infrastructure.

4. Archive identity
Each independent installation represents an Archive Instance with its own durable Archive ID.

The Archive ID must:
- be globally unique;
- persist across normal application restarts;
- survive backup/restore of the same archive;
- travel where the governing Complete Export/Transfer specifications later require archive-origin identity;
- never depend on hostname, Windows machine identity, Linux machine identity, filesystem path, database name, repository name or network address;
- never silently change;
- distinguish independent archives.
Do not confuse:
- Archive ID;
- installation/machine configuration;
- database/schema identity marker;
- application version;
- repository identity.
The existing Phase 0 schema_identity marker is not the Archive ID.

Establish explicit tests for these distinctions.

5. Object identity foundation
Implement the common identity mechanism required for later persistent archive objects.

Underlying identity must be globally unambiguous.

Use the previously approved UUIDv7 approach where supported by the application architecture.

Friendly archive-local identifiers may coexist with global IDs.

Examples of future friendly identifiers include:
- P00127
- F03711
- R01842
- PR0041
Do not create Person, File, Record or Property objects in Phase 1 merely to consume these prefixes.

Instead, establish the reusable infrastructure needed for later typed objects.

Requirements:
- permanent IDs are never reused;
- deletion, retirement, consolidation or merge must not make an identifier available for reuse;
- friendly IDs are presentation/researcher identifiers, not the underlying globally unique identity;
- filenames, paths, names and database row numbers must not become object identity;
- identifier generation must not require a distributed service;
- Windows and Linux must produce semantically equivalent identifiers.
Avoid speculative identifier machinery beyond what later phases actually require.

6. Archive configuration versus installation configuration
Phase 0 established machine/installation configuration.

Phase 1 shall establish the durable archive configuration boundary.

Archive configuration represents settings that belong to the archive itself and therefore travel with the archive where required.

Installation configuration remains machine-local.

Examples of archive-level configuration expected eventually include controlled classifications, relationship types, record types, roles, vocabularies, Archive ID and archive preferences.

Do not implement later-phase vocabularies merely because the configuration mechanism can support them.

Phase 1 should establish the mechanism and only the minimum Phase 1 configuration necessary to prove it.

Tests must demonstrate that:
- Archive ID is archive-level;
- database connection information is installation-level;
- Storage Volume mount/root locations are installation-level;
- machine secrets are installation-level;
- archive configuration is not silently sourced from machine-local settings;
- archive-level configuration can later participate in Complete Export/Transfer without exposing machine-specific configuration.

7. Immutable / Correctable / Interpretive foundation
Preserve the locked three-category distinction:
Immutable
Examples include permanent IDs and immutable provenance identifiers.
Correctable
Information that may legitimately be corrected while preserving significant history.
Interpretive
Research conclusions, hypotheses, identity resolutions and similar information that evolves with evidence.

Phase 1 should implement only the core mechanism actually required now.

Do not build the later Evidence, Claim, Conclusion or Candidate Resolution models in Phase 1.

Do not create a generic EAV framework to model these categories.

8. Change/history foundation
Implement the minimum reusable foundation required to preserve significant archive changes.

Maintain the distinction between:
- technical diagnostic logs;
- researcher-facing Change History;
- archival Operation History.
Do not turn Change History into surveillance or a low-level database transaction log.

The eventual system must be able to answer appropriate questions such as what materially changed, when, and by whom.

Do not require researchers to provide administrative explanations for ordinary work.

Interpretive “why” information remains optional unless a later specific workflow requires it.

9. Authentication and authorization boundary
Follow the exact Phase 1 responsibilities assigned by the governing Build and Acceptance Specifications.

The approved security model ultimately uses:
- Administrator
- Editor
- Viewer
with local authentication independent of commercial identity providers.

Do not introduce:
- social login;
- mandatory cloud identity;
- enterprise IAM;
- LDAP/Active Directory dependency;
- public profiles;
- external authentication dependency.
Use mature password hashing and secure session practices.

Authorization must occur at the application/service boundary rather than relying solely on hidden UI controls.

Do not prematurely implement later object-level restriction workflows beyond what Phase 1 requires.

If the governing specification places part of this functionality in a later phase, follow the specification rather than expanding Phase 1.

10. Lightweight-first requirement
The locked principle remains:

Complexity required for archival integrity belongs primarily beneath the interface. Routine research and data entry should remain simple.

And:

Future capability shall be enabled by extensible architecture, not by prematurely exposing complexity in the initial implementation.

Phase 1 must not become a generic framework-building exercise.

Implement only infrastructure justified by an approved Phase 1 requirement.

11. No generic entity architecture
Do not introduce a universal Entity table, generic entity/attribute/value system, arbitrary graph model or similar abstraction intended to represent all future genealogy concepts.

Later phases require explicit domain models.

Common infrastructure is appropriate.

Generic replacement of the domain model is not.

PostgreSQL remains the sole authoritative relational database.

12. Preserve Phase 0 invariants
All accepted Phase 0 behavior must continue to pass.

In particular preserve:
- Java 21;
- Spring Boot 4.1.x;
- Maven Wrapper;
- explicit SQL / Spring JdbcClient;
- no ORM/JPA;
- Flyway migration discipline;
- PostgreSQL 16 minimum;
- PostgreSQL 18 compatibility;
- Windows/Linux single portable codebase;
- platform isolation;
- portable path rules;
- Storage Volume ID + relative path model;
- secret-handling protections;
- canonical JDBC validation;
- health/readiness behavior;
- JSON logging;
- native Windows/Linux service support;
- offline-capable architecture;
- GitHub Actions native Windows/Linux verification;
- prohibited-infrastructure architecture tests.
Do not weaken a Phase 0 test to accommodate Phase 1.

13. Explicitly prohibited Phase 1 expansion
Do not implement Phase 2+ functionality, including:
- archival file ingest;
- hashing/deduplication workflow;
- thumbnails;
- image processing;
- OCR;
- transcription;
- Repository/Source/Archival Record domain models;
- Person;
- Person Name;
- genealogical relationships;
- Historical Date model;
- Historical Place model;
- Property/Structure;
- Events;
- Organizations;
- DNA Evidence;
- Candidate Resolution;
- Source Statement/Evidence/Claim/Conclusion;
- research questions/hypotheses/actions;
- Collections/Tags beyond anything explicitly required by Phase 1;
- global historical search;
- Complete Archive Export;
- Transfer Packages;
- Complete Archival Backup/Restore;
- embedded AI;
- live synchronization;
- distributed replication;
- cloud runtime dependency.
Phase 1 should leave clean extension points for these without implementing them.

14. Cross-platform requirement
Everything introduced in Phase 1 must remain first-class on both:
- Windows
- Linux
Do not encode:
- drive letters;
- POSIX mount paths;
- path separators;
- hostname assumptions;
- OS-specific identifiers;
- service-manager assumptions
into archival identity or domain semantics.

Platform-specific implementation belongs behind the accepted Phase 0 platform boundary.

15. Testing
Add deterministic tests for all Phase 1 invariants.

At minimum, tests should cover applicable requirements involving:
- Archive ID creation;
- Archive ID persistence;
- Archive ID immutability;
- distinction between Archive ID and schema identity;
- global object identity generation;
- friendly ID behavior;
- identifier non-reuse where Phase 1 lifecycle operations exercise it;
- archive configuration persistence;
- machine/archive configuration separation;
- change-history behavior;
- authentication/security behavior actually implemented in Phase 1;
- authorization enforcement actually implemented in Phase 1;
- migration correctness;
- upgrade behavior from the accepted Phase 0 schema;
- PostgreSQL 16;
- PostgreSQL 18;
- Windows/Linux equivalence;
- restart behavior;
- readiness implications of the new Phase 1 state.
Do not use test names that imply broader guarantees than the assertions establish.

Tests should prove behavior, not merely implementation details.

16. Migration requirements
Phase 1 must upgrade cleanly from the accepted Phase 0 database state.

Do not edit an accepted Phase 0 migration.

Create new sequential migration(s).

Verify:

accepted Phase 0 database → Phase 1 migrations → valid Phase 1 state

Migration failure must continue to fail visibly and safely.

Do not silently repair or reinterpret inconsistent archival identity.

17. CI
Run the complete suite against:
- native Windows + PostgreSQL 16;
- native Windows + PostgreSQL 18;
- native Linux + PostgreSQL 16;
- native Linux + PostgreSQL 18.
All accepted Phase 0 tests remain part of the suite.

Packaged smoke and native service checks remain required.

A Phase 1 completion report must not claim success unless the complete matrix passes.

18. Development workflow
Create a dedicated Phase 1 branch and pull request.

Do not work directly on main.

Do not merge the Phase 1 PR.

Claude Code remains the implementation agent.

Codex will independently review the completed Phase 1 implementation before acceptance.

Do not ask Codex or another AI provider to modify the Phase 1 implementation.

No DeepSeek access to this project is authorized.

19. Before coding
Before modifying the repository, produce a concise Phase 1 pre-coding report containing:
1. exact Phase 1 requirements you believe apply;
2. proposed database additions;
3. proposed Java/module additions;
4. proposed archive-configuration mechanism;
5. proposed identity mechanism;
6. proposed history mechanism;
7. authentication/authorization work you believe belongs in Phase 1;
8. migration plan from the accepted Phase 0 baseline;
9. test plan;
10. any genuine specification ambiguities requiring owner decision.
Do not ask for routine implementation preferences.

Do not start coding until genuine owner-level ambiguities, if any, have been resolved.

If there are no genuine owner-level ambiguities, explicitly say so and proceed after presenting the report.

20. Phase 1 completion report
When implementation is complete, report:
- starting Phase 0 baseline/tag;
- Phase 1 head SHA;
- files changed;
- migrations added;
- schema additions;
- identity implementation;
- archive configuration implementation;
- history implementation;
- authentication/authorization implementation;
- tests added;
- total test counts;
- four-way CI results;
- packaged smoke/service results;
- acceptance-specification coverage;
- known limitations;
- explicitly deferred Phase 2+ functionality;
- deviations from this brief, if any;
- confirmation that Phase 2 was not started;
- PR number and status.
Do not merge the Phase 1 PR.

Do not declare Phase 1 accepted yourself.

Acceptance follows independent review.