# Genealogy Archive System - Acceptance Specification v1.1

Genealogy Archive System

                                   Acceptance Specification v1.1
 Finalized acceptance baseline for the lightweight personal Genealogy Archive System




Purpose
This specification defines the scenario-based acceptance requirements that determine whether the archive behaves
correctly. It is intentionally stronger than a feature checklist: archival identity, provenance, uncertainty, preservation,
recovery, security and interchange requirements remain binding even when a normal user interface appears to work.


Governing principle
      Complexity required for archival integrity belongs primarily beneath the interface. Routine research and data
      entry should remain simple.


Test structure
Each test identifies a scenario or action and states the required and/or forbidden result. Applicable tests are
release-blocking. A feature is not complete merely because its happy-path interface works.





1. Global Acceptance Rules
A-001 - Permanent identity survives ordinary change
Scenario: Create a Person, File, Archival Record, Property, Place or other durable object. Subsequently change any
correctable attribute: name, classification, description, date, repository information, managed filename, etc.
Required: The object's immutable global identity remains unchanged. Its friendly local ID also remains unchanged unless an
explicit administrative ID-correction mechanism is later defined.
Forbidden: Delete-and-recreate behavior merely because descriptive information changed.

A-002 - Retired identifiers are never reused
Create an object, retire/delete/merge it according to the applicable workflow, and then create additional objects.
Required: Neither its global ID nor friendly local ID is assigned to another object. Gaps in numbering are valid.
Example: F01001 retired; F01002 active; F01003 newly created. F01001 must never subsequently identify unrelated
content.

A-003 - Incomplete information is valid
Create objects with only the minimum information actually known. Examples include an unidentified photograph, Person with
uncertain name, deed with unreadable party, unknown repository, approximate date, unresolved place, unknown property, or
Record awaiting classification.
Required: Save succeeds when archival integrity does not genuinely require the missing information.
Forbidden: Inventing placeholder historical facts merely to satisfy database or UI requirements. This directly tests the
Researcher Friction Principle.

A-004 - Original source expression is preserved
Enter historically reported information and subsequently normalize or interpret it.
Example: Source expression "abt March 1847"; interpretation "approximately March 1847."
Required: The original expression remains available.
Forbidden: Replacing the source wording with the normalized interpretation.

A-005 - Uncertainty remains uncertainty
Enter an approximate, disputed, proposed, unresolved or otherwise uncertain fact.
Required: The uncertainty survives saving, searching, export, backup, restoration and Transfer Package processing.
Forbidden: Software silently converting uncertainty into certainty.

A-006 - Suggestions never become conclusions automatically
Cause the system to identify a possible matching Person, Place, Property, Record or other object.
Required: It remains a candidate/proposal until explicitly accepted through the appropriate research action.
Forbidden: Similarity, search ranking, matching names, OCR, dates or automated processing establishing historical identity.

A-007 - Source information and researcher conclusions remain distinct
Record what a source states and a researcher conclusion derived from it.
Required: Both remain independently identifiable and traceable.
Forbidden: Researcher interpretation overwriting the source statement.





A-008 - Correcting metadata does not alter preservation content
Import an archival File and subsequently change its classification, managed filename, description, Record association, tags
or other correctable metadata.
Required: Preservation-original bytes and SHA-256 remain unchanged.
Forbidden: Rewriting the original File merely to make its metadata agree with the correction.

A-009 - No relationship by coincidence
Associate two Persons with the same Record, Event, Property, Place, household, Capture Session or Organization.
Required: No additional Person relationship is created unless explicitly supported or entered.
Forbidden: Inferring spouse, parent, sibling, household or other historical relationships solely from co-occurrence.

A-010 - Historical contradictions are permitted
Enter two legitimate sources that disagree, for example Source A reports birth 1841 and Source B reports birth 1843.
Required: Both can coexist and be evaluated.
Forbidden: Database uniqueness rules or UI behavior forcing one to replace the other.



2. File Preservation & Ingestion
F-001 - Verified ingestion
Import a photograph.
Required sequence: source -> staging -> byte size + SHA-256 -> copy to managed storage -> verify stored copy -> commit
active File.
Only after successful verification may the system report the File as safely archived. The source File remains untouched.

F-002 - Interrupted ingestion
Interrupt the system during File copying.
Required: On restart, the operation is identifiable as incomplete. The system must safely resume, restart, quarantine or
abandon the incomplete operation according to recoverable state.
Forbidden: Reporting the incomplete File as a valid preservation original.

F-003 - Exact duplicate
Import a byte-identical copy of an existing File.
Required: Size and SHA-256 identify the duplicate candidate. Before permanent omission/destruction of incoming content
solely because it is considered identical, byte-for-byte equality is confirmed. The system may avoid storing duplicate physical
bytes while preserving legitimate new provenance and relationships.
Forbidden: Creating false provenance implying the earlier capture was the newly imported capture.

F-004 - Same photograph, changed bytes
Import a photograph, then import a rotated, recompressed or metadata-modified version.
Required: Different bytes result in a distinct File identity and hash.
Forbidden: Treating visually similar material as an exact duplicate merely because it looks the same.

F-005 - Multiple independent captures
Import four photographs of the same courthouse page with different exposures or focus.



Required: Four distinct Files may coexist. Metadata can group them for viewing.
Forbidden: Automatic deletion or consolidation based on visual similarity.

