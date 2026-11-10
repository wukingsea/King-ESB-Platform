# Tasks — King ESB Platform

Legend: **Status** `pending|in_progress|completed` · **Wired** `no|yes|n/a` · **Verified** `no|yes`

## Progress Summary

| Status | Count |
|--------|-------|
| Total | 40 |
| Completed | 40 |
| Wired | 29 |
| Verified | 40 |

---

## Phase 0 — Setup

### T-1: Initialize Maven multi-module project
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: NFR-7
- **Description**: Create parent `pom.xml` (packaging `pom`, Spring Boot 3.5.16 parent, Java release 17), module list, `.gitignore`, `.editorconfig`, module skeletons. Include Maven proxy/mirror note in README build section.
- **Acceptance**: `mvn -q -N validate` succeeds; `mvn -q clean package` (empty modules) succeeds.
- **Dependencies**: none

### T-2: esb-common module
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: NFR-6, US-2
- **Description**: Shared primitives: `EsbErrorCode` enum, `EsbException`, `EsbConstants` (headers, reserved paths), `JsonUtil` (Jackson helpers).
- **Acceptance**: Unit test round-trips JSON; `EsbException` exposes code + message.
- **Dependencies**: T-1

### T-3: Core message & exchange model
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-2
- **Description**: `EsbMessage` (byte body, case-insensitive headers, contentType, helpers), `EsbExchange` (traceId, routeId, method, path, query, request/response, status, error, timing, variables), `ExchangeStatus`.
- **Acceptance**: Unit tests for header get/set/remove (case-insensitive), body string conversion, status transitions.
- **Dependencies**: T-2

### T-4: Core route & pipeline model
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1, US-3, US-4, US-5
- **Description**: Records/enums: `RouteDefinition`, `ProcessorDefinition`, `ProcessorType`, `RateLimitConfig`, `TargetDefinition`, `EndpointDefinition`, `EndpointType`, `LoadBalanceStrategy`, `PredicateDefinition`, `PredicateOperator`, `TransformType`, `ErrorHandlerConfig`, `ErrorStrategy`.
- **Acceptance**: Jackson (de)serialization of a full `RouteDefinition` round-trips.
- **Dependencies**: T-2

## Phase 1 — Core Engine

### T-5: Expression resolver & predicate evaluator
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-3
- **Description**: `ExpressionContext` (headers/body/path/method/query) + `${...}` resolver (dot-path into JSON body); `PredicateEvaluator` supporting EQ, NE, CONTAINS, STARTS_WITH, REGEX, EXISTS over `header:`/`path`/`method`/`query:`/`body:` sources.
- **Acceptance**: Unit tests cover each operator and each source; unknown expressions resolve to empty string, not error.
- **Dependencies**: T-3

### T-6: Processor SPI & pipeline engine
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-2, US-3
- **Description**: `Processor` interface, `PipelineContext`, `PipelineEngine` (ordered execution, error capture, error-handler strategies DEFAULT/CONTINUE), `PipelineExecutionResult`.
- **Acceptance**: Unit tests: ordered execution; a throwing processor triggers error handler; CONTINUE keeps last response.
- **Dependencies**: T-4

### T-7: Basic processors
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-3
- **Description**: `SetHeaderProcessor`, `RemoveHeaderProcessor`, `LogProcessor`, `FilterProcessor` (204 on false).
- **Acceptance**: Unit tests per processor incl. filter short-circuit.
- **Dependencies**: T-6

### T-8: Router, load balancer & call processors
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-3, US-4
- **Description**: `ContentBasedRouter` (predicate → target, default fallback), `LoadBalancer` (ROUND_ROBIN/RANDOM/FAILOVER), `RouterProcessor`, `ServiceCallProcessor` (delegate via `OutboundClientRegistry` SPI).
- **Acceptance**: Unit tests: predicate selection, default fallback, round-robin distribution, failover on error.
- **Dependencies**: T-6, T-5

### T-9: Route registry & matcher
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1, US-2
- **Description**: `CompiledRoute`, `RouteRegistry` (atomic map, compile/publish/enable/disable/remove/load), `RouteMatcher` (priority order, exact-then-`/**` wildcard, method match incl. `*`).
- **Acceptance**: Unit tests: exact match, wildcard match, method filter, disabled routes skipped, publish swaps atomically.
- **Dependencies**: T-4

## Phase 2 — Transport & Transform

