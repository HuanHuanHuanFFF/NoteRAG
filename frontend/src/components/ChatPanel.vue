<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import type { ChatSession, ChatTurn, NoteListItem } from '@/api/types';

const props = defineProps<{
  session: ChatSession | null;
  selectedNotes: NoteListItem[];
  activeCitation: { turnId: number; index: number | null } | null;
  expandedCitation: { turnId: number; indices: number[] } | null;
}>();

const emit = defineEmits<{
  (e: 'submit', question: string): void;
  (e: 'open-citation', turnId: number, index: number | null): void;
  (e: 'toggle-source', turnId: number, index: number): void;
}>();

const input = ref('');
const listRef = ref<HTMLElement | null>(null);

const presetQuestions = [
  'MyISAM 和 InnoDB 有什么区别?',
  'MySQL 的 MVCC 依赖哪些机制实现?',
  'B+ 树为什么适合数据库索引?',
  '如何防止幻读?',
];

const turns = computed<ChatTurn[]>(() => props.session?.turns ?? []);
const scopeText = computed(() => {
  const count = props.selectedNotes.length;
  if (count === 0) return '跨全部笔记检索';
  if (count === 1) return `已限定到笔记 ${props.selectedNotes[0].title}`;
  return `已限定到 ${count} 篇笔记`;
});

watch(
  turns,
  async () => {
    await nextTick();
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight;
  },
  { deep: true, flush: 'post' }
);

function submit() {
  const q = input.value.trim();
  if (!q) return;
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

interface AnswerSegment {
  type: 'text' | 'citation';
  value: string;
  index?: number;
}

function parseAnswer(answer: string): AnswerSegment[] {
  const segments: AnswerSegment[] = [];
  const regex = /\[(\d+)\]/g;
  let lastIndex = 0;
  let match;
  while ((match = regex.exec(answer)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: answer.slice(lastIndex, match.index) });
    }
    segments.push({ type: 'citation', value: match[0], index: parseInt(match[1], 10) });
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < answer.length) {
    segments.push({ type: 'text', value: answer.slice(lastIndex) });
  }
  return segments;
}

function isCitationActive(turnId: number, index: number): boolean {
  return props.activeCitation?.turnId === turnId && props.activeCitation?.index === index;
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
</script>

<template>
  <section class="relative flex h-full flex-col">
    <header class="px-1 pb-4">
      <div class="flex items-center gap-2">
        <h2 class="text-[18px] font-semibold tracking-tight text-white">Q&amp;A</h2>
        <span
          class="rounded-md bg-amber-300/[0.08] px-2 py-0.5 font-mono text-[10px] font-medium uppercase tracking-wider text-amber-300/85"
        >
          mock data
        </span>
      </div>
      <p class="mt-1 flex items-center gap-1.5 text-[12px] text-white/40">
        <span v-if="selectedNotes.length > 0" class="inline-flex items-center gap-1">
          <svg class="h-3 w-3 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <span class="font-medium text-white/70">{{ scopeText }}</span>
        </span>
        <span v-else>{{ scopeText }}</span>
      </p>
    </header>

    <div ref="listRef" class="flex-1 overflow-y-auto pr-1">
      <div
        v-if="turns.length === 0"
        class="flex h-full flex-col items-center justify-center px-8 text-center"
      >
        <div
          class="mb-5 flex h-12 w-12 items-center justify-center rounded-2xl border border-white/[0.08] bg-white/[0.02] backdrop-blur-sm"
        >
          <svg class="h-5 w-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="1.8"
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
            />
          </svg>
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

              <div v-else>
                <div class="text-[14px] leading-[1.75] text-white/85">
                  <template
                    v-for="(seg, i) in parseAnswer(turn.answer)"
                    :key="i"
                  >
                    <span v-if="seg.type === 'text'" class="whitespace-pre-wrap">{{ seg.value }}</span>
                    <button
                      v-else
                      type="button"
                      class="mx-0.5 inline-flex h-[18px] min-w-[20px] items-center justify-center rounded px-1 align-middle font-mono text-[11px] font-medium transition-all duration-150"
                      :class="
                        isCitationActive(turn.id, seg.index!)
                          ? 'bg-accent/20 text-accent ring-1 ring-accent/50'
                          : 'bg-white/[0.06] text-accent hover:bg-accent/15'
                      "
                      :aria-label="`查看来源 ${seg.index}`"
                      @click="emit('open-citation', turn.id, seg.index!)"
                    >
                      {{ seg.index }}
                    </button>
                  </template>
                </div>

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

    <div class="relative mt-4">
      <div
        class="rounded-2xl border border-white/[0.08] bg-white/[0.02] backdrop-blur-xl transition-colors duration-150 focus-within:border-accent/40 focus-within:bg-white/[0.03]"
      >
        <textarea
          v-model="input"
          rows="2"
          maxlength="2000"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          class="block w-full resize-none rounded-2xl bg-transparent px-4 py-3 text-[14px] leading-relaxed text-white placeholder-white/30 focus:outline-none"
          @keydown="handleKeydown"
        />
        <div class="flex items-center justify-between px-4 pb-3">
          <span class="font-mono text-[11px] tabular-nums text-white/30">
            {{ input.length }} / 2000
          </span>
          <button
            type="button"
            :disabled="!input.trim()"
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