F-006 - Redundant capture consolidation
Researcher selects four Files and explicitly designates F01003 as preferred.
Required: The system previews relationships and provenance that could be affected before consolidation. Redundant Files
can enter recoverable deletion. Necessary relationships are preserved appropriately.
Forbidden: Rewriting provenance to claim F01003 was the historical source of relationships belonging specifically to another
capture.

F-007 - Permanent redundant-File deletion
Permanently delete F01001 after consolidation.
Required: Physical bytes may be removed. F01001 remains retired and never reusable. Appropriate tombstone information
remains.

F-008 - Preservation original immutability
Attempt to crop, rotate, enhance or alter a preservation original.
Required: Original remains byte-identical. Operation is either display metadata or produces a derivative.

F-009 - Derivative provenance
Create a cropped derivative from F01003.
Required: New File identity and SHA-256. Parent F01003 remains identifiable. Transformation and provenance are recorded
sufficiently for their purpose.

F-010 - Storage Volume unavailable
Disconnect or unmount the primary NAS.
Required: Operations requiring that volume stop safely and explain why. Safe database-only activity may continue.
Forbidden: Creating active File records for content that was never successfully stored.



3. Archival Records & Courthouse Workflow
R-001 - Multi-page deed
Import photographs representing pages 227-229 of one deed.
Required: One Archival Record can reference all relevant Files. Each File remains independently identified. Court/source
page number remains distinct from image sequence.

R-002 - Multiple captures of one page
Two photographs represent page 227.
Required: Both can associate with the same Record/page and appear together in the viewer. No redundant-capture
conclusion is required.

R-003 - One photograph contains two Records
A courthouse photograph contains the end of Deed A and beginning of Deed B.
Required: The same File may associate with both Records. This permanently tests the File-to-Record many-to-many rule.





R-004 - Start-New-Record worksheet
Import a sequential courthouse batch using Start New Record = YES on appropriate rows.
Required: Subsequent blank rows continue the preceding Record until another start marker. Where explicit record_group is
supplied, it is authoritative.

R-005 - Stop halfway
Begin processing 200 courthouse images and stop after 83.
Required: The remaining 117 ungrouped or unclassified Files remain valid archived material.
Forbidden: Requiring the researcher to finish indexing the batch.

R-006 - Classification correction
Classify R01842 as DEED, then correct it to DTRUST.
Required: Record ID and associated File IDs remain unchanged. Managed presentation names may update according to
configured naming rules. Preservation objects are not physically renamed merely because classification changed.



4. People & Relationships
P-001 - Multiple names
Person has birth name, married name, nickname and variant spelling.
Required: All can coexist with provenance and context. Changing preferred display Name does not delete or invalidate the
others.

P-002 - Parents never married
A and B are biological parents of C. No marriage is known.
Required: Both parent relationships are accepted.
Forbidden: Creating Marriage Event, spouse relationship or warning requiring marriage.

P-003 - Child predates marriage
A and B marry in 1852. C is their child and was born in 1849.
Required: Valid without data-error warning based solely on chronology.

P-004 - Remarriage
A marries B and later C. Children exist from one or both relationships.
Required: Parentage remains directly associated with the appropriate Persons.
Forbidden: Assigning children to parents merely because of marriage membership.

P-005 - Competing parentage
Evidence supports two competing candidates for one parent.
Required: Both hypotheses or Claims may coexist until research resolves them.
Forbidden: Exactly-one-parent database constraint forcing premature resolution.

P-006 - Guardian is not parent
Source identifies A as guardian of B.
Required: Guardian relationship recorded.



Forbidden: Parentage inferred.

P-007 - Apprenticeship is not family
A is apprentice to B.
Required: Apprenticeship relationship may be represented.
Forbidden: Parent, guardian or household relationship automatically generated.

P-008 - Collective reference
Will states "to the children of my deceased son John" and individual children are not yet identified.
Required: Statement can be preserved without manufacturing individual Person records.

P-009 - Direct grandparent statement
Source explicitly identifies A as B's grandfather, but intermediate parent is unknown.
Required: Historical relationship can be represented without creating fictional intermediate Person.

P-010 - Duplicate Persons
Two Person objects later appear to represent the same individual.
Required: They may coexist during research. Controlled merge or resolution preserves identity and history.
Forbidden: Automatic merge based on matching name or date.



5. Historical Dates & Places
D-001 - Partial date
Enter March 1847.
Forbidden result: 1847-03-01. Month precision must remain month precision.

D-002 - Approximate year
Enter about 1847.
Required: It remains approximate.

D-003 - Before, after and range
Before 1847, after 1847 and 1845-1849 remain structurally distinct.

D-004 - Conflicting dates
Conflicting source dates coexist.

D-005 - Calendar distinction
Julian, Gregorian and dual-date information survives round trips where supplied.

L-001 - Historical place name
Historical and current names can refer to the same Place without changing Place identity.

L-002 - Jurisdiction change
A Place changes county or state jurisdiction over time.
Required: Historical jurisdiction can be represented without changing physical Place identity.




L-003 - Unresolved place
Source contains ambiguous place wording.
Required: Preserve wording and unresolved state without requiring premature Place creation or resolution.

L-004 - Property is not Place
Creating a farm or property located at a Place does not collapse Property and Place into one object.



6. Sources, Evidence, Claims & Conclusions
E-001 - Source statement without conclusion
A deed states "John Smith and wife Mary."
Required: The statement may exist without requiring a Claim or Conclusion.
Forbidden: Automatically establishing a marriage solely because the phrase was transcribed.

