import type { ChatSession, NoteListItem, SourceChunk } from '@/api/types';
import { baseMockNotes, mockNotes, mockSessions } from '@/utils/mockData';

interface CitationState {
  turnId: number;
  index: number | null;
}

interface ExpandedCitationState {
  turnId: number;
  indices: number[];
}

interface WorkspaceSeed {
  notes: NoteListItem[];
  sessions: ChatSession[];
  sourcesOpen: boolean;
  sourcesData: SourceChunk[];
  activeCitation: CitationState | null;
  expandedCitation: ExpandedCitationState | null;
}

const enableLayoutDemo = import.meta.env.VITE_ENABLE_LAYOUT_DEMO === 'true';

export function createWorkspaceSeed(): WorkspaceSeed {
  const sessions = enableLayoutDemo ? cloneSessions(mockSessions) : [];
  const initialTurnWithSources = sessions[0]?.turns.find((turn) => turn.sources.length > 0);

  return {
    notes: cloneNotes(enableLayoutDemo ? mockNotes : baseMockNotes),
    sessions,
    sourcesOpen: initialTurnWithSources != null,
    sourcesData: initialTurnWithSources?.sources ?? [],
    activeCitation: initialTurnWithSources
      ? { turnId: initialTurnWithSources.id, index: null }
      : null,
    expandedCitation: initialTurnWithSources
      ? { turnId: initialTurnWithSources.id, indices: [1, 2] }
      : null,
  };
}

function cloneNotes(notes: NoteListItem[]): NoteListItem[] {
  return notes.map((note) => ({ ...note }));
}

function cloneSessions(sessions: ChatSession[]): ChatSession[] {
  return sessions.map((session) => ({
    ...session,
    turns: session.turns.map((turn) => ({
      ...turn,
      sources: turn.sources.map((source) => ({ ...source })),
    })),
  }));
}
