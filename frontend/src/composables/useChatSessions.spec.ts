import { isReactive } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ChatSession } from '@/api/types';
import { useChatSessions } from './useChatSessions';

const listChatMessagesMock = vi.hoisted(() => vi.fn());

vi.mock('@/api/noterag', () => ({
  deleteChatSession: vi.fn(),
  listChatMessages: listChatMessagesMock,
  listChatSessions: vi.fn(),
  renameChatSession: vi.fn(),
}));

afterEach(() => {
  listChatMessagesMock.mockReset();
});

describe('useChatSessions', () => {
  it('returns reactive sessions and turns for streaming mutations', () => {
    const { createSessionInternal, appendPendingTurn } = useChatSessions({});

    const session = createSessionInternal();
    const turn = appendPendingTurn(session, 'question');

    expect(isReactive(session)).toBe(true);
    expect(isReactive(turn)).toBe(true);
  });

  it('does not replace session turns while a streamed turn is loading', async () => {
    const session: ChatSession = {
      id: 'backend-session-1',
      backendSessionId: 1,
      title: 'Chat',
      messagesLoaded: false,
      turns: [
        {
          id: 1,
          question: 'q',
          answer: '',
          sources: [],
          loading: true,
        },
      ],
    };

    const { loadMessagesForSession } = useChatSessions({ initialSessions: [session] });
    await loadMessagesForSession(session);

    expect(listChatMessagesMock).not.toHaveBeenCalled();
    expect(session.turns).toHaveLength(1);
    expect(session.turns[0]?.loading).toBe(true);
  });
});
