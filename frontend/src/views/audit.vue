<template>
  <div class="page-shell audit-page">
    <PageHeader
      title="安全审计"
      subtitle="追溯登录、权限拒绝和管理员变更；敏感字段在写入前完成脱敏。"
    >
      <v-chip color="warning" variant="tonal" size="small" prepend-icon="mdi-lock-outline">
        仅管理员可查询
      </v-chip>
      <v-btn
        variant="tonal"
        size="small"
        prepend-icon="mdi-download-outline"
        :loading="exporting"
        @click="exportEvents"
      >
        导出 CSV
      </v-btn>
    </PageHeader>

    <v-row density="comfortable" class="mb-1">
      <v-col cols="12" md="4">
        <v-card flat class="metric-card ds-card">
          <v-card-text>
            <span class="metric-label">当前结果</span>
            <strong>{{ totalItems }}</strong>
            <span class="metric-note">符合筛选条件的审计事件</span>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="4">
        <v-card flat class="metric-card metric-card--warning ds-card">
          <v-card-text>
            <span class="metric-label">警示事件</span>
            <strong>{{ warningCount }}</strong>
            <span class="metric-note">WARNING 与 CRITICAL</span>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="4">
        <v-card flat class="metric-card metric-card--danger ds-card">
          <v-card-text>
            <span class="metric-label">拒绝/失败</span>
            <strong>{{ rejectedCount }}</strong>
            <span class="metric-note">需要关注的操作结果</span>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-card flat class="ds-card filter-card">
      <v-card-text class="ds-card__content">
        <v-row density="comfortable" align="center">
          <v-col cols="12" sm="6" lg="2">
            <v-text-field
              v-model="filters.from"
              type="date"
              density="compact"
              variant="outlined"
              label="开始日期"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="6" lg="2">
            <v-text-field
              v-model="filters.to"
              type="date"
              density="compact"
              variant="outlined"
              label="结束日期"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="6" lg="2">
            <v-text-field
              v-model="filters.actor"
              density="compact"
              variant="outlined"
              label="操作者"
              prepend-inner-icon="mdi-account-outline"
              hide-details
              @keyup.enter="search(1)"
            />
          </v-col>
          <v-col cols="12" sm="6" lg="2">
            <v-text-field
              v-model="filters.action"
              density="compact"
              variant="outlined"
              label="动作"
              prepend-inner-icon="mdi-flash-outline"
              hide-details
              @keyup.enter="search(1)"
            />
          </v-col>
          <v-col cols="6" sm="3" lg="1">
            <v-select
              v-model="filters.outcome"
              :items="outcomeOptions"
              density="compact"
              variant="outlined"
              label="结果"
              clearable
              hide-details
            />
          </v-col>
          <v-col cols="6" sm="3" lg="1">
            <v-select
              v-model="filters.severity"
              :items="severityOptions"
              density="compact"
              variant="outlined"
              label="级别"
              clearable
              hide-details
            />
          </v-col>
          <v-col cols="12" lg="2" class="d-flex justify-end">
            <v-btn
              color="primary"
              variant="flat"
              prepend-icon="mdi-magnify"
              :loading="loading"
              @click="search(1)"
            >
              查询
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-card ds-table-wrap ds-table-scroll">
      <v-card-text class="ds-card__content">
        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          closable
          class="mb-4"
          @click:close="error = ''"
        >
          {{ error }}
        </v-alert>
        <v-data-table-server
          :headers="headers"
          :items="events"
          :items-length="totalItems"
          :loading="loading"
          :items-per-page="pageSize"
          :page="page"
          item-value="id"
          density="comfortable"
          class="audit-table"
          @update:options="handleTableOptions"
          @click:row="openDetail"
        >
          <template #item.occurredAt="{ item }">
            <div class="time-cell">
              <strong>{{ formatDate(item.occurredAt) }}</strong>
              <span>{{ formatTime(item.occurredAt) }}</span>
            </div>
          </template>
          <template #item.actorUsername="{ item }">
            <div class="actor-cell">
              <v-icon size="16" :color="item.actorType === 'SYSTEM' ? 'secondary' : 'primary'">
                {{
                  item.actorType === 'SYSTEM' ? 'mdi-robot-outline' : 'mdi-account-circle-outline'
                }}
              </v-icon>
              <span>{{ item.actorUsername || 'SYSTEM' }}</span>
            </div>
          </template>
          <template #item.action="{ item }">
            <div class="action-cell">
              <span class="action-code">{{ item.action }}</span>
              <span class="action-resource">
                {{ item.resourceType || 'API' }}
                <template v-if="item.resourceId"> · {{ item.resourceId }}</template>
              </span>
            </div>
          </template>
          <template #item.severity="{ item }">
            <v-chip size="x-small" variant="tonal" :color="severityColor(item.severity)">
              {{ item.severity }}
            </v-chip>
          </template>
          <template #item.outcome="{ item }">
            <div class="outcome-cell">
              <span class="outcome-dot" :class="`outcome-dot--${item.outcome.toLowerCase()}`" />
              <span>{{ outcomeLabel(item.outcome) }}</span>
            </div>
          </template>
          <template #item.requestId="{ item }">
            <code class="request-code" :title="item.requestId || ''">
              {{ shortId(item.requestId) }}
            </code>
          </template>
          <template #no-data>
            <EmptyState
              title="没有匹配的审计事件"
              description="调整日期、操作者或动作后重新查询。审计查询本身也会被记录。"
            />
          </template>
        </v-data-table-server>
      </v-card-text>
    </v-card>

    <v-dialog v-model="detailOpen" max-width="860" scrollable>
      <v-card v-if="selectedEvent">
        <v-card-title class="detail-title">
          <div>
            <span class="detail-eyebrow">SECURITY EVENT #{{ selectedEvent.id }}</span>
            <h2>{{ selectedEvent.action }}</h2>
          </div>
          <v-chip :color="severityColor(selectedEvent.severity)" variant="tonal" size="small">
            {{ selectedEvent.severity }}
          </v-chip>
        </v-card-title>
        <v-card-text class="detail-body">
          <div class="detail-grid">
            <div>
              <span>操作者</span><strong>{{ selectedEvent.actorUsername || 'SYSTEM' }}</strong>
            </div>
            <div>
              <span>发生时间</span><strong>{{ formatFullTime(selectedEvent.occurredAt) }}</strong>
            </div>
            <div>
              <span>结果</span><strong>{{ outcomeLabel(selectedEvent.outcome) }}</strong>
            </div>
            <div>
              <span>资源</span><strong>{{ resourceLabel(selectedEvent) }}</strong>
            </div>
            <div>
              <span>来源 IP</span><strong>{{ selectedEvent.clientIp || '—' }}</strong>
            </div>
            <div>
              <span>请求</span
              ><strong
                >{{ selectedEvent.httpMethod || '—' }} {{ selectedEvent.requestPath || '' }}</strong
              >
            </div>
          </div>

          <v-alert
            v-if="selectedEvent.errorMessage"
            type="warning"
            variant="tonal"
            density="compact"
            class="mt-5"
          >
            {{ selectedEvent.errorCode || 'ERROR' }} · {{ selectedEvent.errorMessage }}
          </v-alert>

          <section class="detail-section">
            <h3>脱敏后的变更</h3>
            <div class="diff-grid">
              <div>
                <span class="json-label">修改前</span>
                <pre>{{ pretty(selectedEvent.beforeData) }}</pre>
              </div>
              <div>
                <span class="json-label">修改后</span>
                <pre>{{ pretty(selectedEvent.afterData) }}</pre>
              </div>
            </div>
            <div v-if="selectedEvent.changedFields?.length" class="changed-fields">
              <v-chip
                v-for="field in selectedEvent.changedFields"
                :key="field"
                size="x-small"
                variant="outlined"
              >
                {{ field }}
              </v-chip>
            </div>
          </section>

          <section class="detail-section">
            <h3>请求上下文</h3>
            <dl class="context-list">
              <dt>Request ID</dt>
              <dd>
                <code>{{ selectedEvent.requestId || '—' }}</code>
              </dd>
              <dt>Session ID</dt>
              <dd>
                <code>{{ selectedEvent.sessionId || '—' }}</code>
              </dd>
              <dt>节点</dt>
              <dd>{{ selectedEvent.nodeId || '—' }}</dd>
              <dt>User-Agent</dt>
              <dd>{{ selectedEvent.userAgent || '—' }}</dd>
            </dl>
            <pre v-if="hasMetadata" class="metadata-json">{{ pretty(selectedEvent.metadata) }}</pre>
          </section>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="detailOpen = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue';
  import dayjs from 'dayjs';
  import PageHeader from '@/components/PageHeader.vue';
  import EmptyState from '@/components/EmptyState.vue';
  import auditService, { type AuditEvent } from '@/service/audit-service';
  import { notify } from '@/stores/notifier';

  const outcomeOptions = ['SUCCESS', 'ACCEPTED', 'DENIED', 'FAILED'];
  const severityOptions = ['INFO', 'WARNING', 'CRITICAL'];
  const headers = [
    { title: '时间', key: 'occurredAt', width: 150 },
    { title: '操作者', key: 'actorUsername', width: 150 },
    { title: '动作 / 资源', key: 'action', minWidth: 260 },
    { title: '级别', key: 'severity', width: 105 },
    { title: '结果', key: 'outcome', width: 110 },
    { title: 'Request ID', key: 'requestId', width: 140 },
  ];

  const events = ref<AuditEvent[]>([]);
  const totalItems = ref(0);
  const page = ref(1);
  const pageSize = ref(20);
  const loading = ref(false);
  const exporting = ref(false);
  const error = ref('');
  const detailOpen = ref(false);
  const selectedEvent = ref<AuditEvent | null>(null);
  const filters = reactive({
    from: dayjs().subtract(7, 'day').format('YYYY-MM-DD'),
    to: dayjs().format('YYYY-MM-DD'),
    actor: '',
    action: '',
    outcome: '',
    severity: '',
  });

  const warningCount = computed(
    () => events.value.filter(event => event.severity !== 'INFO').length
  );
  const rejectedCount = computed(
    () => events.value.filter(event => ['DENIED', 'FAILED'].includes(event.outcome)).length
  );
  const hasMetadata = computed(
    () => !!selectedEvent.value?.metadata && Object.keys(selectedEvent.value.metadata).length > 0
  );

  function queryParams() {
    return {
      from: dayjs(filters.from).startOf('day').toISOString(),
      to: dayjs(filters.to).endOf('day').toISOString(),
      actor: filters.actor || undefined,
      action: filters.action || undefined,
      outcome: filters.outcome || undefined,
      severity: filters.severity || undefined,
      page: page.value - 1,
      pageSize: pageSize.value,
    };
  }

  async function search(nextPage = page.value) {
    page.value = nextPage;
    loading.value = true;
    error.value = '';
    try {
      const result = await auditService.search(queryParams());
      events.value = result.content;
      totalItems.value = result.totalElements;
    } catch (cause: any) {
      error.value = cause?.message || '查询审计事件失败';
      events.value = [];
      totalItems.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleTableOptions(options: { page: number; itemsPerPage: number }) {
    pageSize.value = options.itemsPerPage;
    search(options.page);
  }

  function openDetail(_event: Event, row: { item: AuditEvent }) {
    selectedEvent.value = row.item;
    detailOpen.value = true;
  }

  async function exportEvents() {
    exporting.value = true;
    try {
      const blob = await auditService.exportCsv(queryParams());
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `audit-events-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
      link.click();
      URL.revokeObjectURL(url);
      notify('审计事件已导出', 'success');
    } catch (cause: any) {
      notify(cause?.message || '导出失败', 'error');
    } finally {
      exporting.value = false;
    }
  }

  function formatDate(value: string) {
    return dayjs(value).format('YYYY-MM-DD');
  }

  function formatTime(value: string) {
    return dayjs(value).format('HH:mm:ss');
  }

  function formatFullTime(value: string) {
    return dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  }

  function outcomeLabel(value: string) {
    return (
      {
        SUCCESS: '成功',
        ACCEPTED: '已受理',
        DENIED: '已拒绝',
        FAILED: '失败',
      }[value] || value
    );
  }

  function severityColor(value: string) {
    return value === 'CRITICAL' ? 'error' : value === 'WARNING' ? 'warning' : 'info';
  }

  function resourceLabel(item: AuditEvent) {
    if (!item.resourceType && !item.resourceId) return '—';
    return [item.resourceType, item.resourceId].filter(Boolean).join(' · ');
  }

  function shortId(value: string | null) {
    return value ? value.slice(0, 8) : '—';
  }

  function pretty(value: unknown) {
    if (value === null || value === undefined) return '—';
    return JSON.stringify(value, null, 2);
  }
</script>

<style scoped>
  .audit-page {
    background: radial-gradient(
        circle at 95% 0%,
        rgba(var(--v-theme-warning), 0.08),
        transparent 28%
      ),
      rgb(var(--v-theme-surface));
  }

  .metric-card {
    overflow: hidden;
    position: relative;
  }

  .metric-card::after {
    content: '';
    position: absolute;
    inset: auto -18px -32px auto;
    width: 110px;
    height: 110px;
    border: 18px solid rgba(var(--v-theme-primary), 0.06);
    border-radius: 50%;
  }

  .metric-card--warning::after {
    border-color: rgba(var(--v-theme-warning), 0.12);
  }

  .metric-card--danger::after {
    border-color: rgba(var(--v-theme-error), 0.1);
  }

  .metric-card :deep(.v-card-text) {
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: end;
    gap: 6px 16px;
  }

  .metric-label {
    color: rgba(var(--v-theme-on-surface), 0.58);
    font-size: 12px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
  }

  .metric-card strong {
    grid-row: span 2;
    font-family: 'Roboto Mono', 'SFMono-Regular', monospace;
    font-size: clamp(30px, 4vw, 42px);
    font-weight: 500;
    line-height: 1;
  }

  .metric-note {
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-size: 12px;
  }

  .filter-card {
    margin-bottom: 16px;
  }

  .audit-table :deep(tbody tr) {
    cursor: pointer;
  }

  .audit-table :deep(tbody tr:hover) {
    background: rgba(var(--v-theme-primary), 0.045);
  }

  .time-cell,
  .action-cell,
  .actor-cell,
  .outcome-cell {
    display: flex;
  }

  .time-cell,
  .action-cell {
    flex-direction: column;
    gap: 2px;
  }

  .time-cell strong {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .time-cell span,
  .action-resource {
    color: rgba(var(--v-theme-on-surface), 0.55);
    font-size: 12px;
  }

  .actor-cell,
  .outcome-cell {
    align-items: center;
    gap: 7px;
  }

  .action-code {
    font-family: 'Roboto Mono', 'SFMono-Regular', monospace;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.025em;
  }

  .outcome-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgb(var(--v-theme-info));
  }

  .outcome-dot--success {
    background: rgb(var(--v-theme-success));
  }

  .outcome-dot--accepted {
    background: rgb(var(--v-theme-primary));
  }

  .outcome-dot--denied,
  .outcome-dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .request-code {
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-size: 11px;
  }

  .detail-title {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 12px;
  }

  .detail-title h2 {
    margin: 3px 0 0;
    font-family: 'Roboto Mono', 'SFMono-Regular', monospace;
    font-size: 22px;
  }

  .detail-eyebrow {
    color: rgba(var(--v-theme-on-surface), 0.5);
    font-size: 11px;
    letter-spacing: 0.12em;
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
  }

  .detail-grid div {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  .detail-grid span,
  .json-label {
    color: rgba(var(--v-theme-on-surface), 0.52);
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .detail-grid strong {
    overflow-wrap: anywhere;
    font-size: 13px;
  }

  .detail-section {
    margin-top: 24px;
  }

  .detail-section h3 {
    margin: 0 0 10px;
    font-size: 14px;
  }

  .diff-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .diff-grid pre,
  .metadata-json {
    margin: 6px 0 0;
    max-height: 280px;
    overflow: auto;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.025);
    padding: 12px;
    font-size: 12px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .changed-fields {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 10px;
  }

  .context-list {
    display: grid;
    grid-template-columns: 110px minmax(0, 1fr);
    margin: 0;
    gap: 8px 12px;
    font-size: 13px;
  }

  .context-list dt {
    color: rgba(var(--v-theme-on-surface), 0.52);
  }

  .context-list dd {
    min-width: 0;
    margin: 0;
    overflow-wrap: anywhere;
  }

  @media (max-width: 760px) {
    .detail-grid,
    .diff-grid {
      grid-template-columns: 1fr;
    }

    .context-list {
      grid-template-columns: 1fr;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "安全审计",
    "icon": "mdi-shield-search",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 40
  }
}
</route>
