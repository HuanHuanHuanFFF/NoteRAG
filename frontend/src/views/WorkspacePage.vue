<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import NotesPanel from '@/components/NotesPanel.vue';
import NoteDetailModal from '@/components/NoteDetailModal.vue';
import SessionSelector from '@/components/SessionSelector.vue';
import ChatPanel from '@/components/ChatPanel.vue';
import SourcesPanel from '@/components/SourcesPanel.vue';
import ImportModal from '@/components/ImportModal.vue';
import { ApiError } from '@/api/client';
import {
  checkHealth,
  deleteChatSession,
  deleteNote,
  getNoteDetail as fetchNoteDetail,
  listChatMessages,
  listChatSessions,
  listNotes,
  MAX_NOTE_IDS,
  renameChatSession,
  sendChatMessage,
  sendFirstChatMessage,
} from '@/api/noterag';
import type {
  ChatHistoryMessageResponse,
  ChatSession,
  ChatSessionItemResponse,
  ChatTurn,
  ImportTextResponse,
  NoteDetailResponse,
  NoteListItem,
  SourceChunk,
} from '@/api/types';
import { createWorkspaceSeed } from '@/utils/workspaceSeed';

type HealthStatus = 'checking' | 'connected' | 'disconnected' | 'network-error';

const NOTES_WIDTH_STORAGE_KEY = 'noterag.notesPanelWidth';
const NOTES_WIDTH_DEFAULT = 320;
const NOTES_WIDTH_MIN = 224;
const NOTES_WIDTH_MAX = 420;
const NOTES_RESIZE_BREAKPOINT = 1024;
const HEALTH_CHECK_INTERVAL_MS = 30_000;

const layoutDemoEnabled = import.meta.env.VITE_ENABLE_LAYOUT_DEMO === 'true';
const workspaceSeed = createWorkspaceSeed();

const notes = ref<NoteListItem[]>(layoutDemoEnabled ? workspaceSeed.notes : []);
const sessions = ref<ChatSession[]>(layoutDemoEnabled ? workspaceSeed.sessions : []);
const activeSessionId = ref<string>('');
const importOpen = ref(false);

const notesLoading = ref(false);
const notesError = ref<string | null>(null);
const sessionsLoading = ref(false);
const workspaceError = ref<string | null>(null);
const selectedNoteIds = ref<Set<number>>(new Set());
const detailNoteId = ref<number | null>(null);
const noteDetails = ref<Record<number, NoteDetailResponse>>({});
const noteDetailError = ref<string | null>(null);
const loadingNoteDetailId = ref<number | null>(null);
const healthStatus = ref<HealthStatus>('checking');
const notesPanelWidth = ref(readStoredNotesWidth());
const notesResizable = ref(isLargeViewport());
const notesResizing = ref(false);
const noteDeleteTarget = ref<NoteListItem | null>(null);
const noteDeleteBusy = ref(false);
const noteDeleteError = ref<string | null>(null);
const sessionDeleteTarget = ref<ChatSession | null>(null);
const sessionDeleteBusy = ref(false);
const sessionDeleteError = ref<string | null>(null);
const sessionActionBusyId = ref<string | null>(null);
const sessionActionError = ref<string | null>(null);

const sourcesOpen = ref(layoutDemoEnabled ? workspaceSeed.sourcesOpen : false);
const sourcesClosing = ref(false);
const sourcesLoading = ref(false);
const sourcesData = ref<SourceChunk[]>(layoutDemoEnabled ? workspaceSeed.sourcesData : []);
const activeCitation = ref<{ turnId: number; index: number | null } | null>(
  layoutDemoEnabled ? workspaceSeed.activeCitation : null
);
const expandedCitation = ref<{ turnId: number; indices: number[] } | null>(
  layoutDemoEnabled ? workspaceSeed.expandedCitation : null
);
let sourcesRequestToken = 0;
let noteDetailRequestToken = 0;
let notesResizeStartX = 0;
let notesResizeStartWidth = NOTES_WIDTH_DEFAULT;
let healthCheckTimer: number | null = null;

const loadedNoteDetailIds = new Set<number>();
const loadingMessageSessionIds = ref<Set<number>>(new Set());

const activeSession = computed<ChatSession | null>(
  () => sessions.value.find((s) => s.id === activeSessionId.value) ?? null
);

const activeSessionSubmitting = computed(() => {
  if (sessionsLoading.value) return true;
  const session = activeSession.value;
  if (!session) return false;
  return (
    session.turns.some((turn) => turn.loading) ||
    (session.backendSessionId != null && loadingMessageSessionIds.value.has(session.backendSessionId))
  );
});

