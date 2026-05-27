import { computed, ref } from 'vue';
import { ApiError } from '@/api/client';
import {
  deleteChatSession,
  listChatMessages,
  listChatSessions,
  renameChatSession,
} from '@/api/noterag';
import type {
  ChatHistoryMessageResponse,
  ChatMessageResponse,
  ChatSession,
  ChatSessionItemResponse,
  ChatStreamMetaResponse,
  ChatTurn,
} from '@/api/types';

interface UseChatSessionsOptions {
  initialSessions?: ChatSession[];
}

interface RemoveSessionResult {
  removedId: string;
  wasActive: boolean;
}

export function useChatSessions(options: UseChatSessionsOptions) {
  const sessions = ref<ChatSession[]>(options.initialSessions ?? []);
  const activeSessionId = ref<string>('');
  const sessionsLoading = ref(false);
  const workspaceError = ref<string | null>(null);
  const sessionDeleteTarget = ref<ChatSession | null>(null);
  const sessionDeleteBusy = ref(false);
  const sessionDeleteError = ref<string | null>(null);
  const sessionActionBusyId = ref<string | null>(null);
  const sessionActionError = ref<string | null>(null);
  const loadingMessageSessionIds = ref<Set<number>>(new Set());

  let nextSessionIdx = 1;
  let nextTurnId = 0;

  const activeSession = computed<ChatSession | null>(
    () => sessions.value.find((s) => s.id === activeSessionId.value) ?? null
  );

  function initializeFromSeed() {
    initializeSessionCounters();
    activeSessionId.value = sessions.value[0]?.id ?? createSessionInternal().id;
  }

  async function loadSessions() {
    sessionsLoading.value = true;
    workspaceError.value = null;
    try {
      const response = await listChatSessions();
      const backendSessions = sortBackendSessions(response.sessions ?? []);
      sessions.value = backendSessions.map(toFrontendSession);
      initializeSessionCounters();

      const firstSession = sessions.value[0];
      if (firstSession) {
        activeSessionId.value = firstSession.id;
        await loadMessagesForSession(firstSession);
      } else {
        activeSessionId.value = createSessionInternal().id;
      }
    } catch (e) {
      workspaceError.value = e instanceof ApiError ? e.message : '历史会话加载失败';
      if (sessions.value.length === 0) {
        activeSessionId.value = createSessionInternal().id;
      }
    } finally {
      sessionsLoading.value = false;
    }
  }

  function sortBackendSessions(items: ChatSessionItemResponse[]) {
    return [...items].sort((a, b) => sessionTimeValue(b) - sessionTimeValue(a));
  }

  function sessionTimeValue(session: ChatSessionItemResponse) {
    return Date.parse(session.lastMessageAt ?? session.updatedAt ?? session.createdAt) || 0;
  }

  function toFrontendSession(session: ChatSessionItemResponse): ChatSession {
    return {
      id: backendSessionLocalId(session.id),
      backendSessionId: session.id,
      title: session.title?.trim() || `会话 ${session.id}`,
      turns: [],
      messagesLoaded: false,
    };
  }

  function backendSessionLocalId(sessionId: number) {
    return `backend-session-${sessionId}`;
  }

  async function loadMessagesForSession(session: ChatSession) {
    const backendSessionId = session.backendSessionId;
    if (
      backendSessionId == null ||
      session.messagesLoaded ||
      hasLoadingTurn(session) ||
      loadingMessageSessionIds.value.has(backendSessionId)
    ) {
      return;
    }

    setSessionMessagesLoading(backendSessionId, true);
    const localSessionId = session.id;
    const originalTurnCount = session.turns.length;
    if (activeSessionId.value === localSessionId) {
      workspaceError.value = null;
    }

    try {
      const response = await listChatMessages(backendSessionId);
      if (session.turns.length !== originalTurnCount) {
        return;
      }
      session.turns = toHistoryTurns(response.messages ?? []);
      session.messagesLoaded = true;
    } catch (e) {
      if (activeSessionId.value === localSessionId) {
        workspaceError.value = e instanceof ApiError ? e.message : '历史消息加载失败';
      }
    } finally {
      setSessionMessagesLoading(backendSessionId, false);
    }
  }

  function setSessionMessagesLoading(sessionId: number, loading: boolean) {
    const next = new Set(loadingMessageSessionIds.value);
    if (loading) {
      next.add(sessionId);
    } else {
      next.delete(sessionId);
    }
    loadingMessageSessionIds.value = next;
  }

  function hasLoadingTurn(session: ChatSession) {
    return session.turns.some((turn) => turn.loading);
  }

  function toHistoryTurns(messages: ChatHistoryMessageResponse[]): ChatTurn[] {
    const turns: ChatTurn[] = [];
    for (const message of [...messages].sort(compareHistoryMessages)) {
      if (message.role === 'USER') {
        turns.push({
          id: ++nextTurnId,
          userMessageId: message.id,
          question: message.content ?? '',
          answer: '',
          sources: [],
          loading: false,
        });
        continue;
      }

      const turn = findAttachableTurn(turns);
      turn.assistantMessageId = message.id;
      if (message.status === 'COMPLETED') {
        turn.answer = message.content ?? '';
        turn.sources = message.sources ?? [];
        turn.loading = false;
        turn.pending = false;
        turn.error = undefined;
      } else if (message.status === 'FAILED') {
        turn.answer = '';
        turn.sources = [];
        turn.loading = false;
        turn.pending = false;
        turn.error = message.errorCode || '回答生成失败';
      } else {
        turn.answer = '';
        turn.sources = [];
        turn.loading = false;
        turn.pending = true;
        turn.error = undefined;
      }
    }
    return turns;
  }

  function findAttachableTurn(turns: ChatTurn[]) {
    for (let i = turns.length - 1; i >= 0; i--) {
      if (turns[i].assistantMessageId == null) {
        return turns[i];
      }
    }

    const orphan: ChatTurn = {
      id: ++nextTurnId,
      question: '',
      answer: '',
      sources: [],
      loading: false,
    };
    turns.push(orphan);
    return orphan;
  }

  function messageTimeValue(message: ChatHistoryMessageResponse) {
    return Date.parse(message.createdAt) || 0;
  }

  function compareHistoryMessages(a: ChatHistoryMessageResponse, b: ChatHistoryMessageResponse) {
    return messageTimeValue(a) - messageTimeValue(b) || a.id - b.id;
  }

  function handleCreateSession() {
    const session = createSessionInternal();
    activeSessionId.value = session.id;
  }

  function handleSwitchSession(id: string): boolean {
    const session = sessions.value.find((item) => item.id === id);
    if (!session) return false;
    if (activeSessionId.value !== id) {
      activeSessionId.value = id;
      void loadMessagesForSession(session);
      return true;
    }
    void loadMessagesForSession(session);
    return false;
  }

  async function handleRenameSession(id: string, title: string) {
    const session = sessions.value.find((item) => item.id === id);
    const normalizedTitle = title.trim();
    if (!session || !normalizedTitle) return;
    sessionActionError.value = null;

    if (session.backendSessionId == null) {
      session.title = normalizedTitle;
      return;
    }

    sessionActionBusyId.value = id;
    try {
      const response = await renameChatSession(session.backendSessionId, normalizedTitle);
      session.title = response.title?.trim() || normalizedTitle;
    } catch (e) {
      const message = e instanceof ApiError ? e.message : '会话重命名失败';
      sessionActionError.value = message;
      workspaceError.value = message;
    } finally {
      if (sessionActionBusyId.value === id) {
        sessionActionBusyId.value = null;
      }
    }
  }

  function handleDeleteSessionRequest(id: string) {
    const session = sessions.value.find((item) => item.id === id);
    if (!session) return;
    sessionDeleteTarget.value = session;
    sessionDeleteError.value = null;
  }

  async function confirmDeleteSession(): Promise<RemoveSessionResult | null> {
    const target = sessionDeleteTarget.value;
    if (!target) return null;

    sessionDeleteBusy.value = true;
    sessionDeleteError.value = null;
    sessionActionError.value = null;
    sessionActionBusyId.value = target.id;

    try {
      if (target.backendSessionId != null) {
        await deleteChatSession(target.backendSessionId);
      }
      const result = removeSessionLocally(target.id);
      sessionDeleteTarget.value = null;
      return result;
    } catch (e) {
      if (e instanceof ApiError && e.httpStatus === 404) {
        const result = removeSessionLocally(target.id);
        workspaceError.value = '会话已不存在，已从列表移除';
        sessionDeleteTarget.value = null;
        return result;
      } else {
        sessionDeleteError.value = e instanceof ApiError ? e.message : '删除会话失败';
      }
    } finally {
      sessionDeleteBusy.value = false;
      if (sessionActionBusyId.value === target.id) {
        sessionActionBusyId.value = null;
      }
    }
    return null;
  }

  function removeSessionLocally(id: string): RemoveSessionResult {
    const wasActive = activeSessionId.value === id;
    sessions.value = sessions.value.filter((session) => session.id !== id);

    if (wasActive) {
      const nextSession = sessions.value[0] ?? createSessionInternal();
      activeSessionId.value = nextSession.id;
      void loadMessagesForSession(nextSession);
    }

    return { removedId: id, wasActive };
  }

  function createSessionInternal(): ChatSession {
    const session: ChatSession = {
      id: `local-session-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      title: `新会话 ${nextSessionIdx++}`,
      turns: [],
    };
    sessions.value.unshift(session);
    return session;
  }

  function initializeSessionCounters() {
    nextSessionIdx = sessions.value.length + 1;
    nextTurnId = Math.max(
      0,
      ...sessions.value.flatMap((session) => session.turns.map((turn) => turn.id))
    );
  }

  function nextTurnIdValue() {
    return ++nextTurnId;
  }

  function setActiveSessionId(id: string) {
    activeSessionId.value = id;
  }

  function setWorkspaceError(message: string | null) {
    workspaceError.value = message;
  }

  function appendPendingTurn(session: ChatSession, question: string): ChatTurn {
    const turn: ChatTurn = {
      id: nextTurnIdValue(),
      question,
      answer: '',
      sources: [],
      loading: true,
    };
    session.turns.push(turn);
    return turn;
  }

  function applyChatMeta(session: ChatSession, turn: ChatTurn, meta: ChatStreamMetaResponse) {
    session.backendSessionId = meta.sessionId;
    session.title = meta.sessionTitle?.trim() || session.title;
    turn.userMessageId = meta.userMessageId;
    turn.assistantMessageId = meta.assistantMessageId;
  }

  function appendAnswerDelta(turn: ChatTurn, text: string) {
    turn.answer += text;
  }

  function applyChatResponse(session: ChatSession, turn: ChatTurn, response: ChatMessageResponse) {
    session.backendSessionId = response.sessionId;
    session.title = response.sessionTitle?.trim() || session.title;
    session.messagesLoaded = true;
    turn.userMessageId = response.userMessageId;
    turn.assistantMessageId = response.assistantMessageId;
    turn.answer = response.answer ?? '';
    turn.sources = response.sources ?? [];
    turn.pending = false;
    turn.error = undefined;
  }

  function applyChatFailure(turn: ChatTurn, message: string) {
    turn.error = message;
    turn.answer = '';
    turn.sources = [];
    turn.pending = false;
    turn.loading = false;
  }

  function finishTurn(turn: ChatTurn) {
    turn.loading = false;
  }

  return {
    sessions,
    activeSessionId,
    sessionsLoading,
    workspaceError,
    sessionDeleteTarget,
    sessionDeleteBusy,
    sessionDeleteError,
    sessionActionBusyId,
    sessionActionError,
    loadingMessageSessionIds,
    activeSession,
    initializeFromSeed,
    loadSessions,
    loadMessagesForSession,
    handleCreateSession,
    handleSwitchSession,
    handleRenameSession,
    handleDeleteSessionRequest,
    confirmDeleteSession,
    createSessionInternal,
    setActiveSessionId,
    setWorkspaceError,
    appendPendingTurn,
    applyChatMeta,
    appendAnswerDelta,
    applyChatResponse,
    applyChatFailure,
    finishTurn,
  };
}
