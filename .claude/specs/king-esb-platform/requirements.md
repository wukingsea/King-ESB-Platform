# Requirements — King ESB Platform

## Overview

King ESB Platform is an enterprise-grade Enterprise Service Bus (ESB) that provides a
unified, dynamically configurable layer for integrating heterogeneous systems through
protocol adaptation, message transformation, content-based routing/mediation, and
centralized governance/monitoring.

The platform is a **modular monolith** built on **Java 17 + Spring Boot 3 (3.5.x)**,
persisted on **PostgreSQL**, cached/rate-limited with **Redis**, secured with **JWT + RBAC**,
and shipped with a **Docker Compose** single-node deployment.

## Goals

- **G-1** Allow operators to define, publish, and hot-reload integration routes without restarting the runtime.
- **G-2** Adapt heterogeneous protocols (HTTP/REST and SOAP/XML) into a normalized internal message model.
- **G-3** Transform messages between JSON and XML and apply declarative mappings/templates.
- **G-4** Route and mediate requests using content-based predicates, load balancing, and a composable processor pipeline.
- **G-5** Provide centralized observability (metrics, call logs, health) and governance (rate limiting) for every route.
- **G-6** Provide a web admin console and secured REST API for configuration and monitoring.
- **G-7** Be deployable end-to-end with a single `docker compose up`.

## Personas / Roles

| Role | Description |
|------|-------------|
| **Integration Developer** | Defines routes, transformations, and endpoints via the console/API. |
| **Platform Operator** | Publishes routes, monitors traffic, manages rate limits and health. |
| **Auditor / Viewer** | Read-only access to routes, logs, and metrics. |
| **Client System** | External system (REST/SOAP) that calls the ESB gateway or is called by it. |

## Scope

### In Scope (MVP)
- Dynamic route management (CRUD, enable/disable, publish, hot reload).
- Inbound HTTP gateway matching routes by path + method.
- Mediation pipeline: set/remove headers, log, filter, content-based routing, service call, transform, rate limit.
- Outbound HTTP/REST client with timeout + retry; SOAP inbound parsing and outbound SOAP invocation.
- Transformation: JSON↔XML conversion, placeholder template mapping, field mapping.
- Governance: per-route metrics (Micrometer/Prometheus), persistent call logs, Redis rate limiting, health indicators.
- Security: JWT login, RBAC on the admin API (ADMIN/OPERATOR/VIEWER).
- Admin console (static SPA) for login, dashboard, routes, and logs.
- Deployment: Dockerfile + docker-compose (Postgres + Redis + app), Flyway migrations.

### Out of Scope (MVP)
- Multi-node clustering / distributed runtime coordination (single node only).
- Full XSLT / full JSONata engines (only a constrained mapping DSL is provided).
- Kafka/JMS/File/FTP transports (HTTP/REST + SOAP only).
- OAuth2/OIDC / SAML SSO (local JWT only).
- Multi-tenancy and billing.

## User Stories & Acceptance Criteria (EARS)

### US-1: Route Management

**As an** Integration Developer
**I want** to create, update, enable/disable, and publish routes
**So that** I can change integration behavior at runtime without redeploying.

1. WHEN a user with OPERATOR or ADMIN role submits a valid route definition to `POST /admin/api/v1/routes`
   THE SYSTEM SHALL persist the route and return `201` with the generated route id and version 1.
1. WHEN a route definition is submitted with a duplicate `name`
   THE SYSTEM SHALL reject it with `409 CONFLICT` and a `ROUTE_NAME_EXISTS` error code.
1. WHEN a route definition is submitted with a missing `name`, missing `path`, or an empty pipeline
   THE SYSTEM SHALL reject it with `400` and a `VALIDATION_ERROR` listing the offending fields.
1. WHEN an operator calls `PUT /admin/api/v1/routes/{id}/status` with `enabled=false`
   THE SYSTEM SHALL mark the route disabled and stop matching new inbound requests to it within the same request cycle.
1. WHEN an operator calls `POST /admin/api/v1/routes/{id}/publish`
   THE SYSTEM SHALL increment the route version, compile the route into the runtime registry, and make it effective without restarting the application.
1. WHEN a route is deleted
   THE SYSTEM SHALL remove it from the runtime registry so subsequent matching requests return `404`.
1. THE SYSTEM SHALL validate that a route `path` starts with `/` and does not conflict with a reserved prefix (`/admin`, `/actuator`).
1. THE SYSTEM SHALL record the creating/updating user and timestamp on every route mutation.

### US-2: Inbound Gateway Execution

**As a** Client System
**I want** to call a stable ESB endpoint
**So that** the ESB can mediate my request to the correct backend.

1. WHEN an inbound request matches an enabled route by method and path pattern
   THE SYSTEM SHALL execute the route's processor pipeline in order and return the final response body and status.
1. WHEN an inbound request matches no enabled route
   THE SYSTEM SHALL return `404` with error code `ROUTE_NOT_FOUND`.
1. WHEN a route pipeline fails with an unhandled exception
   THE SYSTEM SHALL return `502` with error code `GATEWAY_ERROR` and record the failure in the call log.
