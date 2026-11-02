# url-shortener

[![CI](https://github.com/praneetkamble/url-shortener/actions/workflows/ci.yml/badge.svg)](https://github.com/praneetkamble/url-shortener/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-style URL shortener built with **Spring Boot 3**, **PostgreSQL** and **Redis**. It issues compact
base62 codes, supports custom aliases and expiry, protects link creation with rate limiting, records click
analytics and ships with OpenAPI docs and a one-command Docker Compose setup.

## Features

- **Base62 short codes** derived from a database sequence: collision-free by construction, no retry loops, URL-safe.
- **Custom aliases** (`3-32` chars of `A-Z a-z 0-9 - _`) with conflict detection and a reserved word list.
- **Expiring links**: optional TTL per link; expired links answer `410 Gone`, and old rows are purged hourly.
- **Redis read-through cache** on the redirect hot path (configurable TTL, misses are never cached, deletes evict).
- **Rate limiting** on link creation per client address, Redis-backed (shared across instances) or in-memory,
  with `X-RateLimit-*` and `Retry-After` headers. Fails open if Redis is unavailable.
- **Click analytics**: total clicks, clicks per day (last 30 days) and top referrers.
- **OpenAPI / Swagger UI** at `/swagger-ui.html`, errors as RFC 7807 `application/problem+json`.
- **Flyway** migrations, Actuator health endpoint, non-root Docker image.

## Architecture

```mermaid
flowchart LR
    Client([Client]) -->|POST /api/v1/urls| RL{Rate limiter}
    RL -->|allowed| API[UrlController]
    RL -.->|429 + Retry-After| Client
    Client -->|GET /:code| RC[RedirectController]
    API --> SVC[UrlService]
    RC --> SVC
    SVC -->|resolve| RES[UrlResolver<br/>@Cacheable]
    RES <-->|hit / miss| REDIS[(Redis<br/>cache + rate-limit counters)]
    RES -->|miss| PG[(PostgreSQL)]
    SVC -->|create / delete / click events| PG
    RL <-->|INCR + EXPIRE| REDIS
```

Request flow for a redirect: `GET /abc123` → cache lookup in Redis → (on miss) read from PostgreSQL and populate
the cache → check expiry → write a click event + bump the counter → `302 Found`.

## Quick start

### With Docker Compose

```bash
docker compose up --build
```

The API is on <http://localhost:8080> and the interactive docs on <http://localhost:8080/swagger-ui.html>.

### Locally

Requires JDK 21, Maven, a PostgreSQL database and Redis (see `docker-compose.yml` for credentials).

```bash
docker compose up -d postgres redis   # just the infrastructure
mvn spring-boot:run
```

Configuration is via environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`,
`PORT`, `BASE_URL` (public origin used in returned links) and `RATE_LIMIT_STORE` (`redis` or `memory`).
Further tuning (`shortener.cache-ttl`, `shortener.rate-limit.max-requests`, `shortener.rate-limit.window`)
lives in `src/main/resources/application.yml`.

### Run the tests

```bash
mvn verify
```

Tests run against an in-memory H2 database (PostgreSQL mode), a simple cache and the in-memory rate limiter, so
no Docker is needed.

## API examples

Create a short link (all fields except `url` are optional):

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a/very/long/path?utm=1","customAlias":"launch","expiresInSeconds":86400}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/launch
X-RateLimit-Limit: 30
X-RateLimit-Remaining: 29

{
  "code": "launch",
  "shortUrl": "http://localhost:8080/launch",
  "originalUrl": "https://example.com/a/very/long/path?utm=1",
  "customAlias": true,
  "createdAt": "2026-01-15T10:00:00Z",
  "expiresAt": "2026-01-16T10:00:00Z",
  "clickCount": 0
}
```

Follow it:

```bash
curl -i http://localhost:8080/launch
# HTTP/1.1 302  Location: https://example.com/a/very/long/path?utm=1
```

Analytics:

```bash
curl http://localhost:8080/api/v1/urls/launch/stats
```

```json
{
  "code": "launch",
  "totalClicks": 3,
  "clicksByDay": [{ "date": "2026-01-15", "clicks": 3 }],
  "topReferrers": [{ "referrer": "news.example.org", "clicks": 2 }, { "referrer": "direct", "clicks": 1 }]
}
```

| Method & path                    | Description                                   | Success | Errors          |
|----------------------------------|-----------------------------------------------|---------|-----------------|
| `POST /api/v1/urls`              | Create a short URL                            | 201     | 400, 409, 429   |
| `GET /{code}`                    | Redirect to the original URL                  | 302     | 404, 410        |
| `GET /api/v1/urls/{code}`        | Link details                                  | 200     | 404             |
| `GET /api/v1/urls/{code}/stats`  | Click analytics                               | 200     | 404             |
| `DELETE /api/v1/urls/{code}`     | Delete link and its analytics                 | 204     | 404             |

## Tech stack

Java 21 · Spring Boot 3.3 (Web, Data JPA, Data Redis, Cache, Validation, Actuator) · PostgreSQL 16 · Redis 7 ·
Flyway · springdoc-openapi · JUnit 5, AssertJ, Mockito, MockMvc, H2 · Docker / Docker Compose · GitHub Actions.

## Design decisions

- **IDs from a sequence, codes from base62.** The database hands out unique numbers and `Base62.encode` turns them
  into short codes, so generation never collides and needs no coordination between instances. The sequence starts
  at 62⁵, giving 6-character codes (enough for ~56 billion links before reaching 7). A generated code that clashes
  with a custom alias is simply skipped. Codes are sequential and therefore enumerable; if that matters, put a
  reversible bijection (e.g. a Feistel permutation) between the sequence and the encoder.
- **`302`, not `301`.** Permanent redirects are cached by browsers and would bypass analytics and expiry.
- **Cache only what the hot path needs.** `UrlResolver` caches a tiny `ResolvedUrl(id, target, expiresAt)` record.
  Expiry is re-checked on every request against the clock, so a cached entry can never serve an expired link.
  Unknown codes throw, so negative lookups are not cached (and cannot poison the cache).
- **Fixed-window rate limiting with `INCR` + `EXPIRE`.** One round trip per request, trivially shared between
  instances. The trade-off is boundary bursts (up to 2x the limit across a window edge); a sliding window or token
  bucket would smooth that at the cost of a Lua script. The limiter fails open when Redis is down.
- **Synchronous click recording.** A click is one cheap insert in the same transaction as the counter update, which
  keeps the numbers exactly consistent. At very high volume you would move this onto a queue and batch it.
- **Clock injection.** Every time-dependent component uses a `java.time.Clock`, which is what makes expiry, rate
  limit windows and per-day analytics deterministic to test.
- **Errors as problem+json.** One `@RestControllerAdvice` maps domain exceptions to HTTP statuses.

## License

[MIT](LICENSE)
