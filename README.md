# CrawlForge

[![CI](https://github.com/BigFiiish/crawlforge/actions/workflows/ci.yml/badge.svg)](https://github.com/BigFiiish/crawlforge/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/temurin/releases/?version=21)
[![License: MIT](https://img.shields.io/badge/License-MIT-2F6F68.svg)](LICENSE)
[![Live demo](https://img.shields.io/badge/Live-Render-46E3B7?logo=render&logoColor=white)](https://xingji-crawlforge.onrender.com)

Repository: [github.com/BigFiiish/crawlforge](https://github.com/BigFiiish/crawlforge)

Live demo: [xingji-crawlforge.onrender.com](https://xingji-crawlforge.onrender.com)

Deployment configuration is versioned in [`render.yaml`](render.yaml). The public deployment was verified through the dashboard, health endpoint, and an end-to-end crawl of `https://example.com/`.

CrawlForge is a persistent, polite web crawler built with Java 21 and Spring Boot. It is intentionally a systems project rather than a one-file scraper: crawl jobs have a durable breadth-first frontier, every discovered URL is canonicalized and deduplicated, failed fetches are retried with exponential backoff, and an interrupted worker can resume from database state.

The included dashboard and REST API make each run inspectable. The default configuration blocks private-network targets to reduce SSRF risk and respects `robots.txt` before fetching HTML.

## What it demonstrates

- Persistent BFS frontier with explicit `PENDING → FETCHING → DONE / RETRY / SKIPPED / FAILED` states.
- Job lifecycle with `QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED`, and `FAILED` transitions.
- URL canonicalization, tracking-parameter removal, fragment removal, default-port normalization, and per-job deduplication.
- Same-host boundaries checked on discovered links and on every redirect hop.
- `robots.txt` parsing with crawler-specific groups, wildcard rules, end anchors, longest-match precedence, and crawl delays.
- Per-origin rate limiting plus bounded exponential retry for transient HTTP failures.
- Manual redirect handling so SSRF, host, robots, and rate-limit checks run again before every request.
- HTML parsing with jsoup, text/title/link extraction, response-size limits, and SHA-256 content fingerprints.
- Durable H2 storage for local use, restart recovery, Java 21 virtual-thread workers, and a clean REST API.
- A deterministic integration test site covering deduplication, robots exclusion, normalization, and `503 → retry → success` recovery.

## Architecture

```text
Browser / API client
        │
        ▼
CrawlController ── validation and HTTP contract
        │
        ▼
CrawlApplicationService ── job lifecycle
        │
        ▼
CrawlJobManager ── one virtual-thread worker per active job
        │
        ▼
CrawlerWorker
  ├─ CrawlRepository ── crawl_job / crawl_frontier / crawled_page
  ├─ UrlCanonicalizer ── normalize and deduplicate
  ├─ UrlSafetyPolicy ── block private/local destinations
  ├─ RobotsService ── cache and enforce robots.txt
  ├─ HostRateLimiter ── politeness per origin
  └─ PageFetcher + jsoup ── fetch, redirect-check, parse, persist
```

The database is the source of truth. The in-memory job manager prevents duplicate workers inside one process, while the frontier tables preserve crawl progress across application restarts.

## Run locally

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Application state is stored under `./data/` and survives restarts.

The default SSRF policy intentionally rejects `localhost`, RFC1918, loopback, link-local, and multicast targets. Integration tests explicitly enable private hosts because they use an in-process HTTP server.

To inspect the local H2 database console:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Then open `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:file:./data/crawlforge`, user `sa`, and a blank password. Do not enable the H2 console on a public deployment.

## API

Create a bounded crawl:

```bash
curl -i -X POST http://localhost:8080/api/v1/crawls \
  -H "Content-Type: application/json" \
  -d '{
    "seedUrl": "https://example.com/",
    "maxPages": 25,
    "maxDepth": 2,
    "sameHostOnly": true,
    "respectRobots": true,
    "requestsPerSecond": 1.0
  }'
```

The server returns `202 Accepted` with a durable job ID.

```text
GET  /api/v1/crawls
GET  /api/v1/crawls/{id}
GET  /api/v1/crawls/{id}/pages?limit=100
POST /api/v1/crawls/{id}/pause
POST /api/v1/crawls/{id}/resume
POST /api/v1/crawls/{id}/cancel
GET  /actuator/health
```

## Verification

```bash
mvn verify
```

The initial suite contains 13 tests:

- URL canonicalization and host-boundary tests.
- `robots.txt` selection, precedence, wildcard, anchor, and crawl-delay tests.
- SSRF/private-address policy tests.
- End-to-end crawling against an in-process HTTP site, including robots exclusion, duplicate links, tracking parameters, BFS depth, and transient failure recovery.

## Configuration

Configuration lives in `src/main/resources/application.yml` and can be overridden with standard Spring environment variables.

| Property | Default | Purpose |
|---|---:|---|
| `crawler.user-agent` | `CrawlForge/1.0 (...)` | Identifies crawler requests |
| `crawler.connect-timeout` | `5s` | TCP connection timeout |
| `crawler.request-timeout` | `12s` | Per-request timeout |
| `crawler.max-body-bytes` | `1500000` | Maximum downloaded response body |
| `crawler.max-text-characters` | `50000` | Maximum persisted extracted text |
| `crawler.max-redirects` | `5` | Manually validated redirect hops |
| `crawler.max-attempts` | `3` | Total fetch attempts per frontier item |
| `crawler.retry-base-delay` | `2s` | Exponential retry base |
| `crawler.allow-private-hosts` | `false` | Test/local-only SSRF-policy override |

## Design decisions

**Why JDBC instead of hiding the frontier behind an ORM?** The crawler's core behavior is a database-backed state machine. Explicit SQL keeps claims, uniqueness constraints, ordering, and recovery semantics visible during code review and interviews.

**Why one worker per job?** It preserves deterministic breadth-first behavior and makes politeness easy to reason about. A production scale-out would claim frontier rows with database leases and use multiple workers per host-aware partition.

**Why manual redirects?** Automatic redirects can bypass an initial SSRF or robots check. CrawlForge validates every hop before sending the next request.

**Why store content hashes?** SHA-256 fingerprints provide a stable primitive for future duplicate-content detection without treating URL equality as content equality.

## Known boundaries and production extensions

- The H2 file database is ideal for a portable demo. A production deployment should use PostgreSQL and migration tooling.
- Horizontal scale requires database-backed leases or `SKIP LOCKED`, worker heartbeats, and fencing tokens.
- DNS is checked before each request, but production-grade DNS-rebinding protection should connect to the validated address while preserving the HTTP host/TLS identity.
- Response bodies are capped while reading, but bandwidth has already reached the process; a reverse proxy can add stricter ingress controls.
- The current parser indexes HTML text only. PDFs, JavaScript-rendered pages, sitemap seeding, content-language detection, and full-text search are natural extensions.
- Crawling must comply with site terms, applicable law, authentication boundaries, and reasonable traffic limits. `robots.txt` is a minimum politeness control, not permission to collect restricted data.

## Resume-safe bullets

- Built CrawlForge, a Java 21/Spring Boot web crawler with a persistent BFS frontier, URL canonicalization and deduplication, `robots.txt` enforcement, per-host rate limiting, and exponential retry.
- Designed restart-safe crawl jobs with JDBC state transitions, resumable frontier items, Java virtual-thread workers, and an inspectable REST API/dashboard.
- Hardened arbitrary-URL fetching against SSRF and redirect bypasses by blocking private network targets and revalidating host, robots, and rate-limit policy on every redirect hop.

## License

[MIT](LICENSE) © 2026 Xingji Yan.
