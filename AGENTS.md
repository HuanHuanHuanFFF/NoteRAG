# Repository Guidelines

## Project Structure & Module Organization
NoteRAG is a lightweight RAG service for personal Markdown notes. The codebase uses Java 17, Spring Boot 3, MyBatis, PostgreSQL, and pgvector. Main code lives under `src/main/java/com/huanf/noterag`, with future feature slices expected in `controller/`, `service/`, `client/`, `mapper/`, `model/`, `dto/`, and `config/`. Runtime settings are in `src/main/resources/application.properties`. Tests mirror the main package under `src/test/java/com/huanf/noterag`. Build output stays in `target/`.

## Build, Test, and Development Commands
Use the Maven wrapper to keep builds consistent:

```powershell
.\mvnw.cmd test
```
Runs unit and integration tests.

```powershell
.\mvnw.cmd spring-boot:run
```
Starts the app locally; the health check is `GET /api/health`.

```powershell
.\mvnw.cmd package
```
Compiles, tests, and produces the runnable JAR in `target/`.

## Coding Style & Naming Conventions
Use standard Java formatting: 4-space indentation, same-line braces, `PascalCase` for classes, `camelCase` for methods and fields, and lowercase package names. Keep the base package aligned with the repo, `com.huanf.noterag`. Name REST controllers with a `Controller` suffix, request/response objects with clear `Request` or `Response` suffixes, and keep endpoint paths under `/api/...`. Prefer constructor injection and small, single-purpose services.

## Testing Guidelines
Use JUnit 5 with Spring Boot test support. Keep test names ending in `*Tests` and place them beside the package they cover. Focus tests on the first version's core flow: health, Markdown import, chunking, query plumbing, and repository access. Run `.\mvnw.cmd test` before opening a PR.

## Commit & Pull Request Guidelines
There is no established commit history yet, so use short imperative commits such as `Add import-text endpoint` or `Wire pgvector repository`. Keep each commit scoped to one step in the RAG pipeline. PRs should state what changed, how it was tested, and any API behavior changes. Include request/response examples when an endpoint changes.

## Scope Guardrails
This first version should stay narrow: Markdown import, chunking, embedding, pgvector storage, TopK retrieval, LLM answer generation, and `answer + sources` output. Avoid adding users, multi-tenant logic, PDF/Word import, crawlers, agents, Redis, MQ, or advanced reranking unless the scope is explicitly expanded.
