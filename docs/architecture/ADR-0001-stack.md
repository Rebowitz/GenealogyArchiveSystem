# ADR-0001: Implementation stack

- **Status:** Accepted (Phase 0, approved by project owner 2026-10-27/28)
- **Governs:** BUILD-SPEC s.5, s.43, s.47, s.57, s.60, s.67, s.68; PHASE-0 "Technology-selection rule"

## Context
The specification does not mandate a language or framework. The repository was empty. PHASE-0 requires
a mature, maintained, widely deployed stack that runs equally on Windows and Linux as ordinary host
services, has first-class PostgreSQL support, supports migrations, testing, secure sessions and a
background worker without prohibited infrastructure, and works offline. The project owner is an
experienced Java developer who will maintain the system long-term.

## Decision

| Concern | Choice |
|---|---|
| Language/runtime | **Java 21 LTS** (Eclipse Temurin). Supported by Spring Boot 4.1 (Java 17-26). |
| Application framework | **Spring Boot 4.1.x** (4.1.1 at time of writing): web MVC, Actuator, JDBC, structured logging. |
| Build | **Maven** via the **Maven Wrapper** (Maven 3.9.11 pinned in `.mvn/wrapper/maven-wrapper.properties`). Dependency versions pinned through the Spring Boot BOM; one explicit extra version (ArchUnit). |
| Data access | **Plain SQL** with Spring `JdbcClient` over the PostgreSQL JDBC driver and HikariCP. **No ORM.** |
| Migrations | **Flyway** SQL migrations, run by an explicit `migrate` command (ADR-0005). |
| Browser UI | Server-rendered **Thymeleaf** templates; all assets local (no CDN). |
| Tests | JUnit 5, AssertJ, ArchUnit 1.4.1, real PostgreSQL (no Docker/Testcontainers, no H2). |
| Services | systemd on Linux; WinSW v2 on Windows (ADR-0002). |

### Change from initial proposal
Spring Boot 3.5 was proposed first. Its open-source support ended 2026-06-30, so it no longer met the
"actively maintained" rule. Spring Boot 4.1 (GA 2026-06-10, OSS support to 2027-07-31) was adopted
instead, with the owner's approval. Upgrading within 4.x minor releases is expected during the project.

### Why no ORM
BUILD-SPEC s.67 asks for "straightforward SQL/database mappings" and warns against "framework-specific
magic", "undocumented side effects" and "implicit data mutation". JPA/Hibernate dirty checking, cascades,
lazy loading and generated SQL are exactly those. Archival rules (never overwrite, explicit optimistic
concurrency, constraints in PostgreSQL) are easier to guarantee and review with explicit SQL. An ORM may
only be introduced for a concrete, documented requirement. The Maven Enforcer `bannedDependencies` rule
and `ArchitectureTest` block Hibernate/JPA and the BUILD-SPEC s.60 infrastructure.

### SQL organisation
SQL and JDBC are allowed only in `data/` (and Flyway in `migration/`), enforced by `ArchitectureTest`.
Short statements are Java text blocks in the owning repository class; longer statements may be placed
in `src/main/resources/sql/<area>/`. Services own transactions; web code calls services only.

## Consequences
- One executable jar; same artifact on both hosts.
- Familiar to a Java developer; large ecosystem; long support horizon.
- Spring Boot minor upgrades must be tracked (roughly every 6 months).
- Spring Security (local authentication, Argon2/bcrypt, sessions) and the PostgreSQL-backed job queue are
  available within the same stack for Phase 1 without new infrastructure.
