const STREAM_DEBUG_STORAGE_KEY = 'noterag.debug.stream';

export function isStreamDebugEnabled(): boolean {
  if (typeof window === 'undefined') return false;

  try {
    return window.localStorage.getItem(STREAM_DEBUG_STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

export function logStreamDebug(scope: string, event: string, payload: Record<string, unknown> = {}) {
  if (!isStreamDebugEnabled()) return;
  console.debug(`[NoteRAG stream:${scope}] ${event}`, payload);
}
