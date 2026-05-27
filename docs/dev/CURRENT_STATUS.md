# NoteRAG 当前开发状态

更新时间：2026-05-27

这份文档用于换电脑后通过 Git 恢复开发上下文。它描述当前主线状态、恢复步骤和下一步工作重点。

## 快照说明

当前主线是轻量级个人 Markdown 技术笔记 RAG：

```text
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage -> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

编写本文档时，`master` 已同步到远端，最近关键提交包括：

```text
2c25a49 refactor(frontend): 拆分工作区页面状态逻辑
d84a378 fix(frontend): 修复会话输入框禁用状态
5d2d75b feat(frontend): 接入笔记和会话管理
93bae04 feat(chat): 支持笔记归档和会话管理
617fb98 feat(retrieval): 支持按笔记范围检索
f917b13 feat(chat): 增加会话历史和笔记读取接口
```

当前主要开发状态文件：

```text
docs/dev/CURRENT_STATUS.md
docs/dev/FRONTEND_TODO.md
```

`.env` 不提交，需要手动拷贝到新电脑项目根目录。

## 已完成能力

- Markdown 文本导入：`POST /api/note-imports/text`
- Note 读取接口：`GET /api/notes`、`GET /api/notes/{noteId}`，用于前端笔记列表和原文详情。
- 自定义 Markdown chunk：按标题 section 分组，保留 `headingPath`，估算 token，支持 overlap。
- Embedding：通过 Spring AI 接入 OpenAI-compatible 接口，当前本地配置使用 DashScope。
- 向量存储：PostgreSQL + pgvector，当前向量表为 `chunk_embeddings_1024`。
- 检索：基于 pgvector cosine 相似度返回 TopN。
- Rerank：接入 DashScope `qwen3-rerank`，默认从 retrieval top20 中重排到 top8。
- Query 调试接口：`POST /api/query`，当前只返回 rerank 后的 `sources`，不再生成 answer。
- Chat 主链路：已经支持同步单会话问答、历史消息入库、LLM 调用、引用解析和 sources 回写。
- Chat 读取接口：`GET /api/chat-sessions`、`GET /api/chat-sessions/{sessionId}/messages`，用于会话列表和历史消息恢复。
- Note scope：chat、query、retrieval 调试接口都支持 `noteIds`，后端在 SQL 检索阶段过滤，前端已把选中笔记传给 chat 请求。
- 软归档：Note 和 ChatSession 支持归档，归档后列表/detail/send/rename 等路径表现为不可见或 404。
- Chat 会话管理：支持会话列表、历史消息恢复、重命名和归档删除。
- 前端主 Q&A：已经从 mock answer 切到同步 chat API，并完成 notes、历史会话、sources、导入、删除/重命名等基础交互接入。
- 前端工作台重构：`WorkspacePage.vue` 已拆出 composables，降低页面状态耦合；最近一次 build/test 已通过。

## 当前 API

```text
GET  /api/health
POST /api/note-imports/text
GET  /api/notes
GET  /api/notes/{noteId}
DELETE /api/notes/{noteId}
POST /api/retrieval/search
POST /api/query
POST /api/chat-sessions
POST /api/chat-sessions/{sessionId}/messages
GET  /api/chat-sessions
GET  /api/chat-sessions/{sessionId}/messages
PATCH /api/chat-sessions/{sessionId}
DELETE /api/chat-sessions/{sessionId}
```

接口定位：

- `/api/notes`：前端笔记列表接口，返回基础统计和 chunk 数。
- `/api/notes/{noteId}`：前端笔记详情接口，返回原始 Markdown 内容。
- `DELETE /api/notes/{noteId}`：归档笔记，归档后前端列表和 detail 不再展示。
- `/api/retrieval/search`：开发期检索调试接口。
- `/api/query`：开发期 query sources 调试接口，只做 retrieval + rerank。
- `/api/chat-sessions`：创建会话并发送第一条消息。
- `/api/chat-sessions/{sessionId}/messages`：在已有会话中继续发送消息。
- `GET /api/chat-sessions`：读取历史会话列表。
- `GET /api/chat-sessions/{sessionId}/messages`：读取单个会话的历史消息和 assistant sources。
- `PATCH /api/chat-sessions/{sessionId}`：重命名 ACTIVE 会话。
- `DELETE /api/chat-sessions/{sessionId}`：归档 ACTIVE 会话。

## 当前包结构

- `controller/`：HTTP 边界，只接收请求并返回 DTO。
- `service/`：业务编排，核心是 `NoteService`、`QueryService`、`ChatService`。
- `chunk/`：Markdown 解析、section 分组、chunk 组装。
- `client/`：Embedding、Rerank、LLM 外部 API 适配。
- `entity/`：数据库实体，例如 `Note`、`NoteChunk`、`ChatSession`、`ChatMessage`。
- `model/`：后端内部模型，例如 `RetrievedChunk`、`ChatResult`。
- `dto/`：HTTP 请求和响应对象。
- `rag/`：prompt 构建、引用标记、引用解析。
- `mapper/`：MyBatis SQL 持久化。
- `config/`：Spring、数据库、模型客户端和功能开关配置。

## Chat 当前链路

`ChatService.sendMessage(sessionId, content, noteIds)` 当前流程：

```text
创建或校验 chat_session
-> 写入 USER COMPLETED
-> 写入 ASSISTANT PENDING
-> 更新 session.last_message_at
-> 读取最近历史消息
-> QueryService.querySources(question, noteIds)
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
- `noteIds` 最大 100 个，由前端和后端 retrieval 层共同限制；空列表等价于全库检索。

