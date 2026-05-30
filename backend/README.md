# Backend Baseline

## Tech Stack
- Java 21
- Spring Boot 3.3.x
- Maven
- PostgreSQL + Spring Data JPA + Flyway
- Redis (cache/session/captcha)
- Spring Security + JWT
- WebDAV: Apache Jackrabbit WebDAV
- SFTP: Apache Mina SSHD
- Observability: Actuator + Micrometer + Prometheus

## Layered Architecture
- `adapter`: REST / WebDAV protocol adapters
- `application`: use case orchestration
- `domain`: business rules, state machines, permission engine, error codes
- `infrastructure`: persistence, redis, sftp/local drivers, audit integration

## Concurrency Baseline
- All write APIs must carry `version` or `If-Match`.
- Missing concurrency condition -> `VALIDATION_ERROR`.
- Mismatch -> `VERSION_CONFLICT`.

## Logging Baseline
- Plain text logging via `logback-spring.xml` (console + rolling files).
- Correlation fields: `requestId`, `traceId`, `method`, `path`.
- Response header `X-Request-Id` is always returned.
- Audit logs should use logger name `AUDIT` and are written to a dedicated audit file.
- Logging-related runtime components are placed under `common/logging`.
- Request/query/body logging is sanitized deeply for sensitive fields (`password`, `token`, `secret`, `key`, `credential`).

## Security and Audit Baseline
- Method security is enabled via `@EnableMethodSecurity`.
- JPA auditing is enabled via `@EnableJpaAuditing` and `AuditorAware<String>`.
- AOP audit annotation `@AuditAction` is available for service-level audit recording.

## Run (Dev)
1. Ensure PostgreSQL and Redis are available locally (or via docker compose in repo root).
2. `cd backend`
3. `mvn spring-boot:run`
