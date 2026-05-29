<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { ChatSession, ChatTurn } from '@/api/types';
import MarkdownAnswer from '@/components/MarkdownAnswer.vue';
import { isStreamDebugEnabled, logStreamDebug } from '@/utils/streamDebug';

const props = defineProps<{
  session: ChatSession | null;
  submitting: boolean;
  scopeText: string;
  healthStatus: 'checking' | 'connected' | 'disconnected' | 'network-error';
  activeCitation: { turnId: number; index: number | null } | null;
  expandedCitation: { turnId: number; indices: number[] } | null;
}>();

const emit = defineEmits<{
  (e: 'submit', question: string): void;
  (e: 'retry', question: string): void;
  (e: 'open-citation', turnId: number, index: number | null): void;
  (e: 'toggle-source', turnId: number, index: number): void;
}>();

const input = ref('');
const listRef = ref<HTMLElement | null>(null);
const loadingTextStep = ref(0);
let loadingTextTimer: number | null = null;

const presetQuestions = [
  'MyISAM 和 InnoDB 有什么区别?',
  'MySQL 的 MVCC 依赖哪些机制实现?',
  'B+ 树为什么适合数据库索引?',
  '如何防止幻读?',
];

const loadingTexts = [
  '正在等待 NoteRAG 返回',
  '正在处理这次提问',
  '仍在连接后端同步接口',
  '正在等待回答完成',
  '请稍候，仍在等待响应',
];

const turns = computed<ChatTurn[]>(() => props.session?.turns ?? []);
const scrollSignal = computed(() => {
  const lastTurn = turns.value[turns.value.length - 1];
  return [
    turns.value.length,
    lastTurn?.id ?? '',
    lastTurn?.answer.length ?? 0,
    lastTurn?.loading ? 1 : 0,
    lastTurn?.pending ? 1 : 0,
    lastTurn?.error ?? '',
    lastTurn?.sources.length ?? 0,
  ].join(':');
});

const shouldStickToBottom = ref(true);

const healthMeta = computed(() => {
  switch (props.healthStatus) {
    case 'connected':
      return {
        label: '知识库已连接',
        pillClass: 'border-emerald-300/20 bg-emerald-400/[0.08] text-emerald-100/80',
        dotClass: 'bg-emerald-300',
      };
    case 'checking':
      return {
        label: '正在检查连接',
        pillClass: 'border-white/[0.08] bg-white/[0.025] text-white/45',
        dotClass: 'bg-white/35',
      };
    case 'network-error':
      return {
        label: '网络错误',
        pillClass: 'border-rose-300/20 bg-rose-400/[0.07] text-rose-100/75',
        dotClass: 'bg-rose-300',
      };
    default:
      return {
        label: '知识库未连接',
        pillClass: 'border-white/[0.08] bg-white/[0.025] text-white/45',
        dotClass: 'bg-white/28',
      };
  }
});

watch(
  scrollSignal,
  () => {
    if (isStreamDebugEnabled()) {
      const lastTurn = turns.value[turns.value.length - 1];
      logStreamDebug('chat-panel', 'scroll-signal', {
        turnCount: turns.value.length,
        lastTurnId: lastTurn?.id ?? null,
        answerLen: lastTurn?.answer.length ?? 0,
        loading: lastTurn?.loading ?? false,
        shouldStickToBottom: shouldStickToBottom.value,
        ...getScrollMetrics(),
      });
    }
    if (!shouldStickToBottom.value) return;
    scheduleScrollToBottom();
  },
  { flush: 'post' }
);

onMounted(() => {
  loadingTextTimer = window.setInterval(() => {
    loadingTextStep.value = (loadingTextStep.value + 1) % loadingTexts.length;
  }, 2200);
});

onBeforeUnmount(() => {
  if (loadingTextTimer != null) {
    window.clearInterval(loadingTextTimer);
    loadingTextTimer = null;
  }
  if (pendingScrollFrame != null) {
    window.cancelAnimationFrame(pendingScrollFrame);
    pendingScrollFrame = null;
  }
});

function submit() {
  const q = input.value.trim();
  if (!q || props.submitting) return;
  shouldStickToBottom.value = true;
  emit('submit', q);
  input.value = '';
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    submit();
  }
}

