<script setup lang="ts">
import { computed } from 'vue';
import type { NoteListItem } from '@/api/types';

const props = defineProps<{
  notes: NoteListItem[];
  selectedNoteIds: number[];
}>();

const emit = defineEmits<{
  (e: 'toggle', noteId: number): void;
  (e: 'open-import'): void;
}>();

const COLOR_PRESETS = [
  'from-teal-400/16 to-cyan-400/8 text-teal-200 ring-teal-300/15',
  'from-indigo-400/16 to-sky-400/8 text-indigo-200 ring-indigo-300/15',
  'from-amber-300/16 to-yellow-400/8 text-amber-200 ring-amber-300/15',
  'from-rose-300/16 to-fuchsia-400/8 text-rose-200 ring-rose-300/15',
  'from-slate-300/16 to-blue-400/8 text-slate-200 ring-slate-300/15',
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
  props.selectedNoteIds.length === 0 ? 'all' : `${props.selectedNoteIds.length} selected`
);

function handleClick(id: number) {
  emit('toggle', id);
}
</script>

<template>
  <aside class="flex h-full w-full flex-col">
    <header class="flex items-center justify-between px-5 pb-3 pt-5">
      <h2 class="text-[18px] font-semibold tracking-tight text-white">Notes</h2>
      <span class="font-mono text-[11px] tabular-nums text-white/30">
        {{ selectionLabel }}
      </span>
    </header>

    <div class="flex-1 space-y-1.5 overflow-y-auto px-3 pb-3">
      <button
        v-for="note in sortedNotes"
        :key="note.id"
        type="button"
        class="group relative flex w-full appearance-none items-center gap-3 rounded-lg border px-2.5 py-2.5 text-left shadow-none transition-all duration-150"
        :class="
          selectedIdSet.has(note.id)
            ? 'border-accent/35 bg-accent/[0.075] shadow-[0_10px_30px_-26px_rgba(45,212,191,0.75)]'
            : 'border-white/[0.045] bg-white/[0.025] hover:border-white/[0.09] hover:bg-white/[0.045]'
        "
        @click="handleClick(note.id)"
      >
        <span
          v-if="selectedIdSet.has(note.id)"
          class="absolute left-0 top-1/2 h-6 w-[2px] -translate-y-1/2 rounded-r bg-accent"
          aria-hidden="true"
        ></span>
        <span
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-gradient-to-br font-mono text-[13px] font-semibold ring-1 ring-inset"
          :class="colorFor(note.id)"
        >
          {{ initial(note.title) }}
        </span>
        <span class="min-w-0 flex-1">
          <span class="block truncate text-[13px] font-medium text-white/85">{{ note.title }}</span>
          <span class="mt-0.5 flex items-center gap-2 text-[11px] text-white/35">
            <span class="font-mono tabular-nums">{{ note.chunkCount }} chunks</span>
            <span class="h-0.5 w-0.5 rounded-full bg-white/20"></span>
            <span class="font-mono tabular-nums">{{ formatBytes(note.charCount) }}</span>
          </span>
        </span>
      </button>

      <div v-if="sortedNotes.length === 0" class="px-2 py-12 text-center text-[12px] text-white/30">
        暂无笔记
      </div>
    </div>

    <footer class="border-t border-white/[0.04] p-3">
      <button
        type="button"
        class="flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-white/[0.12] bg-white/[0.02] px-3 py-2.5 text-[13px] font-medium text-white/65 transition-all duration-150 hover:border-accent/40 hover:bg-accent/[0.04] hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
        @click="emit('open-import')"
      >
        <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        import note
      </button>
    </footer>
  </aside>
</template>
