import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: () => import('@/views/WorkspacePage.vue'),
    },
    {
      path: '/debug/retrieval',
      name: 'retrieval-debug',
      component: () => import('@/views/RetrievalDebugPage.vue'),
    },
  ],
});
