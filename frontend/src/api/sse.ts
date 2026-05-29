import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source';
import { ApiError } from './client';

const CLIENT_ERROR_CODE = -1;
const NETWORK_HTTP_STATUS = 0;
const TERMINAL_EVENTS = new Set(['done', 'error']);

export interface SseEvent {
  event: string;
  data: unknown;
}

export async function postJsonSse(
  path: string,
  body: unknown,
  onEvent: (event: SseEvent) => void
): Promise<void> {
  const requestBody = stringifyRequestBody(body);
  let terminalEventReceived = false;

  try {
    await fetchEventSource(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        accept: EventStreamContentType,
      },
      body: requestBody,
      fetch: globalThis.fetch,
      openWhenHidden: true,
      async onopen(response) {
        validateOpenResponse(response);
      },
      onmessage(message) {
        const eventName = message.event || 'message';
        if (TERMINAL_EVENTS.has(eventName)) {
          terminalEventReceived = true;
        }
        onEvent(parseSseMessage(eventName, message.data));
      },
      onclose() {
        if (!terminalEventReceived) {
          throw new ApiError('流式响应未正常完成', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
        }
      },
      onerror(error) {
        throw toApiError(error);
      },
    });
  } catch (error) {
    throw toApiError(error);
  }
}

function validateOpenResponse(response: Response) {
  if (!response.ok) {
    throw new ApiError(`流式请求失败 (HTTP ${response.status})`, CLIENT_ERROR_CODE, response.status);
  }

  const contentType = response.headers.get('content-type');
  if (contentType == null || !contentType.toLowerCase().includes(EventStreamContentType)) {
    throw new ApiError('服务端未返回 text/event-stream 响应', CLIENT_ERROR_CODE, response.status);
  }

  if (!response.body) {
    throw new ApiError('服务端未返回可读取的流式响应', CLIENT_ERROR_CODE, response.status);
  }
}

function parseSseMessage(event: string, rawData: string): SseEvent {
  if (!rawData) {
    throw new ApiError(`SSE ${event} 事件缺少 data`, CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }

  try {
    return { event, data: JSON.parse(rawData) as unknown };
  } catch {
    throw new ApiError(`SSE ${event} 事件不是合法 JSON`, CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }
}

function stringifyRequestBody(body: unknown): string {
  try {
    return JSON.stringify(body);
  } catch {
    throw new ApiError('请求参数序列化失败', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  return new ApiError('流式连接失败，请稍后重试', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
}
