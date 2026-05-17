<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import NotesPanel from '@/components/NotesPanel.vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SourcesPanel from '@/components/SourcesPanel.vue';
import ImportModal from '@/components/ImportModal.vue';
import type {
  ChatSession,
  ChatTurn,
  ImportTextResponse,
  NoteListItem,
  SourceChunk,
} from '@/api/types';
import {
  buildMockAnswerText,
  mockNotes,
  mockSessions,
  pickMockSources,
  summarizeQuestion,
} from '@/utils/mockData';

const notes = ref<NoteListItem[]>([...mockNotes]);
const sessions = ref<ChatSession[]>(JSON.parse(JSON.stringify(mockSessions)));
const activeSessionId = ref<string>(sessions.value[0]?.id ?? createSessionInternal().id);

const selectedNoteId = ref<number | null>(null);
const importOpen = ref(false);

const sourcesOpen = ref(false);
const sourcesLoading = ref(false);
const sourcesData = ref<SourceChunk[]>([]);
const activeCitation = ref<{ turnId: number; index: number } | null>(null);

const activeSession = computed<ChatSession | null>(
  () => sessions.value.find((s) => s.id === activeSessionId.value) ?? null
);

const selectedNote = computed<NoteListItem | null>(() =>
  selectedNoteId.value == null
    ? null
    : notes.value.find((n) => n.id === selectedNoteId.value) ?? null
);

let nextSessionIdx = sessions.value.length + 1;
let nextTurnId = sessions.value.flatMap((s) => s.turns).reduce((max, t) => Math.max(max, t.id), 0);

function createSessionInternal(): ChatSession {
  const session: ChatSession = {
    id: `session-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    title: `新会话 ${nextSessionIdx++}`,
    noteId: selectedNoteId.value,
    turns: [],
  };
  sessions.value.unshift(session);
  return session;
}

function handleCreateSession() {
  const session = createSessionInternal();
  activeSessionId.value = session.id;
  closeSources();
}

function handleSwitchSession(id: string) {
  if (activeSessionId.value !== id) {
    activeSessionId.value = id;
    closeSources();
  }
}

function handleRenameSession(id: string, title: string) {
  const session = sessions.value.find((s) => s.id === id);
  if (session) session.title = title;
}

function handleSelectNote(id: number | null) {
  selectedNoteId.value = id;
}

function openImport() {
  importOpen.value = true;
}

function handleImported(result: ImportTextResponse) {
  notes.value.unshift({
    id: result.documentId,
    title: '新导入笔记',
    chunkCount: result.chunkCount,
    charCount: result.charCount,
    tokenCount: result.tokenCount,
    createdAt: new Date().toISOString(),
  });
}

async function handleSubmit(question: string) {
  if (!activeSession.value) return;
  const turn: ChatTurn = {
    id: ++nextTurnId,
    question,
    answer: '',
    sources: [],
    loading: true,
  };
  activeSession.value.turns.push(turn);
  if (activeSession.value.turns.length === 1) {
    activeSession.value.title = summarizeQuestion(question);
  }

  await new Promise((r) => setTimeout(r, 600 + Math.random() * 600));
  const sources = pickMockSources(question);
  turn.sources = sources;
  turn.answer = buildMockAnswerText(question, sources);
  turn.loading = false;
}

function handleOpenCitation(turnId: number, index: number) {
  const turn = activeSession.value?.turns.find((t) => t.id === turnId);
  if (!turn) return;
  if (index < 1 || index > turn.sources.length) return;

  const same =
    sourcesOpen.value &&
    activeCitation.value?.turnId === turnId &&
    sourcesData.value === turn.sources;

  if (!same) {
    sourcesOpen.value = true;
    sourcesLoading.value = true;
    sourcesData.value = [];
    activeCitation.value = { turnId, index };
    nextTick(() => {
      setTimeout(() => {
        sourcesData.value = turn.sources;
        sourcesLoading.value = false;
      }, 280);
    });
  } else {
    activeCitation.value = { turnId, index };
  }
}

function closeSources() {
  sourcesOpen.value = false;
  sourcesData.value = [];
  activeCitation.value = null;
  sourcesLoading.value = false;
}
</script>

<template>
  <div class="grid h-[calc(100vh-56px)] gap-3 px-4 py-3 lg:gap-4 lg:px-6 lg:py-4"
    :class="
      sourcesOpen
        ? 'grid-cols-[260px_1fr_400px]'
        : 'grid-cols-[260px_1fr]'
    "
  >
    <div
      class="overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] backdrop-blur-sm"
    >
      <NotesPanel
        :notes="notes"
        :selected-note-id="selectedNoteId"
        @select="handleSelectNote"
        @open-import="openImport"
      />
    </div>

    <div class="flex min-w-0 flex-col">
      <div class="flex items-center justify-between pb-3">
        <SessionSelector
          :sessions="sessions"
          :active-id="activeSessionId"
          @switch="handleSwitchSession"
          @create="handleCreateSession"
          @rename="handleRenameSession"
        />
        <div class="hidden items-center gap-2 text-[11px] text-white/35 lg:flex">
          <span class="h-1 w-1 rounded-full bg-accent/70"></span>
          <span class="font-mono">localhost:9000</span>
        </div>
      </div>
      <div
        class="flex min-h-0 flex-1 flex-col rounded-2xl border border-white/[0.06] bg-white/[0.015] p-5 backdrop-blur-sm"
      >
        <ChatPanel
          :session="activeSession"
          :selected-note="selectedNote"
          :active-citation="activeCitation"
          @submit="handleSubmit"
          @open-citation="handleOpenCitation"
        />
      </div>
    </div>

    <transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-x-4"
      enter-to-class="opacity-100 translate-x-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="opacity-0 translate-x-4"
    >
      <div
        v-if="sourcesOpen"
        class="overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] backdrop-blur-sm"
      >
        <SourcesPanel
          :sources="sourcesData"
          :highlight-index="activeCitation?.index ?? null"
          :loading="sourcesLoading"
          @close="closeSources"
        />
      </div>
    </transition>
  </div>

  <ImportModal :open="importOpen" @close="importOpen = false" @imported="handleImported" />
</template>
