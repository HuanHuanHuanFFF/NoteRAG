import type { SourceChunk } from '@/api/types';

const FALLBACK_NO_SOURCES = '根据当前笔记内容无法确定。';

export function buildMockAnswer(question: string, sources: SourceChunk[]): string {
  const trimmedQuestion = question.trim();
  if (sources.length === 0) {
    return FALLBACK_NO_SOURCES;
  }
  const top = sources.slice(0, Math.min(3, sources.length));
  const bullets = top
    .map((chunk, idx) => {
      const heading = chunk.headingPath?.trim() || chunk.title;
      const preview = previewContent(chunk.content);
      return `[${idx + 1}] ${heading}：${preview}`;
    })
    .join('\n');
  return [
    '（这是占位回答，未接入 LLM）',
    `针对 "${trimmedQuestion}"，笔记中以下片段最相关：`,
    bullets,
    '请展开下方来源查看完整 chunk 内容。',
  ].join('\n');
}

function previewContent(content: string, max = 80): string {
  const flat = content.replace(/\s+/g, ' ').trim();
  return flat.length > max ? `${flat.slice(0, max)}…` : flat;
}
