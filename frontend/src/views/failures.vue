<template>
  <div class="page-shell failures-page">
    <PageHeader title="错误中心" subtitle="跨采集表查看失败事实、重复错误与历史清理。">
      <v-btn
        v-if="authStore.isAdmin"
        variant="tonal"
        color="error"
        prepend-icon="mdi-delete-sweep-outline"
        @click="openCleanup"
      >
        清理历史
      </v-btn>
    </PageHeader>

    <div class="failures-summary">
      <div class="summary-card">
        <span class="summary-label">失败总数</span>
        <strong>{{ stats.total }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">涉及采集表</span>
        <strong>{{ stats.distinctTables }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">最多失败阶段</span>
        <strong>{{ topLabel(stats.byStage, 'stage') }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">最多错误类别</span>
        <strong>{{ topLabel(stats.byCategory, 'category') }}</strong>
      </div>
    </div>

    <v-card flat class="ds-card failures-card">
      <v-card-text>
        <div class="failures-filters">
          <v-select
            v-model="filters.sourceId"
            :items="sourceOptions"
            item-title="title"
            item-value="value"
            label="采集源"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            class="filter-select"
          />
          <v-select
            v-model="filters.stage"
            :items="stageOptions"
            item-title="title"
            item-value="value"
            label="失败阶段"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            class="filter-select"
          />
          <v-select
            v-model="filters.category"
            :items="categoryOptions"
            item-title="title"
            item-value="value"
            label="错误类别"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            class="filter-select"
          />
          <v-text-field
            v-model="filters.q"
            label="摘要关键字"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            class="filter-keyword"
            @keyup.enter="search"
          />
          <v-text-field
            v-model="filters.from"
            label="开始日期"
            type="date"
            density="compact"
            variant="outlined"
            hide-details
            class="filter-date"
          />
          <v-text-field
            v-model="filters.to"
            label="结束日期"
            type="date"
            density="compact"
            variant="outlined"
            hide-details
            class="filter-date"
          />
          <v-checkbox
            v-model="filters.finalOnly"
            label="仅最终失败"
            density="compact"
            hide-details
          />
          <v-btn color="primary" variant="flat" prepend-icon="mdi-magnify" @click="search">
            查询
          </v-btn>
          <v-btn variant="text" prepend-icon="mdi-refresh" @click="resetFilters">重置</v-btn>
          <v-btn variant="tonal" prepend-icon="mdi-download" @click="exportCsv">导出 CSV</v-btn>
        </div>

        <div class="failures-mode">
          <v-btn-toggle v-model="mode" density="compact" mandatory variant="outlined">
            <v-btn value="detail" prepend-icon="mdi-format-list-bulleted">明细</v-btn>
            <v-btn value="grouped" prepend-icon="mdi-group">按错误聚合</v-btn>
          </v-btn-toggle>
          <v-spacer />
          <v-chip v-if="filters.tid" closable size="small" variant="tonal" @click:close="clearTid">
            采集表 #{{ filters.tid }}
          </v-chip>
          <v-chip
            v-if="filters.fingerprint"
            closable
            size="small"
            variant="tonal"
            @click:close="clearFingerprint"
          >
            错误指纹 {{ filters.fingerprint.slice(0, 10) }}…
          </v-chip>
        </div>

        <v-alert v-if="error" type="error" variant="tonal" class="mt-3">{{ error }}</v-alert>

        <v-data-table-server
          v-if="mode === 'detail'"
          v-model:page="page"
          v-model:items-per-page="pageSize"
          :headers="detailHeaders"
          :items="items"
          :items-length="total"
          :items-per-page-options="[10, 20, 50, 100]"
          :loading="loading"
          item-value="id"
          density="comfortable"
          class="mt-4"
          no-data-text="没有匹配的失败记录"
          @update:options="onOptions"
        >
          <template #item.occurredAt="{ item }">
            <span class="text-no-wrap">{{ item.occurredAt }}</span>
          </template>
          <template #item.table="{ item }">
            <div
              class="table-cell"
              :title="`${item.sourceDb}.${item.sourceTable} → ${item.targetDb}.${item.targetTable}`"
            >
              <strong>{{ item.sourceDb }}.{{ item.sourceTable }}</strong>
              <span>→ {{ item.targetDb }}.{{ item.targetTable }}</span>
            </div>
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
          <template #item.summary="{ item }">
            <span class="summary-cell" :title="item.summary">{{ item.summary }}</span>
          </template>
          <template #item.actions="{ item }">
            <v-btn size="small" variant="text" color="primary" @click="openDetail(item.id)">
              查看
            </v-btn>
          </template>
        </v-data-table-server>

        <v-data-table
          v-else
          :headers="groupHeaders"
          :items="groups"
          :loading="loading"
          item-value="fingerprint"
          density="comfortable"
          class="mt-4"
          no-data-text="没有匹配的聚合错误"
        >
          <template #item.table="{ item }">
            <div
              class="table-cell"
              :title="`${item.sourceDb}.${item.sourceTable} → ${item.targetDb}.${item.targetTable}`"
            >
              <strong>{{ item.sourceDb }}.{{ item.sourceTable }}</strong>
              <span>→ {{ item.targetDb }}.{{ item.targetTable }}</span>
            </div>
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
          <template #item.count="{ item }">
            <v-chip size="small" color="warning" variant="tonal">{{ item.count }}</v-chip>
          </template>
          <template #item.summary="{ item }">
            <span class="summary-cell" :title="item.summary">{{ item.summary }}</span>
          </template>
          <template #item.actions="{ item }">
            <v-btn size="small" variant="text" color="primary" @click="showGroupDetail(item)">
              明细
            </v-btn>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <FailureDrawer v-model="drawerOpen" :failure-id="selectedId" />

    <v-dialog v-model="cleanupOpen" attach="body" max-width="560" persistent>
      <v-card>
        <v-card-title>清理失败记录</v-card-title>
        <v-card-text>
          <v-alert type="warning" variant="tonal" density="compact" class="mb-3">
            只删除失败事实，不会删除原始日志、执行快照或任何采集数据。
          </v-alert>
          <v-text-field
            v-model="cleanup.before"
            label="清理早于该日期的记录"
            type="date"
            density="compact"
            variant="outlined"
          />
          <v-select
            v-model="cleanup.stage"
            :items="stageOptions"
            item-title="title"
            item-value="value"
            label="仅清理指定阶段（可选）"
            density="compact"
            variant="outlined"
            clearable
          />
          <div v-if="cleanupPreview" class="cleanup-preview">
            匹配 <strong>{{ cleanupPreview.matched }}</strong> 条记录
            <span v-if="cleanupPreview.deleted">，已删除 {{ cleanupPreview.deleted }} 条</span>
          </div>
          <v-alert v-if="cleanupError" type="error" variant="tonal" class="mt-3">
            {{ cleanupError }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-btn variant="text" :disabled="cleanupLoading" @click="cleanupOpen = false">取消</v-btn>
          <v-spacer />
          <v-btn
            variant="tonal"
            :loading="cleanupLoading"
            :disabled="!cleanup.before"
            @click="runCleanup(true)"
          >
            预检数量
          </v-btn>
          <v-btn
            color="error"
            variant="flat"
            :loading="cleanupLoading"
            :disabled="!cleanup.before || !cleanupPreview"
            @click="runCleanup(false)"
          >
            确认清理
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import PageHeader from '@/components/PageHeader.vue';
  import FailureDrawer from '@/components/failures/FailureDrawer.vue';
  import failureService from '@/service/failure-service';
  import sourceService from '@/service/source-service';
  import { notify } from '@/stores/notifier';
  import { useAuthStore } from '@/stores/auth';
  import { buildCsv, downloadCsv } from '@/utils/csv';
  import {
    FAILURE_CATEGORY_OPTIONS,
    FAILURE_STAGE_OPTIONS,
    failureCategoryColor,
    failureCategoryLabel,
    failureStageLabel,
  } from '@/utils/failure-labels';
  import type { Failure, FailureGroup, FailureStats } from '@/types/failure';
  import type { EtlSource } from '@/types/database';

  const route = useRoute();
  const router = useRouter();
  const authStore = useAuthStore();

  const stageOptions = FAILURE_STAGE_OPTIONS;
  const categoryOptions = FAILURE_CATEGORY_OPTIONS;
  const stageLabel = failureStageLabel;
  const categoryLabel = failureCategoryLabel;
  const categoryColor = failureCategoryColor;

  const sources = ref<EtlSource[]>([]);
  const sourceOptions = computed(() =>
    sources.value.map(source => ({
      value: source.id,
      title: `${source.name} (#${source.id})`,
    }))
  );

  const filters = ref({
    sourceId: null as number | null,
    stage: null as string | null,
    category: null as string | null,
    fingerprint: null as string | null,
    tid:
      route.query.tid && Number.isFinite(Number(route.query.tid)) ? Number(route.query.tid) : null,
    q: '',
    from: defaultFrom(),
    to: today(),
    finalOnly: false,
  });

  const mode = ref<'detail' | 'grouped'>('detail');
  const page = ref(1);
  const pageSize = ref(20);
  const items = ref<Failure[]>([]);
  const total = ref(0);
  const groups = ref<FailureGroup[]>([]);
  const stats = ref<FailureStats>({ total: 0, distinctTables: 0, byStage: {}, byCategory: {} });
  const loading = ref(false);
  const error = ref('');

  const drawerOpen = ref(false);
  const selectedId = ref<number | null>(null);

  const cleanupOpen = ref(false);
  const cleanupLoading = ref(false);
  const cleanupError = ref('');
  const cleanup = ref({ before: defaultRetentionCutoff(), stage: null as string | null });
  const cleanupPreview = ref<{ matched: number; deleted: number } | null>(null);

  const detailHeaders = [
    { title: '发生时间', key: 'occurredAt', width: 170, sortable: false },
    { title: '采集表', key: 'table', width: 240, sortable: false },
    { title: '阶段', key: 'stage', width: 120, sortable: false },
    { title: '类别', key: 'category', width: 120, sortable: false },
    { title: '摘要', key: 'summary', sortable: false },
    { title: '', key: 'actions', width: 80, sortable: false },
  ];

  const groupHeaders = [
    { title: '采集表', key: 'table', width: 240, sortable: false },
    { title: '阶段', key: 'stage', width: 120, sortable: false },
    { title: '类别', key: 'category', width: 120, sortable: false },
    { title: '次数', key: 'count', width: 80, sortable: false },
    { title: '首次', key: 'firstSeenAt', width: 170, sortable: false },
    { title: '最近', key: 'lastSeenAt', width: 170, sortable: false },
    { title: '摘要', key: 'summary', sortable: false },
    { title: '', key: 'actions', width: 80, sortable: false },
  ];

  function defaultFrom() {
    const date = new Date();
    date.setDate(date.getDate() - 30);
    return date.toISOString().slice(0, 10);
  }

  function today() {
    return new Date().toISOString().slice(0, 10);
  }

  function defaultRetentionCutoff() {
    const date = new Date();
    date.setDate(date.getDate() - 365);
    return date.toISOString().slice(0, 10);
  }

  function topLabel(map: Record<string, number>, kind: 'stage' | 'category') {
    const entry = Object.entries(map)[0];
    if (!entry) return '-';
    return kind === 'stage' ? stageLabel(entry[0]) : categoryLabel(entry[0]);
  }

  function queryParams() {
    return {
      tid: filters.value.tid ?? undefined,
      sourceId: filters.value.sourceId ?? undefined,
      stage: filters.value.stage ?? undefined,
      category: filters.value.category ?? undefined,
      fingerprint: filters.value.fingerprint ?? undefined,
      from: filters.value.from || undefined,
      to: filters.value.to || undefined,
      q: filters.value.q || undefined,
      finalOnly: filters.value.finalOnly,
    };
  }

  async function load() {
    loading.value = true;
    error.value = '';
    try {
      if (mode.value === 'detail') {
        const result = await failureService.getFailures({
          ...queryParams(),
          page: page.value - 1,
          pageSize: pageSize.value,
        });
        items.value = result.content;
        total.value = result.totalElements;
      } else {
        groups.value = await failureService.getGroups({
          ...queryParams(),
          limit: 200,
        });
      }
    } catch (e) {
      error.value = `加载失败记录失败：${(e as Error)?.message || String(e)}`;
    } finally {
      loading.value = false;
    }
  }

  async function loadStats() {
    try {
      stats.value = await failureService.getStats(queryParams());
    } catch {
      // Stats are decorative; the table already reports load errors.
    }
  }

  function search() {
    page.value = 1;
    load();
    loadStats();
  }

  function resetFilters() {
    filters.value = {
      sourceId: null,
      stage: null,
      category: null,
      fingerprint: null,
      tid: null,
      q: '',
      from: defaultFrom(),
      to: today(),
      finalOnly: false,
    };
    search();
  }

  function clearTid() {
    filters.value.tid = null;
    router.replace({ path: '/failures' });
    search();
  }

  function clearFingerprint() {
    filters.value.fingerprint = null;
    search();
  }

  function onOptions(options: { page: number; itemsPerPage: number }) {
    page.value = options.page;
    pageSize.value = options.itemsPerPage;
    load();
  }

  async function exportCsv() {
    try {
      const result = await failureService.getFailures({
        ...queryParams(),
        page: 0,
        pageSize: 1000,
      });
      const rows = result.content.map(failure => ({
        occurredAt: failure.occurredAt,
        table: `${failure.sourceDb}.${failure.sourceTable} → ${failure.targetDb}.${failure.targetTable}`,
        stage: stageLabel(failure.stage),
        category: categoryLabel(failure.category),
        attempt: failure.maxAttempts
          ? `${failure.attemptNo ?? '-'}/${failure.maxAttempts}`
          : (failure.attemptNo ?? ''),
        summary: failure.summary,
        errorClass: failure.errorClass || '',
        errorCode: failure.errorCode || '',
        runType: failure.runType === 'FILLBACK' ? '补数' : '常规',
        bizDate: failure.bizDate || '',
      }));
      downloadCsv(
        `failures-${today()}`,
        buildCsv(
          [
            { title: '发生时间', key: 'occurredAt' },
            { title: '采集表', key: 'table' },
            { title: '阶段', key: 'stage' },
            { title: '类别', key: 'category' },
            { title: '尝试', key: 'attempt' },
            { title: '摘要', key: 'summary' },
            { title: '错误类', key: 'errorClass' },
            { title: '错误码', key: 'errorCode' },
            { title: '运行类型', key: 'runType' },
            { title: '业务日期', key: 'bizDate' },
          ],
          rows
        )
      );
    } catch (e) {
      notify(`导出失败：${(e as Error)?.message || String(e)}`, 'error');
    }
  }

  function openDetail(id: number) {
    selectedId.value = id;
    drawerOpen.value = true;
  }

  function showGroupDetail(group: FailureGroup) {
    filters.value.fingerprint = group.fingerprint;
    filters.value.tid = group.tid;
    mode.value = 'detail';
    search();
  }

  function openCleanup() {
    cleanup.value = { before: defaultRetentionCutoff(), stage: null };
    cleanupPreview.value = null;
    cleanupError.value = '';
    cleanupOpen.value = true;
  }

  async function runCleanup(dryRun: boolean) {
    cleanupLoading.value = true;
    cleanupError.value = '';
    try {
      const result = await failureService.cleanup({
        before: cleanup.value.before,
        stage: cleanup.value.stage || undefined,
        tid: filters.value.tid ?? undefined,
        sourceId: filters.value.sourceId ?? undefined,
        dryRun,
      });
      cleanupPreview.value = { matched: result.matched, deleted: result.deleted };
      if (!dryRun) {
        notify(`已清理 ${result.deleted} 条失败记录`, 'success');
        cleanupOpen.value = false;
        search();
      }
    } catch (e) {
      cleanupError.value = (e as Error)?.message || String(e);
    } finally {
      cleanupLoading.value = false;
    }
  }

  watch(mode, () => search());
  // A confirm must always follow a preview of the exact same cutoff and scope.
  watch(
    () => [cleanup.value.before, cleanup.value.stage],
    () => {
      cleanupPreview.value = null;
    }
  );

  onMounted(async () => {
    loadStats();
    try {
      sources.value = await sourceService.list();
    } catch {
      // The source filter is optional.
    }
  });
</script>

<style scoped>
  .failures-summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }

  .summary-card {
    padding: 16px 18px;
    border-radius: 12px;
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .summary-label {
    display: block;
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .summary-card strong {
    display: block;
    margin-top: 6px;
    font-size: 20px;
    line-height: 1.2;
  }

  .failures-filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }

  .filter-select {
    width: 170px;
  }

  .filter-keyword {
    width: 200px;
  }

  .filter-date {
    width: 160px;
  }

  .failures-mode {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 16px;
  }

  .table-cell {
    min-width: 200px;
    max-width: 240px;
  }

  .table-cell strong,
  .table-cell span {
    display: block;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .table-cell strong {
    font-size: 13px;
  }

  .table-cell span {
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .summary-cell {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }

  .cleanup-preview {
    margin-top: 12px;
    font-size: 14px;
  }

  @media (max-width: 960px) {
    .failures-summary {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "错误中心",
    "icon": "mdi-alert-decagram-outline",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 29,
    "roles": ["admin", "user"]
  }
}
</route>
