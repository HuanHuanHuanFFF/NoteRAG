# NoteRAG 前端后续事项

更新时间：2026-05-27

这份文件记录前端主工作台当前状态和后续事项。它不是产品需求文档，只作为开发备忘；具体任务仍以当轮讨论和 AGENTS.md 为准。

## 当前状态

- 主 Q&A 已从 mock answer 切到后端同步 chat API。
- 首条消息调用 `POST /api/chat-sessions`。
- 已有后端会话 ID 后，继续消息调用 `POST /api/chat-sessions/{sessionId}/messages`。
- 前端本地 `ChatSession.id` 仍使用 string，只额外保存后端 number 类型的 `backendSessionId`。
- 页面初始化会加载真实 notes、历史会话和首个会话的历史消息。
- 切换历史会话时会懒加载消息，并恢复 assistant sources。
- 左侧 Notes 已接入后端列表、详情和归档接口。
- Note scope 已接入 chat 请求；选中笔记会作为 `noteIds` 传给首条和后续消息。
- ChatSession 重命名和归档删除已接入后端。
- 当前没有 SSE 流式返回，chat 仍等待完整 answer 后一次性回填。
- API client 已统一处理请求超时、fetch 网络错误、HTTP 错误、业务错误和 JSON 解析错误。
- `WorkspacePage.vue` 已拆出 `frontend/src/composables/*`，页面主要负责组件编排和 glue code。
- 仓库不再保留开发数据库 dump；前端真实链路测试需要本地重新导入 Markdown 数据。

## 已完成前端能力

### 请求与错误处理

- 使用 `AbortController` 增加请求超时。
- 将 `fetch` 网络错误包装成 `ApiError`。
- 区分后端业务错误、HTTP 错误、响应 JSON 解析失败和网络不可达。
- 主 Q&A、导入、检索调试和会话/笔记管理复用同一套错误处理。
- 请求体无法 JSON 序列化时，在调用 `fetch` 前返回明确的 `ApiError`。

### Notes 与导入

- Notes 列表使用 `GET /api/notes`。
- Note 详情弹窗使用 `GET /api/notes/{noteId}`，支持 Markdown 渲染。
- Note 删除使用 `DELETE /api/notes/{noteId}`，后端实际为软归档。
- 导入弹窗支持选择 `.md/.markdown` 文件，前端读取文件内容并用文件名生成可编辑标题。
- 导入成功后只刷新 Notes 列表，不自动打开 Note 详情。
- 导入后标题和内容暂不可修改，导入弹窗中已有确认提示。

### Chat 会话

- 会话列表使用 `GET /api/chat-sessions`。
- 历史消息使用 `GET /api/chat-sessions/{sessionId}/messages`。
- 会话重命名使用 `PATCH /api/chat-sessions/{sessionId}`。
- 会话删除使用 `DELETE /api/chat-sessions/{sessionId}`，后端实际为软归档。
- 新建本地会话时不会提前写 `backendSessionId`；首条消息成功返回后才回填后端 ID。
- 切换或删除当前会话时会关闭 Sources panel。

### Sources 与引用

- LLM citation marker 由前端渲染成当前 sources 顺序编号。
- 点击正文引用会打开 Sources panel，并高亮/展开对应来源。
- 点击“参考来源”可以展示本轮全部 sources。
- Sources panel 保持 280ms 延迟加载，关闭动画结束后再清理数据。
- Sources、Q&A、Notes 三栏保持独立滚动。

### 前端重构

- `WorkspacePage.vue` 已拆出：
  - `useNotes`
  - `useChatSessions`
  - `useChatSubmit`
  - `useSourcesPanel`
  - `useResizableNotesPanel`
  - `useHealthStatus`
- 本次重构目标是降低页面耦合度，不改变 UI 和交互行为。
- 最近一次验证：`npm.cmd run build` 和 `npm.cmd run test` 均通过。

## 仍需重点验证

前端 build 只能验证类型和打包，不能覆盖真实 RAG 链路。调整 chat、sources 或 API 对接后，建议至少手动测试：

- 后端未启动时的错误展示。
- 首条消息创建会话并返回 `answer + sources`。
- 继续追问时使用同一个 `backendSessionId`。
- note scope 为空时走全库检索，选中笔记时请求携带 `noteIds`。
- LLM 返回 citation marker 后，前端引用按钮能按本次 sources 顺序编号。
- 点击引用按钮能打开 sources panel 并高亮对应来源。
- 点击“参考来源”能展示全部 sources。
- LLM citation 异常时，错误信息不会让页面卡在 loading。
- 切换历史会话后能恢复消息和 sources。
- 删除当前会话后能切换到剩余会话或创建本地空会话。
- 导入成功后统计信息保留，Notes 列表刷新且不自动打开详情。
- 新环境没有内置开发数据时，先通过导入弹窗导入一份 Markdown，再测试 chat、sources 和 note scope。

## 下一步：SSE 流式回答

当前 chat 是同步 HTTP：前端等待完整 answer 返回后再展示。下一步建议改造为 SSE：

- 后端新增流式 chat 接口，保留当前同步接口作为 debug/兜底路径。
- 前端用 `fetch + ReadableStream + TextDecoder` 解析 `text/event-stream`，不要用原生 `EventSource`，因为当前请求需要 POST JSON body。
- 流式事件建议包含：
  - `meta`：回填 `sessionId`、`sessionTitle`、`userMessageId`、`assistantMessageId`
  - `status`：展示后端阶段，例如 retrieving / generating
  - `delta`：追加 answer 文本
  - `done`：用完整 answer 和 sources 做最终覆盖
  - `error`：展示失败并结束 loading
- 首条消息建议在收到 `meta` 后写入 `backendSessionId`，因为后端此时已经创建真实 session 和 pending assistant。
- SSE 完成前暂不放开同一会话并发发送。

## SSE 后再设计

- 停止生成：前端 abort 请求，后端如何标记 assistant 状态需要单独设计。
- 重新生成：应是消息级操作，不要简单覆盖旧 assistant。
- 连续输入：需要前端队列和后端顺序约束，不能直接取消提交锁。
- 更完整的失败重试：区分网络失败、LLM 失败、citation 非法、用户取消。
- 局部 source 编号：如果 citation 仍不稳定，考虑 prompt 内使用局部编号，再由后端映射回真实 chunkId。

## 暂不做

- 多用户、认证、权限。
- 前端复杂工作流。
- 前端持久化离线缓存。
- 离线模式。
- 在没有队列设计前放开同一会话并发发送。
