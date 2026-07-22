# Job Assistant

[![CI](https://github.com/GembitskayaDarya/job-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/GembitskayaDarya/job-assistant/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![License](https://img.shields.io/badge/license-proprietary-lightgrey)

A Spring Boot backend for tracking job applications, with a Telegram bot front-end for
on-the-go status checks and an OpenAI-powered assistant for drafting cover letter suggestions.

## Table of contents

- [Description](#description)
- [Architecture](#architecture)
- [Technologies](#technologies)
- [How to run](#how-to-run)
- [Docker](#docker)
- [Environment variables](#environment-variables)
- [Screenshots](#screenshots)
- [Roadmap](#roadmap)
- [License](#license)

## Description

Job Assistant keeps a structured record of companies, vacancies, and the applications you've
submitted against them — status (`APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN`),
applied date, and notes — exposed through a REST API, with interviews and notifications modeled
alongside.

A Telegram bot is the primary front-end: `/start` registers you (or updates your username if it
changed) and shows a welcome menu with quick-access buttons, `/list` shows your tracked
applications, and `/help` lists everything available. An OpenAI integration can draft a cover
letter opening paragraph from a job description.

## Architecture

The codebase is organized **feature-first**, not layer-first: each domain owns its own
`entity/dto/repository/service/mapper/controller`, rather than the app having one giant
`controllers` package, one giant `services` package, and so on. The ports/adapters (hexagonal)
pattern is applied *only* where it earns its cost — external integrations that have, or will
imminently have, more than one real implementation (job boards, AI providers, notification
channels) — not throughout the whole app. See [`CLAUDE.md`](CLAUDE.md) for the full rationale,
including the rule of thumb used throughout: no interface for a single implementation, and no
port introduced before there's a real second implementation or a real caller.

```
com.darya.jobassistant
├── config                     Spring @Configuration / @ConfigurationProperties only — no business logic
├── entity/BaseEntity.java     Shared JPA base (UUID id, createdAt/updatedAt as Instant)
├── mapper/EntityMapper.java   Shared entity <-> DTO mapping contract, implemented per feature
├── exception                  Shared NotFoundException hierarchy + GlobalExceptionHandler
├── util                       Stateless helpers (e.g. Telegram message formatting)
│
├── companies                  entity/dto/repository/mapper — no REST yet
├── vacancies                  entity/dto/repository/mapper — no REST yet
├── interviews                 entity/dto/repository/mapper — no REST yet
├── notifications              entity/dto/repository/mapper/service — service is currently an empty stub
├── tracking                   Application entity/dto/repository/service/mapper/controller — the one full REST stack today
│
├── telegram                   Inbound delivery channel (the bot is the "UI")
│   ├── JobAssistantTelegramBot.java   Auto-registered Spring long-polling bot
│   ├── command/                       One class per slash command + CommandRegistry + BotResponse
│   └── user/                          User entity/repository/service — registration, dedup, username sync
│
├── integrations/               Ports & adapters boundary — see rationale above
│   └── ai/openai/               OpenAI chat completion wrapper (only AI provider today)
│
└── scheduler                  Periodic use cases spanning multiple features (reminders, sync) — not yet implemented
```

`companies`, `vacancies`, `interviews`, and `notifications` currently expose their domain model
(entity, repository, DTOs, mapper) but no REST endpoints yet — see [Roadmap](#roadmap).
`tracking` is the one domain with a full stack today: `ApplicationController` validates input
and delegates to `ApplicationService`, which resolves `Company`/`Vacancy`/`User` relations via
their mappers/repositories and persists through `ApplicationRepository`. `telegram` and
`integrations` are peer integrations that call into `tracking`'s service layer but sit outside
the core web flow. Database schema is version-controlled with Flyway migrations
(`src/main/resources/db/migration`) rather than Hibernate auto-DDL.

## Technologies

- **Java 21**, **Gradle** (Groovy DSL)
- **Spring Boot 3.5** — Web, Data JPA, Validation, Actuator
- **PostgreSQL** + **Flyway** for schema migrations
- **Telegram Bots Java library** — Spring Boot long-polling starter (non-deprecated API)
- **OpenAI Java SDK**
- **springdoc-openapi** — Swagger UI / OpenAPI docs
- **Lombok**
- **JUnit 5**, **Mockito** + **Testcontainers** for integration tests
- **Docker** / Docker Compose

## How to run

### Prerequisites

- JDK 21
- Docker (for Testcontainers-based tests, and for the Docker workflow below)

### Locally with Gradle

Start a local PostgreSQL instance (or use `docker compose up postgres`), then:

```bash
export DB_HOST=localhost
export DB_USERNAME=postgres
export DB_PASSWORD=<your-local-postgres-password>
./gradlew bootRun
```

The API is available at `http://localhost:8080/api/applications`.
Swagger UI (interactive API docs) is available at `http://localhost:8080/swagger-ui.html`, with
the raw OpenAPI spec at `http://localhost:8080/v3/api-docs`.

### Running tests

```bash
./gradlew test
```

Integration tests use Testcontainers to spin up a real PostgreSQL instance, so Docker must be
running.

## Docker

Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` — Compose refuses to start without it,
there's no hardcoded fallback. Then build and run the full stack:

```bash
docker compose up --build
```

This starts:

- **`postgres`** — PostgreSQL 16, database `job_assistant`, bound to `127.0.0.1:5432` on the
  host only (not exposed to the network); has a `pg_isready` healthcheck
- **`telegram-bot`** — the Spring Boot app, built from the local `Dockerfile`, exposed on
  `8080`; waits for `postgres` to report healthy before starting, and has its own healthcheck
  against `/actuator/health`

Check it's up:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
```

To run the app image standalone (e.g. against an external database):

```bash
docker build -t job-assistant .
docker run -p 8080:8080 --env-file .env job-assistant
```

## Environment variables

Read directly by the Spring Boot app (used when running `./gradlew bootRun` or `docker run`
directly):

| Variable                        | Default        | Description                                     |
|----------------------------------|-----------------|---------------------------------------------------|
| `DB_HOST`                        | `localhost`     | PostgreSQL host                                   |
| `DB_PORT`                        | `5432`          | PostgreSQL port                                   |
| `DB_NAME`                        | `job_assistant` | Database name                                     |
| `DB_USERNAME`                    | `postgres`      | Database username                                 |
| `DB_PASSWORD`                    | *(required)*    | Database password — no default, must be set       |
| `DB_POOL_MIN_IDLE`               | `2`             | HikariCP minimum idle connections                 |
| `DB_POOL_MAX_SIZE`               | `10`            | HikariCP maximum pool size                        |
| `DB_POOL_CONNECTION_TIMEOUT_MS`  | `30000`         | HikariCP connection timeout (ms)                  |
| `SERVER_PORT`                    | `8080`          | HTTP port                                         |
| `TELEGRAM_ENABLED`               | `false`         | Enables the Telegram bot when `true`              |
| `TELEGRAM_BOT_TOKEN`             | *(empty)*       | Telegram bot token from `@BotFather`               |
| `OPENAI_API_KEY`                 | *(empty)*       | OpenAI API key                                    |
| `OPENAI_MODEL`                   | `gpt-4o-mini`   | OpenAI model used for cover letter suggestions    |
| `WEBCLIENT_TIMEOUT_MS`           | `20000`         | Connect/read/write/response timeout for the shared `WebClient` (ms) |
| `WEBCLIENT_MAX_CONNECTIONS`      | `50`            | Max pooled connections for the shared `WebClient` |
| `WEBCLIENT_MAX_IDLE_TIME_MS`     | `30000`         | Max idle time before a pooled connection is evicted (ms) |
| `WEBCLIENT_MAX_LIFE_TIME_MS`     | `300000`        | Max lifetime of a pooled connection (ms)          |
| `WEBCLIENT_PENDING_ACQUIRE_TIMEOUT_MS` | `10000`   | Max wait time to acquire a connection from the pool (ms) |
| `WEBCLIENT_EVICT_IN_BACKGROUND_MS` | `60000`       | Interval for background eviction of idle connections (ms) |
| `WEBCLIENT_MAX_IN_MEMORY_SIZE_BYTES` | `2097152`   | Max buffered response size for the `WebClient` codecs (bytes) |
| `JOBSOURCE_REMOTEOK_BASE_URL`    | `https://remoteok.com/api` | RemoteOK API endpoint polled for vacancies |
| `JOBSOURCE_INGESTION_ENABLED`    | `false`         | Enables the scheduled vacancy ingestion job        |
| `JOBSOURCE_INGESTION_FIXED_DELAY_MS` | `3600000`   | Delay between ingestion runs (ms)                  |
| `JOBSOURCE_INGESTION_INITIAL_DELAY_MS` | `10000`   | Delay before the first ingestion run after startup (ms) |
| `LOG_LEVEL_ROOT`                 | `INFO`          | Root logger level                                 |
| `LOG_LEVEL_APP`                  | `INFO`          | Logger level for `com.darya.jobassistant`         |
| `LOG_LEVEL_SQL`                  | `WARN`          | Logger level for `org.hibernate.SQL`              |

Read by `docker-compose.yml` to configure the `postgres` container, and mapped onto the
`DB_*` variables above for the `telegram-bot` container:

| Variable             | Default          | Description                                                                                          |
|-----------------------|-------------------|--------------------------------------------------------------------------------------------------------|
| `POSTGRES_PASSWORD`   | *(required)*      | Password for both the `postgres` container and the app's `DB_PASSWORD` — Compose fails fast if unset |
| `POSTGRES_USER`       | `postgres`        | Superuser for the `postgres` container and the app's `DB_USERNAME`                                   |
| `POSTGRES_DB`         | `job_assistant`   | Database created in the `postgres` container and the app's `DB_NAME`                                 |

Copy `.env.example` to `.env`, set `POSTGRES_PASSWORD`, and fill in the Telegram/OpenAI values
before running `docker compose up`.

## Screenshots

_Not yet available — add screenshots here as the project matures:_

- _`/start` welcome message and inline keyboard_
- _`/list` output showing tracked applications_
- _Sample `GET /api/applications` response_
- _Actuator health/metrics dashboard_

## Roadmap

- [ ] Wire up the `/start` inline keyboard buttons (Search jobs, My CV, Applications, Settings)
      — they render today but don't yet respond to taps (no `callback_query` handling)
- [ ] Vacancy ingestion from LinkedIn, Greenhouse, Lever, and RemoteOK (`integrations/jobsource`,
      not yet built — see `CLAUDE.md`)
- [ ] Actually send Telegram notifications — `NotificationService` exists but is currently an
      empty stub with no `NotifierPort`/`TelegramNotifier` wired up
- [ ] Authentication/authorization for the REST API (currently unauthenticated)
- [ ] REST controllers + services for `companies`, `vacancies`, `interviews`, `notifications`
      (models exist, no endpoints yet)
- [ ] Pagination and filtering on `GET /api/applications`
- [ ] Scheduled reminders for upcoming interviews via the `scheduler` package

## License

This project does not currently have an open-source license. All rights reserved by the
author; do not reuse or redistribute without permission. Add a `LICENSE` file (e.g. MIT,
Apache-2.0) if and when you decide to open-source it.
