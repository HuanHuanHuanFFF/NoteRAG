import { ApiError, deleteJson, getJson, patchJson, postJson } from './client';
import { postJsonSse, type SseEvent } from './sse';
import type {
  ChatMessageListResponse,
  ChatMessageResponse,
  ChatSessionItemResponse,
  ChatSessionListResponse,
  ChatStreamDeltaResponse,
  ChatStreamErrorResponse,
  ChatStreamMetaResponse,
  ImportTextRequest,
  ImportTextResponse,
  NoteDetailResponse,
  NoteListResponse,
  QueryRequest,
  QuerySourcesResponse,
  RenameChatSessionRequest,
  RetrievalSearchRequest,
  RetrievalSearchResponse,
  SendChatMessageRequest,
} from './types';

export const MAX_NOTE_IDS = 100;
const HEALTH_CHECK_TIMEOUT_MS = 5_000;

export interface ChatStreamHandlers {
  onMeta?: (meta: ChatStreamMetaResponse) => void;
  onDelta?: (delta: ChatStreamDeltaResponse) => void;
  onDone?: (response: ChatMessageResponse) => void;
  onError?: (error: ChatStreamErrorResponse) => void;
}

export function query(question: string, noteIds?: number[]) {
  return postJson<QuerySourcesResponse, QueryRequest>('/api/query', withNoteIds({ question }, noteIds));
}

export function searchRetrieval(question: string, topN?: number, noteIds?: number[]) {
  const body: RetrievalSearchRequest = { question };
  if (topN !== undefined) body.topN = topN;
  return postJson<RetrievalSearchResponse, RetrievalSearchRequest>(
    '/api/retrieval/search',
    withNoteIds(body, noteIds)
  );
}

export function sendFirstChatMessage(content: string, noteIds?: number[]) {
  return postJson<ChatMessageResponse, SendChatMessageRequest>(
    '/api/chat-sessions',
    withNoteIds({ content }, noteIds)
  );
}

export function sendChatMessage(sessionId: number, content: string, noteIds?: number[]) {
  return postJson<ChatMessageResponse, SendChatMessageRequest>(
    `/api/chat-sessions/${sessionId}/messages`,
    withNoteIds({ content }, noteIds)
  );
}

export function streamFirstChatMessage(
  content: string,
  noteIds: number[] | undefined,
  handlers: ChatStreamHandlers
) {
  return streamChatMessageToPath('/api/chat-sessions/stream', content, noteIds, handlers);
}

export function streamChatMessage(
  sessionId: number,
  content: string,
  noteIds: number[] | undefined,
  handlers: ChatStreamHandlers
) {
  return streamChatMessageToPath(
    `/api/chat-sessions/${sessionId}/messages/stream`,
    content,
    noteIds,
    handlers
  );
}

function streamChatMessageToPath(
  path: string,
  content: string,
  noteIds: number[] | undefined,
  handlers: ChatStreamHandlers
) {
  return postJsonSse(path, withNoteIds({ content }, noteIds), (event) => {
    dispatchChatStreamEvent(event, handlers);
  });
}

export function importText(request: ImportTextRequest) {
  return postJson<ImportTextResponse>('/api/note-imports/text', request);
}

export async function checkHealth() {
  const controller = new AbortController();
  const timeoutId = globalThis.setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);
  try {
    const response = await fetch('/api/health', {
      method: 'GET',
      headers: { Accept: 'text/plain, application/json' },
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new ApiError(`健康检查失败 (HTTP ${response.status})`, -1, response.status);
    }
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError('健康检查超时或网络不可用', -1, 0);
  } finally {
    globalThis.clearTimeout(timeoutId);
  }
}

export function listNotes() {
  return getJson<NoteListResponse>('/api/notes');
}

export function getNoteDetail(noteId: number) {
  return getJson<NoteDetailResponse>(`/api/notes/${noteId}`);
}

export function listChatSessions() {
  return getJson<ChatSessionListResponse>('/api/chat-sessions');
}

