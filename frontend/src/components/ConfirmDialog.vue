<script setup lang="ts">
const props = defineProps<{
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  busy?: boolean;
  error?: string | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'confirm'): void;
}>();

function close() {
  if (!props.busy) emit('close');
}
</script>

<template>
  <transition
    enter-active-class="transition duration-150 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-100 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="open"
      class="fixed inset-0 z-[70] flex items-center justify-center bg-black/65 px-4 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      @click.self="close"
    >
      <transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0 scale-[0.98] translate-y-2"
        enter-to-class="opacity-100 scale-100 translate-y-0"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0 scale-[0.98] translate-y-1"
        appear
      >
        <section
          class="w-full max-w-md overflow-hidden rounded-2xl border border-[#4c0519]/55 bg-[#111116]/95 shadow-2xl ring-1 ring-white/[0.04]"
        >
          <header class="flex items-start gap-3 border-b border-white/[0.06] px-5 py-4">
            <span
              class="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#4c0519]/60 bg-[#2a1117] text-rose-200/90"
              aria-hidden="true"
            >
              <svg class="h-[18px] w-[18px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="1.9"
                  d="M12 9v4m0 4h.01M10.3 4.2 2.7 18a2 2 0 0 0 1.75 3h15.1a2 2 0 0 0 1.75-3L13.7 4.2a2 2 0 0 0-3.4 0Z"
                />
              </svg>
            </span>
            <div class="min-w-0 flex-1">
              <h2 class="text-[15px] font-semibold text-white">{{ title }}</h2>
              <p class="mt-1 text-[13px] leading-relaxed text-white/55">{{ message }}</p>
            </div>
            <button
              type="button"
              class="close-icon-button h-9 w-9 shrink-0 rounded-lg"
              title="关闭"
              aria-label="关闭"
              :disabled="busy"
              @click="close"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div v-if="error" class="px-5 pt-4">
            <div
              class="rounded-lg border border-[#4c0519]/70 bg-[#2a1117] px-3 py-2 text-[12px] leading-relaxed text-rose-100/80"
              role="alert"
            >
              {{ error }}
            </div>
          </div>

          <footer class="flex justify-end gap-2 px-5 py-4">
            <button
              type="button"
              class="inline-flex h-10 items-center justify-center rounded-lg border border-white/[0.06] bg-[#101016]/85 px-4 text-[13px] font-medium text-white/65 transition-colors duration-150 hover:border-white/[0.12] hover:bg-[#171922] hover:text-white/85 disabled:cursor-not-allowed disabled:opacity-45 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
              :disabled="busy"
              @click="close"
            >
              取消
            </button>
            <button
              type="button"
              class="danger-action-button h-10 gap-2 rounded-lg px-4 text-[13px] font-semibold"
              :disabled="busy"
              @click="emit('confirm')"
            >
              <span v-if="busy" class="h-3.5 w-3.5 animate-spin rounded-full border border-rose-100/25 border-t-rose-50"></span>
              <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="1.9"
                  d="M6 7h12m-9 0V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7m-7 0 .7 12A2 2 0 0 0 10.7 21h2.6a2 2 0 0 0 2-1.9L16 7M10 11v6m4-6v6"
                />
              </svg>
              {{ busy ? '处理中...' : confirmLabel }}
            </button>
          </footer>
        </section>
      </transition>
    </div>
  </transition>
</template>