E-002 - Statement supports a Claim
A deed identifies William Andrews as owner of a tract. Researcher creates an ownership Claim and associates the statement
as supporting Evidence.
Required: Statement, Evidence and Claim remain distinguishable and traceable.

E-003 - Contradictory Evidence
Source A supports birth 1841. Source B supports birth 1843.
Required: Both Evidence relationships coexist. The researcher may later conclude one is more persuasive.
Forbidden: Automatically discarding or overwriting either.

E-004 - Evidence can contradict
Create Claim C001. Associate one Source Statement as SUPPORTS and another as CONTRADICTS.
Required: Both associations remain independently represented.

E-005 - Indeterminate Evidence
A source is relevant but cannot reasonably be interpreted as supporting or contradicting the Claim.
Required: INDETERMINATE is accepted.
Forbidden: Forcing a binary support or contradict choice.

E-006 - Expected/Absent requires expectation
Researcher examines a complete tax list in which an individual reasonably should appear if resident or eligible under the
research hypothesis. No entry is found.
Required: Researcher may explicitly create Expected/Absent Evidence describing expected information, reason for
expectation and examined scope.

E-007 - Missing from archive is not Expected/Absent
The archive contains no 1847 tax record.
Required: Nothing happens.
Forbidden: System generating Expected/Absent Evidence merely because the archive lacks the document.





E-008 - Failed search is not automatically Expected/Absent
Researcher searches a catalog and does not locate the desired source.
Required: May be recorded as Research Action No Relevant Result.
Forbidden: Automatically creating Expected/Absent Evidence.

E-009 - Conclusion changes
Researcher concludes A is father of B. Later evidence changes the conclusion.
Required: Current Conclusion can change while sufficient history and provenance remain to understand the prior conclusion.
Forbidden: Altering underlying source statements to make them agree with the new conclusion.

E-010 - Conclusion without deleting conflicting evidence
Researcher concludes birth occurred in 1843 despite a source reporting 1841.
Required: 1841 Evidence remains. A Conclusion does not erase disagreement.

E-011 - Selective Claim creation
Index 50 names from a deed book.
Required: They may be indexed without creating 50 research Claims.
Forbidden: Treating every extracted observation as a Claim requiring adjudication.

E-012 - Provenance chain
Select a Conclusion and request its evidence/provenance.
Required: Researcher can navigate sufficiently through Conclusion -> Claim(s) -> Evidence -> Source Statement ->
Citation/Record/Source -> File where applicable, without manually reconstructing database relationships.



7. Candidate Resolution
C-001 - Unresolved Person Mention
A will refers to "my nephew Robert"; identity unknown.
Required: Mention may remain unresolved indefinitely.
Forbidden: Requiring creation of a Person merely to save transcription or indexing.

C-002 - Multiple candidates
Archive contains three plausible Robert Andrews Persons.
Required: All three may be proposed candidates.
Forbidden: Highest search score becoming the resolved identity automatically.

C-003 - Proposed is visibly different from Resolved
Researcher proposes P00127.
Required: UI and stored state distinguish Proposed from Resolved.

C-004 - Explicit resolution
Researcher evaluates evidence and explicitly resolves the Mention to P00127.
Required: Resolution is recorded as a deliberate research decision. Original source wording remains preserved.




C-005 - Resolution reconsidered
Later evidence shows P00127 was wrong.
Required: Resolution can be superseded or rejected and another candidate considered.
Forbidden: Destructive rewriting that makes it appear the original resolution never occurred.

C-006 - Cross-domain Candidate Resolution
Repeat unresolved -> proposed -> resolved behavior for a Place, Property, Structure, Organization or other supported entity.
Required: Same conceptual framework works without unrelated special-purpose resolution systems.



8. Property & Structures
PR-001 - Ownership independent of identity
Create Property PR0041 owned by A. Later B owns it.
Required: PR0041 remains the same Property.
Forbidden: Property identity defined by current owner.

PR-002 - Acreage disagreement
Deed A describes 100 acres. Deed B describes 96 acres.
Required: Both descriptions may coexist.
Forbidden: Automatically replacing one or averaging them.

PR-003 - No arithmetic remainder property
A 100-acre tract conveys 40 acres.
Forbidden: Automatically creating a 60-acre Property. Remaining extent requires historical evidence or research.

PR-004 - Property Mention unresolved
Source references "the old mill tract" but researcher cannot yet identify it.
Required: Preserve unresolved Property Mention.

PR-005 - Property division
Evidence establishes that Property A was subdivided into B and C.
Required: Relationship and history can be represented without changing immutable identity of A, B or C.

PR-006 - Ownership is not residence
A owns Property PR0041.
Forbidden: Automatically creating residence or occupancy.

PR-007 - Residence is not ownership
A census places A at Property PR0041.
Forbidden: Automatically creating ownership.

PR-008 - Structure on Property
Mill Structure S001 is associated with Property PR0041.
Required: They remain distinct objects. Changing Property ownership does not change Structure identity.


PR-009 - Lightweight stopping point
Index a deed's grantor and grantee without reconstructing the property.
Required: Workflow may stop there.
Forbidden: Property creation being mandatory for deed indexing.



9. Events
EV-001 - Record does not automatically create Event
Import a deed.
Required: Record can exist without an Event.

EV-002 - Event can use multiple Records
A Marriage Event is documented by church register, civil return and family Bible.
Required: All may support the same Event.

