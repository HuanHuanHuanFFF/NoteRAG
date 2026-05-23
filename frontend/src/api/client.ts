import type { ApiBody } from './types';

const DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
const CLIENT_ERROR_CODE = -1;
const NETWORK_HTTP_STATUS = 0;

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

export interface PostJsonOptions {
  timeoutMs?: number;
}

export async function postJson<TResp, TReq = unknown>(
  path: string,
  body: TReq,
  options: PostJsonOptions = {}
): Promise<TResp> {
  const requestBody = stringifyRequestBody(body);
  const controller = new AbortController();
  const timeoutMs = options.timeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
  const timeoutId =
    timeoutMs > 0 ? globalThis.setTimeout(() => controller.abort(), timeoutMs) : undefined;

  try {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: requestBody,
      signal: controller.signal,
    });
    return await parseApiResponse<TResp>(response);
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (isAbortError(error)) {
      throw new ApiError('请求超时，请稍后重试', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
    }
    throw new ApiError('无法连接服务器，请确认后端服务已启动', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  } finally {
    if (timeoutId !== undefined) {
      globalThis.clearTimeout(timeoutId);
    }
  }
}

export async function parseApiResponse<T>(response: Response): Promise<T> {
  let payload: ApiBody<T> | null = null;
  try {
    payload = (await response.json()) as ApiBody<T>;
  } catch {
    throw new ApiError(`服务器响应解析失败 (HTTP ${response.status})`, CLIENT_ERROR_CODE, response.status);
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

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

function stringifyRequestBody(body: unknown): string {
  try {
    return JSON.stringify(body);
  } catch {
    throw new ApiError('请求参数序列化失败', CLIENT_ERROR_CODE, NETWORK_HTTP_STATUS);
  }
}
