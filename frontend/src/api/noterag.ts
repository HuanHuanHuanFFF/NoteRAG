import { postJson } from './client';
import type {
  ImportTextRequest,
  ImportTextResponse,
  QueryResponse,
  RetrievalSearchResponse,
} from './types';

export function query(question: string) {
  return postJson<QueryResponse>('/api/query', { question });
}

export function searchRetrieval(question: string, topN?: number) {
  const body: { question: string; topN?: number } = { question };
  if (topN !== undefined) body.topN = topN;
  return postJson<RetrievalSearchResponse>('/api/retrieval/search', body);
}

export function importText(request: ImportTextRequest) {
  return postJson<ImportTextResponse>('/api/note-imports/text', request);
}
