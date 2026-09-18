# URL Shortener Service

A Spring Boot URL shortener prototype built with Java 17, Spring Web, Spring Data JPA, H2 in-memory database, and Swagger/OpenAPI.

## Features

- Create short URLs from a long URL payload
- Store mappings in a persistent database with Liquibase schema versioning
- Redirect short codes to the original long URL
- Track click count and last-accessed timestamp per short code
- Expose analytics for each short code
- Request validation using Jakarta Validation
- Global exception handling for validation and runtime errors
- IP-based bucket rate limiting for POST /api/v1/shorten
- Swagger/OpenAPI docs generated automatically
- Spring Boot Actuator health, metrics, and Prometheus endpoints
- Console and MDC trace-aware logging
- MockMvc + Mockito controller tests covering success and failure paths
- Docker Compose setup for PostgreSQL, Redis, and the application

## Tech Stack

- Java 17
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- PostgreSQL / H2 (development)
- Liquibase
- Redis (deployment support via Docker)
- Bucket4j
- Spring Validation
- SpringDoc OpenAPI UI
- Spring Boot Actuator + Micrometer Prometheus
- JUnit 5 + Mockito + MockMvc

## Architecture Overview

This application is built in a layered MVC-style architecture with separation of concerns:

- Controller layer: accepts HTTP requests and returns HTTP responses
- Service layer: contains the business logic for shortening and redirect resolution
- Repository layer: persists and retrieves `UrlMapping` records via Spring Data JPA
- Entity layer: models the database table representing short code mappings
- Exception layer: centralizes API error handling and returns consistent status/error payloads

## High-Level Architecture Diagram

```text
+-------------------+       HTTP       +---------------------------+
| Client / Browser  | ----------------> | URL Shortener Controller  |
+-------------------+                  +-------------+-------------+
                                              |
                                              v
                                   +-------------------+
                                   | UrlMappingService |
                                   +---------+---------+
                                             |
                         +-------------------+-------------------+
                         |                                       |
                         v                                       v
              +-------------------+                 +---------------------+
              | Spring Data JPA  |                 | Bucket4j Rate Limiter|
              | Repository        |                 | (POST /shorten only) |
              +---------+---------+                 +---------------------+
                        |
                        v
              +-------------------+
              | Liquibase + DB    |
              | PostgreSQL/H2     |
              +-------------------+

Optional runtime services:
+ Redis cache / shared rate-limit store (Docker)
+ Actuator + Prometheus metrics / tracing
```
## Component Diagram

```text
+--------------------------------------------------------------+
|                        URL Shortener App                       |
+--------------------------------------------------------------+
|                                                              |
|  +------------------+   +------------------------------+      |
|  | Controller       |   | Service                     |      |
|  | - POST /shorten  |   | - createShortUrl()          |      |
|  | - GET /{code}    |   | - findByShortCode()         |      |
|  +------------------+   +------------------------------+      |
|             |                        |                         |
|             v                        v                         |
|  +------------------+   +------------------------------+      |
|  | DTO / Validation |   | Repository                  |      |
|  | - UrlShortenReq  |   | - findByShortCode()         |      |
|  | - ErrorResponse  |   | - save()                    |      |
|  +------------------+   +------------------------------+      |
|             |                        |                         |
|             v                        v                         |
|  +----------------------------------------------------------+   |
|  | JPA Entity: UrlMapping                                  |   |
|  | - id, shortCode, originalUrl, createdAt                |   |
|  +----------------------------------------------------------+   |
|                                                              |
|  +--------------------------+                               |
|  | H2 In-Memory Database    |                               |
|  +--------------------------+                               |
+--------------------------------------------------------------+
```

## Project Structure

- `src/main/java/com/test/urlshortner/`
  - `UrlShortnerApplication.java`
  - `controller/` - REST controllers, DTOs, exception handling
  - `service/` - business logic and analytics updates
  - `dao/` - repository layer
  - `entity/` - JPA entity
  - `config/` - rate limiter and configuration classes
