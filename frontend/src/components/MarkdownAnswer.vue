<script setup lang="ts">
import { computed } from 'vue';
import MarkdownIt from 'markdown-it';
import type { SourceChunk } from '@/api/types';

const MARKER_PREFIX = '\uE200cite\uE202';
const MARKER_END = '\uE201';
const CITATION_PATTERN = new RegExp(`${MARKER_PREFIX}([1-9]\\d*)${MARKER_END}`, 'g');
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: false,
});

const props = defineProps<{
  answer: string;
  sources: SourceChunk[];
}>();

const emit = defineEmits<{
  (e: 'open-citation', index: number): void;
}>();

const sourceDisplayIndex = computed(() => {
  const byChunkId = new Map<string, number>();
  props.sources.forEach((source, index) => {
    byChunkId.set(String(source.chunkId), index + 1);
  });
  return byChunkId;
});

const renderedAnswer = computed(() => {
  const replacements: Array<{ placeholder: string; displayIndex: number }> = [];
  const preparedAnswer = (props.answer ?? '').replace(CITATION_PATTERN, (marker, sourceId: string) => {
    const displayIndex = sourceDisplayIndex.value.get(sourceId);
    if (displayIndex == null) {
      return marker;
    }

    const placeholder = `NOTERAG_CITATION_${replacements.length}__`;
    replacements.push({ placeholder, displayIndex });
    return placeholder;
  });

  let html = markdown.render(preparedAnswer);
  for (const replacement of replacements) {
    html = html.split(replacement.placeholder).join(renderCitationButton(replacement.displayIndex));
  }
  return html;
});

function renderCitationButton(displayIndex: number): string {
  return `<button type="button" class="markdown-answer__citation" data-citation-index="${displayIndex}" aria-label="View source ${displayIndex}">${displayIndex}</button>`;
}

function handleClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  const button = target?.closest<HTMLButtonElement>('button[data-citation-index]');
  if (!button) return;

  const index = Number(button.dataset.citationIndex);
  if (!Number.isInteger(index) || index < 1) return;
  emit('open-citation', index);
}
</script>

<template>
  <div class="markdown-answer" v-html="renderedAnswer" @click="handleClick"></div>
</template>

<style scoped>
.markdown-answer {
  color: rgb(255 255 255 / 0.85);
  font-size: 14px;
  line-height: 1.75;
}

.markdown-answer :deep(:first-child) {
  margin-top: 0;
}

.markdown-answer :deep(:last-child) {
  margin-bottom: 0;
}

.markdown-answer :deep(p),
.markdown-answer :deep(ul),
.markdown-answer :deep(ol),
.markdown-answer :deep(pre),
.markdown-answer :deep(blockquote),
.markdown-answer :deep(table) {
  margin: 0.7em 0;
}

.markdown-answer :deep(h1),
.markdown-answer :deep(h2),
.markdown-answer :deep(h3),
.markdown-answer :deep(h4) {
  margin: 1em 0 0.45em;
  color: rgb(255 255 255 / 0.92);
  font-weight: 650;
  line-height: 1.35;
}

.markdown-answer :deep(h1) {
  font-size: 18px;
}

.markdown-answer :deep(h2) {
  font-size: 16px;
}

.markdown-answer :deep(h3),
.markdown-answer :deep(h4) {
  font-size: 15px;
}

.markdown-answer :deep(ul),
.markdown-answer :deep(ol) {
  padding-left: 1.35rem;
}

.markdown-answer :deep(ul) {
  list-style: disc;
}

.markdown-answer :deep(ol) {
  list-style: decimal;
}

.markdown-answer :deep(li + li) {
  margin-top: 0.2em;
}

.markdown-answer :deep(strong) {
  color: rgb(255 255 255 / 0.95);
  font-weight: 650;
}

.markdown-answer :deep(code) {
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 5px;
  background: rgb(255 255 255 / 0.06);
  padding: 0.1rem 0.28rem;
  color: rgb(255 255 255 / 0.9);
  font-size: 0.9em;
}

.markdown-answer :deep(pre) {
  overflow-x: auto;
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 8px;
  background: rgb(0 0 0 / 0.32);
  padding: 0.75rem 0.9rem;
}

.markdown-answer :deep(pre code) {
  border: 0;
  background: transparent;
  padding: 0;
}

.markdown-answer :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-answer :deep(th),
.markdown-answer :deep(td) {
  border: 1px solid rgb(255 255 255 / 0.1);
  padding: 0.35rem 0.55rem;
}

.markdown-answer :deep(th) {
  background: rgb(255 255 255 / 0.06);
  color: rgb(255 255 255 / 0.9);
  font-weight: 600;
}

.markdown-answer :deep(.markdown-answer__citation) {
  display: inline-flex;
  min-width: 20px;
  height: 18px;
  align-items: center;
  justify-content: center;
  margin: 0 2px;
  padding: 0 4px;
  border: 0;
  border-radius: 4px;
  background: rgb(255 255 255 / 0.06);
  color: rgb(45 212 191);
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono',
    'Courier New', monospace;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  vertical-align: 0.08em;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    box-shadow 150ms ease,
    color 150ms ease;
}

.markdown-answer :deep(.markdown-answer__citation:hover) {
  background: rgb(45 212 191 / 0.15);
}

</style>