const selectedNoteIdList = computed(() => {
  const noteOrder = new Map(notes.value.map((note, index) => [note.id, index]));
  return [...selectedNoteIds.value].sort(
    (a, b) => (noteOrder.get(a) ?? Number.MAX_SAFE_INTEGER) - (noteOrder.get(b) ?? Number.MAX_SAFE_INTEGER)
  );
});

const scopeText = computed(() => {
  const count = selectedNoteIds.value.size;
  if (count === 0) return '跨全部笔记检索';
  if (count === 1) return '已选择 1 篇笔记';
  return `已选择 ${count} 篇笔记`;
});

const activeNoteDetail = computed<NoteDetailResponse | null>(() => {
  if (detailNoteId.value == null) return null;
  return noteDetails.value[detailNoteId.value] ?? null;
});

const noteDetailLoading = computed(
  () => detailNoteId.value != null && loadingNoteDetailId.value === detailNoteId.value
);

const notesColumnWidth = computed(() => (notesResizable.value ? notesPanelWidth.value : NOTES_WIDTH_MIN));

const workspaceGridColumns = computed(() => {
  const notesColumn = `${notesColumnWidth.value}px`;
  if (sourcesOpen.value || sourcesClosing.value) {
    return `${notesColumn} minmax(320px, 1fr) minmax(320px, 400px)`;
  }
  return `${notesColumn} minmax(320px, 1fr)`;
});

const workspaceMinWidth = computed(() => (sourcesOpen.value || sourcesClosing.value ? '900px' : '620px'));

const noteDeleteMessage = computed(() =>
  noteDeleteTarget.value
    ? `确定删除「${noteDeleteTarget.value.title}」吗？删除后该笔记会从当前知识库移除。`
    : ''
);

const sessionDeleteMessage = computed(() =>
  sessionDeleteTarget.value
    ? `确定删除「${sessionDeleteTarget.value.title}」吗？删除后该会话记录会从列表中移除。`
    : ''
);

let nextSessionIdx = 1;
let nextTurnId = 0;

if (layoutDemoEnabled) {
  initializeSessionCounters();
  activeSessionId.value = sessions.value[0]?.id ?? createSessionInternal().id;
}

onMounted(() => {
  updateNotesResizeAvailability();
  window.addEventListener('resize', updateNotesResizeAvailability);
  void loadHealth();
  healthCheckTimer = window.setInterval(() => {
    void loadHealth();
  }, HEALTH_CHECK_INTERVAL_MS);
  if (!layoutDemoEnabled) {
    void initializeWorkspace();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateNotesResizeAvailability);
  if (healthCheckTimer != null) {
    window.clearInterval(healthCheckTimer);
    healthCheckTimer = null;
  }
  stopNotesResize();
});

async function initializeWorkspace() {
  await Promise.allSettled([loadNotes(), loadSessions()]);
}

function readStoredNotesWidth() {
  if (typeof window === 'undefined') return NOTES_WIDTH_DEFAULT;
  const stored = Number(window.localStorage.getItem(NOTES_WIDTH_STORAGE_KEY));
  if (!Number.isFinite(stored)) return NOTES_WIDTH_DEFAULT;
  return clampNotesWidth(stored);
}

function isLargeViewport() {
  return typeof window !== 'undefined' && window.innerWidth >= NOTES_RESIZE_BREAKPOINT;
}

function updateNotesResizeAvailability() {
  notesResizable.value = isLargeViewport();
  if (!notesResizable.value && notesResizing.value) {
    stopNotesResize();
  }
}

function clampNotesWidth(width: number) {
  return Math.min(NOTES_WIDTH_MAX, Math.max(NOTES_WIDTH_MIN, Math.round(width)));
}

function persistNotesWidth() {
  window.localStorage.setItem(NOTES_WIDTH_STORAGE_KEY, String(notesPanelWidth.value));
}

async function loadHealth() {
  healthStatus.value = 'checking';
  try {
    await checkHealth();
    healthStatus.value = 'connected';
  } catch (e) {
    healthStatus.value = e instanceof ApiError && e.httpStatus !== 0 ? 'disconnected' : 'network-error';
  }
}

function startNotesResize(event: PointerEvent) {
  if (!notesResizable.value) return;
  notesResizing.value = true;
  notesResizeStartX = event.clientX;
  notesResizeStartWidth = notesPanelWidth.value;
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  window.addEventListener('pointermove', handleNotesResizeMove);
  window.addEventListener('pointerup', stopNotesResize);
  window.addEventListener('pointercancel', stopNotesResize);
}

