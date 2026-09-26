# ASMS Backend

REST API of **ASMS (Assignment Management System)**, a web platform that lets students manage their course group projects in one place: groups, members, phases, tasks, submission milestones, deliverables, peer reviews, notifications and system administration.

- **Specification:** [`../../docs/Assignment_Management_System_Requirement.md`](../../docs/Assignment_Management_System_Requirement.md). Read the relevant use cases, business rules and tables **before** implementing anything.
- **Traceability codes** used everywhere (Javadoc, test names, commits): `Fxx.yy` feature, `UCxx` use case, `BRxx` business rule, `NFRxx` non-functional requirement.
- **Frontend:** React SPA in `../asms-frontend`. It generates its TypeScript client from this API's OpenAPI spec, so **the backend owns the API contract**.

## Tech stack

| Area | Technology |
| --- | --- |
| Language / build | Java 21 (LTS), Maven Wrapper (`./mvnw`) |
| Framework | Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3) |
| Data | PostgreSQL 17, Spring Data JPA, Flyway, Redis (cache, rate limiting, locks) |
| Security | Spring Security, OAuth2 Resource Server (JWT HS256), OAuth2 Client (Google), Argon2 |
| API docs | springdoc-openapi 3 (`/v3/api-docs`, `/swagger-ui.html`) |
| Boilerplate | Lombok, MapStruct |
| Email | Spring Mail + Thymeleaf templates (`resources/templates/email`) |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |
| Formatting | Spotless + Palantir Java Format |

Libraries chosen by the requirement but **added only when the feature needs them**: JobRunr, ShedLock, Bucket4j, AWS SDK v2 (S3 Presigner), Apache Tika, OWASP Java HTML Sanitizer, Apache POI, OpenPDF, Spring WebSocket (phase 3).

> Spring Boot 4 differs from 3.x: starters are modular (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`...), Jackson 3 lives in `tools.jackson.*`, nullability annotations come from JSpecify (`org.jspecify.annotations`). Prefer Boot 4 documentation and examples.

## Commands

```bash
docker compose up -d                 # (this folder) Postgres, Redis, Mailpit - copy .env.example to .env first
docker compose --profile app up -d --build  # Same infrastructure plus the API container (Dockerfile)
./mvnw spring-boot:run               # Run the API, default profile: local
./mvnw spring-boot:test-run          # Run against Testcontainers instead of docker compose
./mvnw test                          # All tests (integration tests need Docker running)
./mvnw test -Dtest=DateTimeUtilsTest # One test class
./mvnw spotless:apply                # Format code - REQUIRED before committing
./mvnw verify                        # Full build + tests (what CI runs, plus spotless:check)
```

Local URLs: Swagger UI http://localhost:8080/swagger-ui.html - Mailpit http://localhost:8025.
Object storage (F05.07) is not in docker-compose yet: `minio/minio` is no longer published on Docker Hub, pick an S3-compatible replacement when attachments are built.
Profiles: `local` (default), `test`, `prod` (all secrets from environment variables).

## Project structure at a glance

Layer-first packages; inside each layer, one sub-package per business module. Full details in [rules/project-structure.md](rules/project-structure.md).

```
com.asms
├── config/  security/  constant/  exception/  util/     # cross-cutting infrastructure
├── controller/<module>/                                  # REST endpoints
├── service/<module>/                                     # business logic, transactions, rule checks
├── repository/<module>/                                  # Spring Data JPA
├── entity/base/  entity/<module>/                        # JPA entities
├── dto/common/   dto/<module>/                           # request / response records
└── mapper/<module>/                                      # MapStruct mappers
```

Modules: `auth`, `user`, `group`, `phase`, `task`, `milestone`, `deliverable`, `review`, `notification`, `activity`, `catalog`, `admin`.

## Rules

Detailed rules live in [`.claude/rules/`](rules/) and are loaded automatically. Follow all of them:

| File | Covers |
| --- | --- |
| [code-comments.md](rules/code-comments.md) | File header (author, version, dates), Javadoc and inline comment rules |
| [project-structure.md](rules/project-structure.md) | Package layout, layer responsibilities, dependency rules |
| [coding-conventions.md](rules/coding-conventions.md) | Naming, Java/Spring/Lombok/JPA style, clean code |
| [api-conventions.md](rules/api-conventions.md) | REST design, validation, errors, pagination, OpenAPI |
| [database.md](rules/database.md) | Flyway migrations, naming, soft delete, indexes |
| [security.md](rules/security.md) | Authentication, two-layer authorization, privacy rules |
| [testing.md](rules/testing.md) | Unit, web and integration tests, coverage |
| [git-workflow.md](rules/git-workflow.md) | Working process, commits, definition of done, checklists |

## Golden rules

1. Read the specification first; if it is ambiguous, **ask** - never invent business behaviour.
2. Every business rule (BRxx) is enforced in the backend, even if the frontend already blocks it.
3. Code, identifiers, comments and documentation are written in **English**.
4. Every hand-written source file carries the standard header (see code-comments.md).
5. Nothing is "done" until `./mvnw spotless:apply` and `./mvnw verify` pass.
