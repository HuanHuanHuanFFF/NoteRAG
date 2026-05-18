<script setup lang="ts">
import { computed } from 'vue';
import type { SourceChunk } from '@/api/types';

const props = defineProps<{
  source: SourceChunk;
  index: number;
  highlight?: boolean;
  expanded?: boolean;
}>();

const emit = defineEmits<{ (e: 'toggle'): void }>();

const headingDisplay = computed(() => props.source.headingPath?.trim() || '—');
const scoreDisplay = computed(() => {
  const s = props.source.score;
  return s == null ? '—' : s.toFixed(4);
});
</script>

<template>
  <article
    :id="`source-${index}`"
    class="rounded-xl border bg-white/[0.02] backdrop-blur-sm transition-all duration-200"
    :class="
      highlight
        ? 'border-accent/40 ring-1 ring-accent/30 shadow-[0_0_24px_-12px_rgba(45,212,191,0.48)]'
        : 'border-white/[0.06]'
    "
  >
    <header class="flex items-start gap-2.5 px-4 pt-3.5">
      <span
        class="mt-0.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded px-1 font-mono text-[11px] font-medium text-accent ring-1 ring-inset ring-accent/30"
      >
        {{ index }}
      </span>
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-[13px] font-semibold text-white/90">{{ source.title }}</h3>
        <p class="mt-0.5 truncate text-[11px] text-white/40">{{ headingDisplay }}</p>
      </div>
      <span class="shrink-0 font-mono text-[11px] tabular-nums text-white/35">
        {{ scoreDisplay }}
      </span>
    </header>

    <div class="px-4 pb-3.5 pt-2.5">
      <button
        type="button"
        class="inline-flex appearance-none items-center gap-1 rounded border border-transparent bg-transparent px-0 text-[11px] font-medium text-white/40 shadow-none transition-colors duration-150 hover:text-accent focus:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
        :aria-expanded="expanded"
        @click="emit('toggle')"
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
        {{ expanded ? '收起内容' : '展开完整内容' }}
      </button>
      <pre
        v-if="expanded"
        class="mt-2 overflow-x-auto whitespace-pre-wrap break-words rounded-md border border-white/[0.05] bg-black/30 p-3 font-mono text-[12px] leading-relaxed text-white/75"
        >{{ source.content }}</pre
      >
    </div>
  </article>
</template>
