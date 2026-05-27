<script setup lang="ts">
import { computed, onMounted } from 'vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import NotesPanel from '@/components/NotesPanel.vue';
import NoteDetailModal from '@/components/NoteDetailModal.vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SourcesPanel from '@/components/SourcesPanel.vue';
import ImportModal from '@/components/ImportModal.vue';
import { useChatSessions } from '@/composables/useChatSessions';
import { useChatSubmit } from '@/composables/useChatSubmit';
import { useHealthStatus } from '@/composables/useHealthStatus';
import { useNotes } from '@/composables/useNotes';
import { useResizableNotesPanel } from '@/composables/useResizableNotesPanel';
import { useSourcesPanel } from '@/composables/useSourcesPanel';
import { createWorkspaceSeed } from '@/utils/workspaceSeed';

const layoutDemoEnabled = import.meta.env.VITE_ENABLE_LAYOUT_DEMO === 'true';
const workspaceSeed = createWorkspaceSeed();

const {
  sessions,
  activeSessionId,
  sessionsLoading,
  workspaceError,
  sessionDeleteTarget,
  sessionDeleteBusy,
  sessionDeleteError,
  sessionActionBusyId,
  sessionActionError,
  loadingMessageSessionIds,
  activeSession,
  initializeFromSeed,
  loadSessions,
  handleCreateSession: createChatSession,
  handleSwitchSession: switchChatSession,
  handleRenameSession,
  handleDeleteSessionRequest,
  confirmDeleteSession: confirmChatSessionDelete,
  createSessionInternal,
  setActiveSessionId,
  setWorkspaceError,
  appendPendingTurn,
  applyChatMeta,
  appendAnswerDelta,
  applyChatResponse,
  applyChatFailure,
  finishTurn,
} = useChatSessions({ initialSessions: layoutDemoEnabled ? workspaceSeed.sessions : [] });

const {
  sourcesOpen,
  sourcesClosing,
  sourcesLoading,
  sourcesData,
  activeCitation,
  expandedCitation,
  handleOpenCitation,
  closeSources,
  handleSourcesAfterLeave,
  handleSourcesExpandedChange,
  handleToggleSource,
} = useSourcesPanel({
  getSourcesForTurn(turnId) {
    return activeSession.value?.turns.find((turn) => turn.id === turnId)?.sources ?? null;
  },
  initialSourcesOpen: layoutDemoEnabled ? workspaceSeed.sourcesOpen : false,
  initialSourcesData: layoutDemoEnabled ? workspaceSeed.sourcesData : [],
  initialActiveCitation: layoutDemoEnabled ? workspaceSeed.activeCitation : null,
  initialExpandedCitation: layoutDemoEnabled ? workspaceSeed.expandedCitation : null,
});

const {
  notes,
  importOpen,
  notesLoading,
  notesError,
  detailNoteId,
  noteDetailError,
  noteDeleteTarget,
  noteDeleteBusy,
  noteDeleteError,
  selectedNoteIdList,
  scopeText,
  activeNoteDetail,
  noteDetailLoading,
  noteDeleteMessage,
  loadNotes,
  handleToggleNoteScope,
  handleOpenNoteDetail,
  closeNoteDetail,
  handleDeleteNoteRequest,
  confirmDeleteNote,
  openImport,
  handleImported,
} = useNotes({
  initialNotes: layoutDemoEnabled ? workspaceSeed.notes : [],
  setWorkspaceError,
});

const {
  notesResizable,
  notesResizing,
  workspaceGridColumns,
  workspaceMinWidth,
  startNotesResize,
  adjustNotesWidth,
} = useResizableNotesPanel(computed(() => sourcesOpen.value || sourcesClosing.value));

const { healthStatus } = useHealthStatus();

const {
  activeSessionSubmitting,
  handleSubmit,
  handleRetry,
  clearSessionSubmitting,
} = useChatSubmit({
  sessionsLoading,
  activeSession,
  loadingMessageSessionIds,
  selectedNoteIds: selectedNoteIdList,
  createSession: createSessionInternal,
  setActiveSessionId,
  appendPendingTurn,
  applyChatMeta,
  appendAnswerDelta,
  applyChatResponse,
  applyChatFailure,
  finishTurn,
});

const sessionDeleteMessage = computed(() =>
  sessionDeleteTarget.value
    ? `确定删除「${sessionDeleteTarget.value.title}」吗？删除后该会话记录会从列表中移除。`
    : ''
);

