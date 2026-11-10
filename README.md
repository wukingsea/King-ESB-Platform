# King ESB Platform

An enterprise-grade **Enterprise Service Bus (ESB)** that provides a unified, dynamically
configurable layer for integrating heterogeneous systems through **protocol adaptation**,
**message transformation**, **content-based routing / mediation**, and **centralized
governance**.

Routes are defined as JSON, persisted in a database, and compiled into a lock-free runtime
registry that is **hot-reloadable** — no restart required to change integration behaviour.

- **Runtime**: Java 17 + Spring Boot 3.5 (modular monolith)
- **Storage**: PostgreSQL (Flyway migrations) · **Cache / rate limit**: Redis
- **Security**: JWT (HS256) + RBAC on the admin API, optional per-route API key on the gateway
- **Observability**: Micrometer → Prometheus, persistent call logs, health indicators
- **Deployment**: single-node `docker compose up`

---

## Architecture

```
                         ┌───────────────────────────────┐
   Client System ──────▶ │        Inbound Gateway        │
   (REST / SOAP)         │  match route → API key →        │
                         │  rate limit → pipeline          │
                         └───────────────┬───────────────┘
                                         │
                         ┌───────────────▼───────────────┐
                         │        Pipeline Engine        │
                         │  SET_HEADER · REMOVE_HEADER ·  │
                         │  LOG · FILTER · ROUTER ·       │
                         │  TRANSFORM · SERVICE_CALL ·    │
                         │  RATE_LIMIT                    │
                         └───────┬───────────────┬───────┘
                                 │               │
                 ┌───────────────▼───┐   ┌───────▼────────────┐
                 │  Transport (SPI)  │   │ Transform (SPI)     │
                 │  HTTP/REST · SOAP │   │ JSON↔XML · Template │
                 │                   │   │ FieldMap            │
                 └───────────────────┘   └────────────────────┘
                                 │
                 ┌───────────────▼────────────────────────────┐
                 │ Governance: metrics · call log · rate limit │
                 │             · health indicators             │
                 └───────────────┬────────────────────────────┘
                                 │
                 ┌───────────────▼────────────────────────────┐
                 │ Admin API + Console (JWT auth)              │
                 │ routes CRUD/publish · logs · metrics · users│
                 └─────────────────────────────────────────────┘
```

### Module layout

| Module | Responsibility |
|--------|----------------|
| `esb-common` | Shared primitives: error codes, `EsbException`, constants, Jackson helpers |
| `esb-core` | Message/exchange model, expression resolver, predicate evaluator, pipeline engine, processors, route registry (SPI definitions) |
| `esb-transport` | HTTP/REST outbound client (timeout + retry) and SOAP envelope handling (SPI implementations) |
| `esb-transform` | JSON↔XML, template and field-map transformers (SPI implementations) |
| `esb-governance` | Metrics, call-log listener, Redis/in-memory rate limiters, health indicators |
| `esb-admin` | JPA entities, Flyway migrations, admin REST API, JWT security, inbound gateway filter, route persistence + mapping |
| `esb-app` | `@SpringBootApplication` bootstrap, configuration, static console, Docker/compose |

Dependency direction is strictly one-way:
`esb-common ◀ esb-core ◀ (esb-transport · esb-transform · esb-governance) ◀ esb-admin ◀ esb-app`.
`esb-core` depends only on SPI interfaces, never on concrete transports/transformers.

---

## Quickstart

### Prerequisites

- JDK 17+ (builds and runs on newer JDKs, e.g. 21/25 — bytecode target is 17)
- Maven 3.9+
- Docker + Docker Compose (for the containerised deployment)

### Option A — Dev profile (no external services)

Runs with an in-memory H2 database and an in-memory rate limiter.

```bash
mvn -q clean package -DskipTests
mvn -pl esb-app spring-boot:run -Dspring-boot.run.profiles=dev
```

Then:

- Console: <http://localhost:8080/console/> (default dev credentials `admin / admin`)
- Health: <http://localhost:8080/actuator/health>
- Prometheus: <http://localhost:8080/actuator/prometheus>
- Demo route (seeded automatically in `dev`): `curl -X POST http://localhost:8080/demo/echo/hello -d '{"ping":1}'`

### Option B — Docker Compose (PostgreSQL + Redis + app)

```bash
cp .env.example .env      # set ESB_JWT_SECRET (>= 32 chars) and ESB_ADMIN_PASSWORD
docker compose up --build
```

The application waits for PostgreSQL and Redis health checks, applies Flyway migrations, then
serves on <http://localhost:8080>.

---

## Route definition reference

A route matches inbound requests by **method + path pattern** and executes an ordered
**pipeline**. The definition is stored as JSON and validated server-side.

### Top-level fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | yes | unique across routes |
| `description` | string | no | free text |
| `path` | string | yes | must start with `/`; a single trailing `/**` acts as a wildcard (e.g. `/orders/**`) |
| `method` | string | no | `GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS` or `*` (default `*`) |
| `priority` | int | no | higher wins when several routes match |
| `enabled` | boolean | no | disabled routes never match |
| `apiKey` | string | no | when set, requests must send `X-ESB-Api-Key` |
| `rateLimit` | object | no | `{ "requestsPerWindow": 100, "windowSeconds": 60, "keyBy": "ip" }` |
| `pipeline` | array | yes | ordered processor steps (see below) |
| `errorHandler` | object | no | `{ "strategy": "DEFAULT" \| "CONTINUE", "fallbackStatus": 502 }` |

