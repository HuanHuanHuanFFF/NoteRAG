<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import NotesPanel from '@/components/NotesPanel.vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SourcesPanel from '@/components/SourcesPanel.vue';
import ImportModal from '@/components/ImportModal.vue';
import { ApiError } from '@/api/client';
import { sendChatMessage, sendFirstChatMessage } from '@/api/noterag';
import type {
  ChatSession,
  ChatTurn,
  ImportTextResponse,
  NoteListItem,
  SourceChunk,
} from '@/api/types';
import { createWorkspaceSeed } from '@/utils/workspaceSeed';

const workspaceSeed = createWorkspaceSeed();

const notes = ref<NoteListItem[]>(workspaceSeed.notes);
const sessions = ref<ChatSession[]>(workspaceSeed.sessions);
const selectedNoteIds = ref<Set<number>>(new Set());
const activeSessionId = ref<string>('');
const importOpen = ref(false);

const sourcesOpen = ref(workspaceSeed.sourcesOpen);
const sourcesClosing = ref(false);
const sourcesLoading = ref(false);
const sourcesData = ref<SourceChunk[]>(workspaceSeed.sourcesData);
const activeCitation = ref<{ turnId: number; index: number | null } | null>(
  workspaceSeed.activeCitation
);
const expandedCitation = ref<{ turnId: number; indices: number[] } | null>(
  workspaceSeed.expandedCitation
);
let sourcesRequestToken = 0;

const activeSession = computed<ChatSession | null>(
  () => sessions.value.find((s) => s.id === activeSessionId.value) ?? null
);

const activeSessionSubmitting = computed(
  () => activeSession.value?.turns.some((turn) => turn.loading) ?? false
);

const selectedNoteIdList = computed<number[]>(() => [...selectedNoteIds.value]);

const selectedNotes = computed<NoteListItem[]>(() =>
  notes.value.filter((note) => selectedNoteIds.value.has(note.id))
);

let nextSessionIdx = 1;
let nextTurnId = 0;

initializeSessionCounters();

function initializeSessionCounters() {
  nextSessionIdx = sessions.value.length + 1;
  nextTurnId = Math.max(
    0,
    ...sessions.value.flatMap((session) => session.turns.map((turn) => turn.id))
  );
}

