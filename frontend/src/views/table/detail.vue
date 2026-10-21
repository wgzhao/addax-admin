<template>
  <div class="table-detail-page page-shell">
    <v-card flat class="ds-card detail-header-card">
      <v-card-text class="ds-card__content d-flex align-center">
        <v-btn icon aria-label="返回" @click="router.back()">
          <v-icon>mdi-arrow-left</v-icon>
        </v-btn>
        <div class="page-title-block">
          <h1 class="page-title">采集表详情</h1>
          <span v-if="tblname" class="page-title__identity">{{ tblname }}</span>
        </div>
      </v-card-text>
    </v-card>

    <v-alert v-if="retiredAt" type="info" variant="tonal" class="mt-3" title="该采集表已退役">
      日常调度与编辑已关闭，当前页面保留退役前配置；历史统计、流水和日志仍可查询。可在采集表列表的“已退役”入口恢复。
      <span v-if="retiredBy"> 退役人：{{ retiredBy }}。</span>
    </v-alert>
    <v-alert
      v-else-if="!sourceEnabled"
      type="warning"
      variant="tonal"
      class="mt-3"
      title="采集源已停用"
    >
      该采集源下的采集表仍可查看和编辑，但不会参与自动调度。
    </v-alert>
    <v-alert
      v-else-if="tableStatus === 'X'"
      type="warning"
      variant="tonal"
      class="mt-3"
      title="该采集表已停采"
    >
      采集定义仍然有效且可编辑，但不会参与自动调度。
    </v-alert>

    <v-card flat class="ds-card table-detail-card">
      <v-card-text class="ds-card__content">
        <v-tabs
          :model-value="currentTab"
          show-arrows
          background-color="transparent"
          class="detail-tabs"
          @update:model-value="handleTabChange"
        >
          <v-tab value="info">基本详情</v-tab>
          <v-tab value="logs">采集日志</v-tab>
          <v-tab value="job">采集任务配置</v-tab>
          <v-tab value="fields">字段比较</v-tab>
          <v-tab value="result">采集结果</v-tab>
          <v-tab value="executions">执行快照</v-tab>
          <v-tab value="history">变更历史</v-tab>
        </v-tabs>

        <v-divider></v-divider>

        <v-tabs-window v-model="currentTab" class="mt-4">
          <v-tabs-window-item value="info">
            <TableDetail ref="detailRef" />
          </v-tabs-window-item>
          <v-tabs-window-item value="logs">
            <LogFiles />
          </v-tabs-window-item>
          <v-tabs-window-item value="job">
            <AddaxJob />
          </v-tabs-window-item>
          <v-tabs-window-item value="fields">
            <FieldsCompare />
          </v-tabs-window-item>
          <v-tabs-window-item value="result">
            <AddaxResult />
          </v-tabs-window-item>
          <v-tabs-window-item value="executions">
            <ExecutionHistory />
          </v-tabs-window-item>
          <v-tabs-window-item value="history">
            <ChangeHistory />
          </v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, watch, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import tableService from '@/service/table-service';

  import TableDetail from '@/components/table/TableDetail.vue';
  import FieldsCompare from '@/components/table/FieldsCompare.vue';
  import AddaxJob from '@/components/table/AddaxJob.vue';
  import AddaxResult from '@/components/table/AddaxResult.vue';
  import ExecutionHistory from '@/components/table/ExecutionHistory.vue';
  import LogFiles from '@/components/table/LogFiles.vue';
  import ChangeHistory from '@/components/table/ChangeHistory.vue';

  const route = useRoute();
  const router = useRouter();

  const currentTab = ref(String(route.query.tab || 'info'));
  const detailRef = ref<{ canLeave: () => boolean } | null>(null);

  const tblname = ref(String(route.query.tblname || ''));
  const retiredAt = ref<string | null>(null);
  const retiredBy = ref<string | null>(null);
  const sourceEnabled = ref(true);
  const tableStatus = ref('');

  // unplugin-vue-router 0.19 does not emit param types for custom route-block
  // paths, so the :tid param is absent from the generated RouteNamedMap
  const tid = String((route.params as Record<string, string>).tid);

  watch(
    () => route.query.tab,
    newTab => {
      currentTab.value = String(newTab || 'info');
    }
  );

  watch(currentTab, val => {
    // Keep URL in sync for deep linking without adding new history entries
    router.replace({
      path: `/table/detail/${tid}`,
      query: { ...route.query, tab: val },
    });
  });

  const handleTabChange = (nextTab: unknown) => {
    const target = String(nextTab);
    if (target === currentTab.value) return;
    if (!detailRef.value?.canLeave()) return;
    currentTab.value = target;
  };

  onMounted(() => {
    tableService
      .fetchTableDetail(Number(tid))
      .then(table => {
        retiredAt.value = table.retiredAt || null;
        retiredBy.value = table.retiredBy || null;
        sourceEnabled.value = table.enabled !== false;
        tableStatus.value = table.status || '';
      })
      .catch(() => {});
    if (!route.query.tab) {
      router.replace({
        path: `/table/detail/${tid}`,
        query: { ...route.query, tab: currentTab.value },
      });
    }
  });
</script>

<style scoped>
  .table-detail-page .page-title {
    margin: 0;
    font-size: 1.25rem;
    font-weight: 600;
    line-height: 1.45;
  }

  .page-title-block {
    display: flex;
    align-items: baseline;
    gap: 10px;
    min-width: 0;
    margin-left: 12px;
  }

  .page-title__identity {
    min-width: 0;
    overflow: hidden;
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
    font-size: 0.84rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .detail-header-card {
    flex: none;
  }

  .table-detail-card {
    margin-top: 12px;
  }

  @media (max-width: 760px) {
    .page-title__identity {
      display: none;
    }

    .page-title-block {
      flex: 1;
    }
  }
</style>

<route lang="json">
{
  "path": "/table/detail/:tid",
  "meta": {
    "title": "采集表详情",
    "navHidden": true
  }
}
</route>
