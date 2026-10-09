import { defineAsyncComponent } from 'vue';
import type { Component } from 'vue';

export type ChartRegistrar = (chart: typeof import('chart.js')) => void;

// 通用异步图表加载器：chart.js 按需进入 bundle；
// 每个图表的骨架/注册只执行一次，并发调用共享同一 promise，避免重复注册
const modulePromises = new Map<string, Promise<Component>>();

export function loadChartComponent(
  moduleName: 'Line' | 'Bar' | 'Doughnut',
  register: ChartRegistrar
) {
  return defineAsyncComponent(async () => {
    let promise = modulePromises.get(moduleName);
    if (!promise) {
      promise = (async () => {
        const module = await import('vue-chartjs');
        const chart = await import('chart.js');
        register(chart);
        return module[moduleName] as Component;
      })();
      modulePromises.set(moduleName, promise);
    }
    return promise;
  });
}
