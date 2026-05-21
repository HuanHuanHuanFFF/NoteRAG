import { postJson } from './client';
import type {
  ChatMessageResponse,
  ImportTextRequest,
  ImportTextResponse,
  QuerySourcesResponse,
  RetrievalSearchResponse,
  SendChatMessageRequest,
} from './types';

export function query(question: string) {
  return postJson<QuerySourcesResponse>('/api/query', { question });
}

export function searchRetrieval(question: string, topN?: number) {
  const body: { question: string; topN?: number } = { question };
  if (topN !== undefined) body.topN = topN;
  return postJson<RetrievalSearchResponse>('/api/retrieval/search', body);
}

export function sendFirstChatMessage(content: string) {
  return postJson<ChatMessageResponse, SendChatMessageRequest>('/api/chat-sessions', { content });
}

export function sendChatMessage(sessionId: number, content: string) {
  return postJson<ChatMessageResponse, SendChatMessageRequest>(
    `/api/chat-sessions/${sessionId}/messages`,
    { content }
  );
}

export function importText(request: ImportTextRequest) {
  return postJson<ImportTextResponse>('/api/note-imports/text', request);
}
