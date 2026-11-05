# Phase 2 owner decisions (File Preservation)

Recorded from the project owner's Phase 2 design finalization and implementation authorization. They bind
Phase 2; later phases may revisit them only with owner approval. Design: ADR-0009 (file preservation) and
ADR-0010 (service identities).

| ID | Decision |
|---|---|
| P2-A | Browser-based multi-file ingestion. |
| P2-B | Each deliberate ingestion creates a distinct File identity and provenance, even when identical content is already stored. Physical content may be shared. |
| P2-C | The ingestion destination is chosen by permanent Storage Volume ID. |
| P2-D | An initialized volume marker containing Archive ID and Volume ID is required. |
| P2-E | Practical Java/filesystem containment safeguards; no native code solely to eliminate very unlikely hostile filesystem races. |
| P2-F | Derivative identity, external derivative ingestion and display-rotation metadata; advanced editing deferred. |
| P2-G | Relationship-aware consolidation deferred to Phase 3. |
| P2-H | Restriction status stays in the File model with enforceable protection for restricted downloads; comprehensive restriction management deferred to Phase 9. |
| P2-I | Administrator-supplied verified repair; original hashes immutable; repair history retained. |
| P2-J | Archival file-operation history separate from background jobs and Change History. |
| P2-K | Prefer Windows virtual service accounts; document a dedicated low-privilege account alternative for workgroup NAS access. |
| B1 | A single Linux service user. |
| B2 | Restricted Files are Administrator-only. |
| B3 | Ingestion needs the worker running. |
| B4 | The workgroup-NAS fallback account is documented only (not CI-tested). |
| B5 | Default upload limit 4 GiB. |

## Additional requirements from the implementation authorization

1. Recovery distinguishes active uploads from abandoned uploads; a slow but active upload is not discarded.
2. Staging files are isolated, protected against unauthorized access, and revalidated by the worker before
   archival promotion.
3. Intended filesystem protection is applied before successful archival completion is reported; failure to
   establish it is handled explicitly.
4. Repair retains accountable references to damaged generations until quarantine is confirmed; destruction
   protects shared content through transactional reference checks.
5. The web service's read-only access to archival originals is enforced in supported Linux and Windows
   production deployments.
6. The 4 GiB default upload limit is configurable and enforced during streaming; framework and reverse-proxy
   limits must not silently impose a conflicting limit.
7. Straightforward, proportionate concurrency controls; no native code or elaborate locking for unlikely
   hostile filesystem races.
8. All existing Phase 0 and Phase 1 functionality, security requirements and CI coverage are preserved.

Standing decision D-0002 (LocalSystem as a Phase 0 dev/CI limitation) is resolved by P2-K / ADR-0010.
