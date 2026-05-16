import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'query',
      component: () => import('@/views/QueryPage.vue'),
    },
    {
      path: '/import',
      name: 'import',
      component: () => import('@/views/ImportPage.vue'),
    },
    {
      path: '/debug/retrieval',
      name: 'retrieval-debug',
      component: () => import('@/views/RetrievalDebugPage.vue'),
    },
  ],
});
