# NoteRAG 开发状态与规划

更新时间：2026-05-31

## 当前定位

NoteRAG 是面向个人 Markdown 技术笔记的轻量级 RAG 问答系统，主链路保持为：

```text
Markdown import -> chunking -> token estimate -> embedding -> pgvector storage
-> TopK retrieval -> rerank -> prompt assembly -> LLM answer -> answer + sources
```

v1 继续坚持核心 RAG 闭环，不扩展用户系统、权限、多租户、PDF/Word、爬虫、Redis/MQ、对象存储、Agent workflow、复杂前端或高级多阶段检索。

## 当前基线

最近关键提交：

```text
4fbd866 feat(import): 优化摘要 chunk 向量文本
76b2c1f feat(import): 添加摘要 chunk
54321b7 feat(deploy): 接入 Nginx
bf572f0 优化前端流式输出
5c8b2f7 feat(frontend): 接入 markstream 渲染引用
62df2bc feat(frontend): 使用 fetch-event-source 接收 SSE
```

当前已完成：

- Markdown 导入：`POST /api/note-imports/text`，导入后保存 Note、切 chunk、生成 embedding。
- 自定义 chunk：按 Markdown 标题 section 分组，保留 `headingPath`，估算 token，支持 overlap。
- Summary chunk：导入时先用 LLM 为整篇 Note 生成全文摘要，落为 `SUMMARY` chunk，并参与 embedding/retrieval。
- Embedding：通过 Spring AI 接入 OpenAI-compatible embedding API，当前向量表为 `chunk_embeddings_1024`。
- Retrieval：PostgreSQL + pgvector cosine TopK，支持 `noteIds` 范围过滤。
- Rerank：接入 DashScope `qwen3-rerank`，默认 retrieval top20 -> rerank top8。
- Query 调试：`POST /api/query` 当前只返回 reranked sources，不生成 answer。
- Chat：支持会话、历史消息、note scope、prompt 构建、LLM 回答、citation 解析和 sources 回写。
- Chat SSE：支持 `meta/delta/done/error`，前端正式发送优先走 SSE，同步接口保留为 debug/兜底。
- Notes 与会话管理：支持列表、详情、重命名、软归档删除。
- 前端工作台：已接入 notes、历史会话、note scope、sources panel、Markdown 流式渲染和基础会话管理。
- Nginx：已接入 Docker 一键启动，Nginx 负责前端静态资源、后端 API 代理和 Chat SSE 转发；本地先按 HTTP 验证，线上 HTTPS 后续放到服务器和真实证书环境处理。

## 当前 API

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

接口定位：

- `/api/retrieval/search`：开发期检索调试接口。
- `/api/query`：开发期 reranked sources 调试接口，只做 retrieval + rerank。
- `/api/chat-sessions/stream` 和 `/api/chat-sessions/{sessionId}/messages/stream`：正式前端问答入口。
- 非 stream chat 接口保留为同步 debug/兜底。
- `DELETE` 目前都是软归档，归档后的 note/session 对列表、详情、发送、重命名不可见。

## 后端状态

核心包职责：

- `controller/`：HTTP 边界，只接收请求并返回 DTO。
- `service/`：业务编排，核心是 `NoteService`、`QueryService`、`ChatService`。
- `chunk/`：Markdown 解析、section 分组、chunk 组装。
- `client/`：Embedding、Rerank、LLM 外部 API 适配。
- `entity/`：数据库实体，例如 `Note`、`NoteChunk`、`ChatSession`、`ChatMessage`。
- `model/`：后端内部模型，例如 `RetrievedChunk`、`ChatResult`。
- `dto/`：HTTP 请求和响应对象。
- `rag/`：prompt 构建、citation marker、引用解析。
- `mapper/`：MyBatis SQL 持久化。
- `config/`：Spring、数据库、模型客户端和功能开关配置。

Chat 同步与流式共用同一套主链路：

```text
创建或校验 chat_session
-> 写入 USER COMPLETED
-> 写入 ASSISTANT PENDING
-> 读取最近历史消息
-> QueryService.querySources(question, noteIds)
-> ChatPromptBuilder.build(...)
-> LlmClient.chat(...) 或 LlmClient.streamChat(...)
-> AnswerCitationExtractor 解析引用
-> 过滤未引用 sources
-> ASSISTANT COMPLETED / FAILED
-> 写入 chat_message_sources
```

当前约束：

- pending assistant 不进入 prompt 历史。
- prompt 历史最多取最近 25 条。
- `noteIds` 最大 100 个，空列表等价于全库检索。
- LLM 不输出 citation marker 时允许成功返回，最终 `sources=[]`。
- LLM 输出非法 marker 或引用不存在的 sourceId 时，返回 `LLM_RESULT_INVALID`。
- INFO 日志只打印结构化信息，不打印 prompt、answer、chunk content 或密钥；DEBUG 可打印 LLM 原始 answer。

## 前端状态

前端主工作台已接入 Chat SSE：

```text
首条消息 -> POST /api/chat-sessions/stream
后续消息 -> POST /api/chat-sessions/{sessionId}/messages/stream
```

当前实现：

