<script setup lang="ts">
import { computed } from 'vue';
import type { NoteListItem } from '@/api/types';

const props = defineProps<{
  notes: NoteListItem[];
  notesLoading: boolean;
  notesError: string | null;
  selectedNoteIds: number[];
}>();

const emit = defineEmits<{
  (e: 'toggle', noteId: number): void;
  (e: 'open-detail', noteId: number): void;
  (e: 'delete', noteId: number): void;
  (e: 'open-import'): void;
}>();

const COLOR_PRESETS = [
  'bg-[#5ea99c] text-[#a8f0e2]',
  'bg-[#8a75ad] text-[#d5c6ff]',
  'bg-[#a4667b] text-[#ffc4d3]',
  'bg-[#a9904e] text-[#ffe28a]',
  'bg-[#6087a8] text-[#a8ddff]',
];

function colorFor(id: number): string {
  return COLOR_PRESETS[id % COLOR_PRESETS.length];
}

function initial(title: string): string {
  const ch = title.trim().charAt(0);
  return ch ? ch.toUpperCase() : '·';
}

function formatBytes(chars: number): string {
  if (chars >= 1000) return (chars / 1000).toFixed(1) + 'k';
  return String(chars);
}

const sortedNotes = computed(() =>
  [...props.notes].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
);

const selectedIdSet = computed(() => new Set(props.selectedNoteIds));

const selectionLabel = computed(() =>
  props.notesLoading ? 'loading' : `total: ${props.notes.length}`
);

function isSelected(id: number) {
  return selectedIdSet.value.has(id);
}
</script>

<template>
  <aside class="flex h-full min-h-0 w-full flex-col overflow-hidden">
    <header class="flex shrink-0 items-center justify-between px-5 pb-3 pt-5">
      <h2 class="text-[18px] font-semibold tracking-tight text-white">Notes</h2>
      <span class="font-mono text-[11px] tabular-nums text-white/30">
        {{ selectionLabel }}
      </span>
    </header>

    <div class="min-h-0 flex-1 space-y-1.5 overflow-y-auto px-3 pb-3">
      <div v-if="notesLoading" class="px-2 py-12 text-center text-[12px] text-white/35">
        正在加载笔记...
      </div>

      <div
        v-else-if="notesError"
        class="rounded-lg border border-rose-400/20 bg-rose-400/[0.06] px-3 py-3 text-[12px] leading-relaxed text-rose-100/75"
      >
        {{ notesError }}
      </div>

      <div
        v-for="note in notesLoading || notesError ? [] : sortedNotes"
        :key="note.id"
        class="note-card group relative overflow-hidden rounded-lg bg-[#17181d] shadow-none transition-colors duration-150"
        :class="
          isSelected(note.id)
            ? 'note-card--selected bg-[#1e2928] hover:bg-[#233231] focus-within:bg-[#233231]'
            : 'note-card--idle hover:bg-[#202229] focus-within:bg-[#202229]'
        "
      >
        <span
          v-if="isSelected(note.id)"
          class="absolute left-0 top-1/2 h-6 w-[2px] -translate-y-1/2 rounded-r bg-accent"
          aria-hidden="true"
        ></span>
        <button
          type="button"
          class="note-card__button flex w-full appearance-none items-center gap-3 rounded-lg border-0 bg-transparent px-2.5 py-2.5 pr-20 text-left shadow-none focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
          :aria-pressed="isSelected(note.id)"
          @click="emit('toggle', note.id)"
        >
          <span
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md font-mono text-[13px] font-semibold"
            :class="colorFor(note.id)"
          >
            {{ initial(note.title) }}
          </span>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-[13px] font-medium text-white/85">{{ note.title }}</span>
            <span class="mt-0.5 flex items-center gap-2 text-[11px] text-white/35">
              <span class="font-mono tabular-nums">{{ formatBytes(note.charCount) }}</span>
            </span>
          </span>
        </button>

        <div
          class="pointer-events-none absolute right-2 top-1/2 z-10 flex -translate-y-1/2 items-center gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-within:opacity-100"
        >
          <button
            type="button"
            class="pointer-events-auto inline-flex h-8 w-8 appearance-none items-center justify-center rounded-md border border-white/[0.06] bg-[#101016]/85 text-white/55 shadow-none transition-colors duration-150 hover:border-accent/35 hover:bg-accent/[0.1] hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            title="查看笔记内容"
            aria-label="查看笔记内容"
            @click.stop="emit('open-detail', note.id)"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.8"
                d="M7 4h10a2 2 0 0 1 2 2v14l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2Zm2 5h6m-6 4h4"
              />
            </svg>
          </button>
          <button
            type="button"
            class="danger-icon-button pointer-events-auto"
            title="删除笔记"
            aria-label="删除笔记"
            @click.stop="emit('delete', note.id)"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.8"
                d="M6 7h12m-9 0V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7m-7 0 .7 12A2 2 0 0 0 10.7 21h2.6a2 2 0 0 0 2-1.9L16 7M10 11v6m4-6v6"
              />
            </svg>
          </button>
        </div>
      </div>

      <div
        v-if="!notesLoading && !notesError && sortedNotes.length === 0"
        class="px-2 py-12 text-center text-[12px] text-white/30"
      >
        暂无笔记
      </div>
    </div>

    <footer class="shrink-0 border-t border-white/[0.04] p-3">
      <button
        type="button"
        class="flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-white/[0.12] bg-white/[0.02] px-3 py-2.5 text-[13px] font-medium text-white/65 transition-all duration-150 hover:border-accent/40 hover:bg-accent/[0.04] hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
        @click="emit('open-import')"
      >
        <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        导入笔记
      </button>
    </footer>
  </aside>
</template>

<style scoped>
.note-card {
  background-color: #17181d;
  background-image: none;
}

.note-card--idle {
  box-shadow: none;
}

.note-card--idle:hover,
.note-card--idle:focus-within {
  background-color: #202229;
}

.note-card--selected {
  background-color: #1e2928;
  box-shadow: inset 0 0 0 1px rgb(45 212 191 / 0.14);
}

.note-card--selected:hover,
.note-card--selected:focus-within {
  background-color: #233231;
}

.note-card__button,
.note-card__button:hover,
.note-card__button:active,
.note-card__button:focus {
  appearance: none;
  -webkit-appearance: none;
  background: transparent !important;
  background-image: none !important;
  border: 0 !important;
  box-shadow: none;
  color: inherit;
}
</style>
