import type { ApiBody } from './types';

export class ApiError extends Error {
  readonly code: number;
  readonly httpStatus: number;

  constructor(message: string, code: number, httpStatus: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

export async function postJson<TResp, TReq = unknown>(path: string, body: TReq): Promise<TResp> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  });
  return parseApiResponse<TResp>(response);
}

export async function parseApiResponse<T>(response: Response): Promise<T> {
  let payload: ApiBody<T> | null = null;
  try {
    payload = (await response.json()) as ApiBody<T>;
  } catch {
    throw new ApiError(`服务器响应解析失败 (HTTP ${response.status})`, -1, response.status);
  }

  if (!response.ok) {
    const message = payload?.message ?? `请求失败 (HTTP ${response.status})`;
    const code = payload?.code ?? -1;
    throw new ApiError(message, code, response.status);
  }

  if (payload && payload.code !== 0) {
    throw new ApiError(payload.message ?? '未知错误', payload.code, response.status);
  }

  return payload!.data;
}