function pick(question: string) {
  input.value = question;
}

function retry(turn: ChatTurn) {
  if (props.submitting) return;
  shouldStickToBottom.value = true;
  emit('retry', turn.question);
}

function loadingTextFor(turnId: number): string {
  return loadingTexts[(loadingTextStep.value + turnId) % loadingTexts.length];
}

function isSourceExpanded(turnId: number, index: number): boolean {
  return props.expandedCitation?.turnId === turnId && props.expandedCitation.indices.includes(index);
}

function handleSourceButtonClick(turnId: number, index: number) {
  if (isSourceExpanded(turnId, index)) {
    emit('toggle-source', turnId, index);
    return;
  }
  emit('open-citation', turnId, index);
}

const AUTO_SCROLL_BOTTOM_THRESHOLD_PX = 48;
let pendingScrollFrame: number | null = null;

function handleMessageListScroll() {
  const element = listRef.value;
  if (!element) return;
  shouldStickToBottom.value =
    element.scrollHeight - element.scrollTop - element.clientHeight <= AUTO_SCROLL_BOTTOM_THRESHOLD_PX;
  if (isStreamDebugEnabled()) {
    logStreamDebug('chat-panel', 'user-scroll', {
      shouldStickToBottom: shouldStickToBottom.value,
      ...getScrollMetrics(),
    });
  }
}

function scheduleScrollToBottom() {
  if (pendingScrollFrame != null) {
    if (isStreamDebugEnabled()) {
      logStreamDebug('chat-panel', 'scroll-skip-frame-pending', getScrollMetrics());
    }
    return;
  }

  pendingScrollFrame = window.requestAnimationFrame(async () => {
    pendingScrollFrame = null;
    await nextTick();
    const element = listRef.value;
    if (!element || !shouldStickToBottom.value) return;
    if (isStreamDebugEnabled()) {
      logStreamDebug('chat-panel', 'scroll-before', getScrollMetrics());
    }
    element.scrollTop = element.scrollHeight;
    if (isStreamDebugEnabled()) {
      logStreamDebug('chat-panel', 'scroll-after', getScrollMetrics());
    }
  });
}

function getScrollMetrics() {
  const element = listRef.value;
  if (!element) {
    return {
      scrollTop: null,
      scrollHeight: null,
      clientHeight: null,
      bottomGap: null,
    };
  }

  return {
    scrollTop: Math.round(element.scrollTop),
    scrollHeight: Math.round(element.scrollHeight),
    clientHeight: Math.round(element.clientHeight),
    bottomGap: Math.round(element.scrollHeight - element.scrollTop - element.clientHeight),
  };
}
</script>

