<script setup lang="ts">
import { ref } from 'vue';
import SourceCard from '@/components/SourceCard.vue';
import { searchRetrieval } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { SourceChunk } from '@/api/types';

const question = ref('');
const topN = ref<number>(20);
const submitting = ref(false);
const sources = ref<SourceChunk[]>([]);
const lastQuery = ref<string | null>(null);
const lastTopN = ref<number | null>(null);
const elapsed = ref<number | null>(null);
const toast = ref<string | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | null = null;

function showToast(msg: string) {
  toast.value = msg;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = null), 3500);
}

async function handleSubmit() {
  const q = question.value.trim();
  if (!q || submitting.value) return;
  submitting.value = true;
  const start = performance.now();
  try {
    const response = await searchRetrieval(q, topN.value);
    sources.value = response.sources ?? [];
    lastQuery.value = q;
    lastTopN.value = topN.value;
    elapsed.value = Math.round(performance.now() - start);
  } catch (error) {
    const msg = error instanceof ApiError ? error.message : '检索失败，请稍后重试';
    showToast(msg);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="mx-auto flex h-full max-w-4xl flex-col gap-5 animate-fade-in">
    <header>
      <div class="flex items-center gap-2">
        <h1 class="text-[22px] font-semibold tracking-tight text-white">检索调试</h1>
        <span
          class="rounded-md bg-amber-300/[0.08] px-2 py-0.5 font-mono text-[10px] font-medium uppercase tracking-wider text-amber-300/80"
          >debug</span
        >
      </div>
      <p class="mt-1 text-[13px] text-white/45">
        直接调用 <code class="rounded bg-white/[0.06] px-1.5 py-0.5 font-mono text-[12px] text-white/75">/api/retrieval/search</code>，查看 pgvector 原始 TopN 召回（不经过 rerank）。
      </p>
    </header>

    <section class="rounded-2xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-sm">
      <div class="grid gap-4 p-5 sm:grid-cols-[1fr_auto_auto] sm:items-end">
        <div>
          <label class="mb-1.5 block text-[12px] font-medium text-white/60">问题</label>
          <input
            v-model="question"
            type="text"
            maxlength="2000"
            placeholder="输入要检索的问题"
            class="block w-full rounded-lg border border-white/[0.08] bg-black/20 px-3 py-2 text-[14px] text-white placeholder-white/25 transition-colors duration-150 focus:border-accent/40 focus:bg-black/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            :disabled="submitting"
            @keydown.enter="handleSubmit"
          />
        </div>
        <div>
          <label class="mb-1.5 block text-[12px] font-medium text-white/60">topN</label>
          <input
            v-model.number="topN"
            type="number"
            min="1"
            max="50"
            class="block w-24 rounded-lg border border-white/[0.08] bg-black/20 px-3 py-2 font-mono text-[14px] tabular-nums text-white transition-colors duration-150 focus:border-accent/40 focus:bg-black/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            :disabled="submitting"
          />
        </div>
        <button
          type="button"
          :disabled="!question.trim() || submitting"
          class="inline-flex h-[38px] items-center gap-1.5 self-end rounded-lg bg-accent px-4 text-[13px] font-semibold text-black transition-all duration-150 hover:bg-accent-hover active:scale-[0.97] disabled:cursor-not-allowed disabled:bg-white/[0.08] disabled:text-white/30 disabled:active:scale-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
          @click="handleSubmit"
        >
          <span class="inline-flex h-3.5 w-3.5 items-center justify-center" v-if="submitting">
            <span
              class="block h-3 w-3 animate-spin rounded-full border-[1.5px] border-black/30 border-t-black"
            ></span>
          </span>
          <svg
            v-else
            class="h-3.5 w-3.5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          {{ submitting ? '检索中…' : '检索' }}
        </button>
      </div>
    </section>

    <div v-if="lastQuery" class="flex flex-wrap items-center gap-2 text-[12px] text-white/40">
      <span>结果</span>
      <span
        class="rounded-md border border-white/[0.06] bg-white/[0.03] px-2 py-0.5 text-white/70"
      >
        {{ lastQuery }}
      </span>
      <span class="rounded-md bg-white/[0.04] px-2 py-0.5 font-mono tabular-nums text-white/55">
        topN {{ lastTopN }}
      </span>
      <span class="rounded-md bg-white/[0.04] px-2 py-0.5 font-mono tabular-nums text-white/55">
        {{ sources.length }} 条
      </span>
      <span
        v-if="elapsed != null"
        class="rounded-md bg-accent/[0.08] px-2 py-0.5 font-mono tabular-nums text-accent"
      >
        {{ elapsed }}ms
      </span>
    </div>

    <div v-if="sources.length > 0" class="space-y-2 pb-4">
      <SourceCard
        v-for="(source, idx) in sources"
        :key="source.chunkId"
        :source="source"
        :index="idx + 1"
      />
    </div>

    <div
      class="rounded-xl border border-amber-300/15 bg-amber-300/[0.04] px-4 py-3 text-[12px] text-amber-200/75"
    >
      <strong class="font-semibold text-amber-300/90">TODO</strong> ·
      当前后端尚未提供 rerank 调试入口，本页只展示原始 retrieval 结果。
      可在<router-link to="/" class="ml-0.5 underline underline-offset-2 hover:text-amber-200"
        >问答页</router-link
      >对比 rerank 后效果。
    </div>

    <div
      v-if="toast"
      class="pointer-events-none fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-slide-up rounded-lg border border-rose-400/30 bg-rose-500/[0.12] px-4 py-2 text-[13px] text-rose-200 backdrop-blur-md"
      role="alert"
    >
      {{ toast }}
    </div>
  </div>
</template>
