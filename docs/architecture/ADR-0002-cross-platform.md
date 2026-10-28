# ADR-0002: Cross-platform approach (Windows and Linux hosts)

- **Status:** Accepted (Phase 0)
- **Governs:** CLAUDE.md "v1 hosting requirement"; BUILD-SPEC s.6, s.27, s.28, s.46, s.55, s.63A;
  ACCEPTANCE UP-003, UP-008..UP-011, EX-010, EX-014

## Decision

### One codebase, one artifact
The same jar runs on both hosts. Domain, service, data, migration and interchange code contain no
operating-system branches. Only `org.genealogyarchive.platform` may inspect the host
(`HostPlatform`, `NativePaths`, `FilesystemCapabilities`); `ArchitectureTest` forbids
`System.getProperty` outside it.

### Paths
- **Persisted / exchanged form:** `StorageLocation` = Storage Volume UUID + `PortableRelativePath`.
  A portable path uses `/`, is relative, contains no `..`, no drive letters, no `:` or `\`, no
  Windows-illegal characters or reserved device names, no trailing dot/space, is Unicode NFC, and has
  segments of at most 255 UTF-8 bytes. The rules are identical on both hosts, so any path accepted on
  one host is valid on the other.
- **Native form:** only in installation configuration (`storageRoots`, `logging.directory`,
  `database.passwordFile`), validated as absolute for the running host. A Windows path on Linux (or a
  POSIX path on Windows) is rejected with a hint, never interpreted as relative.
- **Resolution:** `VolumeRoots` is the single place where a `StorageLocation` becomes a native `Path`,
  using `Path.resolve` per segment. `File.separator` is banned by `ArchitectureTest`.
- **Case:** `PortableRelativePath` equality is case-sensitive; `caseFoldKey()` detects names that would
  collide on case-insensitive filesystems. Phase 2 storage naming (identity-derived, lower-case) must
  avoid such collisions.

### Filesystem semantics are probed, not assumed
`FilesystemCapabilities.probe(dir)` measures case sensitivity and same-directory atomic rename on the
actual volume (a NAS share may differ from the host default). `HostFilesystemBehaviourTest`
characterises the CI hosts (NTFS case-insensitive, ext4 case-sensitive; deleting a file held open
fails on Windows and succeeds on Linux) so assumptions are caught on both platforms. Cross-volume moves
must use copy -> verify -> promote (BUILD-SPEC s.35); that protocol is Phase 2.

### Service management
| | Linux | Windows |
|---|---|---|
| Mechanism | systemd units `genealogy-archive-web/worker.service` | WinSW v2 wrappers `GenealogyArchive-Web/Worker.exe` + XML |
| Account | dedicated `genealogy-archive` system user | LocalSystem by default (dedicated account optional) |
| Stop | SIGTERM -> JVM shutdown hooks -> graceful stop (`SuccessExitStatus=143`) | WinSW sends Ctrl+C -> JVM shutdown hooks -> graceful stop |
| Restart | `Restart=on-failure` | `<onfailure action="restart">` |
| Logs | journald + optional rolling files | WinSW `logs\service` + optional rolling files |

WinSW was chosen for Windows because a plain Java process cannot register with the Service Control
Manager by itself; WinSW is a small, widely used, MIT-licensed wrapper with no runtime dependencies
(x64 build). It is obtained once at install time; no Internet access is needed at runtime. CI installs
and drives both service managers for real (`scripts/ci/service-test-*.{sh,ps1}`) and verifies graceful stop.

### No synchronization
Instances are independent peers. There is no replication, distributed locking or live sync code, and none
will be added in v1 (CLAUDE.md). Exchange is via Transfer Packages / exports (Phase 8).

### Line endings and encodings
`.gitattributes` fixes line endings per file type (LF for sources and shell, CRLF for `.ps1/.cmd`). Source
and configuration are UTF-8; the configuration loader tolerates a UTF-8 BOM written by Windows editors.

## Consequences
- Persisted data and interchange formats never contain host paths (UP-010, EX-010).
- Windows and Linux CI must both pass for any change; the matrix runs identical tests.
- Long Windows paths: the JDK handles paths beyond 260 characters; storage roots should still be kept short.

## Phase 0 audit correction

Portable path conversion guarantees lexical containment only. VolumeRoots invokes the platform
PhysicalPaths snapshot check; installation loading also detects observable physical root aliases.
Missing paths are resolved through their nearest existing ancestor; no resolved native path is
persisted as archival identity. File-access-time races and mount/link changes remain a mandatory
Phase 2 invariant; see ../PHASE-0-CORRECTIONS.md. WinSW rendering uses an XML DOM.
LocalSystem is limited to Phase 0 development/CI; resolve low-privilege execution before Phase 2.
