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

const MAX_CONTENT_LENGTH = 100_000;
const TITLE_MAX_LENGTH = 255;

const fileInput = ref<HTMLInputElement | null>(null);
const selectedFileName = ref('');
const title = ref('');
const content = ref('');
const readingFile = ref(false);
const submitting = ref(false);
const error = ref<string | null>(null);
const lastResult = ref<ImportTextResponse | null>(null);

const titleValid = computed(
  () => title.value.trim().length > 0 && title.value.length <= TITLE_MAX_LENGTH
);
const contentValid = computed(
  () => content.value.trim().length > 0 && content.value.length <= MAX_CONTENT_LENGTH
);
const busy = computed(() => readingFile.value || submitting.value);
const canSubmit = computed(() => titleValid.value && contentValid.value && !busy.value);
const submitLabel = computed(() => {
  if (readingFile.value) return '读取中…';
  if (submitting.value) return '导入中…';
  return '导入';
});
const selectedFileHint = computed(() =>
  selectedFileName.value
    ? `${content.value.length.toLocaleString()} / ${MAX_CONTENT_LENGTH.toLocaleString()} 字符`
    : '支持 .md / .markdown'
);

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetSelection();
    }
  }
);

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  error.value = null;
  lastResult.value = null;
  resetSelectedFile();

  if (!file) return;

  if (!isMarkdownFile(file.name)) {
    error.value = '请选择 .md 或 .markdown 文件';
    resetFileInput();
    return;
  }

  const generatedTitle = filenameToTitle(file.name);
  if (generatedTitle.length === 0) {
    error.value = '文件名不能只包含扩展名';
    resetFileInput();
    return;
  }
  if (generatedTitle.length > TITLE_MAX_LENGTH) {
    error.value = `文件名生成的标题不能超过 ${TITLE_MAX_LENGTH} 个字符`;
    resetFileInput();
    return;
  }

  readingFile.value = true;
  try {
    const text = await file.text();
    if (text.trim().length === 0) {
      error.value = 'Markdown 文件内容不能为空';
      resetFileInput();
      return;
    }
    if (text.length > MAX_CONTENT_LENGTH) {
      error.value = `Markdown 内容不能超过 ${MAX_CONTENT_LENGTH.toLocaleString()} 字符，当前 ${text.length.toLocaleString()} 字符`;
      resetFileInput();
      return;
    }

    selectedFileName.value = file.name;
    title.value = generatedTitle;
    content.value = text;
    resetFileInput();
  } catch {
    error.value = '文件读取失败，请重新选择';
    resetFileInput();
  } finally {
    readingFile.value = false;
  }
}

function openFilePicker() {
  if (!busy.value) {
    fileInput.value?.click();
  }
}

async function handleSubmit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  error.value = null;
  try {
    const result = await importText({ title: title.value.trim(), content: content.value });
    lastResult.value = result;
    emit('imported', result);
    resetSelectedFile();
    resetFileInput();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '导入失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

function close() {
  if (!busy.value) emit('close');
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) close();
}

onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));

function isMarkdownFile(filename: string): boolean {
  return /\.(md|markdown)$/i.test(filename);
}

function filenameToTitle(filename: string): string {
  return filename.replace(/\.(md|markdown)$/i, '').trim();
}

function resetSelection(clearResult = true) {
  error.value = null;
  if (clearResult) lastResult.value = null;
  resetSelectedFile();
  resetFileInput();
}

function resetSelectedFile() {
  selectedFileName.value = '';
  title.value = '';
  content.value = '';
}

