<template>
  <div class="review-step">
    <div class="review-hero">
      <div>
        <div class="step-kicker">Step 4</div>
        <h2 class="step-title">预览与校验</h2>
        <div class="step-desc">
          批量配置已应用到全部表。逐表确认目标结构、字段映射和阻断项后再创建。
        </div>
      </div>
      <v-chip
        :color="blockedCount ? 'error' : warningCount ? 'warning' : 'success'"
        variant="tonal"
        size="large"
      >
        共 {{ rows.length }} 张 · {{ includedCount }} 张纳入创建
      </v-chip>
    </div>

    <div class="summary-grid">
      <button
        class="summary-card summary-card--ready"
        :class="{ 'summary-card--active': filter === 'READY' }"
        type="button"
        :aria-pressed="filter === 'READY'"
        @click="toggleFilter('READY')"
      >
        <span class="summary-card__value">{{ readyCount }}</span>
        <span class="summary-card__label">校验通过</span>
      </button>
      <button
        class="summary-card summary-card--warning"
        :class="{ 'summary-card--active': filter === 'WARNING' }"
        type="button"
        :aria-pressed="filter === 'WARNING'"
        @click="toggleFilter('WARNING')"
      >
        <span class="summary-card__value">{{ warningCount }}</span>
        <span class="summary-card__label">需要留意</span>
      </button>
      <button
        class="summary-card summary-card--error"
        :class="{ 'summary-card--active': filter === 'ERROR' }"
        type="button"
        :aria-pressed="filter === 'ERROR'"
        @click="toggleFilter('ERROR')"
      >
        <span class="summary-card__value">{{ blockedCount }}</span>
        <span class="summary-card__label">阻断项</span>
      </button>
      <div class="summary-note">
        <v-icon size="18">mdi-shield-check-outline</v-icon>
        <span>{{ modeLabel }}：{{ modeDescription }}</span>
      </div>
    </div>

    <v-alert
      v-if="invalidIncludedCount"
      type="error"
      variant="tonal"
      density="comfortable"
      class="step-alert"
    >
      有 {{ invalidIncludedCount }} 张纳入创建的表映射不完整或重复，修正后底部按钮才可用。
    </v-alert>

    <div class="review-toolbar">
      <div class="filter-state">
        <span>当前显示：{{ currentFilterLabel }}</span>
        <v-btn v-if="filter !== 'ALL'" variant="text" size="small" @click="filter = 'ALL'">
          显示全部
        </v-btn>
      </div>
      <div class="toolbar-actions">
        <v-btn
          variant="text"
          size="small"
          prepend-icon="mdi-check-all"
          :disabled="!includedCount"
          @click="completeMissingMappings"
        >
          补全缺失映射
        </v-btn>
        <v-btn
          variant="text"
          size="small"
          prepend-icon="mdi-filter-remove-outline"
          :disabled="!includedCount"
          @click="excludeBlocked"
        >
          排除无效项
        </v-btn>
      </div>
    </div>

    <div class="review-list">
      <div
        v-for="row in visibleRows"
        :key="row.table.sourceTable"
        class="review-row"
        :class="`review-row--${row.plan.status.toLowerCase()}`"
      >
        <v-checkbox-btn
          :model-value="row.included"
          :aria-label="`纳入创建 ${row.table.sourceTable}`"
          class="row-check"
          @update:model-value="value => (row.included = Boolean(value))"
        />
        <button type="button" class="review-row__main" @click="openRow(row)">
          <div class="route">
            <span class="route-source" :title="row.table.sourceTable">{{
              row.table.sourceTable
            }}</span>
            <v-icon size="16" color="primary">mdi-arrow-right</v-icon>
            <span class="route-target" :title="`${row.plan.targetDb}.${row.plan.targetTable}`">
              {{ row.plan.targetDb }}.{{ row.plan.targetTable }}
            </span>
          </div>
          <div class="metric">
            <span class="metric-label">字段</span>
            <strong>{{ mappingProgress(row) }}</strong>
          </div>
          <div class="metric metric--action">
            <span class="metric-label">目标动作</span>
            <strong>{{ actionLabel(row.plan.action) }}</strong>
          </div>
          <v-chip size="small" :color="rowStatusColor(row)" :variant="rowStatusVariant(row)">
            {{ rowStatusLabel(row) }}
          </v-chip>
          <span class="row-summary" :title="row.plan.summary">{{ row.plan.summary }}</span>
          <v-icon size="18" class="row-chevron">mdi-chevron-right</v-icon>
        </button>
      </div>

      <div v-if="!visibleRows.length" class="empty-state">
        <v-icon size="42">mdi-filter-off-outline</v-icon>
        <strong>当前筛选下没有表</strong>
        <span>切换筛选条件继续查看。</span>
      </div>
    </div>

    <!-- Why: Vuetify cards set z-index:0, which would trap the fixed drawer below
         the app footer (hiding and blocking its action bar), so mount it on body. -->
    <Teleport to="body">
      <v-navigation-drawer
        v-model="drawerOpen"
        temporary
        location="right"
        width="760"
        class="plan-drawer"
      >
        <template v-if="activeRow">
          <div class="drawer-head">
            <div>
              <div class="drawer-kicker">
                {{ modeLabel }} · {{ actionLabel(activeRow.plan.action) }}
              </div>
              <div class="drawer-title">
                {{ activeRow.table.sourceTable }}
                <v-icon size="18">mdi-arrow-right</v-icon>
                {{ activeRow.plan.targetDb }}.{{ activeRow.plan.targetTable }}
              </div>
            </div>
            <v-btn
              icon="mdi-close"
              variant="text"
              aria-label="关闭预览"
              @click="drawerOpen = false"
            />
          </div>

          <div class="drawer-body">
            <v-alert
              v-if="activeRow.plan.findings.length"
              :type="activeRow.plan.status === 'ERROR' ? 'error' : 'warning'"
              variant="tonal"
              density="comfortable"
            >
              <div v-for="finding in activeRow.plan.findings" :key="finding.code + finding.column">
                {{ finding.message }}
              </div>
            </v-alert>
            <v-alert v-else type="success" variant="tonal" density="comfortable">
              目标计划与字段映射检查通过。
            </v-alert>
            <v-alert v-if="activeRowIssue" type="error" variant="tonal" density="comfortable">
              当前映射存在问题：{{ activeRowIssueLabel }}。修正后才能确认创建。
            </v-alert>

            <section class="drawer-section">
              <div class="section-heading">
                <span>字段映射</span>
                <v-chip size="x-small" variant="tonal"
                  >{{ activeRow.mappings.length }} 个源字段</v-chip
                >
              </div>
              <div class="mapping-table">
                <div class="mapping-head">
                  <span>源字段</span>
                  <span>源类型</span>
                  <span>目标字段</span>
                  <span>计划类型</span>
                </div>
                <div
                  v-for="mapping in activeRow.mappings"
                  :key="mapping.sourceColumnName"
                  class="mapping-row"
                >
                  <span class="mono">{{ mapping.sourceColumnName }}</span>
                  <span class="type-text">{{ sourceTypeOf(mapping.sourceColumnName) }}</span>
                  <v-combobox
                    v-model="mapping.targetColumnName"
                    :items="targetColumnNames"
                    :aria-label="`${mapping.sourceColumnName} 的目标字段`"
                    density="compact"
                    variant="outlined"
                    hide-details
                  />
                  <span class="type-text">{{ targetTypeOf(mapping.targetColumnName) }}</span>
                </div>
              </div>
            </section>

            <section v-if="activeRow.plan.planDdl" class="drawer-section">
              <v-expansion-panels variant="accordion">
                <v-expansion-panel title="查看目标 DDL（只读）">
                  <v-expansion-panel-text>
                    <pre class="ddl-preview">{{ activeRow.plan.planDdl }}</pre>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>
            </section>
          </div>
          <div class="drawer-footer">
            <span>映射调整即时生效，最终确认时会重新校验。</span>
            <v-btn color="primary" variant="flat" @click="drawerOpen = false">完成</v-btn>
          </div>
        </template>
      </v-navigation-drawer>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import type { CollectionPlanReviewRow, PlannedTargetColumn } from '@/types/database';
  import { reviewRowIssue, reviewRowIssueLabel, reviewRowValid } from '@/utils/collection-plan';

  type Filter = 'ALL' | 'READY' | 'WARNING' | 'ERROR';

  const rows = defineModel<CollectionPlanReviewRow[]>('rows', { required: true });
  const drawerOpen = ref(false);
  const active = ref<CollectionPlanReviewRow | null>(null);
  const filter = ref<Filter>('ALL');

  const readyCount = computed(() => rows.value.filter(row => row.plan.status === 'READY').length);
  const warningCount = computed(
    () => rows.value.filter(row => row.plan.status === 'WARNING').length
  );
  const blockedCount = computed(() => rows.value.filter(row => row.plan.status === 'ERROR').length);
  const includedCount = computed(() => rows.value.filter(row => row.included).length);
  const invalidIncludedCount = computed(
    () => rows.value.filter(row => row.included && !reviewRowValid(row)).length
  );
  const mode = computed(() => rows.value[0]?.plan.schemaManagementMode || 'MANAGED');
  const modeLabel = computed(() => (mode.value === 'EXTERNAL' ? '外部管理' : '平台管理'));
  const modeDescription = computed(() =>
    mode.value === 'EXTERNAL'
      ? '只校验现有目标表，不创建或修改结构'
      : '确认后将创建或演进目标表结构'
  );
  const activeRow = computed(() => active.value);
  const activeRowIssue = computed(() => (active.value ? reviewRowIssue(active.value) : null));
  const activeRowIssueLabel = computed(() => reviewRowIssueLabel(activeRowIssue.value));
  const displayColumns = computed<PlannedTargetColumn[]>(() => {
    if (!active.value) return [];
    const plan = active.value.plan;
    if (plan.schemaManagementMode === 'MANAGED') {
      return plan.plannedColumns;
    }
    const primaryKeys = new Set(plan.actualTargetTable.primaryKeys.map(name => name.toLowerCase()));
    return plan.actualTargetTable.columns.map(column => ({
      name: column.name,
      type: column.type,
      nullable: column.nullable,
      primaryKey: primaryKeys.has(column.name.toLowerCase()),
      role: 'EXISTING',
      ordinal: column.ordinal,
    }));
  });
  const targetColumnNames = computed(() => displayColumns.value.map(column => column.name));

  const currentFilterLabel = computed(() => {
    if (filter.value === 'READY') return `校验通过 ${readyCount.value}`;
    if (filter.value === 'WARNING') return `需要留意 ${warningCount.value}`;
    if (filter.value === 'ERROR') return `阻断项 ${blockedCount.value}`;
    return `全部 ${rows.value.length}`;
  });

  const toggleFilter = (next: Filter) => {
    filter.value = filter.value === next ? 'ALL' : next;
  };

  const visibleRows = computed(() => {
    const filtered =
      filter.value === 'ALL'
        ? rows.value
        : rows.value.filter(row => row.plan.status === filter.value);
    return [...filtered].sort((a, b) => statusRank(a.plan.status) - statusRank(b.plan.status));
  });

  const statusRank = (status: string) => (status === 'ERROR' ? 0 : status === 'WARNING' ? 1 : 2);

  const openRow = (row: CollectionPlanReviewRow) => {
    active.value = row;
    drawerOpen.value = true;
  };

  const completeMissingMappings = () => {
    rows.value.forEach(row => {
      const suggestions = new Map(
        row.plan.mappings.map(mapping => [mapping.sourceColumnName, mapping.targetColumnName])
      );
      row.mappings.forEach(mapping => {
        // Why: only fill gaps so bulk help never overwrites a manual mapping.
        if (mapping.targetColumnName?.trim()) return;
        const suggestion = suggestions.get(mapping.sourceColumnName);
        if (suggestion) mapping.targetColumnName = suggestion;
      });
    });
  };

  const excludeBlocked = () => {
    rows.value.forEach(row => {
      const mappingReady = row.mappings.every(mapping => mapping.targetColumnName?.trim());
      const targetReady =
        row.plan.schemaManagementMode === 'MANAGED' ||
        (row.plan.actualTargetTable.exists &&
          row.mappings.every(mapping => {
            const target = mapping.targetColumnName?.trim().toLowerCase();
            return row.plan.actualTargetTable.columns.some(
              column => column.name.toLowerCase() === target
            );
          }));
      if (!mappingReady || !targetReady) row.included = false;
    });
  };

  const sourceTypeOf = (sourceColumnName: string) =>
    active.value?.plan.sourceColumns.find(column => column.name === sourceColumnName)?.type || '-';

  const targetTypeOf = (targetColumnName?: string | null) =>
    displayColumns.value.find(
      column => column.name.toLowerCase() === targetColumnName?.trim().toLowerCase()
    )?.type || '-';

  const mappingProgress = (row: CollectionPlanReviewRow) => {
    const complete = row.mappings.filter(mapping => mapping.targetColumnName?.trim()).length;
    return `${complete}/${row.mappings.length}`;
  };

  const actionLabel = (action: string) => {
    if (action === 'CREATE_TABLE') return '创建目标表';
    if (action === 'UPDATE_TABLE') return '演进目标表';
    if (action === 'VALIDATE_EXTERNAL') return '仅校验';
    return '已阻断';
  };

  const statusLabel = (status: string) =>
    status === 'ERROR' ? '阻断' : status === 'WARNING' ? '有告警' : '通过';

  const statusColor = (status: string) =>
    status === 'ERROR' ? 'error' : status === 'WARNING' ? 'warning' : 'success';

  // Why: the server plan status cannot see edits made in the drawer; surface them per row.
  const rowIssue = (row: CollectionPlanReviewRow) => (row.included ? reviewRowIssue(row) : null);
  const rowStatusLabel = (row: CollectionPlanReviewRow) => {
    const issue = rowIssue(row);
    return issue ? reviewRowIssueLabel(issue) : statusLabel(row.plan.status);
  };
  const rowStatusColor = (row: CollectionPlanReviewRow) =>
    rowIssue(row) ? 'error' : statusColor(row.plan.status);
  const rowStatusVariant = (row: CollectionPlanReviewRow): 'tonal' | 'outlined' =>
    rowIssue(row) || row.plan.status !== 'READY' ? 'tonal' : 'outlined';
