import { createRouter, createWebHistory } from 'vue-router';

const enableDebugRoutes = import.meta.env.VITE_ENABLE_DEBUG_ROUTES === 'true';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: () => import('@/views/WorkspacePage.vue'),
    },
    ...(enableDebugRoutes
      ? [
          {
            path: '/debug/retrieval',
            name: 'retrieval-debug',
            component: () => import('@/views/RetrievalDebugPage.vue'),
          },
        ]
      : []),
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
});
