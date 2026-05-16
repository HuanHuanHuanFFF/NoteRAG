<script setup lang="ts">
import { computed, ref } from 'vue';
import { NCollapse, NCollapseItem, NTag } from 'naive-ui';
import type { SourceChunk } from '@/api/types';

const props = defineProps<{
  source: SourceChunk;
  index: number;
}>();

const collapseValue = ref<string[]>([]);
const headingDisplay = computed(() => props.source.headingPath?.trim() || '—');
const scoreDisplay = computed(() => {
  const score = props.source.score;
  return score == null ? '—' : score.toFixed(4);
});
</script>

<template>
  <div class="rounded-lg border border-slate-200 bg-white px-4 py-3">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <div class="flex items-center gap-2 text-sm">
          <n-tag size="small" :bordered="false" type="info">[{{ index }}]</n-tag>
          <span class="font-medium text-slate-900 truncate">{{ source.title }}</span>
        </div>
        <div class="mt-1 text-xs text-slate-500 truncate">章节: {{ headingDisplay }}</div>
      </div>
      <div class="text-xs text-slate-400 shrink-0 tabular-nums">score {{ scoreDisplay }}</div>
    </div>
    <n-collapse v-model:expanded-names="collapseValue" class="mt-2" arrow-placement="right">
      <n-collapse-item title="展开内容" name="content">
        <pre
          class="whitespace-pre-wrap break-words text-[13px] leading-relaxed text-slate-700 font-sans"
          >{{ source.content }}</pre
        >
      </n-collapse-item>
    </n-collapse>
  </div>
</template>