</script>

<style scoped lang="scss">
  @use './batch-step-shared' as *;

  .review-step {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 18px;
    padding: 22px;
  }

  .review-hero {
    position: relative;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    padding-left: 14px;
  }

  .review-hero::before {
    position: absolute;
    inset: 4px auto 4px 0;
    width: 3px;
    border-radius: 999px;
    background: linear-gradient(
      180deg,
      rgb(var(--v-theme-primary)),
      rgba(var(--v-theme-primary), 0.24)
    );
    content: '';
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(120px, 0.6fr)) minmax(280px, 1.4fr);
    gap: 10px;
  }

  .summary-card {
    display: flex;
    min-height: 82px;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    padding: 12px 16px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 14px;
    background: linear-gradient(
      145deg,
      rgba(var(--v-theme-surface), 0.98),
      rgba(var(--v-theme-surface), 0.82)
    );
    text-align: left;
    transition: transform 160ms ease, border-color 160ms ease;
  }

  .summary-card:hover {
    transform: translateY(-1px);
    border-color: rgba(var(--v-theme-primary), 0.28);
  }

  .summary-card--active {
    border-color: rgba(var(--v-theme-primary), 0.42);
    box-shadow: inset 0 0 0 1px rgba(var(--v-theme-primary), 0.08);
  }

  .summary-card__value {
    font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
    font-size: 1.55rem;
    font-weight: 750;
    line-height: 1;
  }

  .summary-card__label {
    margin-top: 7px;
    color: var(--ds-text-2);
    font-size: 0.78rem;
  }

  .summary-card--ready .summary-card__value {
    color: rgb(var(--v-theme-success));
  }

  .summary-card--warning .summary-card__value {
    color: rgb(var(--v-theme-warning));
  }

  .summary-card--error .summary-card__value {
    color: rgb(var(--v-theme-error));
  }

  .summary-note {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 16px;
    border: 1px solid rgba(var(--v-theme-primary), 0.16);
    border-radius: 14px;
    background: radial-gradient(
        circle at 90% 10%,
        rgba(var(--v-theme-primary), 0.12),
        transparent 42%
      ),
      rgba(var(--v-theme-primary), 0.035);
    color: var(--ds-text-2);
    font-size: 0.84rem;
    line-height: 1.55;
  }

  .review-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 0;
    border-top: 1px solid var(--ds-border-subtle);
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .filter-state,
  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .filter-state {
    color: var(--ds-text-2);
    font-size: 0.8rem;
  }

  .review-list {
    display: flex;
    flex-direction: column;
    gap: 7px;
  }

  .review-row {
    position: relative;
    display: flex;
    align-items: center;
    gap: 4px;
    width: 100%;
    padding: 6px 12px 6px 4px;
    overflow: hidden;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 12px;
    background: rgba(var(--v-theme-surface), 0.88);
    color: rgb(var(--v-theme-on-surface));
    text-align: left;
    transition: transform 150ms ease, box-shadow 150ms ease;
  }

  // Why: v-selection-control defaults to flex:1 0, which would eat half the row.
  .review-row .row-check {
    flex: 0 0 auto;
  }

  .review-row__main {
    display: grid;
    // Why: flex-basis auto sizes each row from its text, so long summaries shift the columns.
    flex: 1 1 0;
    grid-template-columns: minmax(200px, 1.5fr) 80px 100px 70px minmax(150px, 1fr) 20px;
    align-items: center;
    gap: 10px;
    min-width: 0;
    min-height: 40px;
    padding: 2px 0;
    border: 0;
    background: transparent;
    color: inherit;
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  // Keep the chip at its natural width so its label stays centered in its grid cell.
  .review-row__main > .v-chip {
    align-self: center;
    justify-self: center;
  }

  .review-row::before {
    position: absolute;
    /* Why: a full-height bar is clipped by the row's rounded corners and reads as a brace. */
    inset: 10px auto 10px 0;
    width: 3px;
    border-radius: 999px;
    background: rgb(var(--v-theme-success));
    content: '';
  }

  .review-row--warning::before {
    background: rgb(var(--v-theme-warning));
  }

  .review-row--error::before {
    background: rgb(var(--v-theme-error));
  }

  .review-row:hover {
    transform: translateX(2px);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.07);
  }

  .route {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 7px;
  }

  .route-source,
  .route-target {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .route-source {
    font-weight: 650;
  }

  .route-target,
  .mono {
    font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  }

  .metric {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .metric-label {
    color: var(--ds-text-3);
    font-size: 0.67rem;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

  .metric strong {
    font-size: 0.8rem;
    font-weight: 650;
  }

  .row-summary {
    overflow: hidden;
    color: var(--ds-text-2);
    font-size: 0.78rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-chevron {
    color: var(--ds-text-3);
  }

  .empty-state {
    display: flex;
    min-height: 160px;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    border: 1px dashed var(--ds-border-subtle);
    border-radius: 14px;
    color: var(--ds-text-2);
  }

  .drawer-head {
    display: flex;
    flex: 0 0 auto;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 20px 22px 16px;
    border-bottom: 1px solid var(--ds-border-subtle);
    background: radial-gradient(
        circle at 90% 0%,
        rgba(var(--v-theme-primary), 0.12),
        transparent 40%
      ),
      var(--ds-surface-raised);
  }

  .drawer-kicker {
    color: var(--ds-text-3);
    font-size: 0.72rem;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }

  .drawer-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 5px;
    font-size: 1rem;
    font-weight: 700;
    flex-wrap: wrap;
  }

  .drawer-body {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    gap: 20px;
    min-height: 0;
    padding: 18px 22px 34px;
    overflow-y: auto;
    overscroll-behavior: contain;
  }

  // Why: Vuetify alerts set flex-grow, so spare scroll height would stretch them.
  .drawer-body > * {
    flex: 0 0 auto;
  }

  .drawer-section {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .section-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    font-size: 0.88rem;
    font-weight: 700;
  }

  .mapping-table {
    overflow: hidden;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 12px;
  }

  .mapping-head,
  .mapping-row {
    display: grid;
    grid-template-columns: 1fr 110px 1.2fr 110px;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
  }

  .mapping-head {
    background: rgba(var(--v-theme-on-surface), 0.035);
    color: var(--ds-text-3);
    font-size: 0.68rem;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

  .mapping-row + .mapping-row {
    border-top: 1px solid var(--ds-border-subtle);
  }

  .type-text {
    color: var(--ds-text-2);
    font-size: 0.76rem;
  }

  .ddl-preview {
    margin: 0;
    padding: 14px;
    overflow: auto;
    border-radius: 10px;
    background: #111827;
    color: #d1fae5;
    font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
    font-size: 0.74rem;
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .drawer-footer {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 22px;
    border-top: 1px solid var(--ds-border-subtle);
    background: var(--ds-surface-raised);
  }

  .drawer-footer span {
    color: var(--ds-text-3);
    font-size: 0.74rem;
  }

  // Why: only the drawer body should scroll, so the footer stays reachable.
  .plan-drawer :deep(.v-navigation-drawer__content) {
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  @media (max-width: 1100px) {
    .summary-grid {
      grid-template-columns: repeat(3, 1fr);
    }

    .summary-note {
      grid-column: 1 / -1;
    }
  }

  @media (max-width: 980px) {
    .review-row__main {
      grid-template-columns: minmax(170px, 1.5fr) 70px 90px 64px minmax(110px, 1fr) 18px;
      gap: 8px;
    }
  }

  @media (max-width: 760px) {
    .review-step {
      padding: 16px;
    }

    .review-hero,
    .review-toolbar {
      flex-direction: column;
      align-items: stretch;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }

    .review-row__main {
      grid-template-columns: minmax(0, 1fr) auto 20px;
      gap: 8px;
    }

    .metric,
    .row-summary {
      display: none;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .summary-card,
    .review-row {
      transition: none;
    }

    .summary-card:hover,
    .review-row:hover {
      transform: none;
    }
  }
</style>
