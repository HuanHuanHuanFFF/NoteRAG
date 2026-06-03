# NoteRAG

NoteRAG 是一个面向个人 Markdown 技术笔记的轻量级 RAG 问答系统，目标是把已有笔记变成可检索、可追问、可引用来源的个人知识库。

项目只聚焦 RAG 核心链路：

```text
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage
-> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

第一版不做复杂知识平台能力：不包含用户系统、权限、多租户、PDF/Word 导入、爬虫、Redis、MQ、对象存储、Agent workflow、复杂前端或高级多阶段检索。

## 当前阶段

当前已经跑通核心 RAG 闭环和单会话 Chat：

- Markdown 导入：支持文本/文件内容导入，入库后切 chunk。
- 自定义 chunk：按 Markdown 标题 section 分组，保留 `headingPath`，估算 token，支持 overlap。
- Token 统计：入库、接口返回和 chunk metadata 使用 CL100K_BASE；切块边界仍保留轻量估算。
- Summary chunk：导入时生成全文摘要 chunk，作为全局视角补充参与 embedding 和 retrieval。
- Embedding：通过 Spring AI 接入 OpenAI-compatible embedding API。
- 向量存储：PostgreSQL + pgvector，当前使用 `chunk_embeddings_1024`。
- 检索与 rerank：pgvector TopK 召回后接入 rerank，`/api/query` 保留为 reranked sources 调试接口。
- Chat：支持会话、历史消息、note scope、LLM 回答、citation 解析和 sources 回写。
- SSE：前端正式发送走 Chat SSE，支持 `meta/delta/done/error` 流式事件。
- 前端工作台：已接入 notes、历史会话、note scope、sources panel、Markdown 流式渲染、左右面板拖拽和基础会话管理。
- 部署：已接入 Docker Compose + Nginx，支持前端静态资源、后端 API 和 Chat SSE 代理。

更详细的当前状态见 [`docs/dev/CURRENT_STATUS.md`](docs/dev/CURRENT_STATUS.md)，SSE 契约见 [`docs/dev/CHAT_SSE.md`](docs/dev/CHAT_SSE.md)。

## 核心接口

```text
GET    /api/health
POST   /api/note-imports/text

GET    /api/notes
GET    /api/notes/{noteId}
DELETE /api/notes/{noteId}

POST   /api/retrieval/search
POST   /api/query

GET    /api/chat-sessions
POST   /api/chat-sessions
POST   /api/chat-sessions/stream
GET    /api/chat-sessions/{sessionId}/messages
POST   /api/chat-sessions/{sessionId}/messages
POST   /api/chat-sessions/{sessionId}/messages/stream
PATCH  /api/chat-sessions/{sessionId}
DELETE /api/chat-sessions/{sessionId}
```

说明：

- `/api/query` 当前只返回 rerank 后的 sources，用于调试检索结果。
- 正式问答使用 Chat 接口；前端优先使用 SSE stream 接口。
- `DELETE` 目前是软归档，归档后的 note/session 对列表和详情不可见。

## 项目结构

后端主包：`src/main/java/com/huanf/noterag`

```text
controller/   HTTP 边界，保持薄 controller
service/      业务编排，例如 NoteService、QueryService、ChatService
chunk/        Markdown 解析、headingPath、chunk 组装
client/       Embedding、Rerank、LLM 外部 API 适配
entity/       数据库实体，例如 Note、NoteChunk、ChatSession
model/        后端内部模型，例如 RetrievedChunk、ChatResult
dto/          HTTP 请求和响应对象
rag/          prompt 构建、citation marker、引用解析
mapper/       MyBatis SQL 持久化
config/       Spring、模型客户端、功能开关配置
```

前端代码位于 `frontend/`，当前主工作台使用 Vue 3 + composables 拆分状态逻辑。Chat answer 使用流式 Markdown 渲染，Sources 和 Note 详情使用普通 Markdown 渲染。

数据库初始化 SQL 位于 `docker/postgres/init/`。注意：`CREATE TABLE IF NOT EXISTS` 不会迁移已有 Docker volume，结构变化后开发环境通常需要重建 volume 或引入正式 migration。

## 切块策略

NoteRAG 当前采用自定义 Markdown heading-aware chunk 策略：

```text
heading section -> paragraph merge -> estimated token control -> overlap -> headingPath
```

chunk 入向量时会临时拼入文档标题和章节路径，但数据库中的 `note_chunks.content` 仍保留原始正文。

此外，导入时会生成一个 `SUMMARY` chunk，用于补充“整篇笔记讲了什么”这类全文视角问题的召回能力。summary chunk 会参与 embedding 和 retrieval，但仍以独立 chunk 类型保存。

已完成一轮 retrieval baseline 对比：在 JavaGuide MySQL 文档上，自定义方案与 Spring AI `TokenTextSplitter` 调整到接近 chunk 数量后，二者 `Recall@5 / Recall@10` 均为 100%；自定义方案在排序和 source 可解释性上更好。

完整实验记录见 [`retrieval-baseline-report.md`](src/test/http/responses/compare/retrieval-baseline-report.md)。

## 本地运行

`.env` 不提交，需要自行在项目根目录准备。不要把 API key、数据库密码或模型密钥写入代码或提交到 Git。

```powershell
# 启动数据库和应用
docker compose up -d --build

# 后端测试
.\mvnw.cmd test

# 前端
cd frontend
npm.cmd run test
npm.cmd run build
```

新环境没有内置开发数据，需要先导入 Markdown，再生成 embedding，之后才能测试 retrieval/chat。

## English

NoteRAG is a lightweight RAG system for personal Markdown technical notes. It focuses on the core pipeline only:

```text
Markdown import -> chunking -> embedding -> pgvector storage
-> retrieval -> rerank -> prompt -> LLM -> answer + sources
```

The current version has connected Markdown import, custom heading-aware chunking, CL100K token counting for persisted metadata, summary chunks, embeddings through Spring AI, PostgreSQL + pgvector storage, TopK retrieval, rerank, chat sessions, source citation filtering, SSE streaming chat, and Docker/Nginx deployment.

The project intentionally avoids user accounts, permissions, multi-tenancy, PDF/Word import, crawlers, Redis, MQ, object storage, agent workflows, complex frontend workflows, and advanced multi-stage retrieval in v1.

See [`docs/dev/CURRENT_STATUS.md`](docs/dev/CURRENT_STATUS.md) for the latest development status and [`docs/dev/CHAT_SSE.md`](docs/dev/CHAT_SSE.md) for the SSE contract.
