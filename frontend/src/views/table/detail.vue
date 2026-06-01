<template>
  <div class="table-detail-page page-shell">
    <v-card flat>
      <v-card-text class="ds-card__content d-flex align-center">
        <v-btn icon @click="router.back()" aria-label="返回">
          <v-icon>mdi-arrow-left</v-icon>
        </v-btn>
        <div class="page-title">
          采集表详情: <v-chip>{{ tblname }}</v-chip>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-detail-card">
      <v-card-text class="ds-card__content">
        <v-tabs v-model="currentTab" background-color="transparent">
          <v-tab value="info">基本详情</v-tab>
          <v-tab value="logs">采集日志</v-tab>
          <v-tab value="job">采集任务配置</v-tab>
          <v-tab value="fields">字段比较</v-tab>
          <v-tab value="result">采集结果</v-tab>
          <v-tab value="history">变更历史</v-tab>
        </v-tabs>

        <v-divider></v-divider>

        <v-tabs-window v-model="currentTab" class="mt-4">
          <v-tabs-window-item value="info">
            <TableDetail />
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

  import TableDetail from '@/components/table/TableDetail.vue';
  import FieldsCompare from '@/components/table/FieldsCompare.vue';
  import AddaxJob from '@/components/table/AddaxJob.vue';
  import AddaxResult from '@/components/table/AddaxResult.vue';
  import LogFiles from '@/components/table/LogFiles.vue';
  import ChangeHistory from '@/components/table/ChangeHistory.vue';

  const route = useRoute();
  const router = useRouter();

  const currentTab = ref(String(route.query.tab || 'info'));

  const tblname = ref(String(route.query.tblname || ''));

  watch(
    () => route.query.tab,
    newTab => {
      currentTab.value = String(newTab || 'info');
    }
  );

  watch(currentTab, val => {
    // Keep URL in sync for deep linking without adding new history entries
    router.replace({
      path: `/table/detail/${route.params.tid}`,
      query: { ...route.query, tab: val },
    });
  });

  onMounted(() => {
    if (!route.query.tab) {
      router.replace({
        path: `/table/detail/${route.params.tid}`,
        query: { ...route.query, tab: currentTab.value },
      });
    }
  });
</script>

<style scoped>
  .table-detail-page .page-title {
    font-size: 1.25rem;
    font-weight: 600;
    margin-left: 12px;
  }

  .table-detail-card {
    margin-top: 12px;
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
