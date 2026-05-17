import { describe, it, expect, vi, afterEach } from 'vitest';
import { ApiError, postJson } from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

afterEach(() => {
  fetchMock.mockReset();
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
});