### T-10: Outbound HTTP client
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-4, NFR-3
- **Description**: `OutboundClient` SPI, `OutboundClientRegistry`, `HttpOutboundClient` (connect/read timeout, retry on 5xx/IOError with exponential backoff, header/body mapping, status mapping).
- **Acceptance**: Unit test with embedded `HttpServer` stub: 200 passthrough; 500 then 200 with retryCount=1 succeeds; retries exhausted → `GATEWAY_ERROR`.
- **Dependencies**: T-4, T-3

### T-11: SOAP support
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-4
- **Description**: `SoapRequestParser` (detect by content type, extract Body + SOAPAction), `SoapOutboundClient` (wrap in SOAP 1.1 envelope, set SOAPAction, parse response, map Fault → error).
- **Acceptance**: Unit tests for envelope wrapping, response body extraction, fault → `GATEWAY_ERROR` with fault code.
- **Dependencies**: T-10

### T-12: JSON↔XML transformer
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-5
- **Description**: `Transformer` SPI, `TransformerRegistry`, `JsonXmlTransformer` (JSON_TO_XML with root name, XML_TO_JSON).
- **Acceptance**: Unit tests round-trip JSON→XML→JSON preserving values; content-type header updated.
- **Dependencies**: T-4, T-3

### T-13: Template transformer
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-5
- **Description**: `TemplateTransformer` rendering `${...}` placeholders from message via the expression resolver.
- **Acceptance**: Unit test renders mixed header/body/query placeholders; output content type applied.
- **Dependencies**: T-12, T-5

### T-14: Field-map transformer
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-5
- **Description**: `FieldMapTransformer` with `mappings[{target,source,default,required}]`, dot-path extraction, required-missing → `TRANSFORM_ERROR`.
- **Acceptance**: Unit tests: rename, nested source, default applied, required missing fails with code.
- **Dependencies**: T-12, T-5

## Phase 3 — Governance

### T-15: Metrics
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-6, NFR-5
- **Description**: `EsbMetrics` recording `esb.exchange.duration` timer + `esb.exchange.total`/`esb.exchange.errors` counters tagged `route`,`status`; `ExchangeListener` SPI in core.
- **Acceptance**: Unit test asserts counter/timer registration for a synthetic exchange.
- **Dependencies**: T-3

### T-16: Call-log listener & sink SPI
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-6
- **Description**: `CallLogSink` SPI in core; `CallLogExchangeListener` in governance mapping an exchange into a `CallLogRecord` (trace, route, method, path, status, httpStatus, duration, clientIp, error) and handing to the sink.
- **Acceptance**: Unit test asserts a completed exchange yields a correctly populated `CallLogRecord`.
- **Dependencies**: T-15

### T-17: Rate limiter
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-6, NFR-3
- **Description**: `RateLimiter` SPI; `RedisRateLimiter` (INCR+EXPIRE fixed window), `InMemoryRateLimiter` (sliding window), `ResilientRateLimiter` (Redis errors → in-memory).
- **Acceptance**: Unit tests: allows within limit, blocks over limit, resilient wrapper falls back when delegate throws.
- **Dependencies**: T-4

### T-18: Health indicators
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-6
- **Description**: `RouteRegistryHealthIndicator` (published route count), plus enable DB/Redis health contributions.
- **Acceptance**: Actuator health includes `esbRoutes` component with status UP when ≥1 published route.
- **Dependencies**: T-9, T-15

## Phase 4 — Admin, Security & Gateway

### T-19: Persistence entities, repositories & migrations
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1, US-6, US-7
- **Description**: JPA entities `RouteRecord`, `CallLogEntity`, `UserRecord`, `AuditLogEntry`; Spring Data repositories; Flyway `V1__init.sql` (PostgreSQL + H2 compatible).
- **Acceptance**: Integration test (H2) boots context and Flyway creates tables; repository save/read works.
- **Dependencies**: T-18

### T-20: Route config mapper
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1
- **Description**: `RouteConfigMapper` between `RouteDefinition` and `RouteRecord.config_json` (Jackson), including validation of path/method/pipeline and reserved-prefix rejection.
- **Acceptance**: Unit tests: round-trip; invalid path/pipeline produce validation errors; reserved prefix rejected.
- **Dependencies**: T-19, T-4

