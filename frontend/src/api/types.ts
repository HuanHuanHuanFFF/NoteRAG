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

export interface NoteListItem {
  id: number;
  title: string;
  chunkCount: number;
  charCount: number;
  tokenCount: number;
  createdAt: string;
}

export interface ChatTurn {
  id: number;
  question: string;
  answer: string;
  sources: SourceChunk[];
  loading: boolean;
  error?: string;
}

export interface ChatSession {
  id: string;
  title: string;
  turns: ChatTurn[];
  noteId: number | null;
}