## 前端当前状态

前端主工作台已经接入同步 chat API：

```text
首条消息 -> POST /api/chat-sessions
后续消息 -> POST /api/chat-sessions/{sessionId}/messages
```

当前前端行为：

- `ChatSession.id` 仍是前端本地 string，后端 ID 存在 `backendSessionId`。
- 页面初始化会加载真实 notes、历史会话和首个会话历史消息。
- 切换历史会话时按需懒加载消息，assistant sources 会恢复到对应 turn。
- 单个前端会话中只允许一个发送请求进行中，避免首条消息未返回时创建多个后端 session。
- 成功后回填 `sessionTitle`、`userMessageId`、`assistantMessageId`、`answer`、`sources`。
- 失败时只在当前 turn 上展示错误；首条消息只有成功返回后才写入 `backendSessionId`。
- 导入弹窗已支持选择 `.md/.markdown` 文件，前端读取文件内容并用文件名生成可编辑标题，仍复用 `POST /api/note-imports/text`。
- 左侧 Notes 已接入后端列表和详情接口，选中笔记会作为 `noteIds` 传给首条和后续 chat 请求。
- Note 详情、Note 归档、ChatSession 重命名/归档已经接入后端。
- Sources panel 继续保持 280ms 延迟加载、引用高亮/展开、关闭动画结束后清理数据。
- `WorkspacePage.vue` 的 notes、sessions、submit、sources、health、resize 逻辑已拆入 `frontend/src/composables/*`。
- API client 已统一处理请求超时、fetch 网络错误、HTTP 错误、业务错误和 JSON 解析错误。

## 新电脑恢复步骤

```powershell
# 1. clone/pull 仓库后，把本机拷贝的 .env 放到项目根目录

# 2. 启动数据库和应用
docker compose up -d --build

# 3. 查看日志
docker logs -f noterag-app
```

当前仓库不再保留开发数据库 dump。旧的 `docs/dev/db/noterag-dev-db-20260521.dump` 已删除，不要再依赖它恢复 notes、chunks、embeddings 或 chat 测试数据。新环境需要重新导入 Markdown，重新生成 embedding。

如果本地已有旧 volume，init SQL 不会自动迁移已有表结构。开发环境最简单的处理方式是删除本地 NoteRAG Docker volume 后重新启动，再重新导入测试笔记。

## 本地配置说明