- `ChatSession.id` 仍是前端本地 string，后端 ID 保存在 `backendSessionId`。
- 页面初始化加载真实 notes、历史会话和首个会话历史消息。
- 切换历史会话时懒加载消息，并恢复 assistant sources。
- 单个会话发送中保持提交锁，避免并发写入同一会话。
- SSE 接收层使用 `@microsoft/fetch-event-source`。
- Markdown 流式渲染使用 `markstream-vue`。
- `delta` 先进入 40ms buffer，再批量写入 `turn.answer`，避免高频 token 触发 Markdown 重渲染。
- `useChatSessions` 返回插入响应式数组后的 session/turn，避免 raw object 更新绕过 Vue proxy。
- streaming 阶段 citation 按钮禁用，done 后才允许点击并打开 sources。
- done 后只对 `sources` 中存在的 chunkId 渲染引用按钮，避免点不开的无效引用。
- Sources panel 保持 280ms 延迟加载、引用高亮/展开、关闭动画结束后清理数据。
- stream debug 默认关闭，可在本地排查 delta、buffer、Markdown 渲染和滚动链路。

前端状态拆分：

- `useNotes`
- `useChatSessions`
- `useChatSubmit`
- `useDeltaFlushBuffer`
- `useSourcesPanel`
- `useResizableNotesPanel`
- `useHealthStatus`

## SSE 契约

详细契约见 `docs/dev/CHAT_SSE.md`。

当前约定：

- `meta`：后端已经创建或确认 session，并写入 USER/ASSISTANT pending，前端据此回填后端 ID。
- `delta`：固定 JSON `{ "text": "..." }`，不兼容纯文本或 `{ "delta": "..." }`。
- `done`：后端完成 citation 解析、sources 过滤和数据库回写后，返回完整 `ChatMessageResponse`。
- `error`：终止事件，前端收到后停止流式播放，不再等待 `done`。
- SSE 断开只记录日志，后端继续消费 LLM stream 并尝试完成落库。
- 当前不支持用户取消生成。

## 当前风险与观察点

- Citation marker 仍使用真实 `chunkId`。如果后续仍不稳定，优先改为 prompt 内局部 source 编号，再由后端映射回真实 chunkId。
- SSE 已修复一次“输出几行后卡住”的问题，真实前端长回答流式输出、自动滚动、Markdown 渲染、citation marker 和 sources panel 已完成阶段性回归。
- Summary chunk 已跑通，但还有较大优化空间：摘要 prompt、摘要长度、summary embedding text、summary chunk 是否需要单独权重或召回策略，都需要结合真实笔记和查询效果继续调。
- 失败重试已经具备基础能力；停止生成、重新生成、连续输入、局部 source 编号暂不作为近期任务。
- 数据库 init SQL 适合新库初始化，已有 Docker volume 不会自动迁移；上线前需要正式 migration 方案。
- 新环境没有内置开发数据，需要重新导入 Markdown 并生成 embedding。

## 下一步规划

当前优先级先保留以下方向：

1. 替换入库 token 统计：Note 原文和 chunk 的 token 统计改用 Spring AI 自带 token 计算，使用通用编码 `EncodingType.CL100K_BASE`，替代当前估算逻辑。
2. 优化 summary chunk：基于真实召回效果继续调整摘要 prompt、summary embedding text 和召回表现；当前已完成基础链路，不再把它当成未开始任务。
3. 做 LLM rewrite + 原始问题双路召回：对用户问题生成改写查询，同时保留原始问题检索，两路召回后合并去重，再进入 rerank。
4. 优化前端布局：给右侧 Sources panel 增加可拖拽宽度，交互和左侧 Notes panel 保持一致，并设置合理的最小/最大宽度。

暂不推进：

- Chat SSE 端到端测试文档。
- 停止生成、重新生成、连续输入。
- 局部 source 编号。
- 正式 migration 方案。
- 更细的日志 requestId/provider requestId 追踪。

## 回归清单

前端真实链路调整后，至少手工确认：

- 后端未启动时错误可见。
- 首条消息通过 SSE 返回 `meta/delta/done`，并回填 `backendSessionId`。
- 继续追问使用同一个后端 session。
- note scope 为空时全库检索，选中笔记时请求携带 `noteIds`。
- 长回答不会只显示几行后卡住。
- streaming 阶段 citation 按钮禁用，done 后恢复点击。
- 点击正文引用能打开 sources panel 并高亮来源。
- 点击“参考来源”能展示本轮全部 sources。
- LLM citation 异常时页面不会卡在 loading。
- 切换历史会话后能恢复消息和 sources。
- 删除当前会话后能切换到剩余会话或创建本地空会话。
- 导入成功后 Notes 列表刷新，且不自动打开详情。

## 常用命令

后端测试：

```powershell
.\mvnw.cmd test
```

前端测试和构建：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

Docker 启动：

```powershell
docker compose up -d --build
```

查看日志：

```powershell
docker logs -f noterag-app
```

## 接手时先读

```text
AGENTS.md
docs/dev/CURRENT_STATUS.md
docs/dev/CHAT_SSE.md
src/main/java/com/huanf/noterag/service/ChatService.java
src/main/java/com/huanf/noterag/rag/ChatPromptBuilder.java
src/main/java/com/huanf/noterag/rag/CitationMarkers.java
src/main/java/com/huanf/noterag/rag/AnswerCitationExtractor.java
frontend/src/composables/useChatSubmit.ts
frontend/src/composables/useChatSessions.ts
frontend/src/composables/useDeltaFlushBuffer.ts
frontend/src/components/ChatPanel.vue
frontend/src/components/MarkdownAnswer.vue
frontend/src/api/noterag.ts
```