EV-003 - Record date versus Event date
Marriage occurred June 4; return filed June 9; photograph captured 2026.
Required: These dates remain semantically distinct.

EV-004 - Record Role versus Event Role
A witness appears on a marriage Record.
Forbidden: Automatically treating witness as spouse or Event principal.

EV-005 - Participation does not establish residence
A participates in an Event at Winchester.
Forbidden: Automatically creating Winchester residence.

EV-006 - No manufactured transition Event
Source A shows Person in Virginia in 1840; Source B shows Person in Ohio in 1850.
Forbidden: Automatically creating a Migration Event. Researcher may create one if evidence supports it.

EV-007 - Uncertain Event
Evidence suggests but does not establish an Event or date.
Required: Uncertainty can be represented through normal Claim and Evidence mechanisms.



10. Organizations & Repositories
O-001 - Organization identity survives rename
Church changes name.
Required: Same Organization can retain historical and current Names.

O-002 - Person role is not family relationship
A is church elder.




Forbidden: Any genealogical Person relationship generated.

O-003 - Unknown Repository
Record's present custodian is unknown.
Required: Source and Record can still be entered.

O-004 - Creator differs from Repository
County court created a record now held by a state archive.
Required: Historical creator and current Repository remain distinct.

O-005 - Repository differs from building
Records are physically consulted in a courthouse.
Required: Government Organization, courthouse Structure, Repository and Jurisdiction may remain distinct concepts.

O-006 - Access Provider
Digital image is accessed through another provider.
Required: Access Provider can be represented without incorrectly making it the historical creator or repository.



11. DNA Evidence
DNA-001 - Tested Person
A living descendant takes an autosomal test to investigate an eighteenth-century ancestor.
Required: DNA Test belongs to the person actually tested.
Forbidden: Assigning the test to the historical ancestor.

DNA-002 - External analysis
Researcher imports a report generated by an external DNA service or tool.
Required: Archive stores it as source material or derived analysis with appropriate provenance.
Forbidden: Archive pretending it performed the genetic analysis.

DNA-003 - DNA supports genealogical Claim
External analysis supports biological relationship Claim.
Required: It can participate in ordinary Evidence -> Claim framework. No separate truth system is created.

DNA-004 - DNA contradiction
DNA Evidence contradicts proposed biological parentage.
Required: Contradiction is preserved.
Forbidden: Automatically deleting the parentage or changing the conclusion.

DNA-005 - Social relationships survive DNA
DNA contradicts biological parentage. An adoptive, step, foster or guardian relationship also exists.
Forbidden: Non-biological relationship being invalidated.





DNA-006 - Restricted DNA source, ordinary conclusion
DNA source material is Restricted. Researcher creates a non-sensitive genealogical Conclusion suitable for Normal access.
Required: Access classification of source material does not automatically require every derived conclusion to expose the
restricted source. Authorization must still prevent unauthorized users from traversing into restricted material.

DNA-007 - Raw data
Import raw DNA data.
Required: It may be archived as Restricted source material.
Forbidden: v1 attempting genomic interpretation.



12. Research Knowledge
Q-001 - Research Question without Project
Create: "Was Samuel Andrews the father of Robert Andrews?"
Required: No Project or container required.

Q-002 - Hypothesis
Create a Hypothesis associated with the question.
Required: It remains distinguishable from Conclusion.

Q-003 - Research Action - Found
Record search of Deed Book 14 resulting in relevant Record R01842.
Required: Action may link to the found Record.

Q-004 - Research Action - No Relevant Result
Researcher examines a defined source and finds nothing relevant.
Required: Record No Relevant Result.
Forbidden: Automatically turning this into Expected/Absent Evidence.

Q-005 - Repeat search
Same repository or source is searched later.
Required: New Research Action permitted. Prior action may be shown or suggested.
Forbidden: Blocking search because it was previously attempted.

Q-006 - No task-management requirement
Create a Research Question.
Forbidden: Requiring due date, assignee, project, sprint, status workflow or task.

Q-007 - Research history versus audit history
Researcher records why a source was searched. Application internally records that a database object changed.
Required: These remain conceptually separate.





13. Collections, Tags & Classification
CT-001 - Collection does not own object
Add R01842 to three Collections.
Required: One Record, three memberships. Deleting one Collection does not delete the Record.

CT-002 - Tags are optional
Create and fully use a Record with no Tags.
Required: No warning or workflow penalty.

CT-003 - Classification is correctable
Change classification.
Required: Identity and provenance unaffected.

CT-004 - Classification does not determine physical storage
Change DEED to DTRUST.
Forbidden: Reorganizing preservation-original filesystem merely to mirror classification.

CT-005 - No mandatory taxonomy
User creates a useful custom Tag.
Required: No ontology or hierarchy required.



14. Search & Discovery
S-001 - Global search
Search a Person name.
Required: Relevant results are grouped intelligibly by object type rather than presenting raw database rows.

S-002 - Alternate Name
Search an alternate documented Person Name.
Required: Person can be discovered without changing preferred display Name.

S-003 - Fuzzy search
Misspell a Name.
Required: System may suggest likely results.
Forbidden: Adding the misspelling as a Person Name or establishing identity.

S-004 - OCR result
Search text occurring only in machine OCR.
Required: Relevant File or Record may be discovered. UI distinguishes machine OCR from reviewed transcription where
relevant.
Forbidden: OCR match automatically creating Person, Place or Property identity.





