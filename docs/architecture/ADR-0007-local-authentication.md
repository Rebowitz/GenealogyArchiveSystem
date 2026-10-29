# ADR-0007: Local authentication and service-boundary authorization

Status: Accepted for Phase 1 implementation (owner decision P1-C). Phase 1.

## Decision
- **Local accounts only** in `gas.app_user`: no social, cloud, LDAP or external identity provider.
  Usernames are case-insensitively unique and immutable; accounts are disabled, never deleted, so they
  remain available for attribution.
- **Roles**: Administrator, Editor, Viewer. In Phase 1 only Administrators have administrative permissions
  (configure archive, manage users, manage Storage Volumes, view operations, retry jobs). Editor and Viewer
  may view and change their own password; Editor gains editing rights when Phase 2+ objects exist.
  No signed-in role may initialize the archive — only the command-line SYSTEM actor.
- **Passwords**: Spring Security `DelegatingPasswordEncoder` with bcrypt (cost 12; `{bcrypt}` prefix allows
  future migration to Argon2 without a schema change). Policy: at least 12 characters, at most 72 UTF-8
  bytes (bcrypt would otherwise truncate silently), not the username. Passwords never enter change history
  or logs; the first administrator's password comes from `GAS_NEW_ADMIN_PASSWORD` or a console prompt,
  never a command-line argument.
- **Sessions**: Spring Security form login with CSRF protection, session-ID renewal on sign-in, 30-minute
  idle timeout, sign-out. Each request re-reads the account, so disabling an account or changing its role
  takes effect on that user's next request.
- **Authorization is enforced in services**, not only in controllers: every service operation takes an
  explicit `Actor` and calls `AccessPolicy.require`. Spring Security is confined to `security`, `web` and
  `boot` (ArchUnit rule). Denials carry no detail.
- **Exposure (enforced, correction GAS-P1-002)**: `TransportSecurity` decides.
  - *Deployment*: a loopback bind (`127.0.0.0/8`, `::1`, `localhost`) is accepted. Any other bind -
    LAN address, host name, `0.0.0.0`, `::`, or no address - makes `check-config` and `serve` fail
    (exit 2) unless `server.tlsReverseProxy.addresses` lists the proxy's exact IP addresses (literals
    only: no host names, ranges or wildcards; no DNS).
  - *Requests* (`TransportSecurityFilter`, before sign-in and sessions): accepted only directly from a
    loopback address that is not a declared proxy, or from a declared proxy address carrying exactly
    one `X-Forwarded-Proto: https`. Forwarding headers from any other address are ignored. Only
    `GET /actuator/health/**` is exempt. The servlet container's own forwarded-header handling is off
    (`server.forward-headers-strategy=none`), so the peer address cannot be rewritten by headers.
  - Behind a declared proxy the session cookie is marked `Secure`.
  - Built-in TLS remains a later phase.

## Consequences
- The public home page shows operational readiness only, never archive identity or data.
- `/actuator/health/*` remains unauthenticated for service managers; other actuator endpoints are not exposed.
