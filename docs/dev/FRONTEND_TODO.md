# NoteRAG 前端后续事项

更新时间：2026-05-22

这份文件记录前端主 Q&A 接入后端同步 chat API 后，后续需要补齐但暂不在当前任务中处理的事项。它不是产品需求文档，只作为开发备忘。

## 当前状态

- 主 Q&A 已从 mock answer 切到同步 chat API。
- 首条消息调用 `POST /api/chat-sessions`。
- 已有后端会话 ID 后，继续消息调用 `POST /api/chat-sessions/{sessionId}/messages`。
- 前端本地 `ChatSession.id` 仍使用 string，只额外保存 `backendSessionId`。
- 当前只支持内存态会话；刷新页面后本地会话会丢失。
- 当前没有 SSE 流式返回。

## 请求超时与网络错误

当前 `api/client.ts` 没有统一请求超时。后端未启动、代理挂起或网络异常时，页面可能长时间停留在 loading 状态。

后续建议在 API client 层统一处理：

- 使用 `AbortController` 增加请求超时。
- 将 `fetch` 网络错误包装成 `ApiError`。
- 区分后端业务错误、HTTP 错误、响应 JSON 解析失败和网络不可达。
- 给主 Q&A、导入、检索调试复用同一套错误处理。

## 会话列表与历史恢复

当前前端会话只存在内存中，适合调试同步 chat 主链路，但不适合真实使用。

后续需要等后端提供接口后再接入：

- 会话列表。
- 会话详情或消息列表。
- 切换会话时恢复历史消息和 sources。
- 刷新页面后恢复最近会话。

## 三栏独立滚动

设计参考见 `docs/design/front.png`。图中左侧 Notes、中间 Q&A、右侧 Sources 是三个独立滚动区域。

当前页面更接近整体区域一起滚动：当 chat 内容变长时，左右两侧也需要跟随上下滑动，和设计目标不一致。

后续需要调整布局：

- 外层页面固定在视口高度内，避免主页面出现统一滚动条。
- Notes 列、Q&A 列、Sources 列分别拥有自己的滚动容器。
- 中间 Q&A 的消息列表独立滚动，底部输入框固定在中间列底部。
- 左侧导入按钮固定在 Notes 列底部，笔记列表单独滚动。
- 右侧 Sources 列只让 sources 列表滚动，标题和关闭按钮保持稳定。

预计改动范围是中等，不是纯 CSS 小改，但应集中在前端布局组件：

- `WorkspacePage.vue`：重新理顺外层 grid 高度、三列容器的 `min-h-0` 和 `overflow-hidden`。
- `ChatPanel.vue`：确认消息列表与输入框的 flex 高度链路，保证消息列表独立滚动。
- `NotesPanel.vue`：拆成笔记列表滚动区域和底部固定 import 按钮。
- `SourcesPanel.vue`：拆成固定 header 和独立滚动的 sources 列表。

主要风险是 flex/grid 高度链路。任意父级漏掉 `min-h-0` 或 `overflow-hidden`，都可能退回整页滚动。实现后需要实际测试长 chat、长 notes、长 sources 三种情况。

## 笔记范围选择

当前左侧笔记选择仍保留在 UI 中，但 chat 请求只发送 `content`，后端没有接收 note scope。

后续如果要支持限定笔记范围，需要同步设计：

- 后端 chat 请求是否接收 note IDs。
- retrieval 是否按 note scope 过滤。
- 前端如何展示“全库检索”和“限定笔记检索”的真实状态。
- Debug retrieval 与正式 chat 的 scope 行为是否保持一致。

在此之前，主 Q&A 顶部不应暗示已经限定到某些笔记。

## 发送并发策略

当前策略是单个前端会话中只允许一个请求进行中，避免首条消息未返回时创建多个后端 chat session。

后续如果要支持连续输入，需要单独设计：

- 前端消息队列。
- 请求顺序和失败恢复。
- 后端 session 创建中的临时状态。
- 用户取消、重试和重新生成回答。

不要在没有队列设计时简单放开并发发送。

## 真实链路测试清单

前端 build 只能验证类型和打包，不能覆盖真实 RAG 链路。每次调整 chat UI 或 API 对接后，建议至少手动测试：

- 后端未启动时的错误展示。
- 首条消息创建会话并返回 `answer + sources`。
- 继续追问时使用同一个 `backendSessionId`。
- LLM 返回 citation marker 后，前端引用按钮能按本次 sources 顺序编号。
- 点击引用按钮能打开 sources panel 并高亮对应来源。
- 点击“参考来源”能展示全部 sources。
- LLM citation 异常时，错误信息不会让页面卡在 loading。
- 切换前端本地会话后，sources panel 状态能正确关闭或切换。

## 暂不做

- SSE 流式回答。
- 多用户、认证、权限。
- 前端复杂工作流。
- 前端持久化缓存。
- 离线模式。
