import { ApiError } from './client';

const CLIENT_ERROR_CODE = -1;
const NETWORK_HTTP_STATUS = 0;

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

  try {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: requestBody,
    });

    if (!response.ok) {
      throw new ApiError(`流式请求失败 (HTTP ${response.status})`, CLIENT_ERROR_CODE, response.status);
    }
    if (!response.body) {
      throw new ApiError('服务器未返回可读取的流式响应', CLIENT_ERROR_CODE, response.status);
    }

    await readSseStream(response.body, onEvent);
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError('流式连接失败，请稍后重试', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }
}

async function readSseStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void
) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = dispatchCompleteEvents(buffer, onEvent);
    }

    buffer += decoder.decode();
    const remaining = buffer.trim();
    if (remaining) {
      onEvent(parseSseBlock(remaining));
    }
  } finally {
    reader.releaseLock();
  }
}

function dispatchCompleteEvents(
  buffer: string,
  onEvent: (event: SseEvent) => void
) {
  let normalized = buffer.replace(/\r\n/g, '\n');
  let boundary = normalized.indexOf('\n\n');

  while (boundary >= 0) {
    const block = normalized.slice(0, boundary);
    normalized = normalized.slice(boundary + 2);
    if (block.trim()) {
      onEvent(parseSseBlock(block));
    }
    boundary = normalized.indexOf('\n\n');
  }

  return normalized;
}

function parseSseBlock(block: string): SseEvent {
  let event = 'message';
  const dataLines: string[] = [];

  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue;
    const separator = line.indexOf(':');
    const field = separator >= 0 ? line.slice(0, separator) : line;
    let value = separator >= 0 ? line.slice(separator + 1) : '';
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }

    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    throw new ApiError(`SSE ${event} 事件缺少 data`, CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }

  const rawData = dataLines.join('\n');
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
