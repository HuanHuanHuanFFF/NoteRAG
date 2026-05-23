import { describe, it, expect, vi, afterEach } from 'vitest';
import { ApiError, postJson } from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

afterEach(() => {
  fetchMock.mockReset();
  vi.useRealTimers();
});

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('postJson', () => {
  it('unwraps ApiBody.data on success', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { code: 0, message: 'success', data: { ok: true } })
    );
    const result = await postJson<{ ok: boolean }>('/api/test', { a: 1 });
    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/test',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('throws ApiError when http status is not 2xx', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 40001, message: '参数错误', data: null })
    );
    await expect(postJson('/api/test', {})).rejects.toMatchObject({
      name: 'ApiError',
      code: 40001,
      httpStatus: 400,
      message: '参数错误',
    });
  });

  it('throws ApiError when 200 but business code is non-zero', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { code: 50001, message: '业务异常', data: null })
    );
    await expect(postJson('/api/test', {})).rejects.toBeInstanceOf(ApiError);
  });

  it('throws ApiError when response is not valid json', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('not-json', { status: 500, headers: { 'Content-Type': 'text/plain' } })
    );
    await expect(postJson('/api/test', {})).rejects.toBeInstanceOf(ApiError);
  });

  it('wraps network failure as ApiError', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(postJson('/api/test', {})).rejects.toMatchObject({
      name: 'ApiError',
      code: -1,
      httpStatus: 0,
      message: '无法连接服务器，请确认后端服务已启动',
    });
  });

  it('throws ApiError when request body cannot be serialized', async () => {
    const circularBody: Record<string, unknown> = {};
    circularBody.self = circularBody;

    await expect(postJson('/api/test', circularBody)).rejects.toMatchObject({
      name: 'ApiError',
      code: -1,
      httpStatus: 0,
      message: '请求参数序列化失败',
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('aborts and wraps timeout as ApiError', async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementationOnce((_path: string, init?: RequestInit) => {
      return new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => {
          reject(new DOMException('Aborted', 'AbortError'));
        });
      });
    });

    const request = postJson('/api/test', {}, { timeoutMs: 10 }).catch((error) => error);
    await vi.advanceTimersByTimeAsync(10);

    await expect(request).resolves.toMatchObject({
      name: 'ApiError',
      code: -1,
      httpStatus: 0,
      message: '请求超时，请稍后重试',
    });
  });
});
