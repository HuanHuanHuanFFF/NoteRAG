# Repository Guidelines

## NoteRAG Engineering Goal
NoteRAG is a lightweight RAG service for personal Markdown technical notes. Keep the v1 goal narrow and runnable:

`Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopK retrieval -> prompt assembly -> LLM answer -> answer + sources`

Every change should move this chain forward or make one link safer. Do not turn the project into a generic knowledge platform.

## Layer Responsibilities
- `controller/`: HTTP boundary only. Validate request shape, call services, return DTOs. Do not place chunking, prompt assembly, database decisions, or model-call logic here.
- `service/`: Application workflow orchestration, such as import and query flows. For document import, persist the source document first, obtain its database ID, then run chunking.
- Chunking services/components: Markdown parsing, heading path handling, overlap, `charCount`, and estimated `tokenCount`.
- `chunk/` transformer contract: source `Document` metadata must contain the persisted document ID. Chunking components must preserve that ID into each chunk metadata so later storage, batch import, or async chunk workers can map chunks back to the source document.
- `client/` or Spring AI adapter components: embedding/chat model calls. Spring AI is for model integration; NoteRAG still explicitly controls chunk strategy, storage schema, retrieval results, and prompt assembly.
- `mapper/`: MyBatis persistence only. Keep SQL explicit and predictable; do not hide business rules in mapper methods.
- `model/`: Database-shaped entities such as `Document`, `DocumentChunk`, and persisted vector records. No workflow logic.
- `dto/`: API request/response objects, including `answer + sources`.
- `config/`: Spring, datasource, pgvector, and model configuration wiring.

## Project Structure
Main code lives under `src/main/java/com/huanf/noterag`. Tests mirror packages under `src/test/java/com/huanf/noterag`. PostgreSQL initialization SQL lives in `docker/postgres/init/`. Runtime configuration belongs in `src/main/resources/application.properties` or external environment variables; do not hardcode passwords, URLs, API keys, or model secrets in code.

## Database Change Rules
When changing table fields or persistence shape, update all related surfaces in the same change:

`docker/postgres/init/*.sql`, `model/`, mapper SQL/result mappings, DTOs if exposed, and tests.

For document/chunk data, keep `char_count`, `token_count`, `heading_path`, `chunk_index`, and document-chunk association consistent. Document import must follow `insert document -> get id -> pass id in metadata -> chunk -> persist chunks`. If an initialized Docker volume already exists, remember that `CREATE TABLE IF NOT EXISTS` will not migrate old tables; either document a reset or add a real migration before relying on new columns.

## Testing Focus
Use JUnit 5 and Spring Boot test support. Prioritize tests around NoteRAG failure points:

- Markdown heading parsing and fenced code blocks using both ``` and ~~~
- chunk overlap, chunk index reset per document, `charCount`, and `tokenCount`
- document to chunk association and mapper result mapping
- embedding failure handling and clear error boundaries
- TopK retrieval ordering and returned `sources`
- final `/api/query` response shape: `answer + sources`

Run `.\mvnw.cmd test` before committing meaningful behavior changes.

## Scope Guardrails
For v1, do not add users, authentication, authorization, multi-tenancy, PDF/Word import, crawlers, Redis, MQ, object storage, agent workflows, advanced reranking, or complex frontend work unless explicitly requested. Keep imports to Markdown file/text, storage to PostgreSQL + pgvector, retrieval to TopK, and answer generation to a direct LLM call.

## Design Bias
Prefer a clear, working RAG loop over premature architecture. Do not add interfaces, factories, event systems, generic platforms, or "enterprise" layers unless a real current requirement needs them. Keep abstractions small and named after NoteRAG concepts, not framework patterns.

## Basic Commands & Style
Use Java 17, Spring Boot 3, MyBatis, PostgreSQL + pgvector, and Spring AI. Keep Java formatting conventional: 4-space indentation, `PascalCase` classes, `camelCase` methods/fields, constructor injection, and package base `com.huanf.noterag`.

Common commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
.\mvnw.cmd package
```

Keep commits scoped to one RAG-chain step, for example `feat(chunk): add token estimation` or `feat(query): return answer sources`.
