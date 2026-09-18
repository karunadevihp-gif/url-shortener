# AI Engineering Scenarios & Requirement Decomposition

This artifact documents the execution, task decomposition, and validation of the three core assignment scenarios: Greenfield, Brownfield, and Ambiguous Requirements[cite: 1].

---

## 1. Greenfield Scenario: URL Shortener Core Engine

* **Requirement**: Build a working URL shortener prototype from scratch[cite: 1].
* **Task Decomposition**:
  1. Define domain entity (`UrlMapping`) with indexed `shortCode` and `originalUrl` fields[cite: 1].
  2. Implement Base62 encoding service with collision handling[cite: 1].
  3. Implement `POST /api/v1/shorten` for key creation and `GET /{shortCode}` for HTTP 302 redirects[cite: 1].
* **Execution & AI Role**: Copilot generated initial controller templates and JPA repository methods[cite: 1].
* **Validation**: MockMvc unit tests verifying HTTP 201 creation and HTTP 302 redirect responses.

---

## 2. Brownfield Scenario: Analytics & Schema Versioning

* **Requirement**: Enhance an existing codebase with analytics tracking without breaking contracts[cite: 1].
* **Task Decomposition**:
  1. Extend `UrlMapping` domain model with `clickCount` and `lastAccessedAt` attributes[cite: 1].
  2. Implement `GET /api/v1/analytics/{shortCode}` endpoint[cite: 1].
  3. Introduce Liquibase (`db.changelog-master.xml`) to manage schema updates safely.
  4. Expose metrics and health endpoints for operational visibility in Docker and local deployments.
  5. Harden Docker startup for Postgres and Redis readiness before app boot.
* **Execution & AI Role**: AI assisted in drafting non-blocking asynchronous click-tracking updates, deployment-ready observability changes, and distributed rate-limiting adjustments.
* **Validation**: Integration tests verifying click counter increments on redirects while existing REST contracts remain unaltered[cite: 1]. Metrics endpoints are also validated for operational readiness[cite: 1]. PostgreSQL and Redis startup health checks were verified via Docker Compose orchestration.

---

## 3. Ambiguous Requirement Normalization: "API Safety & Resilience"

* **Ambiguous Intent**: *"Ensure the service is safe, performant, and resilient under production load."*[cite: 1]
* **Requirement Normalization & Engineering Specs**:
  1. **Risk Identified**: Automated script spamming `POST /api/v1/shorten` causing database bloat, key exhaustion, and CPU spikes.
  2. **Technical Solution**: Implemented IP-based distributed rate limiting via `IpRateLimitingFilter` using Redis-backed Bucket4j.
  3. **Architectural Guardrail**: Restricted filter execution strictly to write paths (`POST /api/v1/shorten` @ 10 req/min/IP).
  4. **Bypass Rules**: Read operations (`GET /{shortCode}`) and operational health checks (`/actuator/*`) bypass the filter to ensure high-throughput lookups and continuous telemetry.
  5. **Operational Concern**: Single-JVM state was replaced with shared Redis bucket state so multiple app instances behind a load balancer enforce the same global limit.
* **Validation & Safety Guardrails**:
  * Exceeding 10 POST requests/minute from a single IP returns `HTTP 429 Too Many Requests` with JSON error details.
  * Unit test suite (`IpRateLimitingFilterTest`) validates rate limit enforcement on `POST` while confirming uncapped access for `GET` endpoints.
  * Docker validation confirms Redis stores keys such as `rate_limit:*` and that repeated requests are throttled across the app runtime.