1. THE SYSTEM SHALL propagate inbound headers to the internal message headers while excluding hop-by-hop headers (`host`, `connection`, `content-length`, `transfer-encoding`).
1. WHEN any inbound request is processed
   THE SYSTEM SHALL assign a `X-ESB-Trace-Id` (UUID) to the exchange and return it as a response header.
1. THE SYSTEM SHALL support path patterns with a single trailing wildcard (e.g. `/orders/**`).

### US-3: Routing & Mediation

**As an** Integration Developer
**I want** to route and mediate messages declaratively
**So that** I can implement integration logic without writing code.

1. WHEN a pipeline contains a content-based router whose predicate evaluates true for a target
   THE SYSTEM SHALL select that target endpoint; WHEN no predicate matches THE SYSTEM SHALL use the configured default target.
1. WHEN a target is configured with multiple endpoint URLs and strategy `ROUND_ROBIN`
   THE SYSTEM SHALL distribute successive calls across the endpoints in round-robin order.
1. WHEN a target is configured with strategy `RANDOM`
   THE SYSTEM SHALL select an endpoint uniformly at random.
1. WHEN a `SET_HEADER` processor executes
   THE SYSTEM SHALL set or overwrite the named header with the resolved value.
1. WHEN a `REMOVE_HEADER` processor executes
   THE SYSTEM SHALL remove the named header if present without failing when absent.
1. WHEN a `FILTER` processor predicate evaluates false
   THE SYSTEM SHALL short-circuit the exchange and return `204` with no body.
1. WHEN a `LOG` processor executes
   THE SYSTEM SHALL emit a structured log entry containing the trace id, route id, and rendered message.
1. THE SYSTEM SHALL evaluate predicates against an expression context exposing `headers`, `body`, `path`, `method`, and `query`.

### US-4: Protocol Adaptation

**As a** Client System
**I want** the ESB to speak both REST/JSON and SOAP/XML
**So that** I can integrate legacy and modern systems uniformly.

1. WHEN an outbound step target is `http` or `https`
   THE SYSTEM SHALL invoke it using the configured method, headers, and body with the configured connect/read timeout.
1. WHEN an outbound HTTP call returns a `5xx` response and `retry` is configured with count N
   THE SYSTEM SHALL retry up to N times with exponential backoff before failing the step.
1. WHEN an inbound request has `Content-Type: text/xml` or `application/soap+xml`
   THE SYSTEM SHALL parse the SOAP envelope and expose the SOAP `Body` and `SOAPAction` to the pipeline.
1. WHEN an outbound target type is `SOAP`
   THE SYSTEM SHALL wrap the current body in a SOAP 1.1 envelope with the configured `soapAction` before invoking the endpoint.
1. WHEN a SOAP fault is received from a backend
   THE SYSTEM SHALL surface it as a step error and record the fault code in the call log.
1. THE SYSTEM SHALL reject inbound requests whose path matches a route but whose required content type is not satisfied with `415`.

### US-5: Message Transformation

**As an** Integration Developer
**I want** to transform messages between formats and shapes
**So that** producers and consumers can exchange different representations.

1. WHEN a `TRANSFORM` processor has type `JSON_TO_XML`
   THE SYSTEM SHALL convert the JSON body to its XML equivalent using the configured root element name (default `root`).
1. WHEN a `TRANSFORM` processor has type `XML_TO_JSON`
   THE SYSTEM SHALL convert the XML body to a JSON object.
1. WHEN a `TRANSFORM` processor has type `TEMPLATE`
   THE SYSTEM SHALL render the configured template, substituting `${path}` placeholders from the source message.
1. WHEN a `TRANSFORM` processor has type `FIELD_MAP`
   THE SYSTEM SHALL produce a new JSON body containing only the mapped output fields, applying renames, defaults, and dot-path source extraction.
1. WHEN a transformation cannot be applied (e.g. malformed input)
   THE SYSTEM SHALL fail the step with error code `TRANSFORM_ERROR` and a message identifying the transformer type.
1. WHEN a transformation succeeds
   THE SYSTEM SHALL update the message `Content-Type` header to match the output format.

### US-6: Governance & Observability

**As a** Platform Operator
**I want** metrics, logs, rate limiting, and health for every route
**So that** I can operate the platform reliably.

1. WHEN any exchange completes
   THE SYSTEM SHALL record a timer and counters (requests, errors) tagged by route id and status.
1. WHEN any exchange completes
   THE SYSTEM SHALL asynchronously persist a call log record with trace id, route id, method, path, status, duration, and error summary.
1. WHEN a route has a rate limit configured and the client exceeds it within the window
   THE SYSTEM SHALL reject the request with `429` and error code `RATE_LIMITED`.
1. WHEN Redis is unavailable
   THE SYSTEM SHALL fall back to an in-process rate-limit store and continue serving traffic (fail-open for the limiter, never fail the exchange).
