import { ApiError, deleteJson, getJson, patchJson, postJson } from './client';
import type {
  ChatMessageListResponse,
  ChatMessageResponse,
  ChatSessionItemResponse,
  ChatSessionListResponse,
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
