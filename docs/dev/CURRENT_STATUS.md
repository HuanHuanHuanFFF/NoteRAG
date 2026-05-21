# NoteRAG 当前开发状态

更新时间：2026-05-21

这份文档用于换电脑后通过 Git 恢复开发上下文。它描述当前主线状态、开发数据库快照、恢复步骤和下一步工作重点。

## 快照说明

当前主线是轻量级个人 Markdown 技术笔记 RAG：

```text
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

编写本文档时，`master` 已同步到远端，最近关键提交包括：

```text
2a04dc6 fix(chat): 校验回答引用并补充调试日志
4e3a494 refactor(model): 分离数据库实体包
889931a feat(chat): 实现单会话消息主链路
9c031ca refactor(query): 改为返回重排后的 sources
```

当前新增的开发快照文件：

```text
docs/dev/CURRENT_STATUS.md
docs/dev/db/noterag-dev-db-20260521.dump
```

`.env` 不提交，需要手动拷贝到新电脑项目根目录。

## 已完成能力

- Markdown 文本导入：`POST /api/note-imports/text`
- 自定义 Markdown chunk：按标题 section 分组，保留 `headingPath`，估算 token，支持 overlap。
- Embedding：通过 Spring AI 接入 OpenAI-compatible 接口，当前本地配置使用 DashScope。
- 向量存储：PostgreSQL + pgvector，当前向量表为 `chunk_embeddings_1024`。
- 检索：基于 pgvector cosine 相似度返回 TopN。
- Rerank：接入 DashScope `qwen3-rerank`，默认从 retrieval top20 中重排到 top8。
- Query 调试接口：`POST /api/query`，当前只返回 rerank 后的 `sources`，不再生成 answer。
- Chat 主链路：已经支持同步单会话问答、历史消息入库、LLM 调用、引用解析和 sources 回写。

## 当前 API

```text
GET  /api/health
POST /api/note-imports/text
POST /api/retrieval/search
POST /api/query
POST /api/chat-sessions
POST /api/chat-sessions/{sessionId}/messages
```

接口定位：

- `/api/retrieval/search`：开发期检索调试接口。
- `/api/query`：开发期 query sources 调试接口，只做 retrieval + rerank。
- `/api/chat-sessions`：创建会话并发送第一条消息。
- `/api/chat-sessions/{sessionId}/messages`：在已有会话中继续发送消息。

## 当前包结构

- `controller/`：HTTP 边界，只接收请求并返回 DTO。
- `service/`：业务编排，核心是 `NoteImportService`、`QueryService`、`ChatService`。
- `chunk/`：Markdown 解析、section 分组、chunk 组装。
- `client/`：Embedding、Rerank、LLM 外部 API 适配。
- `entity/`：数据库实体，例如 `Note`、`NoteChunk`、`ChatSession`、`ChatMessage`。
- `model/`：后端内部模型，例如 `RetrievedChunk`、`ChatResult`。
- `dto/`：HTTP 请求和响应对象。
- `rag/`：prompt 构建、引用标记、引用解析。
- `mapper/`：MyBatis SQL 持久化。
- `config/`：Spring、数据库、模型客户端和功能开关配置。

## Chat 当前链路

`ChatService.sendMessage(sessionId, content)` 当前流程：

```text
创建或校验 chat_session
-> 写入 USER COMPLETED
-> 写入 ASSISTANT PENDING
-> 更新 session.last_message_at
-> 读取最近历史消息
-> QueryService.querySources(question)
-> ChatPromptBuilder.build(...)
-> LlmClient.chat(...)
-> AnswerCitationExtractor 解析引用
-> 过滤未引用 sources
-> ASSISTANT COMPLETED / FAILED
-> 写入 chat_message_sources
```

当前约束：

- pending assistant 不进入 prompt 历史。
- prompt 历史最多取最近 25 条。
- LLM 返回如果缺少引用标记，且不是固定无法回答句子，会返回 `LLM_RESULT_INVALID`。
- 当前日志会在 INFO 打印候选 `chunkIds`，DEBUG 会打印 LLM 原始 answer。

## 开发数据库快照

数据库快照在：

```text
docs/dev/db/noterag-dev-db-20260521.dump
```

它只是开发快照，用于换电脑后快速恢复当前 notes、chunks、embeddings、chat 测试数据。它不是正式 migration，也不是生产备份。

新电脑恢复步骤：

```powershell
# 1. clone/pull 仓库后，把本机拷贝的 .env 放到项目根目录

# 2. 启动 PostgreSQL
docker compose up -d db

