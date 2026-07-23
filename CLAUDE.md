# AI Job Search Assistant

## Project Overview

This project is a personal AI-powered Telegram bot that automates the entire job search process for a Senior Java Backend Engineer.

The long-term vision is to build an autonomous assistant capable of:

- Searching for new vacancies from multiple job providers
- Collecting and storing vacancies
- Removing duplicates
- Matching vacancies against my professional profile using AI
- Ranking vacancies by relevance
- Sending only the best matches to Telegram
- Generating tailored CVs and cover letters
- Tracking application status
- Assisting with interview preparation

The application is built incrementally and is intended to become production-ready software.

---

# Technology Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Flyway
- Docker
- Telegram Bot API
- Gradle
- Spring AI + OpenAI (job-to-profile matching via `JobAnalysisService`)
- RemoteOK (first `JobSourcePort` adapter)

Future integrations:

- Additional job providers: Greenhouse, Lever, Ashby, LinkedIn (if possible)
- Additional AI providers (Claude API) — only if a second provider is actually needed
- Other public job providers

---

# Architecture

The codebase is **package-by-feature**, not package-by-layer: each domain owns its own
`entity/dto/repository/service/mapper/controller` rather than the app having one giant
`controllers`, one giant `services`, etc. This is what makes it scale by feature count instead
of by layer size.

Clean Architecture's port/adapter (dependency inversion) pattern is applied **only at the
integration boundary** — under `integrations/` — not throughout the app:

- Feature modules (`companies`, `vacancies`, `interviews`, `tracking`, `notifications`) are
  plain Spring: concrete service classes calling Spring Data JPA repositories directly. The
  repository interface *is* the port; the JPA proxy *is* the adapter — no hand-rolled interface
  needed on top.
- `integrations/` is where a capability has, or will imminently have, more than one real
  implementation: multiple job boards, potentially multiple AI providers, potentially multiple
  notification channels. That's the only place introducing a port interface earns its cost.

**Rule of thumb, applied consistently and enforced in code review:**
- Don't create a `Foo` interface for a single `FooImpl`. Merge them into one concrete class.
- Don't create a port (`XyzPort`) until there's a second real implementation *or* a first real
  caller. A port with one implementation and no caller is dead scaffolding, not architecture.
  Concretely: `NotifierPort` waits until something actually pushes a Telegram notification
  outside a reply handler; `AssistantPort` (under `integrations/ai/`) waits until a second AI
  provider is actually added alongside OpenAI.

## Package structure

```
com.darya.jobassistant
├── config                          Spring @Configuration / @ConfigurationProperties only — no business logic
├── entity/BaseEntity.java          Shared JPA base (id, createdAt, updatedAt)
├── mapper/EntityMapper.java        Shared entity <-> DTO mapping contract, implemented per feature
├── exception                       Shared NotFoundException hierarchy + GlobalExceptionHandler
├── util                            Stateless helpers (e.g. Telegram message formatting)
├── candidates/CandidateProfile.java  The single hardcoded candidate profile used for AI matching
│
├── companies                       entity/dto/repository/mapper/service — no public REST endpoints yet
├── vacancies                       entity/dto/repository/mapper/service — no public REST endpoints yet
├── interviews                      entity/dto/repository/mapper/service — no public REST endpoints yet
├── notifications                   entity/dto/repository/mapper/service — service is currently an empty stub
├── tracking                        Application entity/dto/repository/service/mapper/controller — the one full REST stack today
│
├── telegram                        Inbound delivery channel (the bot is the "UI")
│   ├── JobAssistantTelegramBot.java
│   ├── command/                    One class per slash command + CommandRegistry
│   └── user/                       User entity + repository (a subscriber, not an "integration")
│
├── integrations/                   Ports & adapters boundary — see principles above
│   ├── jobsource/                  JobSourcePort + JobOffer + JobSourceController
│   │   └── remoteok/               RemoteOkJobSourceAdapter — first (and currently only) job source
│   └── ai/openai/                  OpenAiAssistantService (chat) + JobAnalysisService (profile/job matching, scores + pros/cons)
│
└── scheduler                       VacancyIngestionJob — periodic ingestion across all JobSourcePort beans
```

Where future integrations land when they're actually built (not yet present):

- `integrations/jobsource/{greenhouse,lever,ashby,linkedin}/` — new adapters alongside `remoteok`,
  built one at a time as each provider is actually wired in.
- `integrations/notifier/` — `NotifierPort` + `telegram/TelegramNotifier`, built when something
  needs to push a notification to a user outside of replying to their message (e.g. from
  `scheduler` or `notifications`).
- `integrations/ai/` gets an `AssistantPort` interface only if/when a second AI provider is
  added alongside OpenAI.
- Ranking + auto-send of best matches to Telegram, CV/cover letter generation, and the interview
  assistant are not implemented yet — see Future Modules below.

## Testing

- Unit tests: JUnit 5 + Mockito, colocated per feature (`src/test/.../tracking/service/ApplicationServiceTest.java`).
- Repository tests: `@DataJpaTest` + Testcontainers (`ApplicationRepositoryTest`) — needs Docker running.
- Controller tests: `@WebMvcTest` + `@MockitoBean` for the service layer.

---

# Development Principles

Always write production-quality code.

Follow:

- SOLID
- Clean Architecture
- High cohesion
- Low coupling
- Constructor injection
- Immutable DTOs
- Java Records where appropriate
- Small focused classes
- Readable code
- Meaningful naming

Avoid:

- God classes
- Static utility classes without good reason
- Duplicate code
- Overengineering
- Premature optimization

---

# Design Guidelines

Every new feature should be designed for future extensibility.

Prefer interfaces over concrete implementations only when multiple providers are expected — see
the port/adapter rule of thumb above. A single implementation gets a concrete class, not an
interface + impl pair.

Business logic must not depend on external APIs.

External providers should be easily replaceable.

Configuration should be externalized whenever possible.

Transactions should be used only when necessary.

---

# Future Modules

Already implemented: job ingestion from a first provider (RemoteOK) into `vacancies`, AI-based
job-to-profile matching (`JobAnalysisService`), and application tracking (`tracking`).

Still to build, and the architecture should keep these easy to add:

- Additional job providers (Greenhouse, Lever, Ashby, LinkedIn)
- Duplicate detection across job providers
- Ranking vacancies by AI match score and auto-sending only the best matches to Telegram
- CV generation
- Cover letter generation
- Interview assistant
- Analytics
- User profile management (today's `CandidateProfile` is a single hardcoded profile)

Avoid designs that would make these features difficult to integrate later.

---

# Candidate Profile

The application is optimized for the following candidate profile:

Experience:
- Senior Java Backend Engineer
- 6+ years

Main stack:
- Java
- Spring Boot
- PostgreSQL
- Kafka
- Redis
- AWS
- Docker

Preferred jobs:
- Remote
- Product companies
- Europe
- B2B

Languages:
- English
- Polish
- Russian