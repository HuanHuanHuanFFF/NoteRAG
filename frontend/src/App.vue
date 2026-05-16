<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import {
  NConfigProvider,
  NLayout,
  NLayoutHeader,
  NLayoutContent,
  NMessageProvider,
  zhCN,
  dateZhCN,
} from 'naive-ui';

const route = useRoute();
const navItems = [
  { path: '/', label: '问答' },
  { path: '/import', label: '导入' },
  { path: '/debug/retrieval', label: '检索调试' },
];

const activeKey = computed(() => route.path);
</script>

<template>
  <n-config-provider :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <n-layout class="h-full">
        <n-layout-header bordered class="h-14 flex items-center px-6">
          <div class="flex items-center gap-2 font-semibold text-base">
            <span class="inline-block w-2 h-2 rounded-full bg-emerald-500"></span>
            NoteRAG
          </div>
          <nav class="flex items-center gap-1 ml-8">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="px-3 py-1.5 rounded-md text-sm transition-colors"
              :class="
                activeKey === item.path
                  ? 'bg-slate-100 text-slate-900'
                  : 'text-slate-500 hover:text-slate-900 hover:bg-slate-50'
              "
            >
              {{ item.label }}
            </router-link>
          </nav>
        </n-layout-header>
        <n-layout-content class="px-6 py-6" content-style="height: calc(100vh - 56px);">
          <router-view />
        </n-layout-content>
      </n-layout>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.router-link-active {
  font-weight: 500;
}
</style>
