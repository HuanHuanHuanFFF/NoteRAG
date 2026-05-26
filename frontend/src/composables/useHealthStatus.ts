import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError } from '@/api/client';
import { checkHealth } from '@/api/noterag';

export type HealthStatus = 'checking' | 'connected' | 'disconnected' | 'network-error';

const HEALTH_CHECK_INTERVAL_MS = 30_000;

export function useHealthStatus() {
  const healthStatus = ref<HealthStatus>('checking');
  let healthCheckTimer: number | null = null;

  onMounted(() => {
    void loadHealth();
    healthCheckTimer = window.setInterval(() => {
      void loadHealth();
    }, HEALTH_CHECK_INTERVAL_MS);
  });

  onBeforeUnmount(() => {
    if (healthCheckTimer != null) {
      window.clearInterval(healthCheckTimer);
      healthCheckTimer = null;
    }
  });

  async function loadHealth() {
    healthStatus.value = 'checking';
    try {
      await checkHealth();
      healthStatus.value = 'connected';
    } catch (e) {
      healthStatus.value = e instanceof ApiError && e.httpStatus !== 0 ? 'disconnected' : 'network-error';
    }
  }

  return {
    healthStatus,
  };
}
