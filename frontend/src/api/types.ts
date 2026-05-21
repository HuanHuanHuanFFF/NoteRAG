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

export interface SendChatMessageRequest {
  content: string;
}

export interface ChatMessageResponse {
  sessionId: number;
  sessionTitle: string;
  userMessageId: number;
  assistantMessageId: number;
  answer: string;
  sources: SourceChunk[];
}

export interface RetrievalSearchResponse {
  sources: SourceChunk[];
}

export interface QuerySourcesResponse {
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
  userMessageId?: number;
  assistantMessageId?: number;
  question: string;
  answer: string;
  sources: SourceChunk[];
  loading: boolean;
  error?: string;
}

export interface ChatSession {
  id: string;
  backendSessionId?: number;
  title: string;
  turns: ChatTurn[];
}