- `src/main/resources/db/changelog/db.changelog-master.xml` - Liquibase schema migrations
- `src/test/java/com/test/urlshortner/` - JUnit + Mockito tests
- `src/main/resources/application.properties` - datasource, Actuator, logging config
- `docker-compose.yml` - PostgreSQL, Redis, and app orchestration
- `Dockerfile` - application container image

## Prerequisites

- Java 17 or later
- Gradle wrapper included in the project

## Setup Instructions

### Option 1: Local run with Java 17

1. Open a terminal in the project root.
2. Ensure Java 17 is active in your environment.
3. Run:

```bash
./gradlew.bat bootRun
```

Windows PowerShell:

```powershell
cd C:\Users\vinayagam\IdeaProjects\url-shortner
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
cd /path/to/url-shortner
./gradlew bootRun
```

After startup, the app runs at:

```text
http://localhost:8080
```

### Option 2: Docker Compose run

From the project root:

```bash
docker-compose up --build
```

This starts:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- URL shortener app on `http://localhost:8080`

To stop containers:

```bash
docker-compose down
```

## Swagger / OpenAPI

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

Spring Boot Actuator endpoints are also exposed:

```text
http://localhost:8080/actuator/health
http://localhost:8080/actuator/info
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus
```

Example health response:

```json
{
  "status": "UP"
}
```

Example info response:

```json
{
  "app": {
    "name": "URL Shortener Service",
    "description": "Shortens long URLs and redirects to original URLs",
    "version": "1.0.0"
  }
}
```

## API Endpoints

### 1) Create short URL

HTTP Method: `POST`

Path:

```text
/api/v1/shorten
```

Request body:

```json
{
  "longURL": "https://www.yahoo.com/"
}
```

Example response:

```json
{
  "shortURL": "http://localhost:8080/YYw7Kpg8"
}
```

### 2) Redirect short URL

HTTP Method: `GET`

Path:

```text
/{shortCode}
```

Example:

```text
http://localhost:8080/YYw7Kpg8
```

This performs an HTTP 302 redirect to the original URL and increments the click count.

### 3) Get short URL analytics

HTTP Method: `GET`

Path:

```text
/api/v1/analytics/{shortCode}
```

Example response:

```json
{
  "shortCode": "YYw7Kpg8",
  "originalUrl": "https://www.yahoo.com/",
  "clickCount": 9,
  "lastAccessedAt": "2026-09-17T19:46:00"
}
```

## Validation Rules

- `longURL` is required
- It must be a valid URL format starting with `http://`, `https://`, or `ftp://`

## Rate Limiting

The application applies an in-memory IP-based rate limit using Bucket4j for the creation endpoint only.

- Endpoint: `POST /api/v1/shorten`
- Limit: 10 requests per IP per minute
- When exceeded, the API returns HTTP 429 and a JSON error body

Example response:

```json
{
  "status": 429,
  "error": "Too many requests: limit 10 requests per IP per minute"
}
```

Read-only routes like `GET /{shortCode}` and `/actuator/*` bypass the limiter.

## Error Responses

### Invalid input

```json
{
  "status": 400,
  "error": "invalid format for longURL"
}
```

### Missing short code / no matching redirect target

```json
{
  "status": 404,
  "error": "couldn't find a matching long URL to redirect"
}
```

## Testing Commands

### Run all tests

Windows:

```bash
./gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

### Run the application

Windows:

```bash
./gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

### Optional: run a specific test class or method

```bash
./gradlew.bat test --tests "com.test.urlshortner.UrlControllerTest"
```

## Notes

- The app uses a file-backed H2 database by default for local development, with Liquibase controlling schema versioning.
- A Docker Compose setup is provided for PostgreSQL + Redis + Spring Boot deployment testing.
- The rate limiter is in-memory and suitable for a single node; a multi-node deployment should use Redis-backed distributed rate limiting.
- This is a prototype intended for engineering review and testing, with durability and operational hardening added incrementally.
