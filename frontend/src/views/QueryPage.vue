<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { NButton, NInput, NSpin, NEmpty, useMessage } from 'naive-ui';
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
const message = useMessage();
const listRef = ref<HTMLElement | null>(null);
let nextId = 1;

async function handleSubmit() {
  const question = input.value.trim();
  if (!question || submitting.value) return;
  if (question.length > 2000) {
    message.warning('问题长度不能超过 2000 字');
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
    message.error(msg);
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
</script>

<template>
  <div class="mx-auto flex h-full max-w-3xl flex-col">
    <header class="mb-4">
      <h1 class="text-xl font-semibold text-slate-900">问答</h1>
      <p class="mt-1 text-sm text-slate-500">
        基于已导入的笔记进行问答。Answer is mocked, sources are real.
      </p>
    </header>

    <div ref="listRef" class="flex-1 space-y-6 overflow-y-auto pr-1">
      <n-empty
        v-if="turns.length === 0"
        description="开始向 NoteRAG 提问吧"
        class="mt-20"
      />
      <article
        v-for="turn in turns"
        :key="turn.id"
        class="space-y-3 rounded-xl border border-slate-200 bg-white p-4"
      >
        <div class="flex items-start gap-2">
          <span
            class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-medium text-slate-600"
            >Q</span
          >
          <div class="flex-1 whitespace-pre-wrap text-sm text-slate-900">{{ turn.question }}</div>
        </div>
        <div class="flex items-start gap-2">
          <span
            class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-xs font-medium text-emerald-700"
            >A</span
          >
          <div class="flex-1">
            <n-spin v-if="turn.loading" size="small" />
            <div v-else-if="turn.error" class="text-sm text-red-600">{{ turn.error }}</div>
            <div v-else class="space-y-2">
              <div class="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                {{ turn.answer }}
              </div>
              <div v-if="turn.answerIsMocked" class="text-xs text-amber-600">
                * 该回答为前端 mock，sources 来自后端真实数据
              </div>
            </div>
          </div>
        </div>
        <div v-if="!turn.loading && turn.sources.length > 0" class="space-y-2 pt-2">
          <div class="text-xs font-medium text-slate-500">参考来源（{{ turn.sources.length }}）</div>
          <SourceCard
            v-for="(source, idx) in turn.sources"
            :key="`${turn.id}-${source.chunkId}`"
            :source="source"
            :index="idx + 1"
          />
        </div>
      </article>
    </div>

    <div class="mt-4 rounded-xl border border-slate-200 bg-white p-3">
      <n-input
        v-model:value="input"
        type="textarea"
        placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :disabled="submitting"
        @keydown="handleKeydown"
      />
      <div class="mt-2 flex items-center justify-between">
        <span class="text-xs text-slate-400">{{ input.length }} / 2000</span>
        <n-button type="primary" :loading="submitting" :disabled="!input.trim()" @click="handleSubmit">
          发送
        </n-button>
      </div>
    </div>
  </div>
</template>