function resetFileInput() {
  if (fileInput.value) {
    fileInput.value.value = '';
  }
}
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
                选择本地 Markdown 笔记，导入后即可用于问答
              </p>
            </div>
            <button
              type="button"
              class="close-icon-button h-8 w-8 rounded-md"
              :disabled="busy"
              title="关闭"
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
                Markdown 文件
                <span class="ml-1 text-rose-400/80">*</span>
              </label>
              <input
                ref="fileInput"
                type="file"
                accept=".md,.markdown"
                class="sr-only"
                tabindex="-1"
                :disabled="busy"
                @change="handleFileChange"
              />
              <div
                class="flex min-h-16 items-center gap-3 rounded-lg border border-white/[0.08] bg-white/[0.03] px-3.5 py-3 transition-colors duration-150 hover:border-white/[0.14]"
              >
                <div
                  class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-accent/20 bg-accent/[0.08] text-accent"
                  aria-hidden="true"
                >
                  <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.8"
                      d="M7 3.75h7.25L19 8.5v11.75H7z"
                    />
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.8"
                      d="M14 3.75V9h5M9.5 13h5M9.5 16h4"
                    />
                  </svg>
                </div>
                <div class="min-w-0 flex-1">
                  <div
                    class="truncate text-[13px] font-medium text-white/85"
                    :title="selectedFileName || '请选择 Markdown 文件'"
                  >
                    {{ selectedFileName || '请选择 Markdown 文件' }}
                  </div>
                  <div class="mt-0.5 font-mono text-[11px] tabular-nums text-white/35">
                    {{ selectedFileHint }}
                  </div>
                </div>
                <button
                  type="button"
                  class="inline-flex h-11 shrink-0 items-center justify-center rounded-lg border border-white/[0.08] bg-white/[0.04] px-3.5 text-[12px] font-medium text-white/75 transition-colors duration-150 hover:border-accent/35 hover:bg-accent/[0.08] hover:text-accent disabled:cursor-not-allowed disabled:opacity-45 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
                  :disabled="busy"
                  @click="openFilePicker"
                >
                  {{ selectedFileName ? '更换文件' : '选择文件' }}
                </button>
              </div>
            </div>

            <div v-if="selectedFileName">
              <div class="mb-1.5 flex items-center justify-between gap-3">
                <label class="block text-[12px] font-medium text-white/60">
                  笔记标题
                  <span class="ml-1 text-rose-400/80">*</span>
                </label>
                <span class="font-mono text-[11px] tabular-nums text-white/30">
                  {{ title.length.toLocaleString() }} / {{ TITLE_MAX_LENGTH }}
                </span>
              </div>
              <input
                v-model="title"
                type="text"
                :maxlength="TITLE_MAX_LENGTH"
                class="block h-10 w-full rounded-lg border border-white/[0.08] bg-black/20 px-3 text-[14px] text-white placeholder-white/25 transition-colors duration-150 focus:border-accent/40 focus:bg-black/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-45"
                placeholder="输入导入后的笔记标题"
                :disabled="busy"
              />
              <p v-if="!titleValid" class="mt-1.5 text-[11px] text-rose-200/80">
                标题不能为空，且不能超过 {{ TITLE_MAX_LENGTH }} 个字符。
              </p>
            </div>

            <div
              class="rounded-lg border border-white/[0.08] bg-white/[0.025] px-3 py-2 text-[12px] leading-relaxed text-white/48"
            >
              导入后标题和内容暂不可修改，请确认文件和标题无误后再导入。
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
              <dl class="grid grid-cols-4 gap-x-4 gap-y-2 text-[12px]">
                <div class="min-w-0">
                  <dt class="text-white/40">ID</dt>
                  <dd class="m-0 mt-1 font-mono text-white">{{ lastResult.documentId }}</dd>
                </div>
                <div class="min-w-0">
                  <dt class="text-white/40">Chunks</dt>
                  <dd class="m-0 mt-1 font-mono text-white">{{ lastResult.chunkCount }}</dd>
                </div>
                <div class="min-w-0">
                  <dt class="text-white/40">字符</dt>
                  <dd class="m-0 mt-1 font-mono text-white">{{ lastResult.charCount.toLocaleString() }}</dd>
                </div>
                <div class="min-w-0">
                  <dt class="text-white/40">Tokens</dt>
                  <dd class="m-0 mt-1 font-mono text-white">{{ lastResult.tokenCount.toLocaleString() }}</dd>
                </div>
              </dl>
            </div>
          </div>

          <footer class="flex justify-end gap-2 border-t border-white/[0.06] px-6 py-4">
            <button
              type="button"
              class="rounded-lg border border-white/[0.08] bg-white/[0.02] px-4 py-2 text-[13px] font-medium text-white/75 transition-colors duration-150 hover:bg-white/[0.06] hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="busy"
              @click="close"
            >
              {{ lastResult ? '关闭' : '取消' }}
            </button>
            <button
              type="button"
              :disabled="!canSubmit"
              class="send-button gap-1.5 rounded-lg px-4 py-2 text-[13px] font-semibold"
              @click="handleSubmit"
            >
              <span class="inline-flex h-3.5 w-3.5 items-center justify-center" v-if="busy">
                <span class="block h-3 w-3 animate-spin rounded-full border-[1.5px] border-current/30 border-t-current"></span>
              </span>
              {{ submitLabel }}
            </button>
          </footer>
        </div>
      </transition>
    </div>
  </transition>
</template>
