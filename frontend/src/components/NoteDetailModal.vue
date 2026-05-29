<script setup lang="ts">
import MarkdownContent from '@/components/MarkdownContent.vue';
import type { NoteDetailResponse } from '@/api/types';

defineProps<{
  open: boolean;
  note: NoteDetailResponse | null;
  loading: boolean;
  error: string | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

function formatNumber(value: number | undefined) {
  return (value ?? 0).toLocaleString();
}

function close() {
  emit('close');
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
      class="fixed inset-0 z-[60] flex items-center justify-center bg-black/65 px-4 py-6 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="查看笔记内容"
      @click.self="close"
    >
      <transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0 scale-[0.98] translate-y-2"
        enter-to-class="opacity-100 scale-100 translate-y-0"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0 scale-[0.98] translate-y-1"
        appear
      >
        <section
          class="flex max-h-[86vh] w-full max-w-4xl min-w-0 flex-col overflow-hidden rounded-2xl border border-white/[0.08] bg-[#0f0f13]/95 shadow-2xl ring-1 ring-white/[0.04]"
        >
          <header class="flex shrink-0 items-start justify-between gap-4 border-b border-white/[0.06] px-6 py-4">
            <div class="min-w-0">
              <p class="text-[11px] font-medium uppercase text-white/35">Note</p>
              <h2 class="mt-1 truncate text-[17px] font-semibold text-white">
                {{ note?.title ?? '查看笔记内容' }}
              </h2>
              <div v-if="note" class="mt-2 flex flex-wrap gap-2 text-[11px] text-white/38">
                <span class="rounded-md border border-white/[0.06] bg-white/[0.03] px-2 py-1 font-mono">
                  {{ formatNumber(note.charCount) }} chars
                </span>
                <span class="rounded-md border border-white/[0.06] bg-white/[0.03] px-2 py-1 font-mono">
                  {{ formatNumber(note.tokenCount) }} tokens
                </span>
              </div>
            </div>
            <button
              type="button"
              class="close-icon-button h-10 w-10 shrink-0 rounded-lg"
              title="关闭"
              aria-label="关闭笔记详情"
              @click="close"
            >
              <svg class="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div class="min-h-[360px] min-w-0 flex-1 overflow-y-auto overflow-x-hidden px-6 py-5">
            <div v-if="loading" class="flex min-h-[320px] items-center justify-center">
              <span class="inline-flex items-center gap-2 text-[13px] text-white/45">
                <span class="h-3.5 w-3.5 animate-spin rounded-full border-[1.5px] border-white/20 border-t-accent"></span>
                正在加载笔记内容...
              </span>
            </div>

            <div
              v-else-if="error"
              class="rounded-xl border border-rose-400/20 bg-rose-400/[0.06] px-4 py-3 text-[13px] leading-relaxed text-rose-100/80"
              role="alert"
            >
              {{ error }}
            </div>

            <MarkdownContent v-else-if="note" :content="note.content" />

            <div v-else class="flex min-h-[320px] items-center justify-center text-[13px] text-white/35">
              暂无笔记内容
            </div>
          </div>
        </section>
      </transition>
    </div>
  </transition>
</template>