S-005 - Historical date search
Search 1840-1850.
Required: Structured approximate, range and partial dates are evaluated using temporal semantics rather than merely string
matching.

S-006 - Place search
Search a historical Place or Jurisdiction.
Required: Structured Place relationships and historical names can contribute to discovery.

S-007 - Role search
Search William Andrews - grantor.
Required: Role-specific association can be distinguished from generic occurrence.

S-008 - Unresolved Mention search
Search wording from an unresolved Person, Place or Property Mention.
Required: It remains discoverable even though no entity has been resolved.

S-009 - Discovery does not change archive
Perform searches, fuzzy searches and related-record discovery.
Required: No historical data changes solely from searching.



15. List-First Interface & Researcher Friction
UI-001 - Major objects have useful one-line summaries
Person, Record, File, Property, Place, Event, Source and other major objects provide concise list representations.
Required: Useful identifying information precedes internal technical details.

UI-002 - Detail by progressive disclosure
Routine list does not expose every note, provenance field, UUID, evidence relationship and administrative property
simultaneously.
Required: Details remain available through selection and detail views.

UI-003 - Minimal entry
Researcher can save useful incomplete information without completing unrelated metadata.

UI-004 - Known context inherited
During a Capture Session with known Repository, Book and context, adding 100 photographs does not require re-entering
the same values 100 times.

UI-005 - No repeated questions
Information already deterministically known from current operation or context should not be repeatedly requested.

UI-006 - Warnings remain proportional
An unresolved Mention or missing optional metadata does not produce alarming error treatment. True archival-integrity
failures do.





16. Bulk Import & Processing
BI-001 - Capture Session inheritance
Create a Capture Session with Repository, record book, researcher, date and capture method. Import 200 photographs.
Required: Known session context can be inherited without entering it 200 times. Individual Files may override appropriate
inherited values without modifying unrelated Files.

BI-002 - Import incomplete metadata
Import Files before identifying Records, Persons, Places or Properties.
Required: Files become valid archival Files after preservation verification.
Forbidden: Interpretation being required before preservation.

BI-003 - Self-describing templates
Request an import template for People, Records, Files or another supported object.
Required: Template reflects active model and configuration and identifies required, optional, conditional, immutable,
correctable and interpretive fields where appropriate.

BI-004 - Import preview
Submit a populated template.
Required: Validate and preview proposed changes before commit, including errors, unresolved references, candidates and
relevant warnings.
Forbidden: Large import silently modifying archive before validation.

BI-005 - Existing Person candidate
Import references John Andrews without an existing Person ID while several plausible Persons exist.
Required: Candidates may be presented.
Forbidden: Automatic Person merge or identity resolution.

BI-006 - Blank Person ID
Import contains a name but existing_person_id is blank.
Required: Follow explicit import instructions or configuration.
Forbidden: Silently creating a Person unless that import operation explicitly calls for Person creation.

BI-007 - Bulk correction
Correct classification on 100 Records.
Required: Operation is transactional or recoverable as applicable and produces appropriate significant change history.
Forbidden: Partial unexplained state after failure.

BI-008 - Interrupted large import
Interrupt a 200-File import after 137 Files have been safely committed.
Required: System identifies completed and incomplete portions and permits safe recovery or resumption.
Forbidden: Duplicating the first 137 during retry.





17. Security & Collaboration
SEC-001 - Administrator
Administrator performs authorized administrative operations.
Required: Access consistent with Administrator role.

SEC-002 - Editor
Editor performs normal research and data-entry operations.
Required: Permitted operations succeed. Permanent destruction of preservation originals remains administrator-controlled
by default.

SEC-003 - Viewer
Viewer accesses permitted information.
Forbidden: Data modification.

SEC-004 - Restricted object
Mark DNA File F02001 Restricted.
Required: Unauthorized Viewer cannot access its content through direct navigation, search, related-record traversal, export
or guessed File reference.

SEC-005 - Restriction propagation
Restrict an object whose dependent material must sensibly inherit protection.
Required: Restricted content is not exposed indirectly through child or File access. Removing inherited restriction requires
deliberate action where appropriate.

SEC-006 - Living status
Person has no death date.
Forbidden: Automatically assuming living or deceased solely from absence of a death date.

SEC-007 - Attribution
Editor changes a significant historical field.
Required: Logged-in user and time are captured automatically.
Forbidden: Requiring researcher to type their own name.

SEC-008 - Concurrent editing
Editor A opens P00127. Editor B modifies and saves it. A attempts to save stale state.
Required: A receives understandable conflict handling.
Forbidden: Silent overwrite of B's changes.

SEC-009 - Authentication independent of Internet
Disconnect Internet access.
Required: Local authentication and normal archive operation continue.

SEC-010 - Diagnostic logs
Exercise Restricted material.




Forbidden: Passwords, session secrets, raw sensitive material or unnecessary archival content being copied into ordinary
diagnostic logs.



18. File Transactions & Failure Recovery
TX-001 - Database transaction rollback
Cause failure during a multi-row database-only operation.
Required: PostgreSQL transaction leaves database in prior consistent state.

TX-002 - Persistent operation state
Begin a multi-step File operation and interrupt the server.
Required: Restart can determine that operation was incomplete without guessing from filenames.

TX-003 - Idempotent retry
Retry interrupted ingestion.
Required: Operation converges on intended result.
Forbidden: Duplicate File objects or content created merely because operation was retried.

