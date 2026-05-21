# NoteRAG 当前开发状态

更新时间：2026-05-22

这份文档用于换电脑后通过 Git 恢复开发上下文。它描述当前主线状态、开发数据库快照、恢复步骤和下一步工作重点。

## 快照说明

当前主线是轻量级个人 Markdown 技术笔记 RAG：

```text
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

编写本文档时，`master` 已同步到远端，最近关键提交包括：

```text
8560d88 docs(dev): 记录前端后续事项
971aaee feat(frontend): 接入同步聊天接口
033bed5 fix(prompt): 优化聊天引用提示词
d03d851 fix(chat): 允许回答不引用来源
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
- 前端主 Q&A：已经从 mock answer 切到同步 chat API，可用来调试真实 LLM answer 和 citation sources。

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
- LLM 不输出 citation marker 时允许成功返回，最终 `sources=[]`。
- LLM 输出非法 marker 或引用不存在的 sourceId 时，返回 `LLM_RESULT_INVALID`。
- 当前日志会在 INFO 打印候选 `chunkIds`，DEBUG 会打印 LLM 原始 answer。

## 前端当前状态

前端主工作台已经接入同步 chat API：

```text
首条消息 -> POST /api/chat-sessions
后续消息 -> POST /api/chat-sessions/{sessionId}/messages
```

当前前端行为：

- `ChatSession.id` 仍是前端本地 string，后端 ID 存在 `backendSessionId`。
- 单个前端会话中只允许一个请求进行中，避免首条消息未返回时创建多个后端 session。
- 成功后回填 `sessionTitle`、`userMessageId`、`assistantMessageId`、`answer`、`sources`。
- 失败时只在当前 turn 上展示错误，不提前写入后端 session id。
- 左侧 Notes 仍是 mock notes，chat 请求暂时不携带 note scope，顶部只显示跨全部笔记检索。
- 刷新页面后前端本地会话会丢失，因为还没有会话列表和历史消息接口。

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

最需要继续观察的是 chat 的引用协议稳定性和前端真实链路体验。

已观察到：

- 调整后的 `ChatPromptBuilder` 明显改善了 citation marker 输出，但仍需要继续用真实前端交互观察。
- 后端会根据 citation marker 过滤 sources；如果 LLM 没有标记，允许返回 answer，但 `sources=[]`。
- 当前 citation marker 仍使用真实 `chunkId`，后续如果稳定性仍不够，可考虑改成 prompt 内局部 source 编号，再在后端映射回真实 chunkId。
- 前端 API client 还没有统一请求超时；后端未启动或网络异常时，可能需要等浏览器 fetch 自己失败。

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

1. 用前端主 Q&A 真实测试同步 chat，重点观察 answer、citation marker、sources panel 是否一致。
2. 如果 citation 仍不稳定，优先考虑局部 source 编号方案，而不是继续堆长提示词。
3. 补 API client 请求超时和网络错误包装，避免页面长时间 loading。
4. Chat 同步接口稳定后，再做 SSE 流式返回。
5. SSE 后再做会话列表、历史消息查询、多会话切换。

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
docs/dev/FRONTEND_TODO.md
src/main/java/com/huanf/noterag/service/ChatService.java
src/main/java/com/huanf/noterag/rag/ChatPromptBuilder.java
src/main/java/com/huanf/noterag/rag/CitationMarkers.java
src/main/java/com/huanf/noterag/rag/AnswerCitationExtractor.java
src/main/java/com/huanf/noterag/service/QueryService.java
src/main/java/com/huanf/noterag/service/RetrievalService.java
src/main/java/com/huanf/noterag/service/RerankService.java
src/test/java/com/huanf/noterag/service/ChatServiceTests.java
src/test/java/com/huanf/noterag/prompt/ChatPromptBuilderTests.java
frontend/src/views/WorkspacePage.vue
frontend/src/components/ChatPanel.vue
frontend/src/api/noterag.ts
src/test/http/chat.http
```

给 Codex 的初始化提示词可以直接这样写：

```text
先阅读 AGENTS.md 和 docs/dev/CURRENT_STATUS.md，了解当前 NoteRAG 开发状态。
不要先改代码。
重点检查当前 chat 主链路、prompt 构建、citation marker 解析、sources 过滤逻辑和前端同步 chat API 对接。
当前首要目标是用前端真实测试 /api/chat-sessions 返回的 answer 和 sources 是否一致。
不要引入用户系统、权限、多租户、PDF/Word、Redis/MQ、Agent workflow 或复杂前端。
如果要修改文件，先说明会改哪些文件和原因。
```

## 当前判断

当前项目已经跑通核心 RAG 闭环、同步单会话 chat 主链路，并且前端主 Q&A 已接入同步 chat API。短期最值得投入的是用真实前端交互验证 `answer + sources` 是否稳定；这个基础可靠后，再推进 SSE、会话列表和历史恢复。
