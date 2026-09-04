# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo has one Maven module, `demo/`, containing a Spring Boot 4.1 (Java 21) REST API called "Taskflow" — a project/task tracker with JWT auth. All commands below are run from `demo/`.

## Commands

All commands use the Maven wrapper from inside `demo/`:

```
./mvnw spring-boot:run              # run the app (needs JWT_SECRET env var and Postgres — see below)
./mvnw test                         # run the full test suite
./mvnw test -Dtest=TaskServiceTest                       # run a single test class
./mvnw test -Dtest=TaskServiceTest#update_bumpsVersion    # run a single test method
./mvnw verify                       # test + package
./mvnw compile                      # compile only
```

On Windows use `mvnw.cmd` instead of `./mvnw` in `cmd.exe`; the wrapper works as-is in Git Bash/PowerShell via the shell script.

**Local run requirements:**
- `JWT_SECRET` env var must be set (`application.yaml` reads `jwt.secret: ${JWT_SECRET}`, no default).
- Postgres via `demo/compose.yaml` (`spring-boot-docker-compose` auto-starts it when running locally) — db `taskflow`/user `taskflow`/password `taskflow`.
- Profiles: `dev` (SQL logging, DEBUG root logger, `DevBanner` logs on startup) and `prod` (quiet logging). No profile is active by default.

**Tests** use the `test` profile (`application-test.yaml`, a fixed dummy `jwt.secret`) and Testcontainers-backed Postgres (`TestcontainersConfiguration`, a static/singleton container reused across test classes via `@ServiceConnection`). No embedded/H2 database is used anywhere — all persistence tests hit real Postgres in a container. WireMock (`wiremock-standalone` 3.13.2, pinned explicitly — see the comment in `pom.xml` for why) is used for testing the outbound `ProductivityTipClient` HTTP call, not Spring's `MockRestServiceServer`.

There's a Postman collection at `demo/postman/Taskflow-API.postman_collection.json` for manual API exploration.

## Architecture

### Domain model & ownership

Three main entities: `User` → owns many `Project`s → each `Project` has many `Task`s (`Task` also has a `Set<Tag>` via a `task_tags` join table). Schema is Flyway-managed (`db/migration/V1`–`V5`, `ddl-auto: validate` — entities must match migrations exactly, never rely on Hibernate to create/alter schema).

Ownership is only stored on `Project.owner`; `Task` has no owner column of its own — a task's owner is `task.getProject().getOwner()`. Keep this in mind when adding task-scoped features: don't add a redundant owner field, walk the association instead (see `TaskGuard`).

`Task.version` (`@Version`, added in `V4`) backs optimistic locking. `ObjectOptimisticLockingFailureException` is translated to `409 Conflict` in `GlobalExceptionHandler`.

### Authorization model

Two layers, and both matter when changing access rules:
1. **`@PreAuthorize` at the controller method** — gates whether the endpoint is reachable at all (e.g. `hasRole('ADMIN') or @projectGuard.isOwner(#id, authentication)`). `ProjectGuard`/`TaskGuard` are `@Component`s referenced by their decapitalized class name in SpEL (`@projectGuard`, `@taskGuard`).
2. **Row-level filtering in the service** — a `@PreAuthorize` can't decide *which rows* come back for a collection endpoint. `ProjectService.findAll()` branches on `ROLE_ADMIN` vs owner-scoped query itself; this can't be pushed into an annotation.

`ProjectGuard.isOwner` / `TaskGuard.isOwner` deliberately return `false` (never throw `NotFoundException`) when the target doesn't exist. This is intentional, not a shortcut: it collapses "doesn't exist" and "exists but isn't yours" into the same `403`, so a non-owner can't use response-code differences to enumerate ids they don't own. Preserve this behavior in any new guard.

`TaskController` endpoints that only address a project (no `taskId` in the path — list/search/summary/status-counts/create) check `@projectGuard.isOwner(#projectId, ...)`; the two endpoints that address a single task (`update`, `delete`) check `@taskGuard.isOwner(#taskId, ...)` instead.

### JWT auth

