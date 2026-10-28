# Phase 0 correction notes

Original audited commit: dee3c3ee7cad93346c0a721410abf0c2ce142b83.
This correction set remains Phase 0; it does not constitute acceptance. PR #1 must not be merged as part of this work.

## GAS-P0-001: secret boundary

JDBC URLs now accept only documented connection-option keys: sslmode, sslrootcert, connectTimeout,
socketTimeout, tcpKeepAlive, targetServerType, loadBalanceHosts, hostRecheckSeconds.
Decoded keys are checked; URL user-info, fragments, unknown/credential/extension parameters and malformed
percent escapes are rejected without echoing input. The DatabaseSettings constructor enforces the
same rule for programmatic callers. Diagnostic URL/config rendering omits query values.
Passwords still come only from the approved environment/protected-file channel.
Malformed YAML parser excerpts are suppressed because they can echo attempted inline secrets.
Tests exercise literal/encoded/case-varied credential keys, malformed encodings, user-info,
CLI output, direct construction, safe options, and malformed-YAML output.
This is an explicit connection-option surface, not a general arbitrary JDBC-driver-properties interface.

## GAS-P0-002: lexical checks versus physical snapshots

PortableRelativePath remains a pure value/conversion type. It prevents lexical traversal and native
absolute syntax; it does not inspect the filesystem or promise race-free containment.

Installation loading checks lexical root overlap and resolves the nearest existing ancestor with
Path.toRealPath, appending any nonexistent suffix. This detects observable aliased/nested roots through
Linux symlinks and Windows junctions, even when final root directories have not yet been created.
Permission errors and unresolved existing links fail configuration validation; absent roots are allowed.
Absolute real paths are temporary validation observations, not persisted archival identities.

VolumeRoots.resolve checks lexical and observable physical containment, including existing links in
the parent of a not-yet-created target. Root aliases and links remaining inside the root are allowed.
The configured native path is returned; neither host-native roots nor resolved paths become File identity.
Regression tests exercise native Windows junctions and Linux symlinks, with no skipped platform cases.

REMAINING PHASE 2 INVARIANT: These are snapshots, not file-access authorization. Root mappings,
mounts, permissions, links, directory entries, and missing suffixes can change after a check. Before
archival storage becomes operational, the file service must enforce containment at actual open/create/
promote/retrieve time; define link/reparse/mount policy, use suitable no-follow/handle-based protections
where available through the platform boundary, control writable ancestors, and test replacement races
and fail-visible behavior on both hosts. A repeated real-path comparison alone is not a race-free protocol.
No archival ingestion or file-serving subsystem is introduced here.

## GAS-P0-003: WinSW XML

The installer populates XML element text through XmlDocument instead of inserting unescaped path text.
Native path characters such as ampersands and apostrophes remain valid. Pure rendering tests round-trip
both templates and all XML text metacharacters. Native Windows service CI also installs in actual
directories containing spaces and an ampersand.

## GAS-P0-004: deterministic artifact verification

Maven 3.9.11 binary zip SHA-256:
0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb

The downloaded archive was checked against the publisher's SHA-512 before deriving the wrapper SHA-256:
03e2d65d4483a3396980629f260e25cac0d8b6f7f2791e4dc20bc83f9514db8d0f05b0479e699a5f34679250c49c8e52e961262ded468a20de0be254d8207076

Source:
https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip.sha512

WinSW 2.12.0 x64 SHA-256:
05b82d46ad331cc16bdc00de5c6332c1ef818df8ceefcd49c726553209b3a0da

Source:
https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe

The WinSW release API provides no published digest for this older asset. This pin records bytes obtained
from the official release over HTTPS; it is not a claim of independent publisher-signature authentication.
The installer compares the pinned digest before any wrapper execution, for manual installs and CI alike.
A tampered-file regression must be rejected; real service CI verifies the matching artifact path.
Maven Wrapper already implements checksum verification on both hosts; its properties now require the
pinned SHA-256. The maintenance workflow requires a checksum when changing Maven and verifies the wrapper.

Remaining normal trust: Maven Central/parent BOM and repository dependencies, JDK distribution/setup,
GitHub Actions and runner images, signed apt/PGDG distribution mechanisms, and Chocolatey/EDB packaging.
Action major tags and OS/database patch packages are not made hermetic by these two artifact pins.
No supply-chain service or framework is added. Updating WinSW intentionally requires updating its pin.

## GAS-P0-005: runtime foundation identity

Readiness/status and worker startup require exactly one expected Genealogy Archive System marker in
addition to supported PostgreSQL and current migration history. Missing/incorrect row yields INVALID;
missing/unreadable table yields not-ready through the existing visible inspection-failure path.
Unexpected marker content is not echoed. Liveness stays independent.
Real PostgreSQL integration regressions corrupt an isolated fixture by removing the row, changing its
value after deliberately dropping its fixture check constraint, or dropping the table; each requires
HTTP 503 readiness and worker startup refusal. Applied production migrations are not edited.

## GAS-P0-006: CI evidence guard

The actual report guard requires testcase evidence in both Surefire and Failsafe report sets.
A combined nonzero count or BUILD SUCCESS alone is insufficient. Fixture regressions execute the
actual guard for missing/empty suites, failures/errors/skips, and a valid two-suite run.
Documented Phase-Gate scripts use clean verify so stale reports cannot stand in for this invocation.

## GAS-P0-007: evidence scope

Names now say proxy-selector observations, portable-value round trips, tested ASCII variants, and
transactional-DDL structure rollback. Assertions were retained; no architectural rule was relaxed.
Proxy observation is not a disconnected-network proof. Native default-filesystem characterization
is not a NAS/cross-filesystem atomicity or operational storage protocol test. Pure portable values
are not PostgreSQL File persistence; domain independence is a future constraint while domain is empty.

## Scope and remaining limits

No production schema migration changes; no historical domain tables, identity/user/configuration
subsystem, persistent job queue, ingestion, backup/export/transfer, synchronization, or Phase 1 work.
Windows LocalSystem remains only the approved Phase 0 development/CI limitation. Resolve a suitable
low-privilege production account before Phase 2 storage is operational.
Manual PostgreSQL dump plus appropriate Storage Volume copy remains the approved temporary
pre-upgrade checkpoint until Phase 8; no premature backup subsystem is required.

Final corrected SHA, exact file list and all four native CI results are recorded in the separate
correction completion report after the final source commit finishes CI. Independent acceptance remains pending.
