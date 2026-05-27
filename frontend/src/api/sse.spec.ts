import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './client';
import { postJsonSse, type SseEvent } from './sse';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

afterEach(() => {
  fetchMock.mockReset();
});

function sseResponse(body: string): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

describe('postJsonSse', () => {
  it('parses JSON SSE events', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        'event: meta\n' +
          'data: {"sessionId":1}\n\n' +
          'event: delta\n' +
          'data: {"text":"hello"}\n\n'
      )
    );

    const events: SseEvent[] = [];
    await postJsonSse('/api/stream', { content: 'q' }, (event) => events.push(event));

    expect(events).toEqual([
      { event: 'meta', data: { sessionId: 1 } },
      { event: 'delta', data: { text: 'hello' } },
    ]);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/stream',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      })
    );
  });

  it('rejects non-JSON event data', async () => {
    fetchMock.mockResolvedValueOnce(sseResponse('event: delta\ndata: plain-text\n\n'));

    await expect(postJsonSse('/api/stream', {}, () => undefined)).rejects.toBeInstanceOf(ApiError);
  });
});
