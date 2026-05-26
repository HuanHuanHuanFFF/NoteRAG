import { nextTick, ref } from 'vue';
import type { SourceChunk } from '@/api/types';

interface SourcesPanelOptions {
  getSourcesForTurn: (turnId: number) => SourceChunk[] | null;
  initialSourcesOpen?: boolean;
  initialSourcesData?: SourceChunk[];
  initialActiveCitation?: { turnId: number; index: number | null } | null;
  initialExpandedCitation?: { turnId: number; indices: number[] } | null;
}

export function useSourcesPanel(options: SourcesPanelOptions) {
  const sourcesOpen = ref(options.initialSourcesOpen ?? false);
  const sourcesClosing = ref(false);
  const sourcesLoading = ref(false);
  const sourcesData = ref<SourceChunk[]>(options.initialSourcesData ?? []);
  const activeCitation = ref<{ turnId: number; index: number | null } | null>(
    options.initialActiveCitation ?? null
  );
  const expandedCitation = ref<{ turnId: number; indices: number[] } | null>(
    options.initialExpandedCitation ?? null
  );

  let sourcesRequestToken = 0;

  function handleOpenCitation(turnId: number, index: number | null) {
    const sources = options.getSourcesForTurn(turnId);
    if (!sources) return;
    if (index != null && (index < 1 || index > sources.length)) return;

    const same =
      sourcesOpen.value &&
      activeCitation.value?.turnId === turnId &&
      sourcesData.value === sources;

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
          sourcesData.value = sources;
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

  return {
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
  };
}
