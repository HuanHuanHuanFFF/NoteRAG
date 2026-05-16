<script setup lang="ts">
import { ref } from 'vue';
import { NButton, NInput, NInputNumber, NTag, useMessage } from 'naive-ui';
import SourceCard from '@/components/SourceCard.vue';
import { searchRetrieval } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { SourceChunk } from '@/api/types';

const question = ref('');
const topN = ref<number | null>(20);
const submitting = ref(false);
const sources = ref<SourceChunk[]>([]);
const lastQuery = ref<string | null>(null);
const lastTopN = ref<number | null>(null);
const message = useMessage();

async function handleSubmit() {
  const q = question.value.trim();
  if (!q || submitting.value) return;
  submitting.value = true;
  try {
    const response = await searchRetrieval(q, topN.value ?? undefined);
    sources.value = response.sources ?? [];
    lastQuery.value = q;
    lastTopN.value = topN.value ?? null;
  } catch (error) {
    const msg = error instanceof ApiError ? error.message : '检索失败，请稍后重试';
    message.error(msg);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="mx-auto flex h-full max-w-3xl flex-col gap-4 overflow-y-auto">
    <header>
      <h1 class="text-xl font-semibold text-slate-900">检索调试</h1>
      <p class="mt-1 text-sm text-slate-500">
        直接调用 <code class="rounded bg-slate-100 px-1">/api/retrieval/search</code>，
        查看 pgvector 原始 TopN 召回（不经过 rerank）。
      </p>
    </header>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="space-y-3">
        <div>
          <div class="mb-1 text-sm text-slate-600">问题</div>
          <n-input
            v-model:value="question"
            placeholder="输入要检索的问题"
            maxlength="2000"
            :disabled="submitting"
          />
        </div>
        <div class="flex items-end gap-3">
          <div>
            <div class="mb-1 text-sm text-slate-600">topN</div>
            <n-input-number
              v-model:value="topN"
              :min="1"
              :max="50"
              :disabled="submitting"
              clearable
              placeholder="默认 20"
            />
          </div>
          <n-button
            class="ml-auto"
            type="primary"
            :loading="submitting"
            :disabled="!question.trim()"
            @click="handleSubmit"
          >
            检索
          </n-button>
        </div>
      </div>
    </div>

    <div v-if="lastQuery" class="flex items-center gap-2 text-sm text-slate-500">
      <span>最近一次检索:</span>
      <n-tag size="small" :bordered="false">{{ lastQuery }}</n-tag>
      <n-tag size="small" :bordered="false" type="info">topN {{ lastTopN ?? '默认' }}</n-tag>
      <n-tag size="small" :bordered="false">{{ sources.length }} 条结果</n-tag>
    </div>

    <div class="space-y-2 pb-6">
      <SourceCard
        v-for="(source, idx) in sources"
        :key="source.chunkId"
        :source="source"
        :index="idx + 1"
      />
    </div>

    <div class="rounded-md bg-amber-50 border border-amber-200 px-3 py-2 text-xs text-amber-700">
      TODO: 当前后端尚未提供 rerank 调试入口，本页只展示原始 retrieval 结果。
      可在问答页对比 rerank 后效果。
    </div>
  </div>
</template>
