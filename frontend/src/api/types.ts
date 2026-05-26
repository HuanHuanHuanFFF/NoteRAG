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
  noteIds?: number[];
}

export interface QueryRequest {
  question: string;
  noteIds?: number[];
}

export interface RetrievalSearchRequest {
  question: string;
  topN?: number;
  noteIds?: number[];
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

export interface NoteListResponse {
  notes: NoteListItem[];
}

export interface NoteDetailResponse {
  id: number;
  title: string;
  content: string;
  charCount: number;
  tokenCount: number;
  createdAt: string;
}

export type ChatMessageRole = 'USER' | 'ASSISTANT';
export type ChatMessageStatus = 'PENDING' | 'COMPLETED' | 'FAILED';
export type ChatSessionStatus = 'ACTIVE' | 'ARCHIVED';

export interface ChatSessionItemResponse {
  id: number;
  title: string;
  status: ChatSessionStatus;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string | null;
}

export interface ChatSessionListResponse {
  sessions: ChatSessionItemResponse[];
}

export interface RenameChatSessionRequest {
  title: string;
}

export interface ChatHistoryMessageResponse {
  id: number;
  role: ChatMessageRole;
  content: string;
  status: ChatMessageStatus;
  errorCode: string | null;
  createdAt: string;
  sources: SourceChunk[];
}

export interface ChatMessageListResponse {
  messages: ChatHistoryMessageResponse[];
}

export interface ChatTurn {
  id: number;
  userMessageId?: number;
  assistantMessageId?: number;
  question: string;
  answer: string;
  sources: SourceChunk[];
  loading: boolean;
  pending?: boolean;
  error?: string;
}

export interface ChatSession {
  id: string;
  backendSessionId?: number;
  title: string;
  turns: ChatTurn[];
  messagesLoaded?: boolean;
}