### Pipeline processors

| `type` | `config` keys | Behaviour |
|--------|---------------|-----------|
| `LOG` | `level`, `message` | Emits a structured log line with trace id + route id |
| `SET_HEADER` | `name`, `value`, `applyTo` (`request`/`response`) | Sets/overwrites a header (value supports `${...}`) |
| `REMOVE_HEADER` | `name`, `applyTo` | Removes a header; absent is a no-op |
| `FILTER` | `when`: list of predicates | Short-circuits with `204` when the predicate group does not match |
| `ROUTER` | `targets`: list of targets | Content-based routing; picks the first matching target or the default |
| `TRANSFORM` | `transformType`, plus type-specific keys | Applies a transformation (see below) |
| `SERVICE_CALL` | `target`, `source` (`request`/`response`) | Invokes a target endpoint |
| `RATE_LIMIT` | `requestsPerWindow`, `windowSeconds`, `keyBy` | Enforces a fixed-window limit; `429` when exceeded |

**Predicate** (`when` entries and target routing): `{ "source": "header:X-Tenant", "operator": "EQ", "value": "acme" }`.
Operators: `EQ`, `NE`, `CONTAINS`, `STARTS_WITH`, `REGEX`, `EXISTS` (default).

**Target**: `{ "id", "strategy": "ROUND_ROBIN" | "RANDOM" | "FAILOVER", "isDefault": true, "when": [...], "endpoints": [ ... ] }`.

**Endpoint**: `{ "id", "uri", "type": "HTTP" | "SOAP", "method", "headers", "connectTimeoutMs", "readTimeoutMs", "retryCount", "soapAction" }`.

### Transformation types

| `transformType` | config | Output content type |
|-----------------|--------|---------------------|
| `JSON_TO_XML` | `rootName` (default `root`) | `application/xml` |
| `XML_TO_JSON` | — | `application/json` |
| `TEMPLATE` | `template` (with `${...}` placeholders), `contentType` (default `text/plain`) | configured |
| `FIELD_MAP` | `mappings`: list of `{ "target", "source", "default", "required" }` | `application/json` |

### Expression syntax

Usable in `SET_HEADER.value`, `LOG.message` and `TEMPLATE.template`; also as predicate `source`:

| Token | Meaning |
|-------|---------|
| `${method}`, `${path}` | Inbound HTTP method / path |
| `${body}` | Raw body as string |
| `${header:Name}` or `${headers.Name}` | Request header (case-insensitive) |
| `${query:name}` or `${query.name}` | Query parameter |
| `${body:a.b.c}` or `${body.a.b.c}` | Dot-path into a JSON body |

### Example: JSON → SOAP passthrough with mediation + rate limit

```json
{
  "name": "orders-create",
  "description": "Mediate POST /orders to the legacy SOAP backend",
  "path": "/orders/**",
  "method": "POST",
  "priority": 100,
  "enabled": true,
  "apiKey": "s3cr3t",
  "rateLimit": { "requestsPerWindow": 60, "windowSeconds": 60, "keyBy": "ip" },
  "pipeline": [
    { "id": "log", "type": "LOG", "config": { "level": "info", "message": "order ${method} ${path}" } },
    { "id": "tag", "type": "SET_HEADER", "config": { "name": "X-ESB-Route", "value": "orders-create" } },
    { "id": "strip", "type": "REMOVE_HEADER", "config": { "name": "Cookie" } },
    {
      "id": "to-xml",
      "type": "TRANSFORM",
      "config": { "transformType": "JSON_TO_XML", "rootName": "order" }
    },
    {
      "id": "call",
      "type": "SERVICE_CALL",
      "config": {
        "target": {
          "id": "legacy",
          "strategy": "ROUND_ROBIN",
          "isDefault": true,
          "endpoints": [
            {
              "id": "legacy-1",
              "uri": "https://legacy.example.com/orderService",
              "type": "SOAP",
              "method": "POST",
              "soapAction": "createOrder",
              "connectTimeoutMs": 3000,
              "readTimeoutMs": 10000,
              "retryCount": 2
            }
          ]
        }
      }
    }
  ],
  "errorHandler": { "strategy": "DEFAULT", "fallbackStatus": 502 }
}
```

### Example: content-based routing

```json
{
  "name": "geo-router",
  "path": "/geo/**",
  "method": "*",
  "enabled": true,
  "pipeline": [
    {
      "id": "route",
      "type": "ROUTER",
      "config": {
        "targets": [
          {
            "id": "eu",
            "strategy": "ROUND_ROBIN",
            "when": [{ "source": "header:X-Region", "operator": "EQ", "value": "eu" }],
            "endpoints": [{ "id": "eu-1", "uri": "http://eu.backend/api", "type": "HTTP", "method": "POST" }]
          },
          {
            "id": "default",
            "strategy": "ROUND_ROBIN",
            "isDefault": true,
            "endpoints": [{ "id": "us-1", "uri": "http://us.backend/api", "type": "HTTP", "method": "POST" }]
          }
        ]
      }
    }
  ]
}
```

