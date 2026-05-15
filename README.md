# NoteRAG

NoteRAG 是一个面向个人 Markdown 技术笔记的轻量级 RAG 问答系统。

项目只聚焦 RAG 核心链路：

```text
Markdown -> 切块 -> 向量化 -> 存储 -> 提问 -> 检索 -> 拼接 Prompt -> 调用 LLM -> 返回答案
```

第一版目标保持简单：导入 Markdown 笔记，将文本切成 chunk，使用 Embedding 生成向量，存入 PostgreSQL + pgvector，查询时检索 TopK 相关片段，拼接 Prompt 调用 LLM，最终返回答案和来源。

这个项目不做复杂知识平台能力。第一版不包含用户系统、权限、多租户、PDF/Word 解析、爬虫、Agent、工作流、Redis、消息队列、对象存储、高级 rerank 或复杂前端交互。

## 包结构


主要目录：

```text
controller/
service/
client/
mapper/
model/
dto/
config/
```

## 包职责

`controller/` 放 HTTP 接口入口，例如健康检查、Markdown 导入和问答查询。

`service/` 放应用业务逻辑，负责组织 RAG 流程，包括文档导入、切块、检索、Prompt 拼接和答案生成。

`client/` 放外部模型 API 适配，主要是 `EmbeddingClient` 和 `LlmClient`。

`mapper/` 放 MyBatis Mapper，用于访问 PostgreSQL 和 pgvector。

`model/` 放持久化领域对象，例如文档、文本块、向量和检索记录。

`dto/` 放接口请求和响应对象。

`config/` 放 Spring 配置、数据库配置、模型客户端配置和其他应用级配置。

## 当前范围

当前开发优先跑通最小后端：

```text
GET  /api/health
POST /api/note-imports/text
POST /api/retrieval/search   # 开发期召回测试接口
POST /api/query              # 后续完整问答接口
```

实现时先保持简单，先跑通完整 RAG 闭环，再逐步完善每个环节。

## 切块策略

NoteRAG 当前采用自定义 Markdown heading-aware chunk 策略：

```text
heading section -> paragraph merge -> estimated token control -> overlap -> headingPath
```

chunk 入向量时会临时拼入文档标题和章节路径，但数据库中的 `note_chunks.content` 仍保留原始正文。

已完成一轮 retrieval baseline 对比：在 JavaGuide MySQL 文档上，自定义方案与 Spring AI `TokenTextSplitter` 调整到接近 chunk 数量后，二者 `Recall@5 / Recall@10` 均为 100%；自定义方案在排序和 source 可解释性上更好：

| 方案 | chunks | Hit@1 | Hit@3 | Recall@5 | MRR@5 |
|---|---:|---:|---:|---:|---:|
| Spring AI TokenTextSplitter baseline | 69 | 80.0% | 93.3% | 100% | 0.883 |
| NoteRAG heading-aware chunk | 71 | 93.3% | 100% | 100% | 0.967 |

完整实验记录见 [`retrieval-baseline-report.md`](src/test/http/responses/compare/retrieval-baseline-report.md)。

## English

NoteRAG is a lightweight RAG question-answering system for personal Markdown technical notes.

The project focuses on the core RAG pipeline only:

```text
Markdown -> chunking -> embedding -> storage -> query -> retrieval -> prompt -> LLM -> answer
```

The first version is intentionally small. It is designed to import Markdown notes, split them into chunks, store vectors in PostgreSQL with pgvector, retrieve TopK related chunks, call an LLM, and return an answer with sources.

This project does not try to become a full knowledge platform. The first version does not include user accounts, permissions, multi-tenancy, PDF/Word parsing, crawlers, agents, workflow engines, Redis, message queues, object storage, advanced reranking, or complex frontend interactions.

## Package Structure

Main package layout:

```text
controller/
service/
client/
mapper/
model/
dto/
config/
```

## Package Responsibilities

`controller/` contains HTTP API endpoints, such as health checks, Markdown import, and query entry points.

`service/` contains application logic and coordinates the RAG flow, including document import, chunking, retrieval, prompt building, and answer generation.

`client/` contains external model API adapters, mainly `EmbeddingClient` and `LlmClient`.

`mapper/` contains MyBatis mapper interfaces for PostgreSQL and pgvector access.

`model/` contains persistent domain objects, such as documents, chunks, embeddings, and retrieval records.

`dto/` contains request and response objects used by API endpoints.

`config/` contains Spring configuration, database configuration, model client configuration, and other application-level settings.

## Current Scope

Current development should first make the minimal runnable backend work:

```text
GET  /api/health
POST /api/note-imports/text
POST /api/retrieval/search   # development retrieval test endpoint
POST /api/query              # future full QA endpoint
```

Implementation should stay simple: run the core path first, then improve each step after the full RAG loop is connected.

## Chunking Strategy

NoteRAG currently uses a custom Markdown heading-aware chunking strategy:

```text
heading section -> paragraph merge -> estimated token control -> overlap -> headingPath
```

For embedding, each chunk is formatted with the note title and heading path, while `note_chunks.content` keeps the original chunk body.

A retrieval baseline comparison has been added. On a JavaGuide MySQL document, the custom strategy and Spring AI `TokenTextSplitter` both reached 100% `Recall@5 / Recall@10` at similar chunk counts, while the custom strategy produced better ranking and source explainability.

See [`retrieval-baseline-report.md`](src/test/http/responses/compare/retrieval-baseline-report.md) for details.
