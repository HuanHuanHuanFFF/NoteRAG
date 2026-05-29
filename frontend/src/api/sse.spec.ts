import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { postJsonSse, type SseEvent } from './sse';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

afterEach(() => {
  fetchMock.mockReset();
});

function sseResponse(body: string, init?: ResponseInit): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream; charset=utf-8' },
    ...init,
  });
}

describe('postJsonSse', () => {
  it('parses JSON SSE events and resolves after done then close', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        'event: meta\n' +
          'data: {"sessionId":1}\n\n' +
          'event: delta\n' +
          'data: {"text":"hello"}\n\n' +
          'event: done\n' +
          'data: {"answer":"final"}\n\n'
      )
    );

    const events: SseEvent[] = [];
    await postJsonSse('/api/stream', { content: 'q' }, (event) => events.push(event));

    expect(events).toEqual([
      { event: 'meta', data: { sessionId: 1 } },
      { event: 'delta', data: { text: 'hello' } },
      { event: 'done', data: { answer: 'final' } },
    ]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/stream',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          accept: 'text/event-stream',
        }),
        body: '{"content":"q"}',
      })
    );
  });

  it('rejects non-JSON event data without retrying', async () => {
    fetchMock.mockResolvedValueOnce(sseResponse('event: delta\ndata: plain-text\n\n'));

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects non-2xx HTTP responses without retrying', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse('', {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toMatchObject({
      httpStatus: 500,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects missing content-type without retrying', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('event: done\ndata: {"answer":"final"}\n\n', { status: 200 })
    );

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toMatchObject({
      message: '服务端未返回 text/event-stream 响应',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects network failures without retrying', async () => {
    fetchMock.mockRejectedValueOnce(new Error('network down'));

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects close before done or error without retrying', async () => {
    fetchMock.mockResolvedValueOnce(sseResponse('event: delta\ndata: {"text":"partial"}\n\n'));

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toMatchObject({
      message: '流式响应未正常完成',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
