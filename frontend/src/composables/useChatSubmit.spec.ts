import { computed, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/client';
import type {
  ChatMessageResponse,
  ChatSession,
  ChatStreamDeltaResponse,
  ChatStreamErrorResponse,
  ChatStreamMetaResponse,
  ChatTurn,
} from '@/api/types';
import { useChatSubmit } from './useChatSubmit';

const streamFirstChatMessageMock = vi.hoisted(() => vi.fn());
const streamChatMessageMock = vi.hoisted(() => vi.fn());

vi.mock('@/api/noterag', () => ({
  streamFirstChatMessage: streamFirstChatMessageMock,
  streamChatMessage: streamChatMessageMock,
}));

interface StreamHandlers {
  onMeta?: (meta: ChatStreamMetaResponse) => void;
  onDelta?: (delta: ChatStreamDeltaResponse) => void;
  onDone?: (response: ChatMessageResponse) => void;
  onError?: (error: ChatStreamErrorResponse) => void;
}

afterEach(() => {
  streamFirstChatMessageMock.mockReset();
  streamChatMessageMock.mockReset();
  vi.useRealTimers();
});

describe('useChatSubmit', () => {
  it('buffers deltas before flushing them to the answer', async () => {
    vi.useFakeTimers();
    let resolveStream!: () => void;

    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onDelta?.({ text: 'hello' });
      handlers.onDelta?.({ text: ' world' });
      await new Promise<void>((resolve) => {
        resolveStream = resolve;
      });
      handlers.onDone?.(chatResponse('final answer'));
    });

    const harness = createSubmitHarness();
    const submitPromise = harness.handleSubmit('question');
    const turn = harness.session.value.turns[0];

    expect(turn.answer).toBe('');
    expect(harness.appendAnswerDelta).not.toHaveBeenCalled();

    vi.advanceTimersByTime(39);
    expect(turn.answer).toBe('');

    vi.advanceTimersByTime(1);
    expect(turn.answer).toBe('hello world');
    expect(harness.appendAnswerDelta).toHaveBeenCalledTimes(1);

    resolveStream();
    await submitPromise;

    expect(turn.answer).toBe('final answer');
  });

  it('flushes pending deltas before applying the final response', async () => {
    vi.useFakeTimers();
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onDelta?.({ text: 'tail' });
      handlers.onDone?.(chatResponse('final answer'));
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');

    const turn = harness.session.value.turns[0];
    expect(harness.appendAnswerDelta).toHaveBeenCalledWith(turn, 'tail');
    expect(harness.applyChatResponse).toHaveBeenCalled();
    expect(harness.appendAnswerDelta.mock.invocationCallOrder[0]).toBeLessThan(
      harness.applyChatResponse.mock.invocationCallOrder[0]
    );
    expect(turn.answer).toBe('final answer');

    vi.advanceTimersByTime(40);
    expect(harness.appendAnswerDelta).toHaveBeenCalledTimes(1);
  });

  it('stops the delta buffer when an error event arrives', async () => {
    vi.useFakeTimers();
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onDelta?.({ text: 'partial' });
      handlers.onError?.({ code: 50001, message: 'stream failed' });
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');
    vi.advanceTimersByTime(40);

    const turn = harness.session.value.turns[0];
    expect(harness.appendAnswerDelta).not.toHaveBeenCalled();
    expect(turn.answer).toBe('');
    expect(turn.error).toBe('stream failed');
    expect(harness.activeSessionSubmitting.value).toBe(false);
  });

  it('keeps a turn failed when a done event arrives after an error event', async () => {
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onError?.({ code: 50001, message: 'stream failed' });
      handlers.onDone?.(chatResponse('success'));
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');

    const turn = harness.session.value.turns[0];
    expect(turn.error).toBe('stream failed');
    expect(turn.answer).toBe('');
    expect(turn.loading).toBe(false);
    expect(harness.applyChatResponse).not.toHaveBeenCalled();
  });

  it('marks the turn failed when the stream fails after a partial delta', async () => {
    vi.useFakeTimers();
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onDelta?.({ text: 'partial' });
      throw new ApiError('connection lost', -1, 0);
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');

    const turn = harness.session.value.turns[0];
    vi.advanceTimersByTime(40);

    expect(harness.appendAnswerDelta).not.toHaveBeenCalled();
    expect(turn.error).toBe('connection lost');
    expect(turn.answer).toBe('');
    expect(turn.loading).toBe(false);
  });

  it('clears submitting state when an error event terminates the stream', async () => {
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onError?.({ code: 50001, message: 'stream failed' });
      throw new ApiError('stream failed', 50001, 0);
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');

    expect(harness.activeSessionSubmitting.value).toBe(false);
    expect(harness.session.value.turns[0]?.error).toBe('stream failed');
  });

  it('ignores meta events after a done event', async () => {
    streamFirstChatMessageMock.mockImplementationOnce(async (...args: unknown[]) => {
      const handlers = args[2] as StreamHandlers;
      handlers.onDone?.(chatResponse('success'));
      handlers.onMeta?.({
        sessionId: 2,
        sessionTitle: 'late meta',
        userMessageId: 20,
        assistantMessageId: 21,
      });
    });

    const harness = createSubmitHarness();
    await harness.handleSubmit('question');

    expect(harness.applyChatMeta).not.toHaveBeenCalled();
    expect(harness.session.value.backendSessionId).toBe(1);
    expect(harness.session.value.title).toBe('New chat');
  });
});

function createSubmitHarness() {
  const session = ref<ChatSession>({
    id: 'local-session',
    title: 'New chat',
    turns: [],
  });

  const appendPendingTurn = vi.fn((target: ChatSession, question: string) => {
    const turn: ChatTurn = {
      id: target.turns.length + 1,
      question,
      answer: '',
      sources: [],
      loading: true,
    };
    target.turns.push(turn);
    return turn;
  });

  const applyChatResponse = vi.fn(
    (target: ChatSession, turn: ChatTurn, response: ChatMessageResponse) => {
      target.backendSessionId = response.sessionId;
      target.title = response.sessionTitle;
      turn.userMessageId = response.userMessageId;
      turn.assistantMessageId = response.assistantMessageId;
      turn.answer = response.answer;
      turn.sources = response.sources;
      turn.error = undefined;
    }
  );

  const applyChatFailure = vi.fn((turn: ChatTurn, message: string) => {
    turn.error = message;
    turn.answer = '';
    turn.sources = [];
    turn.pending = false;
    turn.loading = false;
  });

  const finishTurn = vi.fn((turn: ChatTurn) => {
    turn.loading = false;
  });

  const applyChatMeta = vi.fn();
  const appendAnswerDelta = vi.fn((turn: ChatTurn, text: string) => {
    turn.answer += text;
  });

  const submit = useChatSubmit({
    sessionsLoading: ref(false),
    activeSession: computed(() => session.value),
    loadingMessageSessionIds: ref(new Set<number>()),
    selectedNoteIds: ref([]),
    createSession: () => session.value,
    setActiveSessionId: vi.fn(),
    appendPendingTurn,
    applyChatMeta,
    appendAnswerDelta,
    applyChatResponse,
    applyChatFailure,
    finishTurn,
  });

  return {
    session,
    activeSessionSubmitting: submit.activeSessionSubmitting,
    applyChatMeta,
    appendAnswerDelta,
    applyChatResponse,
    handleSubmit: submit.handleSubmit,
  };
}

function chatResponse(answer: string): ChatMessageResponse {
  return {
    sessionId: 1,
    sessionTitle: 'New chat',
    userMessageId: 10,
    assistantMessageId: 11,
    answer,
    sources: [],
  };
}
