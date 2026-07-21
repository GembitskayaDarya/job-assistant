# Job Assistant

Spring Boot 3.5 / Java 21 service for tracking job applications, with a Telegram bot front-end
and OpenAI-powered cover letter suggestions.

## Stack

- Java 21, Gradle (Groovy DSL)
- Spring Web, Spring Data JPA, Spring Validation, Spring Actuator
- PostgreSQL + Flyway
- TelegramBots (long polling)
- OpenAI Java SDK
- Lombok
- JUnit 5 + Testcontainers

## Running locally

```bash
cp .env.example .env
docker compose up --build
```

The API is available at `http://localhost:8080/api/job-applications`, health at
`http://localhost:8080/actuator/health`.

To run without Docker, start a local PostgreSQL instance and set `DB_HOST`, `DB_USERNAME`,
`DB_PASSWORD`, `DB_NAME` accordingly, then:

```bash
./gradlew bootRun
```

## Telegram bot

Set `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_BOT_USERNAME` to enable the bot.
It is disabled by default so the app starts without a token.

## OpenAI

Set `OPENAI_API_KEY` (and optionally `OPENAI_MODEL`, default `gpt-4o-mini`) to enable
`OpenAiAssistantService`.

## Tests

```bash
./gradlew test
```

Integration tests spin up PostgreSQL via Testcontainers — Docker must be running.