function handleNotesResizeMove(event: PointerEvent) {
  if (!notesResizing.value) return;
  notesPanelWidth.value = clampNotesWidth(notesResizeStartWidth + event.clientX - notesResizeStartX);
}

function stopNotesResize() {
  if (notesResizing.value) {
    persistNotesWidth();
  }
  notesResizing.value = false;
  if (typeof document !== 'undefined') {
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', handleNotesResizeMove);
    window.removeEventListener('pointerup', stopNotesResize);
    window.removeEventListener('pointercancel', stopNotesResize);
  }
}

function adjustNotesWidth(delta: number) {
  if (!notesResizable.value) return;
  notesPanelWidth.value = clampNotesWidth(notesPanelWidth.value + delta);
  persistNotesWidth();
}

function initializeSessionCounters() {
  nextSessionIdx = sessions.value.length + 1;
  nextTurnId = Math.max(
    0,
    ...sessions.value.flatMap((session) => session.turns.map((turn) => turn.id))
  );
}

function createSessionInternal(): ChatSession {
  const session: ChatSession = {
    id: `local-session-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    title: `新会话 ${nextSessionIdx++}`,
    turns: [],
  };
  sessions.value.unshift(session);
  return session;
}

async function loadNotes(preferredNoteId?: number) {
  notesLoading.value = true;
  notesError.value = null;
  try {
    const response = await listNotes();
    notes.value = response.notes ?? [];
    const existingIds = new Set(notes.value.map((note) => note.id));
    selectedNoteIds.value = new Set([...selectedNoteIds.value].filter((id) => existingIds.has(id)));
    if (preferredNoteId != null && notes.value.some((note) => note.id === preferredNoteId)) {
      await handleOpenNoteDetail(preferredNoteId);
    } else if (detailNoteId.value != null && !existingIds.has(detailNoteId.value)) {
      closeNoteDetail();
    }
  } catch (e) {
    notes.value = [];
    notesError.value = e instanceof ApiError ? e.message : '笔记列表加载失败';
  } finally {
    notesLoading.value = false;
  }
}

async function loadSessions() {
  sessionsLoading.value = true;
  workspaceError.value = null;
  try {
    const response = await listChatSessions();
    const backendSessions = sortBackendSessions(response.sessions ?? []);
    sessions.value = backendSessions.map(toFrontendSession);
    initializeSessionCounters();

    const firstSession = sessions.value[0];
    if (firstSession) {
      activeSessionId.value = firstSession.id;
      await loadMessagesForSession(firstSession);
    } else {
      activeSessionId.value = createSessionInternal().id;
    }
  } catch (e) {
    workspaceError.value = e instanceof ApiError ? e.message : '历史会话加载失败';
    if (sessions.value.length === 0) {
      activeSessionId.value = createSessionInternal().id;
    }
  } finally {
    sessionsLoading.value = false;
  }
}

function sortBackendSessions(items: ChatSessionItemResponse[]) {
  return [...items].sort((a, b) => sessionTimeValue(b) - sessionTimeValue(a));
}

function sessionTimeValue(session: ChatSessionItemResponse) {
  return Date.parse(session.lastMessageAt ?? session.updatedAt ?? session.createdAt) || 0;
}

function toFrontendSession(session: ChatSessionItemResponse): ChatSession {
  return {
    id: backendSessionLocalId(session.id),
    backendSessionId: session.id,
    title: session.title?.trim() || `会话 ${session.id}`,
    turns: [],
    messagesLoaded: false,
  };
}

function backendSessionLocalId(sessionId: number) {
  return `backend-session-${sessionId}`;
}

async function loadMessagesForSession(session: ChatSession) {
  const backendSessionId = session.backendSessionId;
  if (
    backendSessionId == null ||
    session.messagesLoaded ||
    loadingMessageSessionIds.value.has(backendSessionId)
  ) {
    return;
  }

  setSessionMessagesLoading(backendSessionId, true);
  const localSessionId = session.id;
  const originalTurnCount = session.turns.length;
  if (activeSessionId.value === localSessionId) {
    workspaceError.value = null;
  }

  try {
    const response = await listChatMessages(backendSessionId);
    if (session.turns.length !== originalTurnCount) {
      return;
    }
    session.turns = toHistoryTurns(response.messages ?? []);
    session.messagesLoaded = true;
  } catch (e) {
    if (activeSessionId.value === localSessionId) {
      workspaceError.value = e instanceof ApiError ? e.message : '历史消息加载失败';
    }
  } finally {
    setSessionMessagesLoading(backendSessionId, false);
  }
}

function setSessionMessagesLoading(sessionId: number, loading: boolean) {
  const next = new Set(loadingMessageSessionIds.value);
  if (loading) {
    next.add(sessionId);
  } else {
    next.delete(sessionId);
  }
  loadingMessageSessionIds.value = next;
}

function toHistoryTurns(messages: ChatHistoryMessageResponse[]): ChatTurn[] {
  const turns: ChatTurn[] = [];
  for (const message of [...messages].sort(compareHistoryMessages)) {
    if (message.role === 'USER') {
      turns.push({
        id: ++nextTurnId,
        userMessageId: message.id,
        question: message.content ?? '',
        answer: '',
        sources: [],
        loading: false,
      });
      continue;
    }

    const turn = findAttachableTurn(turns);
    turn.assistantMessageId = message.id;
    if (message.status === 'COMPLETED') {
      turn.answer = message.content ?? '';
      turn.sources = message.sources ?? [];
      turn.loading = false;
      turn.pending = false;
      turn.error = undefined;
    } else if (message.status === 'FAILED') {
      turn.answer = '';
      turn.sources = [];
      turn.loading = false;
      turn.pending = false;
      turn.error = message.errorCode || '回答生成失败';
    } else {
      turn.answer = '';
      turn.sources = [];
      turn.loading = false;
      turn.pending = true;
      turn.error = undefined;
    }
  }
  return turns;
}

function findAttachableTurn(turns: ChatTurn[]) {
  for (let i = turns.length - 1; i >= 0; i--) {
    if (turns[i].assistantMessageId == null) {
      return turns[i];
    }
  }

  const turn: ChatTurn = {
    id: ++nextTurnId,
    question: '',
    answer: '',
    sources: [],
    loading: false,
  };
  turns.push(turn);
  return turn;
}

function messageTimeValue(message: ChatHistoryMessageResponse) {
  return Date.parse(message.createdAt) || 0;
}

function compareHistoryMessages(a: ChatHistoryMessageResponse, b: ChatHistoryMessageResponse) {
  return messageTimeValue(a) - messageTimeValue(b) || a.id - b.id;
}

function handleCreateSession() {
  const session = createSessionInternal();
  activeSessionId.value = session.id;
  closeSources();
}

function handleSwitchSession(id: string) {
  const session = sessions.value.find((s) => s.id === id);
  if (activeSessionId.value !== id) {
    activeSessionId.value = id;
    closeSources();
  }
  if (session) {
    void loadMessagesForSession(session);
  }
}

async function handleRenameSession(id: string, title: string) {
  const session = sessions.value.find((s) => s.id === id);
  if (!session) return;

  const normalizedTitle = title.trim();
  if (!normalizedTitle) return;
  sessionActionError.value = null;

  if (session.backendSessionId == null) {
    session.title = normalizedTitle;
    return;
  }

  sessionActionBusyId.value = id;
  try {
    const updated = await renameChatSession(session.backendSessionId, normalizedTitle);
    session.title = updated.title?.trim() || normalizedTitle;
  } catch (e) {
    const message = e instanceof ApiError ? e.message : '会话重命名失败';
    sessionActionError.value = message;
    workspaceError.value = message;
  } finally {
    if (sessionActionBusyId.value === id) {
      sessionActionBusyId.value = null;
    }
  }
}

function handleToggleNoteScope(noteId: number) {
  const next = new Set(selectedNoteIds.value);
  if (next.has(noteId)) {
    next.delete(noteId);
  } else {
    if (next.size >= MAX_NOTE_IDS) {
      workspaceError.value = `最多选择 ${MAX_NOTE_IDS} 篇笔记作为检索范围`;
      return;
    }
    next.add(noteId);
  }
  selectedNoteIds.value = next;
}

async function handleOpenNoteDetail(noteId: number) {
  detailNoteId.value = noteId;
  noteDetailError.value = null;
  if (loadedNoteDetailIds.has(noteId)) {
    loadingNoteDetailId.value = null;
    return;
  }

  const requestToken = ++noteDetailRequestToken;
  loadingNoteDetailId.value = noteId;
  try {
    const detail = await fetchNoteDetail(noteId);
    noteDetails.value[noteId] = detail;
    loadedNoteDetailIds.add(noteId);
  } catch (e) {
    if (detailNoteId.value === noteId && requestToken === noteDetailRequestToken) {
      noteDetailError.value = e instanceof ApiError ? e.message : '笔记详情加载失败';
    }
  } finally {
    if (detailNoteId.value === noteId && requestToken === noteDetailRequestToken) {
      loadingNoteDetailId.value = null;
    }
  }
}

function closeNoteDetail() {
  detailNoteId.value = null;
  noteDetailError.value = null;
  loadingNoteDetailId.value = null;
}

function handleDeleteNoteRequest(noteId: number) {
  const note = notes.value.find((item) => item.id === noteId);
  if (!note) return;
  noteDeleteTarget.value = note;
  noteDeleteError.value = null;
}

async function confirmDeleteNote() {
  const target = noteDeleteTarget.value;
  if (!target) return;
  noteDeleteBusy.value = true;
  noteDeleteError.value = null;
  try {
    await deleteNote(target.id);
    removeNoteLocally(target.id);
    noteDeleteTarget.value = null;
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 404) {
      removeNoteLocally(target.id);
      workspaceError.value = '笔记已不存在，已从列表移除';
      noteDeleteTarget.value = null;
    } else {
      noteDeleteError.value = e instanceof ApiError ? e.message : '删除笔记失败';
    }
  } finally {
    noteDeleteBusy.value = false;
  }
}

function removeNoteLocally(noteId: number) {
  notes.value = notes.value.filter((note) => note.id !== noteId);
  const nextSelected = new Set(selectedNoteIds.value);
  nextSelected.delete(noteId);
  selectedNoteIds.value = nextSelected;

  const nextDetails = { ...noteDetails.value };
  delete nextDetails[noteId];
  noteDetails.value = nextDetails;
  loadedNoteDetailIds.delete(noteId);

  if (detailNoteId.value === noteId) {
    closeNoteDetail();
  }
}

function handleDeleteSessionRequest(id: string) {
  const session = sessions.value.find((item) => item.id === id);
  if (!session) return;
  sessionDeleteTarget.value = session;
  sessionDeleteError.value = null;
}

async function confirmDeleteSession() {
  const target = sessionDeleteTarget.value;
  if (!target) return;

  sessionDeleteBusy.value = true;
  sessionDeleteError.value = null;
  sessionActionError.value = null;
  sessionActionBusyId.value = target.id;

  try {
    if (target.backendSessionId != null) {
      await deleteChatSession(target.backendSessionId);
    }
    removeSessionLocally(target.id);
    sessionDeleteTarget.value = null;
  } catch (e) {
    if (e instanceof ApiError && e.httpStatus === 404) {
      removeSessionLocally(target.id);
      workspaceError.value = '会话已不存在，已从列表移除';
      sessionDeleteTarget.value = null;
    } else {
      sessionDeleteError.value = e instanceof ApiError ? e.message : '删除会话失败';
    }
  } finally {
    sessionDeleteBusy.value = false;
    if (sessionActionBusyId.value === target.id) {
      sessionActionBusyId.value = null;
    }
  }
}

function removeSessionLocally(id: string) {
  const wasActive = activeSessionId.value === id;
  sessions.value = sessions.value.filter((session) => session.id !== id);

  if (!wasActive) return;
  closeSources();
  const nextSession = sessions.value[0] ?? createSessionInternal();
  activeSessionId.value = nextSession.id;
  void loadMessagesForSession(nextSession);
}

function openImport() {
  importOpen.value = true;
}

function handleImported(_result: ImportTextResponse) {
  void loadNotes();
}

async function handleSubmit(question: string) {
  if (sessionsLoading.value) return;
  const session = activeSession.value ?? createSessionInternal();
  activeSessionId.value = session.id;
  const noteIds = selectedNoteIdList.value.length > 0 ? selectedNoteIdList.value : undefined;
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
        ? await sendFirstChatMessage(question, noteIds)
        : await sendChatMessage(session.backendSessionId, question, noteIds);

    session.backendSessionId = response.sessionId;
    session.title = response.sessionTitle?.trim() || session.title;
    session.messagesLoaded = true;
    turn.userMessageId = response.userMessageId;
    turn.assistantMessageId = response.assistantMessageId;
    turn.answer = response.answer ?? '';
    turn.sources = response.sources ?? [];
    turn.pending = false;
  } catch (e) {
    turn.error = e instanceof ApiError ? e.message : '发送失败，请稍后重试';
    turn.answer = '';
    turn.sources = [];
    turn.pending = false;
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
