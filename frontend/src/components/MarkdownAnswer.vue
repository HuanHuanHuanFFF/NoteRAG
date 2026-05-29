<script setup lang="ts">
import { computed, provide } from 'vue';
import MarkdownRender, { setCustomComponents } from 'markstream-vue';
import type { SourceChunk } from '@/api/types';
import CitationButton from '@/components/CitationButton.vue';
import { citationContextKey } from '@/components/citationContext';
import { transformCitationMarkers } from '@/utils/citationMarkers';

const CUSTOM_ID = 'noterag-chat-answer';
const CUSTOM_HTML_TAGS = ['citation'];

setCustomComponents(CUSTOM_ID, { citation: CitationButton });

const props = withDefaults(defineProps<{
  answer: string;
  sources: SourceChunk[];
  loading?: boolean;
}>(), {
  loading: false,
});

const emit = defineEmits<{
  (e: 'open-citation', index: number): void;
}>();

provide(citationContextKey, {
  openCitation(index: number) {
    if (props.loading) return;
    emit('open-citation', index);
  },
});

const renderedAnswer = computed(() =>
  transformCitationMarkers(props.answer ?? '', {
    disabled: props.loading,
    sourceIds: props.loading ? undefined : props.sources.map((source) => source.chunkId),
  }).content
);
</script>

<template>
  <div class="markdown-answer">
    <MarkdownRender
      :content="renderedAnswer"
      :custom-html-tags="CUSTOM_HTML_TAGS"
      :final="!loading"
      :typewriter="true"
      smooth-streaming="auto"
      html-policy="escape"
      :custom-id="CUSTOM_ID"
      :is-dark="true"
    />
  </div>
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

.markdown-answer :deep(.markdown-answer__citation:not(:disabled):hover) {
  background: rgb(45 212 191 / 0.15);
}

.markdown-answer :deep(.markdown-answer__citation:disabled) {
  cursor: not-allowed;
  opacity: 0.55;
}

</style>
