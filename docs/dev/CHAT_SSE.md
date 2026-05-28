# Chat SSE 开发契约

本文只记录前后端联调必须一致的轻量契约，不作为完整 API 手册。

## Delta 事件

`event: delta` 的 data 固定为：

```json
{
  "text": "..."
}
```

后端不会发送 `null` delta；没有文本的模型流式 chunk 会被忽略。

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
