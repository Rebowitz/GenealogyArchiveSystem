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
- **Exposure**: the default bind address stays loopback. Password sign-in over unprotected HTTP on a LAN is
  not supported; built-in TLS is a later phase, and a TLS reverse proxy may be used independently.

## Consequences
- The public home page shows operational readiness only, never archive identity or data.
- `/actuator/health/*` remains unauthenticated for service managers; other actuator endpoints are not exposed.