function createSessionInternal(): ChatSession {
  const session: ChatSession = {
    id: `session-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    title: `新会话 ${nextSessionIdx++}`,
    turns: [],
  };
  sessions.value.unshift(session);
  return session;
}

activeSessionId.value = sessions.value[0]?.id ?? createSessionInternal().id;

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

function handleToggleNote(id: number) {
  const next = new Set(selectedNoteIds.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selectedNoteIds.value = next;
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
  const session = activeSession.value ?? createSessionInternal();
  activeSessionId.value = session.id;
  const turn: ChatTurn = {
    id: ++nextTurnId,
    question,
    answer: '',
    sources: [],
    loading: true,
  };
  session.turns.push(turn);

  try {
    const response =
      session.backendSessionId == null
        ? await sendFirstChatMessage(question)
        : await sendChatMessage(session.backendSessionId, question);

    session.backendSessionId = response.sessionId;
    session.title = response.sessionTitle?.trim() || session.title;
    turn.userMessageId = response.userMessageId;
    turn.assistantMessageId = response.assistantMessageId;
    turn.answer = response.answer ?? '';
    turn.sources = response.sources ?? [];
  } catch (e) {
    turn.error = e instanceof ApiError ? e.message : '发送失败，请稍后重试';
    turn.answer = '';
    turn.sources = [];
  } finally {
    turn.loading = false;
  }
}

function handleRetry(question: string) {
  if (activeSessionSubmitting.value) return;
  void handleSubmit(question);
}

function handleOpenCitation(turnId: number, index: number | null) {
  const turn = activeSession.value?.turns.find((t) => t.id === turnId);
  if (!turn) return;
  if (index != null && (index < 1 || index > turn.sources.length)) return;

  const same =
    sourcesOpen.value &&
    activeCitation.value?.turnId === turnId &&
    sourcesData.value === turn.sources;

  if (!same) {
    const requestToken = ++sourcesRequestToken;
    sourcesClosing.value = false;
    sourcesOpen.value = true;
    sourcesLoading.value = true;
    sourcesData.value = [];
    activeCitation.value = { turnId, index };
    expandedCitation.value = { turnId, indices: [] };
    nextTick(() => {
      setTimeout(() => {
        if (requestToken !== sourcesRequestToken || !sourcesOpen.value) return;
        sourcesData.value = turn.sources;
        sourcesLoading.value = false;
      }, 280);
    });
  } else {
    activeCitation.value = { turnId, index };
    if (index == null) {
      expandedCitation.value = { turnId, indices: [] };
    } else {
      const current = expandedCitation.value?.turnId === turnId ? expandedCitation.value.indices : [];
      expandedCitation.value = {
        turnId,
        indices: current.includes(index) ? current : [...current, index],
      };
    }
  }
}

function closeSources() {
  sourcesRequestToken++;
  if (!sourcesOpen.value) {
    resetSources();
    sourcesClosing.value = false;
    return;
  }
  sourcesOpen.value = false;
  sourcesLoading.value = false;
  sourcesClosing.value = true;
}

function resetSources() {
  sourcesData.value = [];
  activeCitation.value = null;
  expandedCitation.value = null;
  sourcesLoading.value = false;
}

function handleSourcesAfterLeave() {
  if (!sourcesClosing.value) return;
  resetSources();
  sourcesClosing.value = false;
}

function handleSourcesExpandedChange(indices: number[]) {
  if (activeCitation.value == null) return;
  expandedCitation.value = {
    turnId: activeCitation.value.turnId,
    indices,
  };
}

function handleToggleSource(turnId: number, index: number) {
  if (expandedCitation.value?.turnId !== turnId) {
    handleOpenCitation(turnId, index);
    return;
  }

  const next = expandedCitation.value.indices.includes(index)
    ? expandedCitation.value.indices.filter((item) => item !== index)
    : [...expandedCitation.value.indices, index];

  expandedCitation.value = { turnId, indices: next };
}
</script>

<template>
  <div class="relative h-[calc(100vh-56px)] min-h-0 overflow-x-auto overflow-y-hidden">
    <div
      class="grid h-full min-h-0 gap-3 overflow-y-hidden px-4 py-3 lg:gap-4 lg:px-6 lg:py-4"
      :class="
        sourcesOpen || sourcesClosing
          ? 'min-w-[900px] grid-cols-[240px_minmax(320px,1fr)_minmax(320px,400px)] lg:min-w-0 lg:grid-cols-[260px_minmax(0,1fr)_400px]'
          : 'min-w-[620px] grid-cols-[240px_minmax(320px,1fr)] lg:min-w-0 lg:grid-cols-[260px_minmax(0,1fr)]'
      "
    >
      <div
        class="min-h-0 overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] backdrop-blur-sm"
      >
        <NotesPanel
          :notes="notes"
          :selected-note-ids="selectedNoteIdList"
          @toggle="handleToggleNote"
          @open-import="openImport"
        />
      </div>

      <div class="flex min-h-0 min-w-0 flex-col overflow-hidden">
        <div class="flex shrink-0 items-center justify-between pb-3">
          <SessionSelector
            :sessions="sessions"
            :active-id="activeSessionId"
            @switch="handleSwitchSession"
            @create="handleCreateSession"
            @rename="handleRenameSession"
          />
        </div>
        <div
          class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] p-5 backdrop-blur-sm"
        >
          <ChatPanel
            :session="activeSession"
            :selected-notes="selectedNotes"
            :submitting="activeSessionSubmitting"
            :active-citation="activeCitation"
            :expanded-citation="expandedCitation"
            @submit="handleSubmit"
            @retry="handleRetry"
            @open-citation="handleOpenCitation"
            @toggle-source="handleToggleSource"
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
        @after-leave="handleSourcesAfterLeave"
      >
        <div
          v-if="sourcesOpen"
          class="min-h-0 overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] backdrop-blur-sm"
        >
          <SourcesPanel
            :sources="sourcesData"
            :highlight-index="activeCitation?.index ?? null"
            :expanded-indices="expandedCitation?.indices ?? []"
            :loading="sourcesLoading"
            @close="closeSources"
            @expanded-change="handleSourcesExpandedChange"
          />
        </div>
      </transition>
    </div>

    <ImportModal :open="importOpen" @close="importOpen = false" @imported="handleImported" />
  </div>
</template>
