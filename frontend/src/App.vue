<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';

const route = useRoute();
const enableDebugRoutes = import.meta.env.VITE_ENABLE_DEBUG_ROUTES === 'true';
const navItems = [
  { path: '/', label: 'Workspace' },
  ...(enableDebugRoutes ? [{ path: '/debug/retrieval', label: '检索调试' }] : []),
];
const activePath = computed(() => route.path);
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <header
      class="sticky top-0 z-30 h-14 border-b border-white/[0.06] bg-[#0a0a0b]/80 backdrop-blur-xl"
    >
      <div class="mx-auto flex h-full items-center gap-8 px-6">
        <div class="flex items-center gap-3">
          <span
            class="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-accent/20 bg-black/40 shadow-[0_0_28px_-14px_rgba(45,212,191,0.9)] ring-1 ring-white/[0.04]"
          >
            <img
              src="/NoteRAG.png"
              width="32"
              height="32"
              alt="NoteRAG"
              class="h-full w-full object-cover"
            />
          </span>
          <span class="text-[15px] font-semibold tracking-tight text-white">NoteRAG</span>
        </div>

        <nav class="flex items-center gap-1">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors duration-150"
            :class="
              activePath === item.path
                ? 'bg-white/[0.06] text-white'
                : 'text-white/50 hover:bg-white/[0.03] hover:text-white/90'
            "
          >
            {{ item.label }}
          </router-link>
        </nav>

        <a
          href="https://github.com/HuanHuanHuanFFF/NoteRAG"
          target="_blank"
          rel="noreferrer"
          aria-label="Open NoteRAG GitHub repository"
          class="ml-auto flex h-9 w-9 items-center justify-center rounded-lg border border-white/[0.08] bg-black/25 text-white/55 shadow-[0_0_24px_-16px_rgba(45,212,191,0.8)] transition-colors duration-150 hover:border-accent/45 hover:bg-accent/10 hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
        >
          <svg
            class="h-[18px] w-[18px]"
            viewBox="0 0 16 16"
            fill="currentColor"
            aria-hidden="true"
          >
            <path
              d="M8 0C3.58 0 0 3.69 0 8.24c0 3.64 2.29 6.73 5.47 7.82.4.08.55-.18.55-.4 0-.2-.01-.85-.01-1.54-2.01.38-2.53-.5-2.69-.96-.09-.24-.48-.96-.82-1.15-.28-.16-.68-.55-.01-.56.63-.01 1.08.59 1.23.84.72 1.25 1.87.9 2.33.69.07-.54.28-.9.51-1.1-1.78-.21-3.64-.92-3.64-4.07 0-.9.31-1.64.82-2.22-.08-.21-.36-1.05.08-2.19 0 0 .67-.22 2.2.85A7.42 7.42 0 0 1 8 3.97c.68 0 1.36.09 2 .28 1.53-1.07 2.2-.85 2.2-.85.44 1.14.16 1.98.08 2.19.51.58.82 1.32.82 2.22 0 3.16-1.87 3.86-3.65 4.07.29.26.54.76.54 1.53 0 1.1-.01 1.99-.01 2.26 0 .22.15.48.55.4A8.17 8.17 0 0 0 16 8.24C16 3.69 12.42 0 8 0Z"
            />
          </svg>
        </a>
      </div>
    </header>

    <main class="relative flex-1">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" :key="activePath" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms ease-out;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