1. THE SYSTEM SHALL expose Prometheus metrics at `/actuator/prometheus`.
1. WHEN a route is disabled or has never been published, the health endpoint SHALL NOT report it as healthy.
1. THE SYSTEM SHALL expose overall health at `/actuator/health` including database and redis status.
1. WHEN a call log query is issued
   THE SYSTEM SHALL support filtering by route id, status, and time range with pagination.

### US-7: Security & Access Control

**As a** Platform Operator
**I want** the admin API and console to be secured
**So that** only authorized users can change configuration.

1. WHEN a user submits valid credentials to `POST /admin/api/v1/auth/login`
   THE SYSTEM SHALL return a signed JWT access token and the user's roles.
1. WHEN a user submits invalid credentials
   THE SYSTEM SHALL return `401` with error code `INVALID_CREDENTIALS` without revealing which field was wrong.
1. WHEN a request without a valid JWT calls a protected admin endpoint
   THE SYSTEM SHALL return `401` with error code `UNAUTHORIZED`.
1. WHEN a VIEWER calls a mutating endpoint
   THE SYSTEM SHALL return `403` with error code `FORBIDDEN`.
1. WHEN an OPERATOR or ADMIN calls a mutating endpoint
   THE SYSTEM SHALL allow it.
1. THE SYSTEM SHALL NOT store passwords in plain text; passwords SHALL be hashed with BCrypt.
1. THE SYSTEM SHALL seed a default `admin` user on first startup whose password is provided via configuration/`ADMIN_PASSWORD` (never hard-coded in source).
1. THE SYSTEM SHALL exempt gateway routes (`/**` outside `/admin` and `/actuator`) from JWT authentication, protecting them instead via an optional route API key.
1. WHEN a route has an `apiKey` configured
   THE SYSTEM SHALL require the `X-ESB-Api-Key` header to match, otherwise return `401`.

### US-8: Admin Console

**As an** Integration Developer or Operator
**I want** a web console
**So that** I can manage and monitor the platform visually.

1. WHEN a user opens the console and is not authenticated
   THE SYSTEM SHALL present a login form.
1. WHEN a user logs in successfully
   THE SYSTEM SHALL show a dashboard with request counts, error rate, and average latency.
1. WHEN a user opens the Routes page
   THE SYSTEM SHALL list routes with name, path, method, enabled state, and version, and allow creating/editing a route as JSON.
1. WHEN a user opens the Logs page
   THE SYSTEM SHALL display recent call logs and allow filtering by route.
1. WHEN a user performs a mutating action without sufficient role
   THE SYSTEM SHALL display the returned error message and take no action.
1. THE SYSTEM SHALL serve the console as static assets from the application root `/console/` with no external CDN dependency.

### US-9: Deployment & Operations

**As a** Platform Operator
**I want** a reproducible local deployment
**So that** I can run the platform end-to-end.

1. WHEN an operator runs `docker compose up`
   THE SYSTEM SHALL start PostgreSQL, Redis, and the ESB application, with the application healthy and migrations applied.
2. THE SYSTEM SHALL read all environment-specific settings (datasource, redis, jwt secret, admin password) from environment variables with sensible defaults for local development.
3. WHEN the application starts, THE SYSTEM SHALL apply Flyway migrations before serving traffic.
4. THE SYSTEM SHALL provide a `dev` profile that runs against an in-memory H2 database for tests and quick local runs.
5. THE SYSTEM SHALL build a runnable jar via `mvn -q clean package` and a container image via the provided `Dockerfile`.

## Non-Functional Requirements

| ID | Category | Requirement |
|----|----------|-------------|
| NFR-1 | Performance | Gateway overhead SHALL be < 20 ms (p95) for a passthrough route excluding backend latency. |
| NFR-2 | Scalability | The runtime SHALL be stateless with respect to route config (config in DB) to allow future horizontal scaling. |
| NFR-3 | Reliability | A failing backend SHALL NOT crash the runtime; errors are contained per exchange. |
| NFR-4 | Security | JWT (HS256), BCrypt password hashing, no secrets in source, admin API authenticated, gateway optionally API-key protected. |
| NFR-5 | Observability | Every exchange produces metrics + a call log; logs include a trace id. |
| NFR-6 | Maintainability | Maven multi-module with clear module boundaries; unit + integration tests for core logic. |
| NFR-7 | Portability | Java 17 bytecode; runs on JDK 17+; container base image `eclipse-temurin:17-jre`. |
| NFR-8 | Config in DB | Routes/pipelines are persisted and hot-reloadable; no rebuild required to change behavior. |

## Open Questions

1. Should route config support a visual pipeline builder in the console, or is JSON editing acceptable for MVP? → **Decision: JSON editing for MVP.**
2. Should JWT be symmetric (HS256) or asymmetric (RS256)? → **Decision: HS256 for MVP (single node, simpler key management).**
3. Should call logging be synchronous or asynchronous? → **Decision: asynchronous (`@Async` + bounded executor) to avoid impacting latency.**
4. Should the console be a Node/Vite SPA? → **Decision: dependency-free static SPA (vanilla JS) served by Spring Boot, to guarantee it runs without a Node toolchain at runtime.**
