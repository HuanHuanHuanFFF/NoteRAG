# Repository Guidelines

## NoteRAG Goal
NoteRAG is a lightweight RAG service for personal Markdown technical notes. Keep v1 narrow and runnable:

`Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources`

Every change should move this chain forward or make one link safer. Keep project status, handoff notes, and next-step planning in `docs/dev/`; keep this file focused on engineering constraints and working style.

## Engineering Boundaries
Keep the code organized by responsibility, not by convenience. HTTP entry points should stay thin, application services should orchestrate workflows, persistence should stay explicit and predictable, and model calls should remain isolated behind client or adapter components.

NoteRAG should explicitly control the important RAG decisions: Markdown chunking, token/character counts, storage shape, retrieval/rerank results, prompt assembly, citation parsing, and returned sources. Spring AI is only the model integration layer, not the owner of the RAG workflow.

Keep `/api/query` as a reranked sources debug endpoint unless explicitly changing that contract. Full answer generation belongs to chat flows.

## Project Layout
Main code lives under `src/main/java/com/huanf/noterag`. Tests mirror packages under `src/test/java/com/huanf/noterag`. PostgreSQL initialization SQL lives in `docker/postgres/init/`. Runtime configuration belongs in `src/main/resources/application.properties` or external environment variables; do not hardcode passwords, URLs, API keys, or model secrets in code.

## Database Changes
When changing table fields or persistence shape, update `docker/postgres/init/*.sql`, entities, mapper SQL/result mappings, exposed DTOs, and tests in the same change. Keep `char_count`, `token_count`, `heading_path`, `chunk_index`, and note-chunk association consistent. `CREATE TABLE IF NOT EXISTS` does not migrate old Docker volumes, so document a reset or add a real migration before relying on new columns.

## Testing
Use JUnit 5 and Spring Boot test support. Prioritize tests around the main failure points:

- Markdown heading parsing and fenced code blocks using both ``` and ~~~
- chunk overlap, chunk index reset per document, `charCount`, and `tokenCount`
- note-to-chunk association and mapper result mapping
- embedding failure handling and clear error boundaries
- TopK retrieval ordering and returned `sources`
- `/api/query` reranked sources debug response shape
- chat `answer + sources` behavior and citation filtering

Run `.\mvnw.cmd test` before committing meaningful behavior changes.

## Scope Guardrails
For v1, do not add users, authentication, authorization, multi-tenancy, PDF/Word import, crawlers, Redis, MQ, object storage, agent workflows, hybrid search, query rewriting, multi-stage retrieval, advanced reranking beyond the current basic rerank flow, or complex frontend work unless explicitly requested. Keep imports to Markdown file/text, storage to PostgreSQL + pgvector, retrieval to TopK + rerank, and answer generation to a direct LLM call.

## Design Bias
Prefer a clear, working RAG loop over premature architecture. Do not add factories, event systems, generic platforms, or extra abstraction layers unless a real current requirement needs them. Keep abstractions small and named after NoteRAG concepts, not framework patterns.

## Working Style

These rules bias toward caution over speed. For trivial tasks, use judgment.

Think before coding. State assumptions, surface tradeoffs, and ask when the request is ambiguous. Do not silently choose between multiple interpretations.

Keep solutions simple. Write the minimum code that solves the current problem. Do not add speculative features, one-off abstractions, unrequested configurability, or defensive handling for impossible scenarios.

Make surgical changes. Touch only what the task requires. Do not refactor adjacent code, reformat unrelated files, or delete pre-existing dead code unless asked. Remove only unused code introduced by your own change.

Work toward verifiable goals. For multi-step work, give a brief plan with success criteria before editing. Prefer tests, builds, lint, or focused manual checks over "looks done".

## Basic Commands & Style
Use Java 17, Spring Boot 3, MyBatis, PostgreSQL + pgvector, and Spring AI. Keep Java formatting conventional: 4-space indentation, `PascalCase` classes, `camelCase` methods/fields, constructor injection, and package base `com.huanf.noterag`.

Common commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
.\mvnw.cmd package
```

Keep commits scoped to one RAG-chain step, for example `feat(chunk): add token estimation` or `feat(query): return answer sources`.