TX-004 - NAS failure during copy
Disconnect target storage while copying.
Required: No active preservation File is declared successful. Incomplete material remains staged, quarantined or
recoverable as appropriate.

TX-005 - Failure after copy but before database completion
Successfully copy and verify content, then interrupt before final database state completes.
Required: Recovery identifies orphaned or incomplete operation and safely finalizes or quarantines it.

TX-006 - Unexplained storage object
Place an unrecognized object in managed storage.
Required: Integrity or recovery tooling reports it.
Forbidden: Automatic adoption or deletion.

TX-007 - Permanent deletion failure
Commit appropriate retirement or tombstone state but simulate physical deletion failure.
Required: System reports physical cleanup incomplete.
Forbidden: Pretending destruction succeeded.

TX-008 - Storage returns
Reconnect previously unavailable NAS.
Required: System can reassess Volume and recover eligible interrupted operations.





19. Integrity Verification
IV-001 - Quick integrity check
Run Quick Check.
Required: Examine archive-level database consistency, required Storage Volume availability, expected File presence,
recorded sizes, storage references and applicable invariants without requiring full rehashing.

IV-002 - Full integrity scan
Run Full Scan.
Required: Recalculate SHA-256 for protected preservation material and compare with authoritative stored hashes.

IV-003 - Hash mismatch
Modify one preservation File outside application.
Required: Full scan reports integrity failure.
Forbidden: Replacing authoritative stored hash with newly calculated hash.

IV-004 - Missing File
Remove an expected physical File.
Required: Report File identity, expected location or information and failure.
Forbidden: Automatically deleting its database record.

IV-005 - Unexpected File
Add unknown content to managed preservation storage.
Required: Report discrepancy without automatic destruction.

IV-006 - Scan history
Run several scans.
Required: Retain lightweight history including type, time and result.

IV-007 - Verified repair
Restore corrupted F03711 from a verified backup.
Required: Restored bytes must match authoritative SHA-256 before File returns to verified status. Maintenance action is
recorded.



20. Complete Archival Backup & Restore
BK-001 - Complete backup
Create Complete Archival Backup.
Required: Include a logically consistent recoverable state containing authoritative database information, preservation
originals, required derivatives, Archive ID, configuration and other non-rebuildable information.

BK-002 - Rebuildable material
Exclude thumbnails, search indexes and cache.
Required: Backup remains fully restorable because excluded material can be regenerated.




BK-003 - Backup manifest
Required: Manifest identifies backup, Archive ID, date/time, relevant versions, contents and verification information.

BK-004 - Verify backup
Verify completed backup.
Required: Detect missing or corrupt required material.
Forbidden: "Successful backup" meaning merely that copy process exited without error.

BK-005 - Complete restoration
Destroy a test installation and restore solely from backup.
Required: Preserve Archive ID; global IDs; friendly IDs; active and retired identities; historical data; relationships;
provenance; Files; required derivatives; restrictions; configuration; and research information.

BK-006 - Rebuild disposable artifacts
After restore, regenerate thumbnails and indexes.
Required: Regeneration does not alter archival identities or historical information.

BK-007 - New hardware
Restore Archive A to different supported hardware, including restoration from a Windows host to a Linux host or from a Linux host to a Windows host where the backup format is supported.
Required: It remains Archive A.
Forbidden: Hardware change generating a new Archive ID.

BK-008 - Offline destination
Create and verify backup on detachable storage, disconnect it and continue archive operation.
Required: Live archive does not depend on backup Volume remaining connected.



21. Complete Archive Export
EX-001 - Application-independent export
Generate Complete Archive Export.
Required: Package contains ordinary Files plus sufficiently complete structured data and documentation to understand
archive independently of running application.

EX-002 - Start-here documentation
Open export without archive software.
Required: Plain-text starting documentation explains package, format version, verification and reconstruction path.

EX-003 - Data dictionary
Required: Export explains objects, fields and semantics, not merely column names.

EX-004 - Relationship documentation
Required: Explain important cardinality and meaning including File-to-Record many-to-many, parentage independence,
Candidate Resolution and evidence chain.





EX-005 - Machine-readable schema
Required: Structured export is accompanied by versioned machine-readable definitions sufficient to validate or reimplement
it.

EX-006 - Controlled vocabularies
Custom classification, role and relationship codes used by archive are exported with their meanings.

EX-007 - Historical dates
Export and reconstruct "about March 1847."
Required: Approximation and month precision survive.
Forbidden: Reconstruction as March 1, 1847.

EX-008 - Uncertainty
Unresolved Mention and Proposed Candidate survive export.
Forbidden: Proposed becoming Resolved.

EX-009 - Files
Every exported preservation File is associated with stable identity, relative export location, size and SHA-256.

EX-010 - No internal-path dependency
Restore or reconstruct export on another operating environment.
Forbidden: Requirement for original /mnt/... paths.

EX-011 - Semantic specification
Required: Export documentation includes sufficient governing semantics to prevent obvious destructive reinterpretation of
relationships, evidence and uncertainty.

EX-012 - Reconstruction guide
A competent independent implementer receives only export package.
Required: Documentation provides understandable sequence for reconstructing archive content.

EX-013 - Export verification
Verify export manifest and File hashes.
Required: Missing or corrupt content causes export verification failure.

EX-014 - Cross-platform export portability
Generate a Complete Archive Export on Windows and validate/reconstruct it in a Linux environment; then perform the reverse.
Required: Export meaning, identities, Files, hashes, provenance, uncertainty, relationships, controlled vocabularies and
reconstruction documentation remain platform-independent.
Forbidden: Dependence on original host drive letters, mount points, path separators, service manager or operating-system-
specific database paths.



