<script setup lang="ts">
import { computed, ref } from 'vue';
import { importText } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { ImportTextResponse } from '@/api/types';

const title = ref('');
const content = ref('');
const submitting = ref(false);
const lastResult = ref<ImportTextResponse | null>(null);
const toast = ref<{ type: 'success' | 'error'; msg: string } | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | null = null;

const titleValid = computed(() => title.value.trim().length > 0 && title.value.length <= 255);
const contentValid = computed(
  () => content.value.trim().length > 0 && content.value.length <= 100_000
);
const canSubmit = computed(() => titleValid.value && contentValid.value && !submitting.value);

function showToast(type: 'success' | 'error', msg: string) {
  toast.value = { type, msg };
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = null), 3500);
}

async function handleSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    lastResult.value = await importText({ title: title.value.trim(), content: content.value });
    showToast('success', '导入成功');
    title.value = '';
    content.value = '';
  } catch (error) {
    const msg = error instanceof ApiError ? error.message : '导入失败，请稍后重试';
    showToast('error', msg);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="mx-auto flex h-full max-w-3xl flex-col gap-5 animate-fade-in">
    <header>
      <h1 class="text-[22px] font-semibold tracking-tight text-white">导入笔记</h1>
      <p class="mt-1 text-[13px] text-white/45">
        粘贴 Markdown 文本，后端会自动切块并向量化存入 pgvector。
      </p>
    </header>

    <section
      class="rounded-2xl border border-white/[0.06] bg-white/[0.02] backdrop-blur-sm"
    >
      <div class="space-y-4 p-5">
        <div>
          <label class="mb-1.5 block text-[12px] font-medium text-white/60">
            标题
            <span class="ml-1 text-rose-400/80">*</span>
          </label>
          <input
            v-model="title"
            type="text"
            maxlength="255"
            placeholder="如：MySQL 索引详解"
            class="block w-full rounded-lg border border-white/[0.08] bg-black/20 px-3 py-2 text-[14px] text-white placeholder-white/25 transition-colors duration-150 focus:border-accent/40 focus:bg-black/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            :disabled="submitting"
          />
          <div class="mt-1 flex justify-end">
            <span class="font-mono text-[11px] tabular-nums text-white/30">
              {{ title.length }} / 255
            </span>
          </div>
        </div>

        <div>
          <label class="mb-1.5 block text-[12px] font-medium text-white/60">
            Markdown 内容
            <span class="ml-1 text-rose-400/80">*</span>
          </label>
          <textarea
            v-model="content"
            rows="14"
            maxlength="100000"
            placeholder="# 标题&#10;&#10;正文..."
            class="block w-full resize-y rounded-lg border border-white/[0.08] bg-black/20 px-3 py-2.5 font-mono text-[13px] leading-relaxed text-white placeholder-white/25 transition-colors duration-150 focus:border-accent/40 focus:bg-black/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            :disabled="submitting"
          ></textarea>
          <div class="mt-1 flex justify-end">
            <span class="font-mono text-[11px] tabular-nums text-white/30">
              {{ content.length.toLocaleString() }} / 100,000
            </span>
          </div>
        </div>

        <div class="flex justify-end pt-1">
          <button
            type="button"
            :disabled="!canSubmit"
            class="inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2 text-[13px] font-semibold text-black transition-all duration-150 hover:bg-accent-hover active:scale-[0.97] disabled:cursor-not-allowed disabled:bg-white/[0.08] disabled:text-white/30 disabled:active:scale-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
            @click="handleSubmit"
          >
            <svg
              v-if="!submitting"
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
                d="M12 4v16m8-8H4"
              />
            </svg>
            <span class="inline-flex h-3.5 w-3.5 items-center justify-center" v-else>
              <span
                class="block h-3 w-3 animate-spin rounded-full border-[1.5px] border-black/30 border-t-black"
              ></span>
            </span>
            {{ submitting ? '导入中…' : '导入' }}
          </button>
        </div>
      </div>
    </section>

    <section
      v-if="lastResult"
      class="rounded-2xl border border-accent/20 bg-accent/[0.04] backdrop-blur-sm animate-slide-up"
    >
      <div class="px-5 py-4">
        <div class="mb-3 flex items-center gap-2">
          <span class="inline-flex h-5 w-5 items-center justify-center rounded-full bg-accent/20">
            <svg class="h-3 w-3 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.5"
                d="M5 13l4 4L19 7"
              />
            </svg>
          </span>
          <h2 class="text-[13px] font-semibold text-white">最近一次导入</h2>
        </div>
        <dl class="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-4">
          <div>
            <dt class="text-[11px] uppercase tracking-wider text-white/35">文档 ID</dt>
            <dd class="mt-1 font-mono text-[15px] tabular-nums text-white">
              {{ lastResult.documentId }}
            </dd>
          </div>
          <div>
            <dt class="text-[11px] uppercase tracking-wider text-white/35">Chunks</dt>
            <dd class="mt-1 font-mono text-[15px] tabular-nums text-white">
              {{ lastResult.chunkCount }}
            </dd>
          </div>
          <div>
            <dt class="text-[11px] uppercase tracking-wider text-white/35">字符</dt>
            <dd class="mt-1 font-mono text-[15px] tabular-nums text-white">
              {{ lastResult.charCount.toLocaleString() }}
            </dd>
          </div>
          <div>
            <dt class="text-[11px] uppercase tracking-wider text-white/35">Tokens</dt>
            <dd class="mt-1 font-mono text-[15px] tabular-nums text-white">
              {{ lastResult.tokenCount.toLocaleString() }}
            </dd>
          </div>
        </dl>
      </div>
    </section>

    <div
      v-if="toast"
      class="pointer-events-none fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-slide-up rounded-lg border px-4 py-2 text-[13px] backdrop-blur-md"
      :class="
        toast.type === 'success'
          ? 'border-accent/30 bg-accent/[0.12] text-accent'
          : 'border-rose-400/30 bg-rose-500/[0.12] text-rose-200'
      "
      role="status"
    >
      {{ toast.msg }}
    </div>
  </div>
</template>