# 3. 把开发数据库快照复制到容器
docker cp .\docs\dev\db\noterag-dev-db-20260521.dump noterag-db:/tmp/noterag-dev-db.dump

# 4. 恢复数据库
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /tmp/noterag-dev-db.dump'

# 5. 启动后端应用
docker compose up -d app

# 6. 查看日志
docker logs -f noterag-app
```

如果本地已有旧 volume，`pg_restore --clean --if-exists` 会尝试清理旧对象后恢复。若恢复过程因旧连接或旧表状态失败，最简单的开发处理方式是删除本地 NoteRAG Docker volume 后重新执行恢复。

## 本地配置说明

`.env` 不在 Git 中，需要单独拷贝。不要把真实 API key、数据库密码、模型 key 写进本文档或提交到 Git。

`.env` 中至少需要覆盖这些方向：

- PostgreSQL：数据库名、用户、密码、连接 URL。
- Embedding：Spring AI embedding model、API key、base URL、维度、batch size。
- Rerank：DashScope rerank key、模型、topK。
- LLM：chat model、API key、base URL、temperature、是否启用。
- Logging：需要排查 LLM 原始输出时设置 `LOGGING_LEVEL_COM_HUANF_NOTERAG=DEBUG`。

## 当前已知问题

最需要继续处理的是 chat 的引用协议稳定性。

已观察到：

- LLM 有时不会稳定输出私有引用标记。
- 后端会根据引用标记过滤 sources，所以标记缺失会导致 `sources` 为空。
- 当前已经加了校验：有候选 sources 但 LLM 回答没有引用标记时，返回 `LLM_RESULT_INVALID`，让前端重试。
- 这说明主链路能跑，但 prompt 约束和 citation marker 仍需要继续打磨。

暂时不是重点：

- 用户系统
- 权限认证
- 多租户
- PDF/Word/爬虫
- Redis/MQ
- Agent workflow
- 复杂前端
- 高级 rerank

## 下一步建议

优先顺序：

1. 继续调整 `ChatPromptBuilder`，让模型更稳定输出 citation marker。
2. 补充 `ChatPromptBuilderTests`，测试中打印或断言最终发给 LLM 的 prompt 结构。
3. 使用 `src/test/http/chat.http` 做真实接口测试，重点观察 answer 和 sources 是否一致。
4. 如果 prompt 约束仍不稳定，再考虑一次轻量 retry 或更强结构化输出协议。
5. Chat 同步接口稳定后，再做 SSE 流式返回。
6. SSE 后再做会话列表、历史消息查询、多会话切换。

## 常用命令

运行后端测试：

```powershell
.\mvnw.cmd -q "-Dtest=ChatServiceTests,ChatControllerIntegrationTests,ChatPromptBuilderTests" test
```

全量测试：

```powershell
.\mvnw.cmd test
```

Docker 启动：

```powershell
docker compose up -d --build
```

查看服务：

```powershell
docker compose ps
```

查看日志：

```powershell
docker logs -f noterag-app
```

## 接手时先读

建议 Codex 或其他 agent 接手时先读这些文件：

```text
AGENTS.md
docs/dev/CURRENT_STATUS.md
src/main/java/com/huanf/noterag/service/ChatService.java
src/main/java/com/huanf/noterag/rag/ChatPromptBuilder.java
src/main/java/com/huanf/noterag/rag/CitationMarkers.java
src/main/java/com/huanf/noterag/rag/AnswerCitationExtractor.java
src/main/java/com/huanf/noterag/service/QueryService.java
src/main/java/com/huanf/noterag/service/RetrievalService.java
src/main/java/com/huanf/noterag/service/RerankService.java
src/test/java/com/huanf/noterag/service/ChatServiceTests.java
src/test/java/com/huanf/noterag/prompt/ChatPromptBuilderTests.java
src/test/http/chat.http
```

给 Codex 的初始化提示词可以直接这样写：

```text
先阅读 AGENTS.md 和 docs/dev/CURRENT_STATUS.md，了解当前 NoteRAG 开发状态。
不要先改代码。
重点检查当前 chat 主链路、prompt 构建、citation marker 解析和 sources 过滤逻辑。
当前首要目标是稳定 LLM 输出引用标记，保证 /api/chat-sessions 返回的 answer 和 sources 一致。
不要引入用户系统、权限、多租户、PDF/Word、Redis/MQ、Agent workflow 或复杂前端。
如果要修改文件，先说明会改哪些文件和原因。
```

## 当前判断

当前项目已经跑通核心 RAG 闭环和同步单会话 chat 主链路。短期最值得投入的是让引用协议稳定下来；只有 `answer + sources` 足够可靠，后续 SSE、多会话和前端体验才有稳定基础。
