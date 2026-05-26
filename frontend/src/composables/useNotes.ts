import { computed, ref } from 'vue';
import { ApiError } from '@/api/client';
import {
  deleteNote,
  getNoteDetail as fetchNoteDetail,
  listNotes,
  MAX_NOTE_IDS,
} from '@/api/noterag';
import type { ImportTextResponse, NoteDetailResponse, NoteListItem } from '@/api/types';

interface UseNotesOptions {
  initialNotes?: NoteListItem[];
  setWorkspaceError: (message: string | null) => void;
}

export function useNotes(options: UseNotesOptions) {
  const notes = ref<NoteListItem[]>(options.initialNotes ?? []);
  const importOpen = ref(false);
  const notesLoading = ref(false);
  const notesError = ref<string | null>(null);
  const selectedNoteIds = ref<Set<number>>(new Set());
  const detailNoteId = ref<number | null>(null);
  const noteDetails = ref<Record<number, NoteDetailResponse>>({});
  const noteDetailError = ref<string | null>(null);
  const loadingNoteDetailId = ref<number | null>(null);
  const noteDeleteTarget = ref<NoteListItem | null>(null);
  const noteDeleteBusy = ref(false);
  const noteDeleteError = ref<string | null>(null);

  let noteDetailRequestToken = 0;
  const loadedNoteDetailIds = new Set<number>();

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

  const noteDeleteMessage = computed(() =>
    noteDeleteTarget.value
      ? `确定删除「${noteDeleteTarget.value.title}」吗？删除后该笔记会从当前知识库移除。`
      : ''
  );

  async function loadNotes() {
    notesLoading.value = true;
    notesError.value = null;
    try {
      const response = await listNotes();
      notes.value = response.notes ?? [];
      const existingIds = new Set(notes.value.map((note) => note.id));
      selectedNoteIds.value = new Set([...selectedNoteIds.value].filter((id) => existingIds.has(id)));
      if (detailNoteId.value != null && !existingIds.has(detailNoteId.value)) {
        closeNoteDetail();
      }
    } catch (e) {
      notes.value = [];
      notesError.value = e instanceof ApiError ? e.message : '笔记列表加载失败';
    } finally {
      notesLoading.value = false;
    }
  }

  function handleToggleNoteScope(noteId: number) {
    const next = new Set(selectedNoteIds.value);
    if (next.has(noteId)) {
      next.delete(noteId);
    } else {
      if (next.size >= MAX_NOTE_IDS) {
        options.setWorkspaceError(`最多选择 ${MAX_NOTE_IDS} 篇笔记作为检索范围`);
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
        options.setWorkspaceError('笔记已不存在，已从列表移除');
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

  function openImport() {
    importOpen.value = true;
  }

  function handleImported(_result: ImportTextResponse) {
    void loadNotes();
  }

  return {
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
  };
}
