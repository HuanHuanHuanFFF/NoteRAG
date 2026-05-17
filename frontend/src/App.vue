<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';

const route = useRoute();
const navItems = [
  { path: '/', label: 'Workspace' },
  { path: '/debug/retrieval', label: '检索调试' },
];
const activePath = computed(() => route.path);
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <header
      class="sticky top-0 z-30 h-14 border-b border-white/[0.06] bg-[#0a0a0b]/80 backdrop-blur-xl"
    >
      <div class="mx-auto flex h-full items-center gap-8 px-6">
        <div class="flex items-center gap-2.5">
          <span class="relative inline-flex h-2 w-2">
            <span
              class="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-60"
            ></span>
            <span class="relative inline-flex h-2 w-2 rounded-full bg-accent"></span>
          </span>
          <span class="text-[15px] font-semibold tracking-tight text-white">NoteRAG</span>
          <span
            class="hidden text-[11px] font-medium uppercase tracking-wider text-white/30 sm:inline"
          >
            v1
          </span>
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

        <div class="ml-auto hidden items-center gap-2 text-[12px] text-white/40 sm:flex">
          <span class="font-mono text-[11px]">RAG demo</span>
        </div>
      </div>
    </header>

    <main class="relative flex-1">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
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
