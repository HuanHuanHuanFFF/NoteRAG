import { computed, ref } from 'vue';
import { ApiError } from '@/api/client';
import { sendChatMessage, sendFirstChatMessage } from '@/api/noterag';
import type { ChatMessageResponse, ChatSession, ChatTurn } from '@/api/types';

interface ReadonlyValue<T> {
  readonly value: T;
}

interface UseChatSubmitOptions {
  sessionsLoading: ReadonlyValue<boolean>;
  activeSession: ReadonlyValue<ChatSession | null>;
  loadingMessageSessionIds: ReadonlyValue<Set<number>>;
  selectedNoteIds: ReadonlyValue<number[]>;
  createSession: () => ChatSession;
  setActiveSessionId: (id: string) => void;
  appendPendingTurn: (session: ChatSession, question: string) => ChatTurn;
  applyChatResponse: (session: ChatSession, turn: ChatTurn, response: ChatMessageResponse) => void;
  applyChatFailure: (turn: ChatTurn, message: string) => void;
  finishTurn: (turn: ChatTurn) => void;
}

export function useChatSubmit(options: UseChatSubmitOptions) {
  const submittingSessionIds = ref<Set<string>>(new Set());

  const activeSessionSubmitting = computed(() => {
    if (options.sessionsLoading.value) return true;
    const session = options.activeSession.value;
    if (!session) return false;
    return (
      submittingSessionIds.value.has(session.id) ||
      (session.backendSessionId != null && options.loadingMessageSessionIds.value.has(session.backendSessionId))
    );
  });

  async function handleSubmit(question: string) {
    if (options.sessionsLoading.value) return;
    const session = options.activeSession.value ?? options.createSession();
    options.setActiveSessionId(session.id);
    if (submittingSessionIds.value.has(session.id)) return;

    const noteIds = options.selectedNoteIds.value.length > 0 ? options.selectedNoteIds.value : undefined;
    const turn = options.appendPendingTurn(session, question);
    setSessionSubmitting(session.id, true);

    try {
      const response =
        session.backendSessionId == null
          ? await sendFirstChatMessage(question, noteIds)
          : await sendChatMessage(session.backendSessionId, question, noteIds);

      options.applyChatResponse(session, turn, response);
    } catch (e) {
      options.applyChatFailure(turn, e instanceof ApiError ? e.message : '发送失败，请稍后重试');
    } finally {
      options.finishTurn(turn);
      setSessionSubmitting(session.id, false);
    }
  }

  function handleRetry(question: string) {
    if (activeSessionSubmitting.value) return;
    void handleSubmit(question);
  }

  function clearSessionSubmitting(sessionId: string) {
    setSessionSubmitting(sessionId, false);
  }

  function setSessionSubmitting(sessionId: string, submitting: boolean) {
    const next = new Set(submittingSessionIds.value);
    if (submitting) {
      next.add(sessionId);
    } else {
      next.delete(sessionId);
    }
    submittingSessionIds.value = next;
  }

  return {
    activeSessionSubmitting,
    handleSubmit,
    handleRetry,
    clearSessionSubmitting,
  };
}