22. Transfer Packages
TP-001 - A to B transfer
Select R01842 and required supporting material in Archive A. Create Transfer Package and import into independent Archive
B.
Required: Meaning, provenance and originating identities survive.

TP-002 - Local ID collision
A transfers P00127; B already has unrelated local P00127.
Required: No identity collision. B may assign its own friendly local identity while preserving globally unambiguous origin
identity and lineage.



TP-003 - Existing historical candidate
Imported Person appears possibly identical to existing Person in B.
Required: Preview presents reconciliation candidate.
Forbidden: Silent merge.

TP-004 - Exact File already present
B already contains byte-identical File.
Required: Hash detects identical content. System can avoid duplicate physical bytes while preserving imported provenance
and relationships.

TP-005 - Different capture of same page
B has another photograph of same courthouse page.
Required: Do not treat as exact duplicate unless bytes actually match.

TP-006 - Dependency closure
Transfer Record requiring Source, Repository, Files and selected relationships.
Required: Include minimum supporting information necessary to preserve transferred meaning.
Forbidden: Recursively exporting unrelated whole archive without explicit selection.

TP-007 - Preview before commit
Required stages: Validate -> Analyze -> Preview -> Reconcile -> Commit.

TP-008 - Round trip A to B to A
Transfer object from A to B and later back to A.
Required: A recognizes originating identity and does not create an unrelated duplicate merely because it returned through B.

TP-009 - A to B to C lineage
Required: C can determine originating object lineage without assuming B created it.

TP-010 - Shared File
One transferred File participates in two selected Records.
Required: One File identity and content relationship supports both Records; no forced duplication.

TP-011 - Windows to Linux transfer
Create selected historical information and Files in a Windows-hosted Archive A. Generate a Transfer Package and import it
into independent Linux-hosted Archive B.
Required: Global identity, origin lineage, provenance, File hashes, relationships, evidence states, uncertainty and controlled
vocabulary survive without platform-specific conversion.

TP-012 - Linux to Windows transfer
Perform the reverse direction from Linux-hosted Archive B to Windows-hosted Archive A.
Required: The same semantics and identity guarantees apply.

TP-013 - Cross-platform round trip
Transfer an originating object Windows A -> Linux B, add legitimate research in B, then transfer selected information back
Linux B -> Windows A. Repeat an equivalent Linux-originating case in the reverse direction.
Required: Originating identities are recognized; reconciliation remains explicit; no silent duplicate, merge or overwrite is
created merely because the object crossed operating systems.



23. Search, Job & Operational Behavior
OP-001 - Background job survives restart
Begin Full Integrity Scan or large export. Restart application or worker.
Required: Persistent job state permits safe restart or recovery rather than silently losing operation.

OP-002 - Failed job
Cause thumbnail generation failure.
Required: Preservation File remains unaffected. Failure can be inspected and retried.

OP-003 - Search index disposable
Destroy rebuildable search index.



Required: Rebuild from authoritative information.
Forbidden: Loss of archival data.

OP-004 - Worker unavailable
Stop background worker.
Required: Appropriate queued work waits and UI reports operational condition.
Forbidden: Corrupting archive.

OP-005 - System status
Administrator can readily determine at least database availability, required Storage Volume availability, worker state, failed
and pending jobs, last verified backup, last integrity scan and relevant free storage. No enterprise monitoring suite required.



24. Deployment & Upgrades
UP-001 - Offline operation
Disconnect Internet.
Required: Authentication, research, search, import, File access, backup, export and integrity functionality that depends only
on local resources continues.

UP-002 - LAN operation
Run the archive server on either supported Windows or Linux host and access it from an authorized browser on the LAN.
Required: No dedicated desktop client application installation is required.

UP-003 - Different storage topology
Deploy Site A with NAS primary storage and Site B with local mirrored storage.
Required: Same application works through configured Storage Volumes.

UP-004 - Versioned migration
Upgrade schema version N to N+1.
Required: Migration is explicit and versioned and post-migration validation runs.

UP-005 - Failed migration
Simulate migration failure.
Required: Database remains or reverts to known recoverable state; pre-upgrade recovery path exists.

UP-006 - Historical semantics survive upgrade
Upgrade software.
Forbidden: Reinterpretation of existing historical information merely because new application version prefers a different
representation.

UP-007 - Configuration portability
Archive configuration travels where required through backup, export and transfer. Machine-specific mount paths and secrets
do not become Genealogy Archive System data.

UP-008 - Windows host
Install and run a clean Archive Instance on a supported Windows host.
Required: Application, PostgreSQL integration, background worker, browser interface, Storage Volumes, migrations, backup,
restore, export, transfer and integrity functions use the same archival semantics as Linux.
Forbidden: A separate incompatible Windows historical/domain model or interchange format.

UP-009 - Linux host
Install and run a clean Archive Instance on a supported Linux host.
Required: The same application codebase and archival semantics used on Windows operate on Linux, with only appropriate
platform integration differences.

UP-010 - Portable storage references
Configure equivalent Storage Volumes using a Windows path and a Linux path.
Required: Persisted archival File identity and interchange data use Storage Volume identity plus portable relative paths.
Forbidden: Windows drive letters, POSIX mount roots or host-specific absolute paths becoming File identity.

