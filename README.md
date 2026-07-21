# Job Assistant

A Spring Boot backend for tracking job applications, with a Telegram bot front-end for
on-the-go status checks and an OpenAI-powered assistant for drafting cover letter suggestions.

## Description

Job Assistant keeps a structured record of companies, vacancies, and the applications you've
submitted against them — status (`APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN`),
applied date, and notes — exposed through a REST API, with interviews and notifications modeled
alongside. A Telegram bot lets you query your applications from your phone, and an OpenAI
integration can draft a cover letter opening paragraph from a job description.

## Architecture

The codebase is organized feature-first: each domain owns its own controller/service/
repository/entity/dto/mapper, and a handful of shared technical packages sit alongside them.

```
com.darya.jobassistant
├── config          Spring configuration & @ConfigurationProperties (OpenAI, Telegram)
├── exception       Shared NotFoundException hierarchy + the global exception handler
├── mapper          EntityMapper<E, ReqD, ResD> — the shared entity/DTO mapping contract
├── util            Stateless helpers (e.g. Telegram message formatting)
├── scheduler       Scheduled jobs (reminders, periodic sync) — not yet implemented
├── telegram        Telegram long-polling bot + the TelegramUser entity/repository
├── openai          OpenAI client wrapper / assistant service
├── tracking        Application entity/dto/repository/service/controller/mapper (the core REST API)
├── companies       Company entity/dto/repository/mapper
├── vacancies       Vacancy entity/dto/repository/mapper
├── interviews      Interview entity/dto/repository/mapper
└── notifications   Notification entity/dto/repository/mapper
```

`companies`, `vacancies`, `interviews`, and `notifications` currently expose their domain model
(entity, repository, DTOs, mapper) but no REST endpoints yet — see Roadmap. `tracking` is the
one domain with a full stack today: `ApplicationController` validates input and delegates to
`ApplicationService`, which resolves `Company`/`Vacancy`/`TelegramUser` relations via their
mappers/repositories and persists through `ApplicationRepository`. `telegram` and `openai` are
peer integrations that call into `tracking`'s service layer but sit outside the core web flow.
Database schema is version-controlled with Flyway migrations (`src/main/resources/db/migration`)
rather than Hibernate auto-DDL.

## Technologies

- **Java 21**, **Gradle** (Groovy DSL)
- **Spring Boot 3.5** — Web, Data JPA, Validation, Actuator
- **PostgreSQL** + **Flyway** for schema migrations
- **TelegramBots** (long polling)
- **OpenAI Java SDK**
- **Lombok**
- **JUnit 5** + **Testcontainers** for integration tests
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
export DB_PASSWORD=postgres
./gradlew bootRun
```

The API is available at `http://localhost:8080/api/applications`.

### Running tests

```bash
./gradlew test
```

Integration tests use Testcontainers to spin up a real PostgreSQL instance, so Docker must be
running.

## Docker

Build and run the full stack (app + PostgreSQL) with Docker Compose:

```bash
docker compose up --build
```

This starts:

- `postgres` — PostgreSQL 16, database `job_assistant`, exposed on `5432`
- `app` — the Spring Boot app, built from the local `Dockerfile`, exposed on `8080`

Check it's up:

```bash
curl http://localhost:8080/actuator/health
```

To run the app image standalone (e.g. against an external database):

```bash
docker build -t job-assistant .
docker run -p 8080:8080 --env-file .env job-assistant
```

## Environment variables

| Variable                      | Default            | Description                                  |
|--------------------------------|---------------------|-----------------------------------------------|
| `DB_HOST`                      | `localhost`         | PostgreSQL host                               |
| `DB_PORT`                       | `5432`              | PostgreSQL port                               |
| `DB_NAME`                       | `job_assistant`     | Database name                                 |
| `DB_USERNAME`                   | `postgres`          | Database username                             |
| `DB_PASSWORD`                   | `postgres`          | Database password                             |
| `DB_POOL_MIN_IDLE`              | `2`                 | HikariCP minimum idle connections             |
| `DB_POOL_MAX_SIZE`              | `10`                | HikariCP maximum pool size                    |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | `30000`             | HikariCP connection timeout (ms)              |
| `SERVER_PORT`                   | `8080`              | HTTP port                                     |
| `TELEGRAM_ENABLED`              | `false`             | Enables the Telegram bot when `true`          |
| `TELEGRAM_BOT_TOKEN`            | *(empty)*           | Telegram bot token from `@BotFather`          |
| `TELEGRAM_BOT_USERNAME`         | *(empty)*           | Telegram bot username                         |
| `OPENAI_API_KEY`                | *(empty)*           | OpenAI API key                                |
| `OPENAI_MODEL`                  | `gpt-4o-mini`       | OpenAI model used for cover letter suggestions|
| `LOG_LEVEL_ROOT`                | `INFO`              | Root logger level                             |
| `LOG_LEVEL_APP`                 | `INFO`              | Logger level for `com.darya.jobassistant`     |
| `LOG_LEVEL_SQL`                 | `WARN`              | Logger level for `org.hibernate.SQL`          |

Copy `.env.example` to `.env` and fill in the Telegram/OpenAI values before running
`docker compose up`. Database credentials for Compose are fixed in `docker-compose.yml`
(`postgres` / `postgres` / `job_assistant`) — override them there if needed.

## Roadmap

- [ ] Authentication/authorization for the REST API (currently unauthenticated)
- [ ] REST controllers + services for `companies`, `vacancies`, `interviews`, `notifications` (models exist, no endpoints yet)
- [ ] Per-user accounts, beyond raw Telegram chat IDs
- [ ] Telegram commands to create/update applications and schedule interviews, not just list them
- [ ] Pagination and filtering on `GET /api/applications`
- [ ] Scheduled reminders/notifications for upcoming interviews via the `scheduler` package
- [ ] OpenAPI/Swagger documentation for the REST API

## Screenshots

_Not yet available — add screenshots of the API responses, Telegram bot conversations, or
Actuator dashboards here as the project matures._

## License

This project does not currently have an open-source license. All rights reserved by the
author; do not reuse or redistribute without permission. Add a `LICENSE` file (e.g. MIT,
Apache-2.0) if and when you decide to open-source it.
