<template>
  <div class="page-shell status-page">
    <PageHeader title="系统状态" subtitle="节点、队列与调度健康度；每 15 秒自动刷新。">
      <v-btn
        variant="tonal"
        size="small"
        :loading="loading"
        prepend-icon="mdi-refresh"
        @click="refresh"
      >
        刷新
      </v-btn>
    </PageHeader>

    <ErrorState v-if="error" :message="error" @retry="refresh" />

    <section class="status-hero" :class="`status-hero--${overall.tone}`">
      <div class="status-hero-icon">
        <v-icon size="24">{{ overall.icon }}</v-icon>
      </div>
      <div class="status-hero-copy">
        <div class="status-hero-title">{{ overall.label }}</div>
        <div class="status-hero-sub">{{ overall.message }}</div>
      </div>
      <div class="status-hero-meta">
        <span>最近更新 {{ updatedAt || '—' }}</span>
        <span v-if="cluster">主节点 {{ cluster.masterInstanceId || '—' }}</span>
      </div>
    </section>

    <div class="status-grid">
      <SectionCard title="集群节点" subtitle="心跳超过 30 秒视为离线" icon="mdi-server-network">
        <SkeletonBlock v-if="loading && !cluster" :height="120" />
        <div v-else-if="cluster?.nodes?.length" class="node-list">
          <div v-for="node in cluster.nodes" :key="node.instanceId" class="node-row">
            <span class="node-dot" :class="{ 'node-dot--offline': !node.online }" />
            <div class="node-copy">
              <div class="node-host">{{ node.host }}</div>
              <div class="node-meta">
                {{ roleLabel(node.role) }} · 并发 {{ node.running }}/{{ node.concurrentLimit }} ·
                权重 {{ node.weight }}
              </div>
            </div>
            <v-chip size="x-small" :color="node.online ? 'success' : 'error'" variant="tonal">
              {{ node.online ? '在线' : '离线' }}
            </v-chip>
          </div>
        </div>
        <div v-else class="status-empty">暂无节点数据</div>
      </SectionCard>

      <SectionCard title="队列与调度" subtitle="当前队列与数据库待处理任务" icon="mdi-tray-full">
        <SkeletonBlock v-if="loading && !queue" :height="120" />
        <div v-else-if="queueEntries.length" class="kv-list">
          <div v-for="item in queueEntries" :key="item.key" class="kv-row">
            <span class="kv-key">{{ item.key }}</span>
            <span class="kv-value">{{ item.value }}</span>
          </div>
        </div>
        <div v-else class="status-empty">暂无队列数据</div>
      </SectionCard>

      <SectionCard title="运行概况" subtitle="当前业务日采集表状态" icon="mdi-chart-donut">
        <SkeletonBlock v-if="loading && !summary" :height="120" />
        <div v-else-if="summary" class="kv-list">
          <div class="kv-row">
            <span class="kv-key">今日采集表</span>
            <span class="kv-value">{{ doneCount }}/{{ tableTotal }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-key">失败表</span>
            <span class="kv-value" :class="{ 'kv-value--bad': failedCount > 0 }">
              {{ failedCount }}
            </span>
          </div>
          <div class="kv-row">
            <span class="kv-key">采集源</span>
            <span class="kv-value">{{ summary.allDbSourceCount }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-key">累计业务日</span>
            <span class="kv-value">{{ summary.totalDays }}</span>
          </div>
        </div>
        <div v-else class="status-empty">暂无运行数据</div>
      </SectionCard>

      <SectionCard title="服务信息" subtitle="版本、构建与运行环境" icon="mdi-information-outline">
        <div class="kv-list">
          <div class="kv-row">
            <span class="kv-key">前端版本</span>
            <span class="kv-value">v{{ APP_VERSION }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-key">构建</span>
            <span class="kv-value">{{ APP_BUILD_SHA }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-key">环境</span>
            <span class="kv-value">{{ APP_ENV_LABEL }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-key">许可</span>
            <span class="kv-value">{{ APP_LICENSE }}</span>
          </div>
        </div>
      </SectionCard>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
  import PageHeader from '@/components/PageHeader.vue';
  import SectionCard from '@/components/common/SectionCard.vue';
  import SkeletonBlock from '@/components/common/SkeletonBlock.vue';
  import ErrorState from '@/components/common/ErrorState.vue';
  import { clusterService, type ClusterStatus } from '@/service/cluster-service';
  import taskService from '@/service/task-service';
  import { dashboardService } from '@/service/dashboard-service';
  import type { DashboardSummary } from '@/types/dashboard';
  import { APP_ENV_LABEL, APP_BUILD_SHA, APP_VERSION } from '@/config/version';
  import { APP_LICENSE } from '@/config/branding';

  const cluster = ref<ClusterStatus | null>(null);
  const queue = ref<Record<string, unknown> | null>(null);
  const summary = ref<DashboardSummary | null>(null);
  const loading = ref(true);
  const error = ref<string | null>(null);
  const updatedAt = ref('');

  const doneCount = computed(() =>
    (summary.value?.ratios ?? []).reduce((sum, row) => sum + Number(row.done ?? 0), 0)
  );
  const failedCount = computed(() =>
    (summary.value?.ratios ?? []).reduce((sum, row) => sum + Number(row.failed ?? 0), 0)
  );
  const tableTotal = computed(() =>
    (summary.value?.ratios ?? []).reduce((sum, row) => sum + Number(row.total ?? 0), 0)
  );

  const QUEUE_LABELS: Record<string, string> = {
    masterInstanceId: '主节点',
    isMaster: '是否主节点',
    queueSize: '队列长度',
    pendingInDatabase: '数据库待处理',
    runningInDatabase: '数据库运行中',
    runningTasks: '运行中任务',
    monitor: '队列监控',
    state: '调度状态',
  };

  const roleLabel = (role: string) => (role.includes('MASTER') ? '主节点' : '工作节点');

  const queueEntries = computed(() => {
    if (!queue.value) return [];
    return Object.entries(queue.value).map(([key, value]) => ({
      key: QUEUE_LABELS[key] || key,
      value:
        value === null || value === undefined
          ? '—'
          : typeof value === 'object'
          ? JSON.stringify(value)
          : String(value),
    }));
  });

  const overall = computed(() => {
    const nodes = cluster.value?.nodes ?? [];
    const offline = nodes.filter(node => !node.online).length;
    const queueSize = Number(queue.value?.queueSize ?? 0);
    if (error.value) {
      return {
        tone: 'critical',
        icon: 'mdi-alert-circle-outline',
        label: '状态未知',
        message: '部分状态数据加载失败，请重试或检查后端服务。',
      };
    }
    if (offline > 0 || failedCount.value > 0) {
      return {
        tone: 'warning',
        icon: 'mdi-alert-outline',
        label: '需要关注',
        message: `${offline > 0 ? `${offline} 个节点离线` : ''}${
          offline > 0 && failedCount.value > 0 ? '，' : ''
        }${failedCount.value > 0 ? `${failedCount.value} 张采集表失败` : ''}。`,
      };
    }
    return {
      tone: 'healthy',
      icon: 'mdi-check-circle-outline',
      label: '运行正常',
      message: `${nodes.length} 个节点在线，队列 ${queueSize} 个任务，今日采集 ${
        tableTotal.value > 0 ? `${doneCount.value}/${tableTotal.value}` : '暂无计划'
      }。`,
    };
  });

  const refresh = async () => {
    loading.value = true;
    error.value = null;
    const results = await Promise.allSettled([
      clusterService.getNodes(),
      taskService.getQueueStatus(),
      dashboardService.fetchSummary(),
    ] as const);
    const [clusterResult, queueResult, summaryResult] = results;
    if (clusterResult.status === 'fulfilled') cluster.value = clusterResult.value;
    if (queueResult.status === 'fulfilled') queue.value = (queueResult.value ?? {}) as Record<string, unknown>;
    if (summaryResult.status === 'fulfilled') summary.value = summaryResult.value;

    const failures = results.filter(result => result.status === 'rejected');
    if (failures.length === results.length) {
      error.value = '状态数据加载失败，请检查后端服务。';
    } else if (failures.length > 0) {
      error.value = `部分状态数据加载失败（${failures.length} 项）。`;
    }
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
    loading.value = false;
  };

  let timer: number | undefined;
  onMounted(() => {
    void refresh();
    timer = window.setInterval(() => void refresh(), 15_000);
  });
  onBeforeUnmount(() => {
    if (timer) window.clearInterval(timer);
  });
</script>

<route lang="json">
{
  "meta": {
    "title": "系统状态",
    "icon": "mdi-heart-pulse",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 5
  }
}
</route>

<style scoped>
  .status-hero {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px 18px;
    border: 1px solid var(--ds-border-default);
    border-radius: 12px;
    background: var(--ds-surface-raised);
  }

  .status-hero-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 42px;
    height: 42px;
    border-radius: 11px;
    flex: none;
  }

  .status-hero--healthy .status-hero-icon {
    background: rgba(var(--v-theme-success), 0.12);
    color: rgb(var(--v-theme-success));
  }

  .status-hero--warning .status-hero-icon {
    background: rgba(var(--v-theme-warning), 0.14);
    color: rgb(var(--v-theme-warning));
  }

  .status-hero--critical .status-hero-icon {
    background: rgba(var(--v-theme-error), 0.12);
    color: rgb(var(--v-theme-error));
  }

  .status-hero-copy {
    flex: 1 1 auto;
    min-width: 0;
  }

  .status-hero-title {
    font-size: 15px;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .status-hero-sub {
    margin-top: 3px;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .status-hero-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 11px;
    color: var(--ds-text-3);
    text-align: right;
    white-space: nowrap;
  }

  .status-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .node-list,
  .kv-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .node-row {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 7px 6px;
    border-radius: 7px;
  }

  .node-row:hover {
    background: rgba(var(--v-theme-on-surface), 0.03);
  }

  .node-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgb(var(--v-theme-success));
    flex: none;
  }

  .node-dot--offline {
    background: rgb(var(--v-theme-error));
  }

  .node-copy {
    flex: 1 1 auto;
    min-width: 0;
  }

  .node-host {
    font-size: 12px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .node-meta {
    margin-top: 1px;
    font-size: 11px;
    color: var(--ds-text-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .kv-row {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    padding: 7px 6px;
    font-size: 12px;
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .kv-row:last-child {
    border-bottom: none;
  }

  .kv-key {
    color: var(--ds-text-2);
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .kv-value {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .kv-value--bad {
    color: rgb(var(--v-theme-error));
  }

  .status-empty {
    padding: 24px 8px;
    text-align: center;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  @media (max-width: 960px) {
    .status-grid {
      grid-template-columns: 1fr;
    }

    .status-hero {
      flex-wrap: wrap;
    }

    .status-hero-meta {
      text-align: left;
    }
  }
</style>