if (layoutDemoEnabled) {
  initializeFromSeed();
}

onMounted(() => {
  if (!layoutDemoEnabled) {
    void initializeWorkspace();
  }
});

async function initializeWorkspace() {
  await Promise.allSettled([loadNotes(), loadSessions()]);
}

function handleCreateSession() {
  createChatSession();
  closeSources();
}

function handleSwitchSession(id: string) {
  if (switchChatSession(id)) {
    closeSources();
  }
}

async function confirmDeleteSession() {
  const result = await confirmChatSessionDelete();
  if (!result) return;
  clearSessionSubmitting(result.removedId);
  if (result.wasActive) {
    closeSources();
  }
}
</script>

<template>
  <div class="relative h-[calc(100vh-56px)] min-h-0 overflow-x-auto overflow-y-hidden">
    <div
      class="grid h-full min-h-0 gap-3 overflow-y-hidden px-4 py-3 lg:gap-4 lg:px-6 lg:py-4"
      :style="{ gridTemplateColumns: workspaceGridColumns, minWidth: workspaceMinWidth }"
    >
      <div
        class="relative min-h-0 overflow-visible rounded-2xl border border-white/[0.06] bg-white/[0.015] backdrop-blur-sm"
      >
        <NotesPanel
          :notes="notes"
          :notes-loading="notesLoading"
          :notes-error="notesError"
          :selected-note-ids="selectedNoteIdList"
          @toggle="handleToggleNoteScope"
          @open-detail="handleOpenNoteDetail"
          @delete="handleDeleteNoteRequest"
          @open-import="openImport"
        />
        <div
          v-if="notesResizable"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整 Notes 栏宽度"
          title="调整 Notes 栏宽度"
          tabindex="0"
          class="group absolute -right-3 top-2 z-20 flex h-[calc(100%-1rem)] w-6 cursor-col-resize items-center justify-center rounded-full outline-none"
          @pointerdown.prevent="startNotesResize"
          @keydown.left.prevent="adjustNotesWidth(-16)"
          @keydown.right.prevent="adjustNotesWidth(16)"
        >
          <span
            class="h-16 w-1 rounded-full bg-white/[0.08] transition-colors duration-150 group-hover:bg-accent/45 group-focus-visible:bg-accent/60"
            :class="notesResizing ? 'bg-accent/70' : ''"
            aria-hidden="true"
          ></span>
        </div>
      </div>

      <div class="flex min-h-0 min-w-0 flex-col overflow-hidden">
        <div class="flex shrink-0 items-center justify-between gap-3 pb-3">
          <SessionSelector
            :sessions="sessions"
            :active-id="activeSessionId"
            :busy-id="sessionActionBusyId"
            :error="sessionActionError"
            @switch="handleSwitchSession"
            @create="handleCreateSession"
            @rename="handleRenameSession"
            @delete="handleDeleteSessionRequest"
          />
          <div class="min-w-0 flex-1 text-right">
            <span v-if="sessionsLoading" class="text-[12px] text-white/35">正在加载历史会话...</span>
            <span v-else-if="workspaceError" class="text-[12px] text-rose-200/80">
              {{ workspaceError }}
            </span>
          </div>
        </div>
        <div
          class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-white/[0.06] bg-white/[0.015] p-5 backdrop-blur-sm"
        >
          <ChatPanel
            :session="activeSession"
            :submitting="activeSessionSubmitting"
            :scope-text="scopeText"
            :health-status="healthStatus"
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
    <NoteDetailModal
      :open="detailNoteId != null"
      :note="activeNoteDetail"
      :loading="noteDetailLoading"
      :error="noteDetailError"
      @close="closeNoteDetail"
    />
    <ConfirmDialog
      :open="noteDeleteTarget != null"
      title="删除笔记"
      :message="noteDeleteMessage"
      confirm-label="删除笔记"
      :busy="noteDeleteBusy"
      :error="noteDeleteError"
      @close="noteDeleteTarget = null"
      @confirm="confirmDeleteNote"
    />
    <ConfirmDialog
      :open="sessionDeleteTarget != null"
      title="删除会话"
      :message="sessionDeleteMessage"
      confirm-label="删除会话"
      :busy="sessionDeleteBusy"
      :error="sessionDeleteError"
      @close="sessionDeleteTarget = null"
      @confirm="confirmDeleteSession"
    />
  </div>
</template>