### T-21: Route service & publish/hot-reload
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1
- **Description**: `RouteService` (CRUD, unique-name check, status toggle) + `RoutePublishService` (version increment, compile into `RouteRegistry`) + startup loader `RouteBootstrap` loading enabled routes.
- **Acceptance**: Integration test: create → publish → registry contains route; disable removes from matching; duplicate name → conflict.
- **Dependencies**: T-20, T-9

### T-22: JWT security & user seeding
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-7
- **Description**: `JwtService` (HS256), `JwtAuthFilter`, `SecurityConfig` (stateless, path rules, `@PreAuthorize`), `UserService` (BCrypt), bootstrap `AdminUserSeeder` from env password.
- **Acceptance**: Integration test: login returns token; protected endpoint 401 without token; VIEWER gets 403 on mutation; no plaintext password in source.
- **Dependencies**: T-19

### T-23: Admin REST API
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-1, US-6, US-7
- **Description**: Controllers: `AuthController`, `RouteAdminController`, `LogAdminController`, `MetricsAdminController`, `UserAdminController`; `GlobalExceptionHandler` producing the uniform error body; DTOs.
- **Acceptance**: MockMvc tests for each endpoint's happy path + key errors with uniform error shape.
- **Dependencies**: T-21, T-22

### T-24: Call-log persistence & query
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-6
- **Description**: `JpaCallLogSink` implementing `CallLogSink` with `@Async` bounded executor; `CallLogQueryService` with filters (routeId/status/from/to) + pagination.
- **Acceptance**: Integration test: processing an exchange persists a log; query filters return expected subset.
- **Dependencies**: T-16, T-19

### T-25: Inbound gateway controller
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-2, US-4, US-6, US-7
- **Description**: `EsbGatewayController` (`/**`, lowest precedence): match route → API-key check → rate-limit check → build exchange → execute pipeline → write status/headers/body + `X-ESB-Trace-Id`; 404 `ROUTE_NOT_FOUND`, 429 `RATE_LIMITED`, 502 `GATEWAY_ERROR`.
- **Acceptance**: Integration test with stub backend: passthrough 200; unknown path 404; over-limit 429; backend failure 502.
- **Dependencies**: T-23, T-21, T-17, T-24

### T-26: Bean wiring & assembly
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-2, US-3, US-4, US-5
- **Description**: Auto-configuration/wiring registering all `Processor`, `Transformer`, `OutboundClient`, `ExchangeListener` beans into their registries; construct `PipelineContext`.
- **Acceptance**: Application context starts; registries contain all expected implementations (integration assertion).
- **Dependencies**: T-7, T-8, T-11, T-13, T-14, T-25

## Phase 5 — App, Console & Deployment

### T-27: Application bootstrap & config
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-9, NFR-8
- **Description**: `EsbApplication`, `application.yml` + `application-dev.yml` (H2, in-memory limiter), actuator + Prometheus exposure, async executor, JPA/Flyway config.
- **Acceptance**: `mvn -pl esb-app spring-boot:run -Dspring-boot.run.profiles=dev` starts; `/actuator/health` UP.
- **Dependencies**: T-26

### T-28: Admin console SPA
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-8
- **Description**: Static `console/index.html`, `app.js`, `styles.css`: login, dashboard metrics, routes list/editor (JSON), logs viewer; JWT in localStorage; no external CDN.
- **Acceptance**: Manual/auto check: console loads at `/console/`, login works, routes list renders from API.
- **Dependencies**: T-27, T-23

### T-29: Container & Compose deployment
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-9
- **Description**: Multi-stage `Dockerfile` (temurin-17-jre), `docker-compose.yml` (postgres, redis, app with healthchecks + depends_on), `.env.example`, `.dockerignore`.
- **Acceptance**: `docker compose config` validates (docker not available in sandbox → config linted/`docker-compose.yml` reviewed); app image builds in CI.
- **Dependencies**: T-27
- **Notes**: Docker unavailable in the build sandbox; verification limited to YAML/config validation.

### T-30: README & architecture docs
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: NFR-6
- **Description**: `README.md` with overview, architecture diagram, quickstart (compose + dev), route-definition reference (JSON schema + examples), API reference, security notes, build/test instructions.
- **Acceptance**: README builds a coherent quickstart; includes a working sample route.
- **Dependencies**: T-28, T-29

## Phase 6 — Testing

### T-31: Core engine unit tests
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-2, US-3
- **Description**: Unit tests across expression/predicate, pipeline, processors, router, load balancer, registry/matcher.
- **Acceptance**: All pass; meaningful branch coverage of engine logic.
- **Dependencies**: T-9