<template>
  <section class="relative flex h-full min-h-0 flex-col overflow-hidden">
    <header class="shrink-0 px-1 pb-4">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h2 class="text-[18px] font-semibold tracking-tight text-white">Q&amp;A</h2>
          <p class="mt-1 flex items-center gap-1.5 text-[12px] text-white/40">
            <span>{{ scopeText }}</span>
          </p>
        </div>
        <span
          class="inline-flex h-7 shrink-0 items-center gap-2 rounded-lg border px-2.5 text-[12px] font-medium transition-colors duration-150"
          :class="healthMeta.pillClass"
        >
          <span class="h-1.5 w-1.5 rounded-full" :class="healthMeta.dotClass" aria-hidden="true"></span>
          {{ healthMeta.label }}
        </span>
      </div>
    </header>

    <div ref="listRef" class="min-h-0 flex-1 overflow-y-auto pr-1" @scroll="handleMessageListScroll">
      <div
        v-if="turns.length === 0"
        class="flex h-full flex-col items-center justify-center px-8 text-center"
      >
        <div
          class="mb-5 flex h-14 w-14 items-center justify-center overflow-hidden rounded-2xl border border-accent/20 bg-black/35 shadow-[0_0_42px_-20px_rgba(45,212,191,0.9)] ring-1 ring-white/[0.04]"
        >
          <img
            src="/NoteRAG.png"
            width="56"
            height="56"
            alt="NoteRAG"
            class="h-full w-full object-cover"
          />
        </div>
        <h3 class="text-[15px] font-medium text-white/85">开始向 NoteRAG 提问</h3>
        <p class="mt-1.5 text-[13px] text-white/40">输入问题或选择下方示例</p>
        <div class="mt-6 flex flex-wrap justify-center gap-2">
          <button
            v-for="q in presetQuestions"
            :key="q"
            type="button"
            class="rounded-full border border-white/[0.08] bg-white/[0.02] px-3.5 py-1.5 text-[12px] text-white/65 transition-all duration-150 hover:border-accent/40 hover:bg-accent/5 hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
            @click="pick(q)"
          >
            {{ q }}
          </button>
        </div>
      </div>

      <div v-else class="space-y-7 pb-2">
        <article v-for="turn in turns" :key="turn.id" class="space-y-4 animate-slide-up">
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
              <div v-if="turn.loading && !turn.answer" class="flex items-center gap-1.5 text-[13px] text-white/40">
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
                <span>{{ loadingTextFor(turn.id) }}…</span>
              </div>

              <div
                v-if="turn.pending"
                class="rounded-xl border border-amber-300/20 bg-amber-300/[0.06] px-3.5 py-3 text-[13px] text-amber-100/80"
              >
                回答仍在生成中，请稍后刷新会话查看。
              </div>

              <div
                v-else-if="turn.error"
                class="rounded-xl border border-rose-400/20 bg-rose-400/[0.06] px-3.5 py-3 text-[13px] text-rose-100/85"
              >
                <div class="flex items-start gap-2.5">
                  <span
                    class="mt-0.5 inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-rose-400/15 text-[12px] font-semibold text-rose-300 ring-1 ring-inset ring-rose-300/25"
                    aria-hidden="true"
                  >
                    !
                  </span>
                  <div class="min-w-0 flex-1">
                    <div class="font-medium text-rose-100">回答生成失败</div>
                    <div class="mt-1 whitespace-pre-wrap leading-relaxed text-rose-100/65">
                      {{ turn.error }}
                    </div>
                    <button
                      type="button"
                      :disabled="submitting"
                      class="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-rose-300/25 bg-rose-300/[0.08] px-3 py-1.5 text-[12px] font-medium text-rose-100 transition-all duration-150 hover:border-rose-200/45 hover:bg-rose-300/[0.13] disabled:cursor-not-allowed disabled:border-white/[0.08] disabled:bg-white/[0.04] disabled:text-white/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300/40"
                      @click="retry(turn)"
                    >
                      重新发送
                    </button>
                  </div>
                </div>
              </div>

              <div v-else>
                <MarkdownAnswer
                  :answer="turn.answer"
                  :sources="turn.sources"
                  :loading="turn.loading"
                  @open-citation="(index) => emit('open-citation', turn.id, index)"
                />

                <div
                  v-if="turn.sources.length > 0"
                  class="mt-3 flex flex-wrap items-center gap-1.5"
                >
                  <button
                    type="button"
                    class="inline-flex h-6 appearance-none items-center rounded-md border border-white/[0.08] bg-white/[0.035] px-2 text-[11px] font-medium text-white/60 shadow-none transition-all duration-150 hover:border-accent/35 hover:bg-accent/[0.08] hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
                    @click="emit('open-citation', turn.id, null)"
                  >
                    参考来源
                  </button>
                  <button
                    v-for="(_, i) in turn.sources"
                    :key="i"
                    type="button"
                    class="inline-flex h-6 min-w-[24px] items-center justify-center rounded-md border px-1.5 font-mono text-[11px] font-medium transition-all duration-150"
                    :class="
                      isSourceExpanded(turn.id, i + 1)
                        ? 'border-accent/50 bg-accent/[0.12] text-accent'
                        : 'border-white/[0.08] bg-white/[0.02] text-white/55 hover:border-accent/40 hover:bg-accent/5 hover:text-accent'
                    "
                    @click="handleSourceButtonClick(turn.id, i + 1)"
                  >
                    {{ i + 1 }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </div>

    <div class="relative mt-4 shrink-0">
      <div
        class="rounded-2xl border border-white/[0.08] bg-white/[0.02] backdrop-blur-xl transition-colors duration-150 focus-within:border-accent/40 focus-within:bg-white/[0.03]"
      >
        <textarea
          v-model="input"
          rows="2"
          maxlength="2000"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
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
            @click="submit"
          >
            发送
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
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
  </section>
</template>
