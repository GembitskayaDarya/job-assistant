# Job Assistant

[![CI](https://github.com/GembitskayaDarya/job-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/GembitskayaDarya/job-assistant/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![License](https://img.shields.io/badge/license-proprietary-lightgrey)

A Spring Boot backend for tracking job applications, with a Telegram bot front-end for
on-the-go status checks, a guided vacancy-import workflow, and an OpenAI-powered assistant for
matching vacancies against your profile.

## Table of contents

- [Description](#description)
- [Guided vacancy import](#guided-vacancy-import)
- [Architecture](#architecture)
- [Technologies](#technologies)
- [Candidate profile](#candidate-profile)
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
changed) and shows a welcome menu, `/add` walks you through importing a vacancy from a URL and
description (see [Guided vacancy import](#guided-vacancy-import) below), `/list` shows your
tracked applications, and `/help` lists everything available. An OpenAI integration extracts
structured vacancy data during import and scores how well a vacancy matches your profile
(`/analyze`, or the Analyze button after a Save).

## Guided vacancy import

`/add` starts a step-by-step import for one vacancy at a time:

```text
/add
→ send the vacancy URL
→ send the full vacancy description
→ review the AI-recognized details
→ press Save
→ press Analyze
```

A few things worth being explicit about:

- **The bot never opens or scrapes the URL itself.** You paste both the link and the full
  vacancy description yourself; the URL is stored as-is (for the "Open vacancy" button and for
  deduplication) and the description is what the AI actually reads to extract structured fields
  (title, company, location, contract type, skills, salary) and, later, to score the match.
- **Any public HTTP/HTTPS link works** — LinkedIn, Just Join IT, No Fluff Jobs, Pracuj.pl,
  a company's own careers page, or anywhere else. There is no per-site adapter or whitelist: the
  URL's hostname is only ever used as a human-readable source label, never as proof the link is
  legitimate or that the pasted description matches it.
- **Retry** discards the current description and asks for a new one, keeping the URL you already
  provided. **Cancel** stops the import at any point before Save.
- **Save** creates a new `Vacancy` — or reuses an existing one if you (or another chat) already
  imported the same URL — and links it to the import session. **Analyze** runs the same AI
  matching pipeline as `/analyze`; pressing it again returns the already-computed result instead
  of calling the AI a second time.
- An import session left idle expires after `VACANCY_IMPORT_SESSION_TTL` (24h by default) and is
  automatically cleaned up by a background job — see [Environment variables](#environment-variables).

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
├── vacancyimport               Guided /add workflow: session state machine, draft persistence, Save/Retry/Cancel/Analyze use cases
├── vacancyextraction           AI extraction of structured vacancy data from a pasted description
│
├── telegram                   Inbound delivery channel (the bot is the "UI")
│   ├── JobAssistantTelegramBot.java   Auto-registered Spring long-polling bot
│   ├── command/                       One class per slash command + CommandRegistry + BotResponse
│   └── user/                          User entity/repository/service — registration, dedup, username sync
│
├── integrations/               Ports & adapters boundary — see rationale above
│   ├── jobsource/remoteok/      RemoteOkJobSourceAdapter — first (and currently only) job source
│   └── ai/openai/               OpenAiAssistantService (chat) + JobAnalysisService (profile/job matching)
│
└── scheduler                  Periodic jobs: vacancy ingestion, job monitoring, vacancy-import session cleanup
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

## Candidate profile

`application.yml` configures the application itself (database, Telegram, AI model, scheduling,
...); it contains no personal data. The candidate profile used for AI vacancy matching — target
role/seniority, experience, skills with proficiency, languages, and work preferences — lives in
its own file, loaded via Spring's Config Data import:

```yaml
spring:
  config:
    import: file:${CANDIDATE_PROFILE_PATH:./config/candidate-profile.yml}
```

### First-time setup

```bash
cp config/candidate-profile.example.yml config/candidate-profile.yml
```

Then edit `config/candidate-profile.yml` with your own role, skills, and preferences.
`config/candidate-profile.yml` is listed in `.gitignore`, so your personal data is never
committed — only `config/candidate-profile.example.yml` (a generic template) is tracked. This
file is required for the application to start; it is not a secret-management mechanism, just a
place to keep personal profile data out of the source tree.

Skills absent from the file are treated as unknown, negligible, or intentionally excluded — don't
add an entry to say "no", just omit it. Supported proficiency values, most to least confident:

- `EXPERT` — deep knowledge, can design solutions and guide others
- `STRONG` — confident production experience, can troubleshoot difficult problems
- `WORKING` — can independently complete normal implementation tasks
- `BASIC` — understands the fundamentals, may need guidance for non-trivial work

`NONE` is not a supported proficiency value.

### Database persistence (in progress)

Sprint 9 Step 1 added an additive PostgreSQL schema for Candidate Profile, skills, and languages
(migration `V16__create_candidate_profile.sql`; entities/repositories under
`com.darya.jobassistant.candidates.entity`/`.repository`). This is a persistence foundation only:

- `ConfigurationCandidateProfileProvider` (reading `config/candidate-profile.yml` as described
  above) remains the **only** runtime source of the Candidate Profile used for AI vacancy
  matching — the new tables are not read or written by any workflow yet.
- No profile is seeded into the new tables; the application starts normally with them empty.
- Migrating real profile data onto this schema and switching the runtime provider over to it is
  later Sprint 9 work, done only after that data has been migrated and validated.
- Career History is intentionally out of scope for this step.
- `candidate_profile_skill.proficiency` supports the same four values as above (`BASIC`,
  `WORKING`, `STRONG`, `EXPERT`, no `NONE`) — enforced by a database `CHECK` constraint. A skill
  the candidate doesn't have is represented by the *absence* of a row, exactly as in the YAML file
  today.

Sprint 9 Step 2 added the clean domain/application boundary on top of that schema, still without
switching anything at runtime:

- `com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate` (with `CandidateSkill`
  and `CandidateLanguage`, both in that same `aggregate` package) is the framework-free domain
  model for the *persisted* profile. It is the intended **future source-of-truth business
  aggregate** for Candidate Profile data — persistence is an implementation detail of what it
  represents, not its purpose — deliberately kept as a different type from the existing
  YAML-sourced `com.darya.jobassistant.candidates.CandidateProfile`, since that model's rich,
  importance-weighted preferences have no equivalent in Step 1's flat schema and dozens of
  existing call sites depend on `CandidateProfile`/`CandidateSkill` staying unchanged. The intended
  transitional architecture:

  ```text
  CandidateProfileAggregate
      = future PostgreSQL-backed source-of-truth domain aggregate

  CandidateProfile
      = current analysis-oriented projection JobAnalysisService actually reads from today,
        sourced from config/candidate-profile.yml via ConfigurationCandidateProfileProvider

  CandidateProfileAnalysisAssembler (Sprint 9 Step 3)
      CandidateProfileAggregate -> CandidateProfile
  ```

  Switching `JobAnalysisService` to consume the assembler's output instead of YAML is later
  Sprint 9 work.
- `CandidateProfileRepositoryPort` (`findByProfileKey`, `save`) is the one repository port for the
  whole aggregate — skills and languages are parts of the Candidate Profile, not separate ports.
  Every save of an *existing* aggregate performs a deliberate, unconditional version-checked
  update of the whole parent row (see `CandidateProfileRepository.updateIfVersionMatches`) and
  always increments `candidate_profile.version` — including a save that only changes skills or
  languages — rather than relying on Hibernate's ordinary scalar-field dirty checking, which
  would not otherwise guarantee either the increment or the optimistic-lock check for a
  children-only change.
- `com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter` implements the
  port against the Step 1 JPA entities/repositories, loading and saving the parent plus its skills
  and languages as one atomic unit (skills/languages are fully replaced on every save), and
  translates a stale or missing-row version check into `CandidateProfileConcurrentModificationException`.
- This adapter is a registered Spring bean but is **not** wired into `JobAnalysisService` or any
  other runtime workflow — `ConfigurationCandidateProfileProvider` remains the only source AI
  vacancy analysis reads from. Provider switching and YAML data migration are later Sprint 9 work.

Sprint 9 Step 3 added an explicit, idempotent, **opt-in only** migration of the real YAML profile
into PostgreSQL, still without switching the runtime provider:

- `V17__extend_candidate_profile_preferences.sql` adds `candidate_profile.current_country`/
  `relocation_allowed`/`salary_expectation_note`, `candidate_profile_skill.note`, and a new
  `candidate_profile_preference` child table (work arrangement, allowed work countries, contract
  types, company type — each with an importance where the source model has one) so every field
  `CandidatePreferences` carries has a lossless PostgreSQL home. V16 is unchanged. Two Step 1 flat
  columns (`preferred_company_type`, `remote_policy`) are deliberately left unpopulated by this
  migration — the new preference rows are the lossless, importance-aware source of truth for those
  two concepts now, and `CandidateProfileAggregate`'s constructor rejects an aggregate that tries
  to set both, so they can never silently disagree.
- `CandidateProfileYamlImportMapper` (`CandidateProfile` → `CandidateProfileAggregate`) and
  `CandidateProfileAnalysisAssembler` (the reverse) are separate, framework-free, stateless
  classes — not one ambiguous bidirectional mapper. Language names (`"English"`) are normalized to
  ISO codes (`en`) through a small explicit lookup table; an unrecognized name fails validation
  rather than being silently dropped.
- `CandidateProfileMigrationUseCase` (`candidates.migration`) runs `DRY_RUN` (read-only, zero
  writes, ever) or `APPLY` (one atomic transaction: create-if-absent, reload, assemble back to the
  analysis shape, and verify that round-trip is semantically identical to the original YAML before
  committing — a mismatch rolls back everything). Statuses: `WOULD_CREATE`/`WOULD_NO_OP`/
  `WOULD_CONFLICT` for `DRY_RUN`, `CREATED`/`NO_OP`/`CONFLICT` for `APPLY`, `VALIDATION_FAILED` for
  either. **`APPLY` never overwrites an existing, different destination profile** — it reports
  `CONFLICT` and leaves the database untouched. Comparison ignores database ids, timestamps,
  persistence version, and any ordering that carries no business meaning.
- `CandidateProfileMigrationRunner` is disabled by default and only runs once, right after startup
  finishes (never delaying or blocking it):

  ```bash
  # Report only - prints what an APPLY would do, writes nothing:
  CANDIDATE_PROFILE_MIGRATION_MODE=DRY_RUN ./gradlew bootRun

  # Actually create the "primary" PostgreSQL profile from your current YAML file:
  CANDIDATE_PROFILE_MIGRATION_MODE=APPLY ./gradlew bootRun
  ```

  (or set `candidate-profile.migration.mode: DRY_RUN`/`APPLY` directly in your environment
  config). Leaving the property unset, or set to `OFF`, keeps the runner bean from doing anything
  — `OFF` is the default and requires no configuration at all.
- **Rollback**: if the application is rolled back after a successful `APPLY`, nothing needs to be
  undone — the previous version keeps using YAML exactly as before, and the imported PostgreSQL
  rows simply sit unused until a later cutover. No destructive database rollback is required or
  performed automatically.

### Default startup

```bash
./gradlew bootRun
```

Loads `config/candidate-profile.yml` from the project root by default. If that file is missing,
startup fails fast rather than falling back to an empty or fake profile — copy the example file
as shown above, or set `CANDIDATE_PROFILE_PATH` (below).

### Custom profile path

```bash
CANDIDATE_PROFILE_PATH=/absolute/path/to/my-candidate-profile.yml \
./gradlew bootRun
```

Useful for keeping your profile entirely outside the repository, or switching between multiple
profiles without editing `config/candidate-profile.yml` in place.

## How to run

### Prerequisites

- JDK 21
- Docker (for Testcontainers-based tests, and for the Docker workflow below)

### Locally with Gradle

Start a local PostgreSQL instance (or use `docker compose up postgres`), then set up your
candidate profile (see [Candidate profile](#candidate-profile) above) if you haven't already:

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

To run a narrower slice:

```bash
# Focused, no Docker required — pure unit tests and in-memory/mocked composition tests
# (e.g. the guided vacancy-import workflow, extraction, cleanup, /help, /start)
./gradlew test --tests "com.darya.jobassistant.vacancyimport.*" \
                --tests "com.darya.jobassistant.vacancyextraction.*" \
                --tests "com.darya.jobassistant.telegram.command.*" \
                --tests "com.darya.jobassistant.scheduler.*"

# PostgreSQL/Testcontainers-backed tests only (requires Docker)
./gradlew test --tests "*IntegrationTest" --tests "*RepositoryTest"
```

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
| `CANDIDATE_PROFILE_PATH`         | `./config/candidate-profile.yml` | Path to the personal candidate profile YAML file — see [Candidate profile](#candidate-profile) |
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
| `OPENAI_MODEL`                   | `gpt-4o-mini`   | OpenAI model used for vacancy extraction and profile-match analysis |
| `VACANCY_IMPORT_SESSION_TTL`     | `24h`           | How long an idle `/add` session stays active before it's eligible for expiration |
| `VACANCY_IMPORT_CLEANUP_ENABLED` | `true`          | Enables the scheduled job that expires stale `/add` sessions — on by default since it's local-database maintenance only |
| `VACANCY_IMPORT_CLEANUP_FIXED_DELAY` | `1h`        | Delay between cleanup runs                        |
| `VACANCY_IMPORT_CLEANUP_INITIAL_DELAY` | `1m`      | Delay before the first cleanup run after startup  |
| `VACANCY_IMPORT_CLEANUP_BATCH_SIZE` | `100`        | Max number of expired sessions expired per run     |
| `JOB_ANALYSIS_CLAIM_STALE_AFTER` | `2m`            | How long an in-progress AI analysis claim is honored before it's considered abandoned and retried |
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
| `JOB_MONITORING_ENABLED`         | `false`         | Enables the scheduled monitoring job (fetch → analyze → notify) — requires `TELEGRAM_ENABLED=true` |
| `JOB_MONITORING_FIXED_DELAY`     | `30m`           | Delay between monitoring runs, measured from the end of the previous run |
| `JOB_MONITORING_INITIAL_DELAY`   | `1m`            | Delay before the first monitoring run after startup |
| `JOB_MONITORING_KEYWORD`         | `java backend`  | Keyword used when fetching vacancies for this run  |
| `JOB_MONITORING_MINIMUM_SCORE`   | `70`            | Minimum AI match score (0-100) to enter the notification backlog |
| `JOB_MONITORING_MAX_NOTIFICATIONS` | `5`           | Max notifications sent per run (excess backlog candidates wait for the next run) |
| `JOB_MONITORING_RECIPIENT_CHAT_ID` | *(required if enabled)* | Telegram chat id notifications are sent to — no default, must be set when `JOB_MONITORING_ENABLED=true` |
| `LOG_LEVEL_ROOT`                 | `INFO`          | Root logger level                                 |
| `LOG_LEVEL_APP`                  | `INFO`          | Logger level for `com.darya.jobassistant`         |
| `LOG_LEVEL_SCHEDULER`            | `INFO`          | Logger level for `com.darya.jobassistant.scheduler` — set to `DEBUG` to see each scheduled run start/duration |
| `LOG_LEVEL_MONITORING`           | `INFO`          | Logger level for `com.darya.jobassistant.monitoring` — set to `DEBUG` for per-run detail beyond the completion summary |
| `LOG_LEVEL_SQL`                  | `WARN`          | Logger level for `org.hibernate.SQL`              |

Automatic monitoring (`JOB_MONITORING_ENABLED=true`) polls RemoteOK on a fixed delay, keeps only
source-independent Java Backend matches (`JavaBackendJobMatchPolicy`), and persists an AI analysis
for each newly ingested vacancy. Only analyses scoring at or above `JOB_MONITORING_MINIMUM_SCORE`
enter the durable notification backlog; the `notification_delivery` table's unique
`(vacancy_id, recipient_chat_id)` constraint prevents the same vacancy from ever being notified
twice to the same recipient, across runs and restarts. `JOB_MONITORING_MAX_NOTIFICATIONS` bounds
how many backlog candidates are sent per run — candidates beyond that limit are not dropped, they
simply remain in the backlog and are picked up (highest score first) on a later run.

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
- [ ] Automatic vacancy ingestion from more job providers — Greenhouse, Lever, Ashby, LinkedIn
      (RemoteOK is already wired up; guided manual import via `/add` covers any other single URL
      today — see [Guided vacancy import](#guided-vacancy-import))
- [ ] Duplicate detection across job providers, and ranking + auto-sending best matches to Telegram
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