### T-32: Transform unit tests
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-5
- **Description**: Unit tests for JSON↔XML, template, field-map transformers incl. error cases.
- **Acceptance**: All pass, incl. `TRANSFORM_ERROR` cases.
- **Dependencies**: T-14

### T-33: Transport unit tests
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-4
- **Description**: Unit tests for HTTP client (retry/timeout/status) and SOAP (envelope/parse/fault) using an embedded mock server.
- **Acceptance**: All pass.
- **Dependencies**: T-11

### T-34: Admin & gateway integration tests
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-1, US-2, US-6, US-7
- **Description**: `@SpringBootTest` + MockMvc with H2 + no-Redis (in-memory limiter) + embedded stub backend: auth, routes CRUD/publish, gateway passthrough, 404/429/502, logs query.
- **Acceptance**: All pass; verifies US-1/2/6/7 acceptance criteria.
- **Dependencies**: T-26, T-23, T-25

### T-35: End-to-end smoke verification
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: US-2, US-6, US-9
- **Description**: Scripted smoke: start app (dev profile), create+publish a sample route to an embedded stub backend, invoke gateway, assert 200 + trace header + call log + metrics endpoint contains `esb_exchange_total`.
- **Acceptance**: Smoke script exits 0; documented in README.
- **Dependencies**: T-34, T-28

## Phase 7 — Polish & Release

### T-36: Error handling & logging polish
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: NFR-3, NFR-5
- **Description**: Consistent error mapping across gateway/admin; structured logging with trace id (MDC) propagated to call log.
- **Acceptance**: Logs contain trace id; gateway errors always use uniform error body.
- **Dependencies**: T-25

### T-37: Demo seed route
- **Status**: completed
- **Wired**: yes
- **Verified**: yes
- **Requirements**: US-9
- **Description**: Dev-profile `DemoRouteSeeder` creating a sample `/demo/echo/**` route proxying to an embedded echo endpoint, to make the platform immediately demonstrable.
- **Acceptance**: After dev startup, `/demo/echo/**` returns a mediated response and appears in logs.
- **Dependencies**: T-27

### T-38: Build & quality gate
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: NFR-6
- **Description**: Ensure `mvn -q clean verify` passes (compile + all tests); fix warnings that break on JDK 25 (target 17 bytecode).
- **Acceptance**: `mvn -q clean verify` exits 0.
- **Dependencies**: T-31, T-32, T-33, T-34

### T-39: Spec validation
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: —
- **Description**: Validate spec ↔ implementation traceability; update task statuses; confirm every US maps to tasks and every task is DONE (Status=completed, Wired=yes/n/a, Verified=yes).
- **Acceptance**: Traceability matrix complete; all tasks DONE.
- **Dependencies**: T-38

### T-40: Local git commit (no push)
- **Status**: completed
- **Wired**: n/a
- **Verified**: yes
- **Requirements**: —
- **Description**: Commit all work to the local git repository on branch `main`. Do NOT push to the remote (`https://github.com/wukingsea/King-ESB-Platform`) until the user explicitly approves.
- **Acceptance**: `git log` shows the commit(s); `git remote` may be configured but no push performed.
- **Dependencies**: T-39

---

## Traceability Matrix

| Requirement | Tasks |
|-------------|-------|
| US-1 Route Management | T-4, T-9, T-19, T-20, T-21, T-23, T-34 |
| US-2 Inbound Gateway | T-2, T-3, T-6, T-9, T-25, T-26, T-31, T-34, T-35 |
| US-3 Routing & Mediation | T-4, T-5, T-6, T-7, T-8, T-26, T-31 |
| US-4 Protocol Adaptation | T-4, T-8, T-10, T-11, T-25, T-26, T-33 |
| US-5 Transformation | T-4, T-12, T-13, T-14, T-26, T-32 |
| US-6 Governance | T-6, T-15, T-16, T-17, T-18, T-24, T-25, T-34, T-35, T-36 |
| US-7 Security | T-19, T-22, T-23, T-25, T-34 |
| US-8 Admin Console | T-23, T-27, T-28 |
| US-9 Deployment | T-27, T-29, T-30, T-35, T-37 |
| NFR-1..8 | T-1, T-9, T-15, T-17, T-27, T-36, T-38 |