---

## Admin REST API (`/admin/api/v1`)

All endpoints require a bearer token (`Authorization: Bearer <jwt>`) obtained from
`POST /auth/login`. Errors use a uniform envelope:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Route definition is invalid",
             "details": [{ "field": "path", "message": "path is required" }],
             "traceId": "…" } }
```

| Method & path | Role | Purpose |
|---------------|------|---------|
| `POST /auth/login` | public | Obtain a JWT (`{ "username", "password" }` → `{ "token", "roles", "expiresIn" }`) |
| `GET /auth/me` | any authenticated | Current user + roles |
| `GET /routes` | VIEWER+ | List routes (paginated, optional `enabled` filter) |
| `GET /routes/{id}` | VIEWER+ | Route summary |
| `GET /routes/{id}/definition` | VIEWER+ | Full route definition (JSON) |
| `POST /routes` | OPERATOR+ | Create a route |
| `PUT /routes/{id}` | OPERATOR+ | Update a route (increments version) |
| `PUT /routes/{id}/status` | OPERATOR+ | Enable/disable (`{ "enabled": true }`) |
| `POST /routes/{id}/publish` | OPERATOR+ | Compile into the runtime registry |
| `DELETE /routes/{id}` | ADMIN | Delete + unpublish |
| `GET /logs` | VIEWER+ | Call logs (`routeId`, `status`, `from`, `to`, paginated) |
| `GET /metrics/summary` | VIEWER+ | Totals, error rate, avg latency, published route count |
| `GET /users` | ADMIN | List users |
| `POST /users` | ADMIN | Create a user (`{ "username", "password", "roles": ["VIEWER"] }`) |

### Gateway behaviour

Any path that is **not** under `/admin`, `/actuator`, `/console` or `/internal` is treated as
gateway traffic: it is matched against the runtime registry (no JWT required). Unmatched requests
return `404 ROUTE_NOT_FOUND`. Every exchange returns an `X-ESB-Trace-Id` response header.

---

## Observability

- `GET /actuator/health` — overall health (includes DB, Redis and a `esbRoutes` indicator).
- `GET /actuator/prometheus` — Micrometer metrics, including:
  - `esb_exchange_total{route,status}`
  - `esb_exchange_errors_total{route,status}`
  - `esb_exchange_duration_seconds{route,status}` (timer)
- Call logs are persisted **asynchronously** (bounded executor) with trace id, route id, method,
  path, status, http status, duration, client ip and error summary.

---

## Security notes

- Passwords are hashed with **BCrypt**; the bootstrap `admin` user is created from
  `esb.security.admin-password` (env `ESB_ADMIN_PASSWORD`) — never hard-coded.
- JWTs are **HS256**; the signing secret comes from `esb.security.jwt-secret` (env `ESB_JWT_SECRET`)
  and must be at least 32 characters.
- Admin API is stateless; the gateway is exempt from JWT but may be protected per route with an
  optional `apiKey` (`X-ESB-Api-Key`).
- `dev` profile ships insecure defaults for local convenience — **never** use it in production.

### Configuration

| Setting | Env var | Default |
|---------|---------|---------|
| JWT secret | `ESB_JWT_SECRET` | dev default only in `dev` profile |
| Bootstrap admin username / password | `ESB_ADMIN_USERNAME` / `ESB_ADMIN_PASSWORD` | `admin` / (none) |
| Token TTL (minutes) | — | `480` |
| Database URL / user / password | `ESB_DB_URL` / `ESB_DB_USER` / `ESB_DB_PASSWORD` | localhost PostgreSQL in `prod` |
| Redis host / port | `ESB_REDIS_HOST` / `ESB_REDIS_PORT` | `localhost` / `6379` |
| Active profile | `SPRING_PROFILES_ACTIVE` | `dev` |

---

## Build & test

```bash
mvn -q clean verify            # compile + all tests (core, transform, transport, admin/gateway)
mvn -q clean package -DskipTests   # build the runnable jar (esb-app/target/king-esb-platform.jar)
```

> **Behind a proxy?** Add a `<mirror>` and `<proxy>` to `~/.m2/settings.xml`. In the build sandbox,
> Maven needed `-Daether.connector.connectTimeout/requestTimeout/retryCount` to avoid SSL read
> hangs; the mirror in use is `https://maven.aliyun.com/repository/public`.

---

## Project layout

```
king-esb-platform/
├── esb-common/       # shared primitives
├── esb-core/         # engine + SPI
├── esb-transport/    # HTTP/REST + SOAP
├── esb-transform/    # JSON↔XML / template / field-map
├── esb-governance/   # metrics, call log, rate limit, health
├── esb-admin/        # persistence, security, REST API, gateway filter
├── esb-app/          # bootstrap, config, console, Dockerfile, compose
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

## License

Proprietary — see the repository owner.
