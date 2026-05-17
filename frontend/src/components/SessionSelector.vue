<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Directive } from 'vue';
import type { ChatSession } from '@/api/types';

const props = defineProps<{
  sessions: ChatSession[];
  activeId: string;
}>();

const emit = defineEmits<{
  (e: 'switch', id: string): void;
  (e: 'create'): void;
  (e: 'rename', id: string, title: string): void;
}>();

const open = ref(false);
const editing = ref<string | null>(null);
const editingTitle = ref('');

const activeSession = computed(() => props.sessions.find((s) => s.id === props.activeId));

const vFocus: Directive<HTMLInputElement> = {
  mounted(el) {
    el.focus();
    el.select();
  },
};

function toggle() {
  open.value = !open.value;
  if (!open.value) cancelEdit();
}

function pick(id: string) {
  emit('switch', id);
  open.value = false;
}

function startRename(session: ChatSession, event: Event) {
  event.stopPropagation();
  editing.value = session.id;
  editingTitle.value = session.title;
}

function commitRename() {
  if (editing.value && editingTitle.value.trim()) {
    emit('rename', editing.value, editingTitle.value.trim());
  }
  cancelEdit();
}

function cancelEdit() {
  editing.value = null;
  editingTitle.value = '';
}

watch(
  () => props.activeId,
  () => {
    open.value = false;
    cancelEdit();
  }
);

function onDocClick(event: MouseEvent) {
  const target = event.target as HTMLElement;
  if (!target.closest('[data-session-selector]')) open.value = false;
}

onMounted(() => window.addEventListener('click', onDocClick));
onBeforeUnmount(() => window.removeEventListener('click', onDocClick));
</script>

<template>
  <div class="flex items-center gap-2" data-session-selector>
    <span class="hidden text-[12px] text-white/40 sm:inline">当前会话</span>

    <div class="relative">
      <button
        type="button"
        class="flex h-8 w-[260px] items-center justify-between rounded-lg border border-white/[0.08] bg-white/[0.02] px-3 text-left text-[13px] text-white/85 transition-colors duration-150 hover:border-white/[0.12] hover:bg-white/[0.04] focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
        :class="open ? 'border-accent/40 ring-1 ring-accent/30' : ''"
        @click="toggle"
      >
        <span class="truncate">{{ activeSession?.title ?? '新会话' }}</span>
        <svg
          class="h-3.5 w-3.5 shrink-0 text-white/40 transition-transform duration-200"
          :class="open ? 'rotate-180' : ''"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      <transition
        enter-active-class="transition duration-150 ease-out"
        enter-from-class="opacity-0 -translate-y-1"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition duration-100 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="open"
          class="absolute left-0 top-[calc(100%+6px)] z-30 max-h-[320px] w-[320px] overflow-y-auto rounded-xl border border-white/[0.08] bg-[#13131a]/95 p-1.5 shadow-2xl backdrop-blur-xl"
        >
          <div
            v-if="sessions.length === 0"
            class="px-3 py-6 text-center text-[12px] text-white/30"
          >
            暂无会话
          </div>
          <div
            v-for="session in sessions"
            :key="session.id"
            class="group flex w-full items-center gap-2 rounded-md px-2.5 py-2 transition-colors duration-150"
            :class="
              session.id === activeId
                ? 'bg-accent/[0.08] text-accent'
                : 'text-white/75 hover:bg-white/[0.04] hover:text-white'
            "
            role="button"
            tabindex="0"
            @click="pick(session.id)"
            @keydown.enter="pick(session.id)"
          >
            <span
              class="inline-flex h-1.5 w-1.5 shrink-0 rounded-full"
              :class="session.id === activeId ? 'bg-accent' : 'bg-white/20'"
              aria-hidden="true"
            ></span>
            <input
              v-if="editing === session.id"
              v-model="editingTitle"
              v-focus
              type="text"
              class="flex-1 rounded bg-black/30 px-1.5 py-0.5 text-[13px] text-white outline-none ring-1 ring-accent/40"
              @click.stop
              @keydown.enter.prevent="commitRename"
              @keydown.esc.prevent="cancelEdit"
              @blur="commitRename"
            />
            <span v-else class="flex-1 cursor-pointer truncate text-[13px]">{{ session.title }}</span>
            <button
              v-if="editing !== session.id"
              type="button"
              class="opacity-0 transition-opacity duration-150 group-hover:opacity-100 focus:opacity-100"
              title="重命名"
              @click="startRename(session, $event)"
            >
              <svg
                class="h-3.5 w-3.5 text-white/40 hover:text-white/80"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
                />
              </svg>
            </button>
          </div>
        </div>
      </transition>
    </div>

    <button
      type="button"
      class="inline-flex h-8 items-center gap-1 rounded-lg border border-white/[0.08] bg-white/[0.02] px-3 text-[12px] font-medium text-white/75 transition-all duration-150 hover:border-accent/40 hover:bg-accent/[0.04] hover:text-accent active:scale-[0.97] focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
      @click="emit('create')"
    >
      <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4" />
      </svg>
      新会话
    </button>
  </div>
</template>
