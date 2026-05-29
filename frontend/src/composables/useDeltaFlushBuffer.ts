import { logStreamDebug } from '@/utils/streamDebug';

const DEFAULT_FLUSH_INTERVAL_MS = 40;

interface UseDeltaFlushBufferOptions {
  onFlush: (text: string) => void;
  flushIntervalMs?: number;
}

export function useDeltaFlushBuffer(options: UseDeltaFlushBufferOptions) {
  const buffer: string[] = [];
  const flushIntervalMs = options.flushIntervalMs ?? DEFAULT_FLUSH_INTERVAL_MS;
  let timer: number | null = null;
  let stopped = false;

  function append(text: string) {
    if (stopped || text.length === 0) return;
    buffer.push(text);
    logStreamDebug('buffer', 'append', {
      textLen: text.length,
      pendingItems: buffer.length,
      timerActive: timer != null,
    });
    scheduleFlush();
  }

  function flush() {
    clearTimer();
    flushBuffer();
  }

  function stop() {
    logStreamDebug('buffer', 'stop', {
      pendingItems: buffer.length,
      timerActive: timer != null,
      stopped,
    });
    clearTimer();
    buffer.length = 0;
    stopped = true;
  }

  function scheduleFlush() {
    if (timer != null) return;
    timer = window.setTimeout(() => {
      timer = null;
      flushBuffer();
    }, flushIntervalMs);
  }

  function flushBuffer() {
    if (stopped || buffer.length === 0) return;
    const partCount = buffer.length;
    const text = buffer.join('');
    buffer.length = 0;
    logStreamDebug('buffer', 'flush', {
      textLen: text.length,
      partCount,
    });
    options.onFlush(text);
  }

  function clearTimer() {
    if (timer == null) return;
    window.clearTimeout(timer);
    timer = null;
  }

  return {
    append,
    flush,
    stop,
  };
}