Stateless JWT via Spring Security's OAuth2 resource server support, *not* a hand-rolled filter — an earlier hand-rolled `JwtAuthFilter` was deliberately deleted once it was proven redundant with `oauth2ResourceServer(...).jwt(...)` wired to the same `JwtDecoder` (see the comment in `SecurityConfig`). `JwtService` only *mints* tokens (`JwtEncoder`); decoding/verification and role-claim → `GrantedAuthority` mapping is entirely Spring's job (`JwtAuthenticationConverter` + `JwtGrantedAuthoritiesConverter`, claim name `"roles"`, **no** `ROLE_` prefix added at decode time because roles are already stored as full authority strings like `"ROLE_USER"` when the token is minted). If you touch role/authority handling, keep the encode and decode sides symmetric or authorities will silently double-prefix or stop matching.

Basic Auth is intentionally not enabled alongside JWT (see `SecurityConfig` comment) — don't re-add it as a parallel auth mechanism.

Filter-chain-level auth failures (`ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`) and controller-layer failures (`GlobalExceptionHandler`) are both wired to produce the same `application/problem+json` shape, so a client can't distinguish which layer rejected a request from body format alone.

### Error handling

All exceptions funnel through `GlobalExceptionHandler` (`@RestControllerAdvice`) into RFC 7807 `ProblemDetail` responses. Order/specificity matters here: several handlers exist specifically to intercept exception types that would otherwise be shadowed by the catch-all `Exception` handler and wrongly turned into a `500` — e.g. `AccessDeniedException` (thrown by `@PreAuthorize` denials as `AuthorizationDeniedException`, a subclass) and `MethodArgumentTypeMismatchException` (bad enum/number in a `@RequestParam`/`@PathVariable`, thrown during argument resolution before the controller body or `@PreAuthorize` even run). When adding a new exception type that should map to a specific status, add a dedicated `@ExceptionHandler`, don't rely on the catch-all.

### Config properties

Two `@ConfigurationProperties` records with validation, both worth following as the pattern for new config: `TaskflowProperties` (`taskflow.*`, pagination defaults, validates `defaultPageSize <= maxPageSize` in a compact constructor) and `JwtProperties`/`ProductivityTipProperties` similarly. `PaginationConfig` wires `TaskflowProperties` into Spring Data's `Pageable` resolution globally (max page size, fallback page).

### External HTTP calls

`ProductivityTipClient` calls an external tips service via `RestClient` (`ProductivityTipConfig`, timeouts from `ProductivityTipProperties`). Convention: any failure (connection refused, timeout, non-2xx, malformed body) degrades to `Optional.empty()` rather than propagating — a missing tip is never worth failing the request over. Follow this degrade-gracefully pattern for any other best-effort external integration.

### Transactional boundaries — a deliberate anti-example

`ProjectService.createBatchUnsafe` and `ProjectBatchService.createBatch` are a deliberate paired example of a Spring AOP proxy pitfall: `createBatchUnsafe` calls `this.create(...)` in a loop from a *non-transactional* method, so Spring's `@Transactional` proxy is never invoked for the batch as a whole — each `create()` call commits independently, and the thrown exception at the end does not roll them back. `ProjectBatchService.createBatch` fixes this by moving the `@Transactional` boundary to a separate service class that calls into `ProjectService`, going through the proxy correctly. `TransactionBoundaryTest` asserts on this difference directly (partial commits survive vs. full rollback) against a real Postgres container — deliberately not `@Transactional`/auto-rollback itself, since that would hide the exact behavior under test. Don't "fix" `createBatchUnsafe` — it exists to demonstrate the bug.

### Testing conventions

- Persistence and integration tests use real Postgres via Testcontainers (`@Import(TestcontainersConfiguration.class)`), never H2/embedded.
- Outbound HTTP client tests use WireMock, constructing the client directly with no Spring context (see `ProductivityTipClientTest`).
- `@SpringBootTest` tests default to `webEnvironment = MOCK` rather than `NONE` — `NONE` fails to load the context here because `SecurityConfig.authenticationManager()` needs an `AuthenticationConfiguration` bean that Spring Security only registers under a web application context.
- Tests that assert on real commit/rollback behavior avoid `@Transactional` on the test class itself (auto-rollback would mask what's being tested) and instead clean up explicitly in `@AfterEach`.
