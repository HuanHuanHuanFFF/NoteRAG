<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { importText } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { ImportTextResponse } from '@/api/types';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'imported', result: ImportTextResponse): void;
}>();

const title = ref('');
const content = ref('');
const submitting = ref(false);
const error = ref<string | null>(null);
const lastResult = ref<ImportTextResponse | null>(null);

const titleValid = computed(() => title.value.trim().length > 0 && title.value.length <= 255);
const contentValid = computed(
  () => content.value.trim().length > 0 && content.value.length <= 100_000
);
const canSubmit = computed(() => titleValid.value && contentValid.value && !submitting.value);

watch(
  () => props.open,
  (open) => {
    if (open) {
      error.value = null;
      lastResult.value = null;
    }
  }
);

async function handleSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = null;
  try {
    const result = await importText({ title: title.value.trim(), content: content.value });
    lastResult.value = result;
    emit('imported', result);
    title.value = '';
    content.value = '';
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '导入失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

function close() {
  if (!submitting.value) emit('close');
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) close();
}

onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<template>
  <transition
    enter-active-class="transition duration-150 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-100 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      @click.self="close"
    >
      <transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0 scale-[0.98] translate-y-2"
        enter-to-class="opacity-100 scale-100 translate-y-0"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
        appear
      >
        <div
          class="relative w-full max-w-2xl rounded-2xl border border-white/[0.08] bg-[#0f0f13]/95 shadow-2xl backdrop-blur-xl"
        >
          <header class="flex items-center justify-between border-b border-white/[0.06] px-6 py-4">
            <div>
              <h3 class="text-[16px] font-semibold text-white">导入笔记</h3>
              <p class="mt-0.5 text-[12px] text-white/45">
                Markdown 文本，后端会自动切块并向量化
              </p>
            </div>
            <button
              type="button"
              class="inline-flex h-7 w-7 items-center justify-center rounded-md text-white/45 transition-colors duration-150 hover:bg-white/[0.06] hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="submitting"
              aria-label="关闭"
              @click="close"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div class="space-y-4 px-6 py-5">
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
            </div>

            <div>
              <label class="mb-1.5 block text-[12px] font-medium text-white/60">
                Markdown 内容
                <span class="ml-1 text-rose-400/80">*</span>
              </label>
              <textarea
                v-model="content"
                rows="12"
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

            <div
              v-if="error"
              class="rounded-lg border border-rose-400/30 bg-rose-500/[0.08] px-3 py-2 text-[12px] text-rose-200"
              role="alert"
            >
              {{ error }}
            </div>

            <div
              v-if="lastResult"
              class="rounded-lg border border-accent/20 bg-accent/[0.05] px-4 py-3"
            >
              <div class="mb-2 flex items-center gap-1.5 text-[12px] font-medium text-accent">
                <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
                </svg>
                导入成功
              </div>
              <dl class="grid grid-cols-4 gap-x-4 gap-y-1 text-[12px]">
                <div>
                  <dt class="text-white/40">ID</dt>
                  <dd class="font-mono text-white">{{ lastResult.documentId }}</dd>
                </div>
                <div>
                  <dt class="text-white/40">Chunks</dt>
                  <dd class="font-mono text-white">{{ lastResult.chunkCount }}</dd>
                </div>
                <div>
                  <dt class="text-white/40">字符</dt>
                  <dd class="font-mono text-white">{{ lastResult.charCount.toLocaleString() }}</dd>
                </div>
                <div>
                  <dt class="text-white/40">Tokens</dt>
                  <dd class="font-mono text-white">{{ lastResult.tokenCount.toLocaleString() }}</dd>
                </div>
              </dl>
            </div>
          </div>

          <footer class="flex justify-end gap-2 border-t border-white/[0.06] px-6 py-4">
            <button
              type="button"
              class="rounded-lg border border-white/[0.08] bg-white/[0.02] px-4 py-2 text-[13px] font-medium text-white/75 transition-colors duration-150 hover:bg-white/[0.06] hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="submitting"
              @click="close"
            >
              {{ lastResult ? '关闭' : '取消' }}
            </button>
            <button
              type="button"
              :disabled="!canSubmit"
              class="inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2 text-[13px] font-semibold text-black transition-all duration-150 hover:bg-accent-hover active:scale-[0.97] disabled:cursor-not-allowed disabled:bg-white/[0.08] disabled:text-white/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
              @click="handleSubmit"
            >
              <span class="inline-flex h-3.5 w-3.5 items-center justify-center" v-if="submitting">
                <span class="block h-3 w-3 animate-spin rounded-full border-[1.5px] border-black/30 border-t-black"></span>
              </span>
              {{ submitting ? '导入中…' : '导入' }}
            </button>
          </footer>
        </div>
      </transition>
    </div>
  </transition>
</template>
