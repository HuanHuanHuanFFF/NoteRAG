# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```powershell
.\mvnw.cmd test                              # run all tests
.\mvnw.cmd -q "-Dtest=SomeTests" test        # run a single test class
.\mvnw.cmd spring-boot:run                   # start the app (port 9000)
.\mvnw.cmd package                           # build jar
```

Java 17, Spring Boot 3, MyBatis, PostgreSQL + pgvector, Spring AI.

Local tests use H2 in-memory; embedding and rerank are disabled by default (`noterag.embedding.enabled=false`, `noterag.rerank.enabled=false`). No external services needed to run tests.

## Project Goal

NoteRAG is a lightweight RAG system for personal Markdown technical notes. The v1 chain:

```
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopN retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

Current status: chain is complete through rerank. Prompt assembly and LLM call are not yet implemented.

## Architecture

Layer responsibilities are strict:

- **controller/** — HTTP boundary only. No business logic.
- **service/** — Business orchestration (import flow, query flow).
- **client/** — External model API adapters (EmbeddingClient, RerankClient).
- **mapper/** — MyBatis persistence. Explicit SQL, no hidden business rules.
- **model/** — Database entities. No workflow logic.
- **dto/** — Request/response objects.
- **config/** — Spring configuration and feature-flag properties.

Key services in the query path:
- `QueryService` — orchestrates retrieval → rerank → (future: prompt → LLM) → response
- `RetrievalService` — embeds question, runs pgvector cosine search
- `RerankService` — calls qwen3-rerank via DashScope, maps results back to chunks

Chunking uses a custom Markdown heading-aware strategy (not Spring AI's splitter). Chunks carry `headingPath`, `chunkIndex`, `charCount`, `tokenCount`. For embedding, chunks are formatted with title + heading path context, but `note_chunks.content` stores raw body only.

## API Endpoints

```
GET  /api/health
POST /api/note-imports/text          # Markdown import
POST /api/retrieval/search           # debug: raw vector retrieval
POST /api/query                      # question -> retrieval -> rerank -> answer + sources
```

`/api/retrieval/search` is a debug endpoint. `/api/query` does not expose topN/topK parameters.

## Database

PostgreSQL + pgvector. Schema lives in `docker/postgres/init/`. Tables include documents, note_chunks, embedding models, and chunk_embeddings_1024. `CREATE TABLE IF NOT EXISTS` won't migrate existing columns — document a reset or add migration if changing schema.

Import flow: insert document → get ID → pass ID in metadata → chunk → persist chunks → embed.

## Scope Guardrails

Do NOT add: user system, auth, multi-tenancy, PDF/Word, crawlers, Redis, MQ, object storage, agent workflows, complex frontend, web search. The LLM must answer only from note chunks — never claim to search the internet.

Do NOT: expose topN/topK on `/api/query`, put business logic in controllers/mappers/models, add interfaces/factories/event systems without a real requirement.

## Conventions

- Java: 4-space indent, PascalCase classes, camelCase methods/fields, constructor injection.
- Package base: `com.huanf.noterag`
- Commit style: `type(scope): 中文描述` — e.g. `feat(query): 添加召回测试接口`
- Config: all secrets via environment variables, never hardcoded.
- Spring AI is only for model integration; chunking, retrieval, rerank, and prompt assembly are explicitly controlled by project code.
