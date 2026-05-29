import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDeltaFlushBuffer } from './useDeltaFlushBuffer';

describe('useDeltaFlushBuffer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('flushes multiple appends in one timer tick', () => {
    const onFlush = vi.fn();
    const buffer = useDeltaFlushBuffer({ onFlush });

    buffer.append('a');
    buffer.append('b');
    buffer.append('c');

    vi.advanceTimersByTime(39);
    expect(onFlush).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(onFlush).toHaveBeenCalledTimes(1);
    expect(onFlush).toHaveBeenCalledWith('abc');
  });

  it('manual flush outputs pending text immediately', () => {
    const onFlush = vi.fn();
    const buffer = useDeltaFlushBuffer({ onFlush });

    buffer.append('a');
    buffer.append('b');
    buffer.flush();

    expect(onFlush).toHaveBeenCalledTimes(1);
    expect(onFlush).toHaveBeenCalledWith('ab');

    vi.advanceTimersByTime(40);
    expect(onFlush).toHaveBeenCalledTimes(1);
  });

  it('manual flush can be followed by another scheduled flush', () => {
    const onFlush = vi.fn();
    const buffer = useDeltaFlushBuffer({ onFlush });

    buffer.append('a');
    buffer.flush();
    buffer.append('b');
    vi.advanceTimersByTime(40);

    expect(onFlush).toHaveBeenCalledTimes(2);
    expect(onFlush).toHaveBeenNthCalledWith(1, 'a');
    expect(onFlush).toHaveBeenNthCalledWith(2, 'b');
  });

  it('stop clears pending text and prevents later flushes', () => {
    const onFlush = vi.fn();
    const buffer = useDeltaFlushBuffer({ onFlush });

    buffer.append('a');
    buffer.stop();
    vi.advanceTimersByTime(40);
    buffer.append('b');
    vi.advanceTimersByTime(40);

    expect(onFlush).not.toHaveBeenCalled();
  });
});