UP-011 - No live synchronization requirement
Operate independent Windows and Linux Archive Instances while the other installation is offline.
Required: Each remains fully usable as an independent archive. Information exchange occurs deliberately through Transfer
Packages or other approved interchange mechanisms.
Forbidden: v1 requiring live site synchronization, cross-site database replication, distributed locking, or continuous
bidirectional connectivity.





25. Explicit v1 Non-Features
NF-001 - No unnecessary infrastructure requirements
A conforming v1 installation shall not require microservices; Kubernetes; distributed database; graph database;
Elasticsearch/OpenSearch; Redis, Kafka, RabbitMQ or equivalent separate infrastructure merely for routine v1 operation;
cloud hosting; cloud identity; Internet connectivity; embedded AI; live synchronization between sites; real-time collaborative
document editing; event sourcing; blockchain; public social functionality; enterprise workflow engine;
project/task-management system; mandatory GIS; or a genetic-analysis engine.
Future versions may add justified capabilities without violating archival invariants.



26. Performance & Scale Acceptance
PF-001 - Ordinary list response
With a realistically populated test archive, normal list, search and detail operations remain comfortably usable on intended
Windows- and Linux-server hardware.

PF-002 - Large courthouse batch
Import at least 1,000 representative image Files without requiring all Files to remain in browser memory or one enormous
database transaction.

PF-003 - Large File collection
File storage architecture must not depend upon placing every archival File into one filesystem directory. Sharding or storage
layout must remain manageable.

PF-004 - Long operation
Full scan, export or large import runs as background work without locking ordinary research unnecessarily.

PF-005 - Progressive scaling
Performance optimization must not compromise identity, provenance or evidence correctness. If future scale requires
additional indexing or infrastructure, it can be introduced after demonstrated need.



27. Clean-Room End-to-End Acceptance Archive
Reference Acceptance Archive - Required contents
Create a purpose-built Reference Acceptance Archive containing at minimum: Persons with multiple Names; biological,
adoptive, step and guardian relationships; parents who never married; child born before parents' later marriage; remarriage;
uncertain and competing parentage; collective Person reference; duplicate Person candidates; exact duplicate Files;
redundant but byte-different captures; multi-page deed; one page shared by two Records; uncertain historical dates;
historical Place and jurisdiction changes; unresolved Place; Property ownership changes; acreage conflict; Structure; Event
supported by multiple Records; Organizations; Repository distinct from creator and access provider; DNA source material;
Restricted material; Research Questions, Hypotheses and Actions; supporting and contradictory Evidence; Expected/Absent
Evidence; revised Conclusion; unresolved, proposed and resolved Candidate cases; retired File ID or tombstone; and
custom classification and role configuration.

CR-001 - Normal research cycle
Use the Reference Archive through ordinary UI workflows.
Required: All applicable domain acceptance tests pass.




CR-002 - Failure cycle
Interrupt imports, background work and NAS access.
Required: No silent archival corruption.

CR-003 - Integrity cycle
Run Quick and Full scans.
Required: Clean archive passes. Introduce known corruption and require detection. Repair from verified source and require
verification to return to PASS.

CR-004 - Disaster-recovery cycle
Create Complete Archival Backup. Destroy test installation. Restore onto clean installation and storage.
Required: Archive passes identity, relationship, File, provenance, security and integrity comparison.

CR-005 - Software-independence cycle
Generate Complete Archive Export. Give only export package and included documentation to an independent
implementation process with no access to originating application's source code or internal PostgreSQL schema.
Required: It can reconstruct historical objects, Files, identities, relationships, uncertainty, evidence states, controlled
vocabulary and provenance sufficiently to satisfy export reconstruction specification.

CR-006 - Transfer cycle
Transfer selected research A to B. Resolve some candidates differently in B. Add legitimate new information in B. Transfer
selected information B to A.
Required: Origin identities and provenance survive and no silent merge or overwrite occurs.

CR-006A - Cross-platform peer cycle
Use one Windows-hosted and one Linux-hosted independent Archive Instance. Exercise Transfer Packages in both directions,
including a round trip with legitimate research changes at the receiving site.
Required: Both installations remain independently operable; cross-platform transfers preserve identity, provenance, Files,
relationships, evidence and uncertainty; no live synchronization is required or performed.

CR-007 - Final invariants
After all preceding abuse, all of the following must pass: preservation originals not deliberately destroyed retain expected
hashes; no retired ID has been reused; no Proposed identity silently became Resolved; no historical contradiction was
silently discarded; no marriage was manufactured from parentage; no unknown Person was manufactured merely to
complete genealogy; no source statement was overwritten by a conclusion; no uncertainty gained false precision; no
restricted content leaked to unauthorized users; no File identity depended on filename or path; Archive ID survived
restoration; and application-independent export remained intelligible.





Acceptance Specification v1.1 - Completion Rule
      A feature is not complete merely because its normal interface operates. It is complete only when its
      applicable archival invariants, domain acceptance scenarios, failure-recovery tests, security requirements,
      preservation requirements and interchange requirements pass.

Where an implementation choice would cause an acceptance test to fail, implementation convenience shall yield to the
acceptance requirement. Where the specification is genuinely ambiguous, the coding agent shall not invent historical
semantics or silently weaken an archival invariant.
Not every test must run on every code change. Fast automated unit and integration tests should run continuously;
storage-failure, large-scale integrity, full backup/restore, clean-room reconstruction and complete transfer tests belong at
appropriate phase and release gates.
