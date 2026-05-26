<script setup lang="ts">
import { computed } from 'vue';
import { markdown } from '@/utils/markdown';

const props = defineProps<{
  content: string;
}>();

const renderedContent = computed(() => markdown.render(props.content ?? ''));
</script>

<template>
  <div class="markdown-content" v-html="renderedContent"></div>
</template>

<style scoped>
.markdown-content {
  max-width: 100%;
  overflow-wrap: anywhere;
  color: rgb(255 255 255 / 0.85);
  font-size: 14px;
  line-height: 1.75;
}

.markdown-content :deep(:first-child) {
  margin-top: 0;
}

.markdown-content :deep(:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(p),
.markdown-content :deep(ul),
.markdown-content :deep(ol),
.markdown-content :deep(pre),
.markdown-content :deep(blockquote),
.markdown-content :deep(table) {
  margin: 0.7em 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 1em 0 0.45em;
  color: rgb(255 255 255 / 0.92);
  font-weight: 650;
  line-height: 1.35;
}

.markdown-content :deep(h1) {
  font-size: 18px;
}

.markdown-content :deep(h2) {
  font-size: 16px;
}

.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  font-size: 15px;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.35rem;
}

.markdown-content :deep(ul) {
  list-style: disc;
}

.markdown-content :deep(ol) {
  list-style: decimal;
}

.markdown-content :deep(li + li) {
  margin-top: 0.2em;
}

.markdown-content :deep(strong) {
  color: rgb(255 255 255 / 0.95);
  font-weight: 650;
}

.markdown-content :deep(code) {
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 5px;
  background: rgb(255 255 255 / 0.06);
  padding: 0.1rem 0.28rem;
  color: rgb(255 255 255 / 0.9);
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 8px;
  background: rgb(0 0 0 / 0.32);
  padding: 0.75rem 0.9rem;
}

.markdown-content :deep(pre code) {
  border: 0;
  background: transparent;
  padding: 0;
}

.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-content :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 520px;
  width: auto;
  height: auto;
  border-radius: 8px;
  object-fit: contain;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid rgb(255 255 255 / 0.1);
  padding: 0.35rem 0.55rem;
}

.markdown-content :deep(th) {
  background: rgb(255 255 255 / 0.06);
  color: rgb(255 255 255 / 0.9);
  font-weight: 600;
}
</style>
