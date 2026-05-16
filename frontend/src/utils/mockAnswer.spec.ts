import { describe, it, expect } from 'vitest';
import { buildMockAnswer } from './mockAnswer';
import type { SourceChunk } from '@/api/types';

function chunk(partial: Partial<SourceChunk>): SourceChunk {
  return {
    noteId: 1,
    chunkId: 1,
    title: 't',
    headingPath: null,
    content: 'c',
    score: 0.9,
    ...partial,
  };
}

describe('buildMockAnswer', () => {
  it('returns fallback when no sources', () => {
    expect(buildMockAnswer('what?', [])).toBe('根据当前笔记内容无法确定。');
  });

  it('mentions the question and the top sources', () => {
    const result = buildMockAnswer('MVCC?', [
      chunk({ chunkId: 1, headingPath: 'MySQL > MVCC', content: 'MVCC 是基于 undo log 实现的' }),
      chunk({ chunkId: 2, headingPath: 'MySQL > 锁', content: '行级锁 ...' }),
    ]);
    expect(result).toContain('MVCC?');
    expect(result).toContain('[1]');
    expect(result).toContain('MySQL > MVCC');
    expect(result).toContain('占位回答');
  });

  it('caps to first three sources', () => {
    const sources = Array.from({ length: 5 }, (_, i) =>
      chunk({ chunkId: i + 1, headingPath: `H${i + 1}`, content: `c${i + 1}` })
    );
    const result = buildMockAnswer('q', sources);
    expect(result).toContain('[1]');
    expect(result).toContain('[2]');
    expect(result).toContain('[3]');
    expect(result).not.toContain('[4]');
  });

  it('falls back to title when headingPath is empty', () => {
    const result = buildMockAnswer('q', [
      chunk({ chunkId: 1, title: 'Plain Title', headingPath: '   ', content: 'body' }),
    ]);
    expect(result).toContain('Plain Title');
  });
});
