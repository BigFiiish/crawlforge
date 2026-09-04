# CrawlForge

[![CI](https://github.com/BigFiiish/crawlforge/actions/workflows/ci.yml/badge.svg)](https://github.com/BigFiiish/crawlforge/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/temurin/releases/?version=21)
[![License: MIT](https://img.shields.io/badge/License-MIT-2F6F68.svg)](LICENSE)
[![Live demo](https://img.shields.io/badge/Live-Render-46E3B7?logo=render&logoColor=white)](https://xingji-crawlforge.onrender.com)

**CrawlForge turns a company Careers page into structured, exportable job intelligence.**

[Open the live product](https://xingji-crawlforge.onrender.com) · [View the public repository](https://github.com/BigFiiish/crawlforge)

Paste a Careers or ATS URL and CrawlForge discovers relevant job pages, extracts `title`, `company`, `location`, `skills`, `experience`, and employment type, then stores the results for inspection or JSON/CSV export. Paste resume text to rank the roles with explainable deterministic scoring; a server-side OpenAI integration can optionally add contextual evaluation.

The product is built on a restart-safe Java 21/Spring Boot crawler rather than a one-page scraper. Its bounded BFS frontier, canonicalization, retries, rate limits, `robots.txt` enforcement, redirect validation, and SSRF protection remain part of every career scan.

## Product workflow

1. Enter a company Careers page or supported ATS listing.
2. CrawlForge follows career-shaped links on the company domain and recognized ATS hosts.
3. It extracts Schema.org `JobPosting` JSON-LD first, then uses guarded HTML heuristics when structured data is unavailable.
4. Results are persisted and rendered as structured job cards.
5. Download the scan as JSON or analysis-ready CSV.
6. Paste resume text to get ranked matches, supported skill overlap, missing skills, and a short explanation.

Supported ATS boundaries currently include Greenhouse, Lever, Workday, Ashby, SmartRecruiters, iCIMS, Jobvite, and Workable.

## Matching modes

- **Deterministic mode** is always available. It compares a normalized technology/skill catalog against the resume and each extracted posting, returns a 0–100 score, and shows the evidence behind it.
- **Optional AI mode** uses the OpenAI Responses API with a strict JSON schema, treats resume and job text as untrusted input, and falls back safely when the provider is unavailable. The API key stays on the server and is never sent to the browser or stored with a scan.
- Only enable AI mode when you are comfortable sending the pasted resume text and extracted job content to the configured provider. The public demo reports whether AI is configured before the checkbox becomes available.

## Architecture

```text
Browser / API client
        │
        ▼
CareerController ── scan, jobs, export, match contracts
        │
        ├── JobExportService ── JSON / CSV
        ├── JobMatchingService ── deterministic score ── optional OpenAI client
        │
        ▼
CareerScanService ── creates a durable crawl + career_scan record
        │
        ▼
CrawlJobManager ── one virtual-thread worker per active job
        │
        ▼
CrawlerWorker
  ├── CrawlRepository ── crawl_job / crawl_frontier / crawled_page
  ├── CareerRepository ── career_scan / job_posting
  ├── CareerLinkPolicy ── company and supported ATS boundaries
  ├── JobPostingExtractor ── JSON-LD first, guarded heuristic fallback
  ├── UrlCanonicalizer + UrlSafetyPolicy
  ├── RobotsService + HostRateLimiter
  └── PageFetcher + jsoup ── fetch, validate redirects, parse, persist
```

The database is the source of truth. A scan can be paused, resumed, or recovered after restart without rebuilding its frontier.

## Careers API

Create a bounded Careers scan:

```bash
curl -i -X POST http://localhost:8080/api/v1/career-scans \
  -H "Content-Type: application/json" \
  -d '{
    "careersUrl": "https://company.example/careers",
    "maxPages": 50,
    "maxDepth": 3,
    "requestsPerSecond": 1.0
  }'
```

```text
POST /api/v1/career-scans
GET  /api/v1/career-scans
GET  /api/v1/career-scans/{id}
GET  /api/v1/career-scans/{id}/jobs
GET  /api/v1/career-scans/{id}/jobs.json
GET  /api/v1/career-scans/{id}/jobs.csv
POST /api/v1/career-scans/{id}/match
GET  /api/v1/career-scans/capabilities
```

Example match request:

```bash
curl -X POST http://localhost:8080/api/v1/career-scans/{id}/match \
  -H "Content-Type: application/json" \
  -d '{"resumeText":"Java and Spring Boot engineer with AWS and PostgreSQL experience...","useAi":false}'
```

The original generic crawler API remains available:

```text
POST /api/v1/crawls
GET  /api/v1/crawls
GET  /api/v1/crawls/{id}
GET  /api/v1/crawls/{id}/pages?limit=100
POST /api/v1/crawls/{id}/pause
POST /api/v1/crawls/{id}/resume
POST /api/v1/crawls/{id}/cancel
GET  /actuator/health
```

## Run locally

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Application state is stored under `./data/` and survives restarts.

To enable optional AI matching, configure the key only in the server environment:

```bash
export OPENAI_API_KEY="your-server-side-key"
export OPENAI_MODEL="gpt-5.6-luna"
mvn spring-boot:run
```

`OPENAI_MODEL`, `OPENAI_BASE_URL`, and the key are configurable; the model defaults to `gpt-5.6-luna`. With no key, the app starts normally and uses deterministic matching.

The default SSRF policy intentionally rejects localhost, RFC1918, loopback, link-local, and multicast targets. Integration tests explicitly enable private hosts because they use an in-process HTTP server.

To inspect the local H2 database, run with the `local` profile and open `/h2-console`. Use JDBC URL `jdbc:h2:file:./data/crawlforge`, user `sa`, and a blank password. Never enable that console on a public deployment.

## Verification

```bash
mvn verify
```

The suite contains 20 tests covering:

- JSON-LD and heuristic job extraction, structured locations, skills, and experience.
- Careers/ATS link boundaries, including subdomains and lookalike-host rejection.
- Deterministic resume matching plus valid JSON and escaped CSV exports.
- URL canonicalization, `robots.txt` selection and precedence, and SSRF/private-address policy.
- End-to-end BFS crawling against an in-process HTTP site, including a complete Careers scan, deduplication, tracking-parameter removal, robots exclusion, and `503 → retry → success` recovery.

## Safety and reliability

- Persistent BFS frontier with explicit `PENDING → FETCHING → DONE / RETRY / SKIPPED / FAILED` states.
- Lifecycle states for `QUEUED`, `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED`, and `FAILED` scans.
- URL canonicalization, tracking-parameter cleanup, fragment removal, default-port normalization, and per-scan deduplication.
- Same-organization or known-ATS boundaries on discovery and every redirect hop.
- `robots.txt` parsing with crawler-specific groups, wildcard rules, end anchors, longest-match precedence, and crawl delays.
- Per-origin rate limiting, response-size caps, SHA-256 fingerprints, and bounded exponential retry.
- Server-side API credentials, prompt-injection-resistant AI instructions, strict structured output, and deterministic fallback.

## Configuration

Configuration lives in `src/main/resources/application.yml` and supports standard Spring environment overrides.

| Property | Default | Purpose |
|---|---:|---|
| `crawler.connect-timeout` | `5s` | TCP connection timeout |
| `crawler.request-timeout` | `12s` | Per-request timeout |
| `crawler.max-body-bytes` | `1500000` | Maximum downloaded response body |
| `crawler.max-text-characters` | `50000` | Maximum persisted extracted text |
| `crawler.max-redirects` | `5` | Manually validated redirect hops |
| `crawler.max-attempts` | `3` | Total fetch attempts per frontier item |
| `crawler.retry-base-delay` | `2s` | Exponential retry base |
| `crawler.allow-private-hosts` | `false` | Test/local-only SSRF override |
| `OPENAI_API_KEY` | empty | Enables optional AI matching when set |
| `OPENAI_MODEL` | `gpt-5.6-luna` | Model used for structured match evaluation |

## Known boundaries

- Server-rendered HTML and Schema.org JSON-LD work best. JavaScript-only job boards may require a future browser-rendering adapter or provider-specific API connector.
- Skill extraction uses a curated engineering vocabulary; it intentionally avoids inventing skills from vague prose.
- H2 is suitable for a portable demo. Production scale should use PostgreSQL, migrations, database leases, and worker fencing.
- The OpenAI integration evaluates at most 15 jobs per request and truncates long inputs to bound latency and cost.
- Crawling must comply with site terms, applicable law, authentication boundaries, and reasonable traffic limits. `robots.txt` is a minimum politeness control, not permission to collect restricted data.

## Resume-safe bullets

- Built CrawlForge, a Java 21/Spring Boot careers-intelligence platform that discovers job listings, extracts structured role data from JSON-LD/HTML, and exports persisted results as JSON or CSV.
- Designed a restart-safe BFS crawler with JDBC state transitions, URL canonicalization, `robots.txt` enforcement, per-host rate limiting, virtual-thread workers, exponential retry, and SSRF-safe redirect validation.
- Implemented explainable resume-to-job ranking with deterministic skill evidence and an optional server-side OpenAI Responses API path using strict structured output and safe fallback behavior.

## License

[MIT](LICENSE) © 2026 Xingji Yan.
