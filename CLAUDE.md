# Job Assistant

Telegram-based job search assistant. Tracks companies, vacancies, applications, and interviews
behind a REST API, with a Telegram bot front-end and an OpenAI-backed assistant for drafting
text. Built to grow for years, eventually ingesting vacancies from LinkedIn, Greenhouse, Lever,
and RemoteOK.

Stack: Java 21, Spring Boot 3, PostgreSQL, Flyway, Gradle, Docker. See `README.md` for how to
run it, environment variables, and Docker Compose setup — this file is about architecture only.

## Architecture principles

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
  (Did this for `ApplicationService` — it used to be `ApplicationService` interface +
  `ApplicationServiceImpl`; now it's just `ApplicationService`.)
- Don't create a port (`XyzPort`) until there's a second real implementation *or* a first real
  caller. A port with one implementation and no caller is dead scaffolding, not architecture.
  Concretely: `NotifierPort` waits until something actually pushes a Telegram notification
  outside a reply handler; `AssistantPort` waits until a second AI provider is actually being
  added; `JobSourcePort` + its adapters (LinkedIn/Greenhouse/Lever/RemoteOK) get built together
  with the first real ingestion job, not as empty stubs ahead of time.

## Package structure

```
com.darya.jobassistant
├── config                          Spring @Configuration / @ConfigurationProperties only — no business logic
├── entity/BaseEntity.java          Shared JPA base (id, createdAt, updatedAt)
├── mapper/EntityMapper.java        Shared entity <-> DTO mapping contract, implemented per feature
├── exception                       Shared NotFoundException hierarchy + GlobalExceptionHandler
├── util                            Stateless helpers (e.g. Telegram message formatting)
│
├── companies                       entity/dto/repository/mapper — no REST yet
├── vacancies                       entity/dto/repository/mapper — no REST yet
├── interviews                      entity/dto/repository/mapper — no REST yet
├── notifications                   entity/dto/repository/mapper/service — service is currently an empty stub
├── tracking                        Application entity/dto/repository/service/mapper/controller — the one full REST stack today
│
├── telegram                        Inbound delivery channel (the bot is the "UI")
│   ├── JobAssistantTelegramBot.java
│   ├── command/                    One class per slash command + CommandRegistry
│   └── user/                       User entity + repository (telegram_id, profile, language/timezone — a subscriber, not an "integration")
│
├── integrations/                   Ports & adapters boundary — see principles above
│   └── ai/openai/OpenAiAssistantService.java   OpenAI chat completion wrapper (only AI provider today)
│
└── scheduler                       Periodic use cases that span multiple features (reminders, sync) — not yet implemented
```

Where future integrations land when they're actually built (not yet present):

- `integrations/jobsource/` — `JobSourcePort` interface + `linkedin/`, `greenhouse/`, `lever/`,
  `remoteok/` adapters, built together with the first real ingestion job, feeding into
  `vacancies`.
- `integrations/notifier/` — `NotifierPort` + `telegram/TelegramNotifier`, built when something
  needs to push a notification to a user outside of replying to their message (e.g. from
  `scheduler` or `notifications`).
- `integrations/ai/` gets an `AssistantPort` interface only if/when a second AI provider is
  added alongside OpenAI.

## Testing

- Unit tests: JUnit 5 + Mockito, colocated per feature (`src/test/.../tracking/service/ApplicationServiceTest.java`).
- Repository tests: `@DataJpaTest` + Testcontainers (`ApplicationRepositoryTest`) — needs Docker running.
- Controller tests: `@WebMvcTest` + `@MockitoBean` for the service layer.
