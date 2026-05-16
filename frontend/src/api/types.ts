export interface ApiBody<T> {
  code: number;
  message: string;
  data: T;
}

export interface SourceChunk {
  noteId: number;
  chunkId: number;
  title: string;
  headingPath: string | null;
  content: string;
  score: number | null;
}

export interface QueryResponse {
  answer: string;
  sources: SourceChunk[];
}

export interface RetrievalSearchResponse {
  sources: SourceChunk[];
}

export interface ImportTextRequest {
  title: string;
  content: string;
}

export interface ImportTextResponse {
  documentId: number;
  chunkCount: number;
  charCount: number;
  tokenCount: number;
}
