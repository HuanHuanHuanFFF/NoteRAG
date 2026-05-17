<script setup lang="ts">
import { computed, ref } from 'vue';
import type { SourceChunk } from '@/api/types';

const props = defineProps<{
  source: SourceChunk;
  index: number;
}>();

const expanded = ref(false);
const headingDisplay = computed(() => props.source.headingPath?.trim() || '—');
const scoreDisplay = computed(() => {
  const s = props.source.score;
  return s == null ? '—' : s.toFixed(4);
});
const contentPreview = computed(() => {
  const flat = props.source.content.replace(/\s+/g, ' ').trim();
  return flat.length > 140 ? flat.slice(0, 140) + '…' : flat;
});
</script>

<template>
  <article
    class="group rounded-xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-sm transition-colors duration-150 hover:border-white/[0.12] hover:bg-white/[0.03]"
  >
    <header class="flex items-start gap-3 px-4 py-3">
      <span
        class="mt-0.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded px-1 font-mono text-[11px] font-medium text-accent ring-1 ring-inset ring-accent/30"
      >
        {{ index }}
      </span>
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-[13px] font-semibold text-white/90">{{ source.title }}</h3>
        <p class="mt-0.5 truncate text-[11px] text-white/40">
          <span class="text-white/30">章节</span>
          <span class="ml-1.5">{{ headingDisplay }}</span>
        </p>
      </div>
      <span class="shrink-0 font-mono text-[11px] tabular-nums text-white/35">
        {{ scoreDisplay }}
      </span>
    </header>

    <div class="px-4 pb-3">
      <div
        v-if="!expanded"
        class="font-mono text-[12px] leading-relaxed text-white/55 line-clamp-2"
      >
        {{ contentPreview }}
      </div>
      <pre
        v-else
        class="overflow-x-auto whitespace-pre-wrap break-words rounded-md border border-white/[0.05] bg-black/30 p-3 font-mono text-[12px] leading-relaxed text-white/75"
        >{{ source.content }}</pre
      >
      <button
        type="button"
        class="mt-2 inline-flex items-center gap-1 rounded text-[11px] font-medium text-white/40 transition-colors duration-150 hover:text-accent focus:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        <svg
          class="h-3 w-3 transition-transform duration-200"
          :class="expanded ? 'rotate-90' : ''"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
        {{ expanded ? '收起' : '展开完整内容' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
