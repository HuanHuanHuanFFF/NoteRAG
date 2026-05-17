<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import SourceCard from '@/components/SourceCard.vue';
import type { SourceChunk } from '@/api/types';

const props = defineProps<{
  sources: SourceChunk[];
  highlightIndex: number | null;
  loading?: boolean;
}>();

defineEmits<{ (e: 'close'): void }>();

const containerRef = ref<HTMLElement | null>(null);
const expandedSet = ref<Set<number>>(new Set());

const items = computed(() =>
  props.sources.map((source, idx) => ({
    source,
    index: idx + 1,
  }))
);

watch(
  () => props.sources,
  () => {
    expandedSet.value =
      props.highlightIndex == null ? new Set() : new Set([props.highlightIndex]);
  }
);

watch(
  () => props.highlightIndex,
  async (idx) => {
    if (idx == null) {
      expandedSet.value = new Set();
      return;
    }
    const next = new Set(expandedSet.value);
    next.add(idx);
    expandedSet.value = next;
    await new Promise((r) => requestAnimationFrame(r));
    const el = containerRef.value?.querySelector(`#source-${idx}`) as HTMLElement | null;
    if (el && containerRef.value) {
      const top = el.offsetTop - containerRef.value.offsetTop - 12;
      containerRef.value.scrollTo({ top, behavior: 'smooth' });
    }
  },
  { flush: 'post' }
);

function toggle(index: number) {
  const next = new Set(expandedSet.value);
  if (next.has(index)) next.delete(index);
  else next.add(index);
  expandedSet.value = next;
}
</script>

<template>
  <aside class="flex h-full flex-col">
    <header class="flex items-center justify-between border-b border-white/[0.04] px-5 py-4">
      <div class="flex items-center gap-2">
        <h2 class="text-[14px] font-semibold tracking-tight text-white">Sources</h2>
        <span class="font-mono text-[11px] tabular-nums text-white/30">
          {{ sources.length }}
        </span>
      </div>
      <button
        type="button"
        class="inline-flex h-7 w-7 items-center justify-center rounded-md text-white/45 transition-colors duration-150 hover:bg-white/[0.06] hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
        title="关闭来源面板"
        aria-label="关闭来源面板"
        @click="$emit('close')"
      >
        <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </header>

    <div ref="containerRef" class="flex-1 space-y-3 overflow-y-auto px-4 py-4">
      <div v-if="loading" class="flex h-full items-center justify-center">
        <span class="inline-flex items-center gap-2 text-[12px] text-white/40">
          <span class="block h-3 w-3 animate-spin rounded-full border-[1.5px] border-white/20 border-t-accent"></span>
          加载来源…
        </span>
      </div>

      <SourceCard
        v-for="item in items"
        v-else
        :key="item.source.chunkId"
        :source="item.source"
        :index="item.index"
        :highlight="highlightIndex === item.index"
        :expanded="expandedSet.has(item.index)"
        @toggle="toggle(item.index)"
      />

      <div
        v-if="!loading && items.length === 0"
        class="flex h-full flex-col items-center justify-center px-4 text-center text-[12px] text-white/35"
      >
        <span>暂无来源</span>
      </div>
    </div>
  </aside>
</template>
