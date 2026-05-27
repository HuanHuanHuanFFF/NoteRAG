import { computed, ref } from 'vue';
import { ApiError } from '@/api/client';
import { streamChatMessage, streamFirstChatMessage } from '@/api/noterag';
import type {
  ChatMessageResponse,
  ChatSession,
  ChatStreamMetaResponse,
  ChatTurn,
} from '@/api/types';

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
  applyChatMeta: (session: ChatSession, turn: ChatTurn, meta: ChatStreamMetaResponse) => void;
  appendAnswerDelta: (turn: ChatTurn, text: string) => void;
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
    let failed = false;
    let done = false;

    try {
      const handlers = {
        onMeta(meta: ChatStreamMetaResponse) {
          if (failed || done) return;
          options.applyChatMeta(session, turn, meta);
        },
        onDelta(delta: { text: string }) {
          if (failed || done) return;
          options.appendAnswerDelta(turn, delta.text);
        },
        onDone(response: ChatMessageResponse) {
          if (failed || done) return;
          done = true;
          options.applyChatResponse(session, turn, response);
        },
        onError(error: { message: string }) {
          if (failed || done) return;
          failed = true;
          options.applyChatFailure(turn, error.message || '发送失败，请稍后重试');
        },
      };

      if (session.backendSessionId == null) {
        await streamFirstChatMessage(question, noteIds, handlers);
      } else {
        await streamChatMessage(session.backendSessionId, question, noteIds, handlers);
      }

      if (!failed && !done) {
        failed = true;
        options.applyChatFailure(turn, '流式响应未正常完成');
      }
    } catch (e) {
      if (!failed && !done) {
        failed = true;
        options.applyChatFailure(turn, e instanceof ApiError ? e.message : '发送失败，请稍后重试');
      }
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