export function listChatMessages(sessionId: number) {
  return getJson<ChatMessageListResponse>(`/api/chat-sessions/${sessionId}/messages`);
}

export function deleteNote(noteId: number) {
  return deleteJson<void>(`/api/notes/${noteId}`);
}

export function deleteChatSession(sessionId: number) {
  return deleteJson<void>(`/api/chat-sessions/${sessionId}`);
}

export function renameChatSession(sessionId: number, title: string) {
  return patchJson<ChatSessionItemResponse, RenameChatSessionRequest>(
    `/api/chat-sessions/${sessionId}`,
    { title }
  );
}

function withNoteIds<T extends object>(body: T, noteIds?: number[]): T & { noteIds?: number[] } {
  if (noteIds == null || noteIds.length === 0) {
    return body;
  }
  if (noteIds.length > MAX_NOTE_IDS) {
    throw new ApiError(`noteIds 不能超过 ${MAX_NOTE_IDS} 个`, -1, 0);
  }
  return { ...body, noteIds };
}

function dispatchChatStreamEvent(event: SseEvent, handlers: ChatStreamHandlers) {
  switch (event.event) {
    case 'meta': {
      const meta = asChatStreamMeta(event.data);
      handlers.onMeta?.(meta);
      break;
    }
    case 'delta': {
      const delta = asChatStreamDelta(event.data);
      handlers.onDelta?.(delta);
      break;
    }
    case 'done': {
      const response = asChatMessageResponse(event.data);
      handlers.onDone?.(response);
      break;
    }
    case 'error': {
      const error = asChatStreamError(event.data);
      handlers.onError?.(error);
      throw new ApiError(error.message, error.code, 0);
    }
    default:
      throw new ApiError(`未知 Chat SSE 事件: ${event.event}`, -1, 0);
  }
}

function asChatStreamMeta(data: unknown): ChatStreamMetaResponse {
  const record = asRecord(data, 'meta');
  return {
    sessionId: readNumber(record, 'sessionId', 'meta'),
    sessionTitle: readString(record, 'sessionTitle', 'meta'),
    userMessageId: readNumber(record, 'userMessageId', 'meta'),
    assistantMessageId: readNumber(record, 'assistantMessageId', 'meta'),
  };
}

function asChatStreamDelta(data: unknown): ChatStreamDeltaResponse {
  const record = asRecord(data, 'delta');
  return {
    text: readString(record, 'text', 'delta'),
  };
}

function asChatStreamError(data: unknown): ChatStreamErrorResponse {
  const record = asRecord(data, 'error');
  return {
    code: readNumber(record, 'code', 'error'),
    message: readString(record, 'message', 'error'),
  };
}

function asChatMessageResponse(data: unknown): ChatMessageResponse {
  const record = asRecord(data, 'done');
  return {
    sessionId: readNumber(record, 'sessionId', 'done'),
    sessionTitle: readString(record, 'sessionTitle', 'done'),
    userMessageId: readNumber(record, 'userMessageId', 'done'),
    assistantMessageId: readNumber(record, 'assistantMessageId', 'done'),
    answer: readString(record, 'answer', 'done'),
    sources: Array.isArray(record.sources) ? record.sources : [],
  } as ChatMessageResponse;
}

function asRecord(data: unknown, event: string): Record<string, unknown> {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new ApiError(`Chat SSE ${event} 事件数据格式错误`, -1, 0);
  }
  return data as Record<string, unknown>;
}

function readNumber(record: Record<string, unknown>, key: string, event: string): number {
  const value = record[key];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ApiError(`Chat SSE ${event} 事件缺少数字字段 ${key}`, -1, 0);
  }
  return value;
}

function readString(record: Record<string, unknown>, key: string, event: string): string {
  const value = record[key];
  if (typeof value !== 'string') {
    throw new ApiError(`Chat SSE ${event} 事件缺少文本字段 ${key}`, -1, 0);
  }
  return value;
}
