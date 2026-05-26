<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Directive } from 'vue';
import type { ChatSession } from '@/api/types';

const props = defineProps<{
  sessions: ChatSession[];
  activeId: string;
  busyId?: string | null;
  error?: string | null;
}>();

const emit = defineEmits<{
  (e: 'switch', id: string): void;
  (e: 'create'): void;
  (e: 'rename', id: string, title: string): void;
  (e: 'delete', id: string): void;
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
  if (isBusy(id)) return;
  emit('switch', id);
  open.value = false;
}

function startRename(session: ChatSession, event: Event) {
  event.stopPropagation();
  if (isBusy(session.id)) return;
  editing.value = session.id;
  editingTitle.value = session.title;
}

function commitRename() {
  if (editing.value && editingTitle.value.trim() && !isBusy(editing.value)) {
    emit('rename', editing.value, editingTitle.value.trim());
  }
  cancelEdit();
}

function requestDelete(session: ChatSession, event: Event) {
  event.stopPropagation();
  if (isBusy(session.id)) return;
  emit('delete', session.id);
}

function cancelEdit() {
  editing.value = null;
  editingTitle.value = '';
}

function isBusy(id: string) {
  return props.busyId === id;
}

watch(
  () => props.activeId,
  () => {
    open.value = false;
    cancelEdit();
  }
);

watch(
  () => props.error,
  (error) => {
    if (error) open.value = true;
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
            v-if="error"
            class="mb-1 rounded-lg border border-rose-300/20 bg-rose-400/[0.07] px-3 py-2 text-[12px] leading-relaxed text-rose-100/75"
            role="alert"
          >
            {{ error }}
          </div>
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
            @keydown.space.prevent="pick(session.id)"
          >
            <input
              v-if="editing === session.id"
              v-model="editingTitle"
              v-focus
              type="text"
              class="min-w-0 flex-1 rounded bg-black/30 px-1.5 py-0.5 text-[13px] text-white outline-none ring-1 ring-accent/40 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isBusy(session.id)"
              @click.stop
              @keydown.stop
              @keydown.enter.stop.prevent="commitRename"
              @keydown.esc.stop.prevent="cancelEdit"
              @blur="commitRename"
            />
            <span v-else class="flex-1 cursor-pointer truncate text-[13px]">{{ session.title }}</span>
            <button
              v-if="editing !== session.id"
              type="button"
              class="inline-flex h-8 w-8 items-center justify-center rounded-md border border-white/[0.06] bg-[#101016]/80 text-white/45 opacity-0 shadow-sm transition-all duration-150 hover:border-accent/30 hover:bg-accent/[0.08] hover:text-accent disabled:cursor-not-allowed disabled:opacity-30 group-hover:opacity-100 focus:opacity-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
              title="重命名"
              aria-label="重命名会话"
              :disabled="isBusy(session.id)"
              @click="startRename(session, $event)"
              @keydown.stop
            >
              <svg
                class="h-4 w-4"
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
            <button
              v-if="editing !== session.id"
              type="button"
              class="danger-icon-button opacity-0 group-hover:opacity-100 focus:opacity-100"
              title="删除会话"
              aria-label="删除会话"
              :disabled="isBusy(session.id)"
              @click="requestDelete(session, $event)"
              @keydown.stop
            >
              <span
                v-if="isBusy(session.id)"
                class="h-3.5 w-3.5 animate-spin rounded-full border border-white/15 border-t-white/70"
                aria-hidden="true"
              ></span>
              <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
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
