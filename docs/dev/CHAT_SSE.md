# Chat SSE 开发契约

本文只记录前后端联调必须一致的轻量契约，不作为完整 API 手册。

## Endpoints

```text
POST /api/chat-sessions/stream
POST /api/chat-sessions/{sessionId}/messages/stream
```

同步 chat 接口仍保留为 debug/兜底路径；正式前端发送优先走 SSE。

## Meta 事件

`event: meta` 表示后端已经创建或确认会话，并写入本轮 USER 消息和 ASSISTANT pending 消息。前端收到后可以回填后端 ID。

meta data 示例：

```json
{
  "sessionId": 1,
  "sessionTitle": "MySQL 的 MVCC",
  "userMessageId": 10,
  "assistantMessageId": 11
}
```

## Delta 事件

`event: delta` 的 data 固定为：

```json
{
  "text": "..."
}
```

后端不会发送 `null` delta；没有文本的模型流式 chunk 会被忽略。

前端只接受 JSON data，不兼容纯文本 delta 或 `{ "delta": "..." }`。

## Done 事件

`event: done` 表示后端已经完成 LLM 调用、citation 解析、sources 过滤和 assistant 消息回写。done data 使用完整的 `ChatMessageResponse` 结构。

前端应使用 done 中的完整 `answer` 和 `sources` 做最终覆盖，而不是只依赖已收到的 delta。

## Error 事件

`event: error` 是终止事件。前端收到后应立即停止流式播放，展示错误状态，不再等待 `done`。

error data 固定为：

```json
{
  "code": 50205,
  "message": "LLM 服务调用失败"
}
```

## 常见 CodeStatus

- `40001 INVALID_REQUEST`：请求参数错误。
- `40400 NOT_FOUND`：会话不存在或已归档。
- `50006 LLM_CONFIG_INVALID`：LLM 未启用或配置不可用。
- `50205 LLM_FAILED`：外部 LLM 服务调用失败。
- `50206 LLM_RESULT_INVALID`：LLM 返回内容不符合后端要求，例如非法 citation。
- `50000 INTERNAL_ERROR`：未预期的服务端错误。

## 当前前端实现

- SSE 接收使用 `@microsoft/fetch-event-source`。
- Markdown 流式渲染使用 `markstream-vue`。
- 高频 delta 会先进入 40ms buffer，再批量写入 `turn.answer`。
- streaming 阶段 citation 按钮禁用；done 后 sources 确认后才允许点击。
