import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const NOTES_WIDTH_STORAGE_KEY = 'noterag.notesPanelWidth';
const NOTES_WIDTH_DEFAULT = 320;
const NOTES_WIDTH_MIN = 224;
const NOTES_WIDTH_MAX = 420;
const NOTES_RESIZE_BREAKPOINT = 1024;

interface BooleanRef {
  readonly value: boolean;
}

export function useResizableNotesPanel(sourcesVisible: BooleanRef) {
  const notesPanelWidth = ref(readStoredNotesWidth());
  const notesResizable = ref(isLargeViewport());
  const notesResizing = ref(false);

  let notesResizeStartX = 0;
  let notesResizeStartWidth = NOTES_WIDTH_DEFAULT;

  const notesColumnWidth = computed(() => (notesResizable.value ? notesPanelWidth.value : NOTES_WIDTH_MIN));

  const workspaceGridColumns = computed(() => {
    const notesColumn = `${notesColumnWidth.value}px`;
    if (sourcesVisible.value) {
      return `${notesColumn} minmax(320px, 1fr) minmax(320px, 400px)`;
    }
    return `${notesColumn} minmax(320px, 1fr)`;
  });

  const workspaceMinWidth = computed(() => (sourcesVisible.value ? '900px' : '620px'));

  onMounted(() => {
    updateNotesResizeAvailability();
    window.addEventListener('resize', updateNotesResizeAvailability);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', updateNotesResizeAvailability);
    stopNotesResize();
  });

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
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(NOTES_WIDTH_STORAGE_KEY, String(notesPanelWidth.value));
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

  return {
    notesResizable,
    notesResizing,
    workspaceGridColumns,
    workspaceMinWidth,
    startNotesResize,
    adjustNotesWidth,
  };
}
