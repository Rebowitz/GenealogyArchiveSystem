# ADR-0010: Service identities and the web service's read-only access to originals

Status: Implemented for Phase 2 review (owner decisions P2-K, B1, B4; requirement 5). Supersedes the
Phase 0 LocalSystem limitation (D-0002). Not yet accepted.

## Windows
- Each service runs under its **own virtual service account**: `NT SERVICE\GenealogyArchiveWeb` and
  `NT SERVICE\GenealogyArchiveWorker` (`sc.exe config <id> obj= "NT SERVICE\<id>"`), with an
  **unrestricted service SID** (`sc.exe sidtype <id> unrestricted`). No passwords exist or are stored.
- `install-services.ps1` grants by SID:

  | Path | Web | Worker | Notes |
  |---|---|---|---|
  | application dir | RX | RX | |
  | `installation.yml` | R | R | |
  | `secrets\` | R | R | inheritance removed; SYSTEM, Administrators full |
  | `staging\` | M | M | inheritance removed; SYSTEM, Administrators full |
  | `cache\` | RX | M | thumbnails are written by the worker |
  | `logs\` | M | M | |
  | `tmp\web`, `tmp\worker` | own only | own only | `-Djava.io.tmpdir` per service |

- `grant-volume-access.ps1 -VolumeRoot <dir>` gives the worker Modify and the web service Read & execute
  **plus an explicit DENY** of write data, append, write (extended) attributes, delete, delete child,
  change permissions and take ownership. A DENY entry overrides any allow the web service could receive
  through a group.
- **Workgroup NAS fallback (B4, documented only, not CI-tested):** a NAS share in a workgroup cannot
  authorize a virtual account. Create a dedicated local low-privilege account per service with the same
  user name and password on the NAS, run the services under those accounts, give the worker account
  modify and the web account read-only on the share. Do not use LocalSystem.

## Linux
- One system account, `genealogy-archive`, runs both units (B1). The separation is provided by systemd
  sandboxing instead of a second account:
  - web unit: `ProtectSystem=strict` — the whole filesystem is read-only to it except
    `ReadWritePaths=/var/lib/genealogy-archive/staging /var/log/genealogy-archive` (and its private /tmp);
  - worker unit: `ProtectSystem=full`; volume roots must therefore not be under `/home`, `/usr`, `/boot`
    or `/etc`.
- `install.sh` creates `/var/lib/genealogy-archive/staging` (0700) and `/var/lib/genealogy-archive/cache`.
  If an installation moves staging or logs, it must change `ReadWritePaths` with a drop-in.

## Verification
- The web process runs a write probe (exclusive create at each usable volume root) whenever the Operations
  page is shown and reports "denied", or that write access exists / could not be confirmed.
- The native CI service tests run both services under these identities and require the probe to report
  denial, then ingest and download a file through the real services. Windows additionally asserts each
  service's configured account.

## Residual notes
- On Linux both processes share one UID; the separation relies on the systemd unit. Running `serve` by hand
  outside systemd as the service account would have write access; the Operations page shows this.
- The CLI (`init-volume`) is run by an administrator (Windows) or as the service account (Linux).