`.env` 不在 Git 中，需要单独拷贝。不要把真实 API key、数据库密码、模型 key 写进本文档或提交到 Git。

`.env` 中至少需要覆盖这些方向：

- PostgreSQL：数据库名、用户、密码、连接 URL。
- Embedding：Spring AI embedding model、API key、base URL、维度、batch size。
- Rerank：DashScope rerank key、模型、topK。
- LLM：chat model、API key、base URL、temperature、是否启用。
- Logging：需要排查 LLM 原始输出时设置 `LOGGING_LEVEL_COM_HUANF_NOTERAG=DEBUG`。

## 当前已知问题

最需要继续观察的是 chat 的引用协议稳定性、SSE 改造后的流式状态，以及真实链路下的失败恢复体验。

已观察到：

- 调整后的 `ChatPromptBuilder` 明显改善了 citation marker 输出，但仍需要继续用真实前端交互观察。
- 后端会根据 citation marker 过滤 sources；如果 LLM 没有标记，允许返回 answer，但 `sources=[]`。
- 当前 citation marker 仍使用真实 `chunkId`，后续如果稳定性仍不够，可考虑改成 prompt 内局部 source 编号，再在后端映射回真实 chunkId。
- 当前 chat 仍是同步 HTTP 返回，长回答需要等待完整 LLM 响应；下一步是 SSE 流式返回。
- 失败重试、取消生成、连续输入和重新生成回答还没有完整产品化设计。
- 数据库 init SQL 适合新库初始化，已有 Docker volume 不会自动迁移；上线前仍需要正式 migration 方案。

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

1. 做 Chat SSE 流式返回，保留现有同步接口作为 debug/兜底路径。
2. SSE 下补齐前端增量渲染、完成回填、错误事件、请求取消和失败状态展示。
3. 用真实前端回归 `answer + sources`、citation marker、sources panel 和 note scope。
4. 如果 citation 仍不稳定，优先考虑局部 source 编号方案，而不是继续堆长提示词。
5. SSE 稳定后，再设计连续输入、停止生成、重新生成回答和更完整的消息级操作。

入库优化 TODO：

- Note 原文入库时的 token 统计改用 Spring AI 自带 token 计算，编码使用 `EncodingType.CL100K_BASE`，替代当前估算逻辑。
- 增加一个全局视角 chunk：把 Note 标题和原文交给 LLM 总结，将总结结果作为独立 chunk 做 embedding 并入库，用于补充全文级召回；具体边界和生成策略等实现前再确认。

## 常用命令

运行后端测试：

```powershell
.\mvnw.cmd -q "-Dtest=ChatServiceTests,ChatControllerIntegrationTests,NoteServiceIntegrationTests,NoteControllerIntegrationTests" test
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
frontend/src/composables/useChatSessions.ts
frontend/src/composables/useChatSubmit.ts
frontend/src/composables/useNotes.ts
frontend/src/composables/useSourcesPanel.ts
frontend/src/components/ChatPanel.vue
frontend/src/api/noterag.ts
src/test/http/chat.http
```

给 Codex 的初始化提示词可以直接这样写：

```text
先阅读 AGENTS.md 和 docs/dev/CURRENT_STATUS.md，了解当前 NoteRAG 开发状态。
不要先改代码。
重点检查当前 chat 主链路、prompt 构建、citation marker 解析、sources 过滤逻辑和前端同步 chat API 对接。
当前首要目标是设计并实现 Chat SSE 流式返回，同时保留同步接口作为 debug/兜底路径。
不要引入用户系统、权限、多租户、PDF/Word、Redis/MQ、Agent workflow 或复杂前端。
如果要修改文件，先说明会改哪些文件和原因。
```

## 当前判断

当前项目已经跑通核心 RAG 闭环、同步单会话 chat 主链路，并且前端主 Q&A 已接入 notes、历史会话、note scope、sources 和基础会话管理。短期最值得投入的是 Chat SSE 流式返回，以及 SSE 下的错误、取消、完成回填和 sources 一致性。
