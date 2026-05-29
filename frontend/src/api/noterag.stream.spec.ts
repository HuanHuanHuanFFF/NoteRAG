import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { streamFirstChatMessage } from './noterag';

const postJsonSseMock = vi.hoisted(() => vi.fn());

vi.mock('./sse', () => ({
  postJsonSse: postJsonSseMock,
}));

afterEach(() => {
  postJsonSseMock.mockReset();
});

describe('chat stream API', () => {
  it('dispatches meta payloads', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({
        event: 'meta',
        data: {
          sessionId: 1,
          sessionTitle: 'Chat',
          userMessageId: 10,
          assistantMessageId: 11,
        },
      });
    });

    const onMeta = vi.fn();
    await streamFirstChatMessage('q', undefined, { onMeta });

    expect(onMeta).toHaveBeenCalledWith({
      sessionId: 1,
      sessionTitle: 'Chat',
      userMessageId: 10,
      assistantMessageId: 11,
    });
  });

  it('dispatches strict delta text payloads', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({ event: 'delta', data: { text: 'hello' } });
    });

    const onDelta = vi.fn();
    await streamFirstChatMessage('q', undefined, { onDelta });

    expect(onDelta).toHaveBeenCalledWith({ text: 'hello' });
  });

  it('rejects legacy delta field names', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({ event: 'delta', data: { delta: 'hello' } });
    });

    await expect(streamFirstChatMessage('q', undefined, {})).rejects.toBeInstanceOf(ApiError);
  });

  it('rejects unknown chat stream events', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({ event: 'status', data: { message: 'working' } });
    });

    await expect(streamFirstChatMessage('q', undefined, {})).rejects.toBeInstanceOf(ApiError);
  });

  it('treats error events as terminal', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({ event: 'error', data: { code: 50001, message: 'stream failed' } });
    });

    const onError = vi.fn();
    await expect(streamFirstChatMessage('q', undefined, { onError })).rejects.toMatchObject({
      message: 'stream failed',
    });
    expect(onError).toHaveBeenCalledWith({ code: 50001, message: 'stream failed' });
  });

  it('dispatches done payloads', async () => {
    postJsonSseMock.mockImplementationOnce(async (...args: unknown[]) => {
      const onEvent = args[2] as (event: { event: string; data: unknown }) => void;
      onEvent({
        event: 'done',
        data: {
          sessionId: 1,
          sessionTitle: 'Chat',
          userMessageId: 10,
          assistantMessageId: 11,
          answer: 'final',
          sources: [],
        },
      });
    });

    const onDone = vi.fn();
    await streamFirstChatMessage('q', undefined, { onDone });

    expect(onDone).toHaveBeenCalledWith({
      sessionId: 1,
      sessionTitle: 'Chat',
      userMessageId: 10,
      assistantMessageId: 11,
      answer: 'final',
      sources: [],
    });
  });
});
