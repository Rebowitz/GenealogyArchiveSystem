# ADR-0004: Installation configuration, archive configuration and secrets

- **Status:** Accepted (Phase 0)
- **Governs:** BUILD-SPEC s.46, s.55; ACCEPTANCE UP-007, SEC-010; PHASE-0 "archive configuration separated
  from installation configuration", "secrets handling"

## Decision

### Two kinds of configuration
| | Installation configuration | Archive configuration |
|---|---|---|
| Scope | this host only | the archive |
| Contains | DB connection, HTTP binding, log location, native Storage Volume roots | Archive ID, classifications, record types, roles, relationship types, vocabularies, archive preferences |
| Stored in | YAML file (`installation.yml`) outside the repository | the archive's PostgreSQL database (Phase 1+) |
| Travels in backup/export/transfer | **never** | **always** (as appropriate) |
| Code | `org.genealogyarchive.config.InstallationConfig` | introduced in Phase 1, separate package |

`InstallationConfigLoader` rejects unknown keys, so archive settings (e.g. `archiveId`) cannot creep into
the installation file. Installation values reach Spring only through `ProcessLauncher`, in one place.

### Secrets
- Never in the installation file (a `password` key is an error) and never in the repository
  (`.gitignore` excludes `installation.yml`, `*.env`, `secrets/`, `db-password*`).
- Supplied by environment variable (`GAS_DB_PASSWORD` by default, or a named variable) or by a
  protected file (`database.passwordFile`): Linux `0640 root:genealogy-archive`; Windows
  `C:\ProgramData\GenealogyArchive\secrets` restricted to SYSTEM and Administrators by the installer.
- Held in memory as `Secret`, whose `toString()` is masked; `reveal()` is called only when building the
  connection pool. `check-config` prints the password as `******`.
- Logged output is tested not to contain the password, including on authentication failure
  (`SecretsNotLoggedIT`, smoke and service tests). Hikari logging is limited to WARN.
- CI generates a random database password per run and masks it.

### Validation
`check-config` reports every problem at once with an explanation; startup of any process fails visibly
(exit code 2) on invalid configuration instead of falling back to defaults.
