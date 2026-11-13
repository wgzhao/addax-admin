<template>
  <div class="failure-history">
    <div class="failure-history__toolbar">
      <v-select
        v-model="stage"
        :items="stageOptions"
        item-title="title"
        item-value="value"
        label="失败阶段"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        class="failure-history__filter"
      />
      <v-select
        v-model="category"
        :items="categoryOptions"
        item-title="title"
        item-value="value"
        label="错误类别"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        class="failure-history__filter"
      />
      <v-checkbox
        v-model="finalOnly"
        label="仅最终失败"
        density="compact"
        hide-details
        class="failure-history__switch"
      />
      <v-spacer />
      <v-btn variant="text" size="small" prepend-icon="mdi-refresh" :loading="loading" @click="load">
        刷新
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-3">{{ error }}</v-alert>

    <v-data-table-server
      :headers="headers"
      :items="items"
      :items-length="total"
      :items-per-page="pageSize"
      :loading="loading"
      item-value="id"
      density="comfortable"
      class="failure-history__table"
      no-data-text="该采集表暂无失败记录"
      @update:options="onOptions"
    >
      <template #item.occurredAt="{ item }">
        <span class="text-no-wrap">{{ item.occurredAt }}</span>
      </template>
      <template #item.stage="{ item }">
        <v-chip size="small" color="error" variant="tonal">
          {{ stageLabel(item.stage) }}
        </v-chip>
      </template>
      <template #item.category="{ item }">
        <v-chip size="small" :color="categoryColor(item.category)" variant="tonal">
          {{ categoryLabel(item.category) }}
        </v-chip>
      </template>
      <template #item.attempts="{ item }">
        <span class="text-no-wrap">
          {{ item.attemptNo ?? '-' }}<span v-if="item.maxAttempts"> / {{ item.maxAttempts }}</span>
        </span>
      </template>
      <template #item.summary="{ item }">
        <span class="failure-history__summary" :title="item.summary">{{ item.summary }}</span>
      </template>
      <template #item.evidence="{ item }">
        <div class="failure-history__evidence">
          <v-icon v-if="item.logId" size="16" color="primary" title="有原始日志">
            mdi-file-document-outline
          </v-icon>
          <v-icon v-if="item.executionId" size="16" color="info" title="有执行快照">
            mdi-history
          </v-icon>
          <span v-if="!item.logId && !item.executionId" class="text-medium-emphasis">-</span>
        </div>
      </template>
      <template #item.actions="{ item }">
        <v-btn size="small" variant="text" color="primary" @click="openDetail(item.id)">查看</v-btn>
      </template>
    </v-data-table-server>

    <FailureDrawer v-model="drawerOpen" :failure-id="selectedId" />
  </div>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';
  import FailureDrawer from '@/components/failures/FailureDrawer.vue';
  import failureService from '@/service/failure-service';
  import {
    FAILURE_CATEGORY_OPTIONS,
    FAILURE_STAGE_OPTIONS,
    failureCategoryColor,
    failureCategoryLabel,
    failureStageLabel,
  } from '@/utils/failure-labels';
  import type { Failure } from '@/types/failure';

  const props = defineProps<{
    tid: number;
    active?: boolean;
  }>();

  const stageOptions = FAILURE_STAGE_OPTIONS;
  const categoryOptions = FAILURE_CATEGORY_OPTIONS;
  const stage = ref<string | null>(null);
  const category = ref<string | null>(null);
  const finalOnly = ref(false);
  const items = ref<Failure[]>([]);
  const total = ref(0);
  const page = ref(1);
  const pageSize = ref(10);
  const loading = ref(false);
  const error = ref('');
  const drawerOpen = ref(false);
  const selectedId = ref<number | null>(null);

  const stageLabel = failureStageLabel;
  const categoryLabel = failureCategoryLabel;
  const categoryColor = failureCategoryColor;

  const headers = [
    { title: '发生时间', key: 'occurredAt', width: 170, sortable: false },
    { title: '阶段', key: 'stage', width: 120, sortable: false },
    { title: '类别', key: 'category', width: 120, sortable: false },
    { title: '尝试', key: 'attempts', width: 80, sortable: false },
    { title: '摘要', key: 'summary', sortable: false },
    { title: '证据', key: 'evidence', width: 80, sortable: false },
    { title: '', key: 'actions', width: 80, sortable: false },
  ];

  async function load() {
    if (!props.tid) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await failureService.getFailures({
        tid: props.tid,
        page: page.value - 1,
        pageSize: pageSize.value,
        stage: stage.value || undefined,
        category: category.value || undefined,
        finalOnly: finalOnly.value,
      });
      items.value = result.content;
      total.value = result.totalElements;
    } catch (e) {
      error.value = `加载失败记录失败：${(e as Error)?.message || String(e)}`;
    } finally {
      loading.value = false;
    }
  }

  function onOptions(options: { page: number; itemsPerPage: number }) {
    page.value = options.page;
    pageSize.value = options.itemsPerPage;
    load();
  }

  function openDetail(id: number) {
    selectedId.value = id;
    drawerOpen.value = true;
  }

  watch([stage, category, finalOnly], () => {
    page.value = 1;
    load();
  });

  watch(
    () => props.active,
    active => {
      if (active) load();
    }
  );

</script>

<style scoped>
  .failure-history__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  .failure-history__filter {
    max-width: 180px;
    min-width: 140px;
  }

  .failure-history__switch {
    flex: none;
  }

  .failure-history__summary {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }

  .failure-history__evidence {
    display: flex;
    gap: 6px;
  }
</style>
