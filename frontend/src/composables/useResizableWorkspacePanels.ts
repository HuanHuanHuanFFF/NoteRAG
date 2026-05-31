import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const NOTES_WIDTH_STORAGE_KEY = 'noterag.notesPanelWidthPercent';
const SOURCES_WIDTH_STORAGE_KEY = 'noterag.sourcesPanelWidthPercent';
const NOTES_WIDTH_DEFAULT = 18;
const NOTES_WIDTH_MIN = 14;
const NOTES_WIDTH_MAX = 26;
const SOURCES_WIDTH_DEFAULT = 26;
const SOURCES_WIDTH_MIN = 20;
const SOURCES_WIDTH_MAX = 36;
const RESIZE_BREAKPOINT = 1024;

interface BooleanRef {
  readonly value: boolean;
}

interface ElementRef {
  readonly value: HTMLElement | null;
}

type ResizeTarget = 'notes' | 'sources';

export function useResizableWorkspacePanels(sourcesVisible: BooleanRef, workspaceElement: ElementRef) {
  const notesPanelPercent = ref(
    readStoredPercent(NOTES_WIDTH_STORAGE_KEY, NOTES_WIDTH_DEFAULT, NOTES_WIDTH_MIN, NOTES_WIDTH_MAX)
  );
  const sourcesPanelPercent = ref(
    readStoredPercent(SOURCES_WIDTH_STORAGE_KEY, SOURCES_WIDTH_DEFAULT, SOURCES_WIDTH_MIN, SOURCES_WIDTH_MAX)
  );
  const panelsResizable = ref(isLargeViewport());
  const notesResizing = ref(false);
  const sourcesResizing = ref(false);

  let resizeTarget: ResizeTarget | null = null;
  let resizeStartX = 0;
  let resizeStartPercent = 0;
  let resizeWorkspaceWidth = 1;

  const notesColumn = computed(() => `${notesPanelPercent.value}%`);
  const sourcesColumn = computed(() => `${sourcesPanelPercent.value}%`);

  const workspaceGridColumns = computed(() => {
    if (sourcesVisible.value) {
      return `${notesColumn.value} minmax(320px, 1fr) ${sourcesColumn.value}`;
    }
    return `${notesColumn.value} minmax(320px, 1fr)`;
  });

  const workspaceMinWidth = computed(() => (sourcesVisible.value ? '900px' : '620px'));

  onMounted(() => {
    updateResizeAvailability();
    window.addEventListener('resize', updateResizeAvailability);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', updateResizeAvailability);
    stopResize();
  });

  function readStoredPercent(key: string, defaultValue: number, min: number, max: number) {
    if (typeof window === 'undefined') return defaultValue;
    const stored = Number(window.localStorage.getItem(key));
    if (!Number.isFinite(stored)) return defaultValue;
    return clampPercent(stored, min, max);
  }

  function isLargeViewport() {
    return typeof window !== 'undefined' && window.innerWidth >= RESIZE_BREAKPOINT;
  }

  function updateResizeAvailability() {
    panelsResizable.value = isLargeViewport();
    if (!panelsResizable.value && resizeTarget) {
      stopResize();
    }
  }

  function clampPercent(width: number, min: number, max: number) {
    return Math.min(max, Math.max(min, Math.round(width * 10) / 10));
  }

  function persistPercent(target: ResizeTarget) {
    if (typeof window === 'undefined') return;
    if (target === 'notes') {
      window.localStorage.setItem(NOTES_WIDTH_STORAGE_KEY, String(notesPanelPercent.value));
      return;
    }
    window.localStorage.setItem(SOURCES_WIDTH_STORAGE_KEY, String(sourcesPanelPercent.value));
  }

  function startNotesResize(event: PointerEvent) {
    startResize('notes', event);
  }

  function startSourcesResize(event: PointerEvent) {
    startResize('sources', event);
  }

  function startResize(target: ResizeTarget, event: PointerEvent) {
    if (!panelsResizable.value) return;
    resizeTarget = target;
    sourcesResizing.value = target === 'sources';
    notesResizing.value = target === 'notes';
    resizeStartX = event.clientX;
    resizeStartPercent = target === 'notes' ? notesPanelPercent.value : sourcesPanelPercent.value;
    resizeWorkspaceWidth = Math.max(1, workspaceElement.value?.getBoundingClientRect().width ?? window.innerWidth);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    window.addEventListener('pointermove', handleResizeMove);
    window.addEventListener('pointerup', stopResize);
    window.addEventListener('pointercancel', stopResize);
  }

  function handleResizeMove(event: PointerEvent) {
    if (!resizeTarget) return;
    const deltaPercent = ((event.clientX - resizeStartX) / resizeWorkspaceWidth) * 100;
    if (resizeTarget === 'notes') {
      notesPanelPercent.value = clampPercent(
        resizeStartPercent + deltaPercent,
        NOTES_WIDTH_MIN,
        NOTES_WIDTH_MAX
      );
      return;
    }
    sourcesPanelPercent.value = clampPercent(
      resizeStartPercent - deltaPercent,
      SOURCES_WIDTH_MIN,
      SOURCES_WIDTH_MAX
    );
  }

  function stopResize() {
    if (resizeTarget) {
      persistPercent(resizeTarget);
    }
    notesResizing.value = false;
    sourcesResizing.value = false;
    resizeTarget = null;
    if (typeof document !== 'undefined') {
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('pointermove', handleResizeMove);
      window.removeEventListener('pointerup', stopResize);
      window.removeEventListener('pointercancel', stopResize);
    }
  }

  function adjustNotesWidth(delta: number) {
    if (!panelsResizable.value) return;
    notesPanelPercent.value = clampPercent(notesPanelPercent.value + delta, NOTES_WIDTH_MIN, NOTES_WIDTH_MAX);
    persistPercent('notes');
  }

  function adjustSourcesWidth(delta: number) {
    if (!panelsResizable.value) return;
    sourcesPanelPercent.value = clampPercent(
      sourcesPanelPercent.value + delta,
      SOURCES_WIDTH_MIN,
      SOURCES_WIDTH_MAX
    );
    persistPercent('sources');
  }

  return {
    notesResizable: panelsResizable,
    sourcesResizable: panelsResizable,
    notesResizing,
    sourcesResizing,
    workspaceGridColumns,
    workspaceMinWidth,
    startNotesResize,
    startSourcesResize,
    adjustNotesWidth,
    adjustSourcesWidth,
  };
}
