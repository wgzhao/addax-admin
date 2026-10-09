import { computed, onMounted, ref } from 'vue';

export type ChartDataState = 'loading' | 'ready' | 'empty' | 'error';

// 图表数据加载的统一状态机：loading / ready / empty / error
export function useChartData<T>(fetcher: () => Promise<T>) {
  const raw = ref<T | null>(null) as { value: T | null };
  const loading = ref(true);
  const error = ref<string | null>(null);

  // 空数据判定：空数组、null，或分桶结果为空
  const isEmpty = (value: T | null): boolean => {
    if (value === null || value === undefined) return true;
    if (Array.isArray(value)) return value.length === 0;
    if (typeof value === 'object' && 'buckets' in value) {
      const buckets = (value as { buckets?: unknown[] }).buckets;
      return !Array.isArray(buckets) || buckets.length === 0;
    }
    return false;
  };

  const state = computed<ChartDataState>(() => {
    if (loading.value) return 'loading';
    if (error.value) return 'error';
    if (isEmpty(raw.value)) return 'empty';
    return 'ready';
  });

  const load = async () => {
    loading.value = true;
    error.value = null;
    try {
      raw.value = await fetcher();
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err);
      console.error('Dashboard chart fetch failed:', err);
    } finally {
      loading.value = false;
    }
  };

  onMounted(load);

  return { raw, loading, error, state, reload: load };
}
