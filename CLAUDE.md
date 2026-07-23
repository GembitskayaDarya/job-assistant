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

Future integrations:

- OpenAI / Claude API
- RemoteOK
- Greenhouse
- Lever
- Ashby
- LinkedIn (if possible)
- Other public job providers

---

# Architecture

The application follows a modular architecture.

```
Telegram Bot
        │
        ▼
Application Layer
        │
 ┌──────┼─────────────┐
 ▼      ▼             ▼
Commands   Scheduler   AI Services
 │            │             │
 ▼            ▼             ▼
Job Service  Job Fetcher   Job Analyzer
 │            │             │
 └────────────┼─────────────┘
              ▼
      External Job Providers
              │
              ▼
         PostgreSQL Database
```

Main modules:

```
telegram/
application/
scheduler/
fetcher/
ai/
repository/
domain/
dto/
config/
```

Responsibilities:

- telegram – Telegram bot and command handlers
- application – business logic and use cases
- scheduler – scheduled jobs
- fetcher – integrations with external job providers
- ai – AI-powered services
- repository – persistence layer
- domain – entities
- dto – request/response models
- config – Spring configuration

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

Prefer interfaces over concrete implementations when multiple providers are expected.

Business logic must not depend on external APIs.

External providers should be easily replaceable.

Configuration should be externalized whenever possible.

Transactions should be used only when necessary.

---

# Future Modules

The architecture should support future implementation of:

- Multiple job providers
- AI vacancy analysis
- CV generation
- Cover letter generation
- Application tracking
- Interview assistant
- Analytics
- User profile management

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