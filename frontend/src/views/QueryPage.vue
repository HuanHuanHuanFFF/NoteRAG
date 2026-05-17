<script setup lang="ts">
import { nextTick, ref } from 'vue';
import SourceCard from '@/components/SourceCard.vue';
import { query } from '@/api/noterag';
import { ApiError } from '@/api/client';
import type { SourceChunk } from '@/api/types';
import { buildMockAnswer } from '@/utils/mockAnswer';

interface ChatTurn {
  id: number;
  question: string;
  answer: string;
  answerIsMocked: boolean;
  sources: SourceChunk[];
  loading: boolean;
  error?: string;
}

const turns = ref<ChatTurn[]>([]);
const input = ref('');
const submitting = ref(false);
const toast = ref<string | null>(null);
const listRef = ref<HTMLElement | null>(null);
let nextId = 1;
let toastTimer: ReturnType<typeof setTimeout> | null = null;

function showToast(msg: string) {
  toast.value = msg;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = null), 3500);
}

async function handleSubmit() {
  const question = input.value.trim();
  if (!question || submitting.value) return;
  if (question.length > 2000) {
    showToast('问题长度不能超过 2000 字');
    return;
  }
  const turn: ChatTurn = {
    id: nextId++,
    question,
    answer: '',
    answerIsMocked: true,
    sources: [],
    loading: true,
  };
  turns.value.push(turn);
  input.value = '';
  submitting.value = true;
  await scrollToBottom();
  try {
    const response = await query(question);
    turn.sources = response.sources ?? [];
    if (response.answer && response.answer.trim()) {
      turn.answer = response.answer;
      turn.answerIsMocked = false;
    } else {
      turn.answer = buildMockAnswer(question, turn.sources);
      turn.answerIsMocked = true;
    }
  } catch (error) {
    const msg = error instanceof ApiError ? error.message : '请求失败，请稍后重试';
    turn.error = msg;
    showToast(msg);
  } finally {
    turn.loading = false;
    submitting.value = false;
    await scrollToBottom();
  }
}

async function scrollToBottom() {
  await nextTick();
  const el = listRef.value;
  if (el) el.scrollTop = el.scrollHeight;
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    void handleSubmit();
  }
}

const presetQuestions = [
  'MyISAM 和 InnoDB 有什么区别?',
  'MySQL 的 MVCC 依赖哪些机制实现?',
  '如何防止幻读?',
];
</script>

<template>
  <div class="flex h-[calc(100vh-104px)] flex-col">
    <header class="mb-5">
      <h1 class="text-[22px] font-semibold tracking-tight text-white">问答</h1>
      <p class="mt-1 text-[13px] text-white/45">
        基于已导入的笔记进行问答
        <span class="mx-2 text-white/20">·</span>
        <span class="font-mono text-[12px] text-amber-300/80">answer is mocked, sources are real</span>
      </p>
    </header>

    <div ref="listRef" class="flex-1 space-y-5 overflow-y-auto pr-1">
      <div
        v-if="turns.length === 0"
        class="flex h-full flex-col items-center justify-center text-center animate-fade-in"
      >
        <div
          class="mb-5 flex h-12 w-12 items-center justify-center rounded-2xl border border-white/[0.08] bg-white/[0.02] backdrop-blur-sm"
        >
          <svg
            class="h-5 w-5 text-accent"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
            />
          </svg>
        </div>
        <h2 class="text-[15px] font-medium text-white/85">开始向 NoteRAG 提问</h2>
        <p class="mt-1.5 text-[13px] text-white/40">输入问题或选择下方示例</p>
        <div class="mt-6 flex flex-wrap justify-center gap-2">
          <button
            v-for="q in presetQuestions"
            :key="q"
            type="button"
            class="rounded-full border border-white/[0.08] bg-white/[0.02] px-3.5 py-1.5 text-[12px] text-white/65 transition-all duration-150 hover:border-accent/40 hover:bg-accent/5 hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
            @click="input = q"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <article
        v-for="turn in turns"
        :key="turn.id"
        class="space-y-4 animate-slide-up"
      >
        <div class="flex items-start gap-3">
          <span
            class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/[0.06] text-[11px] font-semibold text-white/70"
            >Q</span
          >
          <div class="flex-1 whitespace-pre-wrap pt-0.5 text-[14px] leading-relaxed text-white/85">
            {{ turn.question }}
          </div>
        </div>

        <div class="flex items-start gap-3">
          <span
            class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-accent/15 text-[11px] font-semibold text-accent ring-1 ring-inset ring-accent/30"
            >A</span
          >
          <div class="min-w-0 flex-1 pt-0.5">
            <div v-if="turn.loading" class="flex items-center gap-1.5 text-[13px] text-white/40">
              <span class="inline-flex gap-1">
                <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-accent/60"></span>
                <span
                  class="h-1.5 w-1.5 animate-pulse rounded-full bg-accent/60"
                  style="animation-delay: 150ms"
                ></span>
                <span
                  class="h-1.5 w-1.5 animate-pulse rounded-full bg-accent/60"
                  style="animation-delay: 300ms"
                ></span>
              </span>
              <span>正在检索笔记…</span>
            </div>
            <div v-else-if="turn.error" class="text-[13px] text-rose-400">{{ turn.error }}</div>
            <div v-else class="space-y-2.5">
              <div class="whitespace-pre-wrap text-[14px] leading-[1.7] text-white/85">
                {{ turn.answer }}
              </div>
              <div
                v-if="turn.answerIsMocked"
                class="inline-flex items-center gap-1.5 rounded-md bg-amber-300/[0.08] px-2 py-1 text-[11px] text-amber-300/85"
              >
                <span class="h-1 w-1 rounded-full bg-amber-300/80"></span>
                Mock answer · sources are real
              </div>
            </div>
          </div>
        </div>

        <div v-if="!turn.loading && turn.sources.length > 0" class="space-y-2 pl-9">
          <div class="text-[11px] font-medium uppercase tracking-wider text-white/35">
            参考来源 · {{ turn.sources.length }}
          </div>
          <div class="space-y-2">
            <SourceCard
              v-for="(source, idx) in turn.sources"
              :key="`${turn.id}-${source.chunkId}`"
              :source="source"
              :index="idx + 1"
            />
          </div>
        </div>
      </article>
    </div>

    <div class="relative mt-5">
      <div
        class="rounded-2xl border border-white/[0.08] bg-white/[0.02] backdrop-blur-xl transition-colors duration-150 focus-within:border-accent/40 focus-within:bg-white/[0.03]"
      >
        <textarea
          v-model="input"
          rows="2"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          maxlength="2000"
          class="block w-full resize-none rounded-2xl bg-transparent px-4 py-3 text-[14px] leading-relaxed text-white placeholder-white/30 focus:outline-none"
          :disabled="submitting"
          @keydown="handleKeydown"
        />
        <div class="flex items-center justify-between px-4 pb-3">
          <span class="font-mono text-[11px] tabular-nums text-white/30">
            {{ input.length }} / 2000
          </span>
          <button
            type="button"
            :disabled="!input.trim() || submitting"
            class="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3.5 py-1.5 text-[13px] font-semibold text-black transition-all duration-150 hover:bg-accent-hover active:scale-[0.97] disabled:cursor-not-allowed disabled:bg-white/[0.08] disabled:text-white/30 disabled:active:scale-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
            @click="handleSubmit"
          >
            <span>{{ submitting ? '发送中' : '发送' }}</span>
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
                d="M14 5l7 7m0 0l-7 7m7-7H3"
              />
            </svg>
          </button>
        </div>
      </div>
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
