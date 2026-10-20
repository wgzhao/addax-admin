<template>
  <div
    class="table-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible || deleteDialogVisible }"
  >
    <PageHeader title="采集表配置" subtitle="统一维护采集表、批量采集与结构更新。">
      <v-menu offset-y :close-on-content-click="false">
        <template #activator="{ props }">
          <v-btn variant="tonal" prepend-icon="mdi-tune" v-bind="props">显示设置</v-btn>
        </template>
        <v-list density="compact" class="table-display-menu">
          <v-list-subheader>列显示</v-list-subheader>
          <v-list-item
            v-for="col in OPTIONAL_COLUMNS"
            :key="col.key"
            @click="toggleColumn(col.key, hiddenKeys.includes(col.key))"
          >
            <template #prepend>
              <v-icon size="18">
                {{
                  hiddenKeys.includes(col.key)
                    ? 'mdi-checkbox-blank-outline'
                    : 'mdi-checkbox-marked'
                }}
              </v-icon>
            </template>
            <v-list-item-title>{{ col.title }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
      <v-btn
        variant="tonal"
        prepend-icon="mdi-download"
        :disabled="!table.length"
        @click="exportCurrentPage"
      >
        导出本页
      </v-btn>
      <v-btn
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus"
        @click="() => router.push('/table/batch-add')"
      >
        新增表
      </v-btn>
    </PageHeader>

    <v-card flat class="ds-card toolbar-card">
      <v-card-text class="ds-card__content">
        <div class="ds-filters">
          <v-text-field
            v-model="search"
            class="ds-filters__keyword"
            density="compact"
            label="关键字"
            placeholder="表名 / 编码 / 分区字段"
            prepend-inner-icon="mdi-magnify"
            single-line
            variant="outlined"
            hide-details
            clearable
            @keyup.enter="searchTable"
          />
          <!-- Why: a fixed-width trigger keeps the toolbar height stable, while a
               multi-select field grew a line per selected status -->
          <v-menu :close-on-content-click="false" offset-y>
            <template #activator="{ props: menuProps }">
              <v-btn
                v-bind="menuProps"
                :class="[
                  'ds-filters__status',
                  { 'ds-filters__status--active': runStatuses.length },
                ]"
                variant="outlined"
                height="40"
                :title="statusFilterTitle"
              >
                <v-icon size="18" class="mr-2">mdi-filter-variant</v-icon>
                <span class="ds-filters__status-label">{{ statusFilterLabel }}</span>
                <v-icon size="18" class="ml-1">mdi-menu-down</v-icon>
              </v-btn>
            </template>
            <v-list density="compact" class="ds-status-menu">
              <v-list-item
                v-for="option in TABLE_STATUS_OPTIONS"
                :key="option.value"
                :active="runStatuses.includes(option.value)"
                role="menuitemcheckbox"
                :aria-checked="runStatuses.includes(option.value)"
                @click="toggleStatus(option.value)"
              >
                <template #prepend>
                  <v-checkbox-btn
                    :model-value="runStatuses.includes(option.value)"
                    density="compact"
                    tabindex="-1"
                    aria-hidden="true"
                  />
                </template>
                <v-list-item-title>{{ option.label }}</v-list-item-title>
              </v-list-item>
              <template v-if="runStatuses.length">
                <v-divider class="my-1" />
                <v-list-item @click="runStatuses = []">
                  <template #prepend>
                    <v-icon size="18">mdi-filter-remove-outline</v-icon>
                  </template>
                  <v-list-item-title>清除状态筛选</v-list-item-title>
                </v-list-item>
              </template>
            </v-list>
          </v-menu>
          <v-select
            v-model="sourceId"
            class="ds-filters__source"
            :items="sourceOptions"
            item-title="label"
            item-value="value"
            :item-props="item => ({ title: item.label })"
            density="compact"
            variant="outlined"
            single-line
            hide-details
            clearable
            label="采集源"
          />
          <div class="filter-actions">
            <v-btn color="primary" variant="flat" @click="searchTable">查询</v-btn>
            <v-btn variant="text" @click="resetFilters">重置</v-btn>
          </div>
        </div>

        <div v-if="selected.length > 0" class="ds-bulk-bar">
          <span class="ds-bulk-bar__count">已选择 {{ selected.length }} 行</span>
          <div class="ds-bulk-bar__actions">
            <v-btn variant="flat" color="primary" prepend-icon="mdi-database" @click="doEtl(null)">
              批量采集
            </v-btn>
            <v-btn
              variant="flat"
              color="info"
              prepend-icon="mdi-calendar-refresh"
              @click="openDialog('Fillback', 'Fillback')"
            >
              补采数据
            </v-btn>
            <v-btn
              color="secondary"
              prepend-icon="mdi-pencil"
              @click="openDialog('BatchUpdate', 'BatchUpdate')"
            >
              批量修改
            </v-btn>
            <v-btn
              variant="flat"
              color="error"
              prepend-icon="mdi-delete"
              @click="confirmBatchDelete"
            >
              批量删除
            </v-btn>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-card ds-table-wrap ds-table-scroll">
      <v-card-text class="ds-card__content">
        <template v-if="!loading && totalItems === 0">
          <EmptyState
            title="暂无采集表"
            description="当前还没有配置任何采集表。点击下方按钮新增采集表后，即可继续批量采集、调度与结构维护。"
            :primary="{ label: '新增采集表', icon: 'mdi-plus' }"
            :secondary="{ label: '从模板创建', icon: 'mdi-file-import' }"
            @primary="() => router.push('/table/batch-add')"
            @secondary="() => router.push('/table/batch-add')"
          />
        </template>
        <template v-else>
          <v-data-table-server
            v-model="selected"
            v-model:page="currentPage"
            v-model:items-per-page="currPageSize"
            density="comfortable"
            :items="table"
            :headers="visibleHeaders"
            :items-per-page-options="[15, 20, 25, 30, 50, 100]"
            :items-length="totalItems"
            item-value="id"
            :loading="loading"
            show-select
            :item-class="getRowClass"
            @update:options="loadItems"
          >
            <!-- Why: the code stays the primary status signal, so the header carries
                 the legend and the cell keeps its width stable across rows -->
            <template #header.status="{ column, getSortIcon }">
              <div class="v-data-table-header__content ds-status-header">
                <span>{{ column.title }}</span>
                <v-tooltip location="bottom" max-width="260" open-delay="150">
                  <template #activator="{ props: tipProps }">
                    <v-icon
                      v-bind="tipProps"
                      icon="mdi-information-outline"
                      size="14"
                      class="ds-status-header__hint"
                      aria-label="查看状态码说明"
                      @click.stop
                    />
                  </template>
                  <div class="ds-status-legend">
                    <div class="ds-status-legend__title">状态码说明</div>
                    <div
                      v-for="entry in STATUS_LEGEND"
                      :key="entry.code"
                      class="ds-status-legend__row"
                    >
                      <v-icon icon="mdi-circle" size="8" :color="getStatusColor(entry.code)" />
                      <span class="ds-status-legend__code ds-mono-text">{{ entry.code }}</span>
                      <span class="ds-status-legend__label">{{ entry.label }}</span>
                    </div>
                  </div>
                </v-tooltip>
                <v-icon
                  v-if="column.sortable"
                  class="v-data-table-header__sort-icon"
                  :icon="getSortIcon(column)"
                />
              </div>
            </template>
            <template #item.sourceTable="{ item }">
              <div class="ds-cell-stack">
                <span class="ds-cell-meta">{{ item.name }}({{ item.code }})</span>
                <span
                  class="ds-cell-primary ds-ellipsis"
                  :title="`${item.sourceDb || ''}.${item.sourceTable || ''}`"
                >
                  <span class="ds-cell-support">{{ item.sourceDb || '' }}.</span
                  >{{ item.sourceTable || '' }}
                </span>
              </div>
            </template>
            <template #item.status="{ item }">
              <v-chip
                size="small"
                :color="getStatusColor(item.status)"
                variant="tonal"
                class="ds-status-code"
                :class="`ds-status-code--${item.status}`"
                :title="`${item.status} · ${statusLabel(item.status)}`"
              >
                <span class="ds-status-code__glyph">{{ item.status }}</span>
              </v-chip>
              <!-- Kill icon for running tasks: move to right, match E icon style -->
              <template v-if="item.status === 'R'">
                <v-tooltip location="top">
                  <template #activator="{ props }">
                    <v-icon
                      v-bind="props"
                      size="16"
                      color="error"
                      class="ml-1 align-middle cursor-pointer"
                      @click.stop="confirmKill(item)"
                    >
                      mdi-close
                    </v-icon>
                  </template>
                  <span>点击中止当前运行任务</span>
                </v-tooltip>
              </template>
              <template v-if="item.status === 'U' || item.status === 'E'">
                <v-menu open-on-click :close-on-content-click="false" min-width="220" offset-y>
                  <template #activator="{ props }">
                    <v-icon
                      size="16"
                      color="warning"
                      class="ml-1 align-middle cursor-pointer"
                      v-bind="props"
                      @click.stop="fetchErrorMsg(item.id)"
                    >
                      mdi-alert-circle-outline
                    </v-icon>
                  </template>
                  <div class="ds-error-popover">
                    <span v-if="errorMsgMap[item.id] !== undefined">
                      {{ errorMsgMap[item.id] || '无详细错误信息' }}
                    </span>
                    <span v-else>加载中...</span>
                  </div>
                </v-menu>
              </template>
            </template>

            <template #item.targetTable="{ item }">
              <div class="d-flex align-center py-1">
                <v-icon size="14" color="disabled" class="mr-2">mdi-arrow-right</v-icon>
                <span
                  class="ds-cell-primary ds-ellipsis"
                  :title="`${item.targetDb || ''}.${item.targetTable || ''}`"
                >
                  <span class="ds-cell-support">{{ item.targetDb || '' }}.</span
                  >{{ item.targetTable || '' }}
                </span>
              </div>
            </template>

            <template #item.endTime="{ item }">
              <span class="ds-nowrap">{{ item.endTime }}</span>
            </template>

            <template #item.action="{ item }">
              <div class="adjust-start align-center space-between">
                <v-tooltip text="发起采集" location="top">
                  <template #activator="{ props: activatorProps }">
                    <v-icon-btn
                      color="primary"
                      size="small"
                      class="mr-2"
                      icon="mdi-play-circle"
                      :aria-label="`发起采集 ${item.sourceTable || item.id}`"
                      v-bind="activatorProps"
                      @click="doEtl(item)"
                    ></v-icon-btn>
                  </template>
                </v-tooltip>
                <v-tooltip text="更新表结构" location="top">
                  <template #activator="{ props: activatorProps }">
                    <v-icon-btn
                      color="warning"
                      size="small"
                      class="mr-2"
                      icon="mdi-database-refresh"
                      :aria-label="`更新表结构 ${item.sourceTable || item.id}`"
                      v-bind="activatorProps"
                      @click="updateSchema(item)"
                    ></v-icon-btn>
                  </template>
                </v-tooltip>
                <v-tooltip text="删除此采集表" location="top">
                  <template #activator="{ props: activatorProps }">
                    <v-icon-btn
                      color="error"
                      size="small"
                      class="mr-2"
                      icon="mdi-delete"
                      :aria-label="`删除采集表 ${item.sourceTable || item.id}`"
                      v-bind="activatorProps"
                      @click="confirmDelete(item)"
                    ></v-icon-btn>
                  </template>
                </v-tooltip>
                <v-tooltip text="查看详情" location="top">
                  <template #activator="{ props: open }">
                    <v-icon-btn
                      color="secondary"
                      size="small"
                      class="mr-2"
                      icon="mdi-open-in-new"
                      :aria-label="`查看采集表详情 ${item.sourceTable || item.id}`"
                      v-bind="open"
                      @click="viewDetail(item)"
                    ></v-icon-btn>
                  </template>
                </v-tooltip>
              </div>
            </template>
          </v-data-table-server>
        </template>
      </v-card-text>
    </v-card>
  </div>

  <v-dialog
    v-model="dialogVisible"
    class="ds-dialog-overlay"
    :max-width="dialogMaxWidth"
    scrim="rgba(2, 6, 23, 0.62)"
    :retain-focus="false"
    scrollable
  >
    <component
      :is="currentComponent"
      v-bind="currentParams"
      @closeDialog="closeDialog"
      @update:record="handleRecordUpdate"
      @update:batch="handleBatchUpdate"
      @refresh-data="searchTable"
    />
  </v-dialog>

  <v-dialog
    v-model="deleteDialogVisible"
    class="ds-dialog-overlay"
    scrim="rgba(2, 6, 23, 0.62)"
    width="480"
  >
    <v-card>
      <v-card-title class="text-h6">确认删除</v-card-title>
      <v-card-text>{{ deleteConfirmMessage }}</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="deleteDialogVisible = false">取消</v-btn>
        <v-btn
          color="error"
          variant="flat"
          @click="isBatchDelete ? batchDeleteItems() : deleteItem()"
          >确认删除</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>

  <ConfirmDialog
    v-model="killConfirm.open"
    title="中止采集任务"
    :message="killConfirm.message"
    confirm-text="中止任务"
    @confirm="runKill"
  />
</template>

<script setup lang="ts">
  import { ref, shallowRef, defineAsyncComponent, onMounted, watch, computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import EmptyState from '@/components/EmptyState.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import PageHeader from '@/components/PageHeader.vue';
  import { debounce } from '@/utils/debounce';
  import { createSort } from '@/utils/';
  import { buildCsv, downloadCsv } from '@/utils/csv';
  import { TABLE_STATUS_OPTIONS } from '@/utils';
  import tableService from '@/service/table-service';
  import taskService from '@/service/task-service';
  import sourceService from '@/service/source-service';
  import { notify } from '@/stores/notifier';
  import taskCenter from '@/stores/task-center';
  import type { DataTableHeader } from 'vuetify';
  // 异步按需加载组件，减轻首屏体积
  const BatchUpdate = defineAsyncComponent(() => import('@/components/table/BatchUpdate.vue'));
  const Fillback = defineAsyncComponent(() => import('@/components/table/FillbackDialog.vue'));

  const route = useRoute();
  const router = useRouter();

  const table = ref([]);
  const search = ref('');
  const selected = ref([]);
  const currPageSize = ref(15);
  const totalItems = ref(0);
  const loading = ref(true);
  const dialogVisible = ref(false);
  // 当前要显示的组件（默认值为 null，表示对话框无内容）
  // shallowRef 用于定义浅层响应式的引用，它不会递归地将对象或组件内部的数据设为响应式，因此适用于组件这种复杂对象。
  const currentComponent = shallowRef(null);
  const currentDialogName = ref<string | null>(null);
  // 传递给子组件的参数
  const currentParams = ref({});
  const currentSortParam = ref([
    {
      sortField: null,
      sortOrder: null,
    },
  ]);
  const componentMap = {
    BatchUpdate,
    Fillback,
  };

  function getRowClass(item: any) {
    return selected.value.includes(item.id) ? 'selected-row' : '';
  }

  function resetFilters() {
    search.value = '';
    runStatuses.value = [];
    sourceId.value = null;
    searchTable();
  }

  // Why: single letters carry the meaning, so muted greys must still meet a
  // readable contrast in both themes; hue alone no longer disambiguates rows
  const STATUS_COLOR: Record<string, string> = {
    N: 'secondary',
    R: 'info',
    Y: 'success',
    E: 'error',
    X: 'grey',
    W: 'warning',
    U: 'blue-grey-darken-1',
  };

  function getStatusColor(status: string) {
    return STATUS_COLOR[status] || 'grey';
  }

  const STATUS_LABEL: Record<string, string> = {
    N: '未采集',
    Y: '已采集',
    X: '不采集',
    W: '等待采集',
    E: '采集错误',
    R: '采集中',
    U: '待更新',
  };

  const statusLabel = (status: string) => STATUS_LABEL[status] || status || '未知';

  // Lifecycle order reads better than an alphabetical or enum order
  const STATUS_LEGEND = ['N', 'W', 'R', 'Y', 'E', 'U', 'X'].map(code => ({
    code,
    label: STATUS_LABEL[code],
  }));

  const runStatuses = ref<string[]>([]);
  const sourceId = ref<number | null>(null);
  const sourceOptions = ref<any[]>([]);

  // Cap the visible codes so the trigger reads as a summary instead of an ellipsis
  const statusFilterLabel = computed(() => {
    const codes = runStatuses.value;
    if (!codes.length) return '状态';
    if (codes.length <= 3) return codes.join(', ');
    return `${codes.slice(0, 3).join(', ')} +${codes.length - 3}`;
  });
  const statusFilterTitle = computed(() =>
    runStatuses.value.length
      ? runStatuses.value.map(code => `${code} ${statusLabel(code)}`).join('，')
      : '按状态筛选'
  );

  function toggleStatus(code: string) {
    runStatuses.value = runStatuses.value.includes(code)
      ? runStatuses.value.filter(item => item !== code)
      : [...runStatuses.value, code];
  }

  // 优化后的 Headers 配置：精简列数，合并表达
  const headers: DataTableHeader[] = [
    // Why: every short column has a knowable ceiling, so it gets a fixed width and
    // the two table-name columns keep all the remaining space
    // 84px covers six digits, so the next order of magnitude does not clip
    { title: '#', key: 'id', align: 'center', width: 84 },
    {
      title: '源库表映射路径',
      key: 'sourceTable',
      align: 'start',
      sortable: true,
    },
    { title: '目标库表', key: 'targetTable', align: 'start', sortable: true },
    {
      title: '过滤规则',
      key: 'filter',
      align: 'start',
      sortable: true,
      width: 140,
    },
    { title: '状态', key: 'status', align: 'start', sortable: true, width: 92 },
    { title: '耗时(s)', key: 'duration', align: 'end', sortable: true, width: 96 },
    {
      title: '完成时间',
      key: 'endTime',
      align: 'center',
      sortable: true,
      width: 184,
    },
    {
      title: '操作',
      key: 'action',
      align: 'center',
      sortable: false,
      width: 168,
    },
  ];

  const COLUMNS_KEY = 'ds.table.hiddenColumns';
  const OPTIONAL_COLUMNS = [
    { key: 'filter', title: '过滤规则' },
    { key: 'duration', title: '耗时(s)' },
    { key: 'endTime', title: '完成时间' },
  ];

  const readStored = <T>(key: string, fallback: T): T => {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : fallback;
    } catch {
      return fallback;
    }
  };

  const hiddenKeys = ref<string[]>(readStored<string[]>(COLUMNS_KEY, []));
  const visibleHeaders = computed(() =>
    headers.filter(header => !hiddenKeys.value.includes(String(header.key)))
  );

  const toggleColumn = (key: string, visible: boolean) => {
    hiddenKeys.value = visible
      ? hiddenKeys.value.filter(item => item !== key)
      : [...hiddenKeys.value, key];
    localStorage.setItem(COLUMNS_KEY, JSON.stringify(hiddenKeys.value));
  };

  const currentPage = ref(1);

  const writeQuery = () => {
    const query: Record<string, string> = {};
    if (search.value) query.q = search.value;
    if (runStatuses.value.length) query.status = runStatuses.value.join(',');
    if (sourceId.value != null) query.source = String(sourceId.value);
    if (currentPage.value > 1) query.page = String(currentPage.value);
    if (currPageSize.value !== 15) query.size = String(currPageSize.value);
    if (route.query.action) query.action = String(route.query.action);
    router.replace({ path: route.path, query }).catch(() => {});
  };

  const readQuery = () => {
    const query = route.query;
    search.value = typeof query.q === 'string' ? query.q : '';
    runStatuses.value =
      typeof query.status === 'string' && query.status ? query.status.split(',') : [];
    sourceId.value = query.source ? Number(query.source) : null;
    currentPage.value = Number(query.page) > 0 ? Number(query.page) : 1;
    currPageSize.value = Number(query.size) > 0 ? Number(query.size) : 15;
  };

  // Restore filters from the URL before the table mounts, so returning from a
  // detail page keeps the same slice of data instead of refetching page one.
  readQuery();

  function exportCurrentPage() {
    const rows = table.value.map(row => ({
      序号: row.id,
      源库表: `${row.sourceDb || ''}.${row.sourceTable || ''}`,
      目标库表: `${row.targetDb || ''}.${row.targetTable || ''}`,
      过滤规则: row.filter || '',
      状态: statusLabel(row.status),
      耗时秒: row.duration ?? '',
      完成时间: row.endTime || '',
    }));
    if (!rows.length) return;
    const csvHeaders = Object.keys(rows[0]).map(key => ({ title: key, key }));
    downloadCsv(`tables-page-${currentPage.value}`, buildCsv(csvHeaders, rows));
  }

  const errorMsgMap = ref<Record<number, string>>({});

  function fetchErrorMsg(tid: number) {
    errorMsgMap.value[tid] = '';
    taskService
      .getLastError(tid)
      .then(res => {
        errorMsgMap.value[tid] = res || '无详细错误信息';
      })
      .catch(err => {
        const msg = err instanceof Error ? err.message : String(err);
        errorMsgMap.value[tid] = '获取错误信息失败: ' + msg;
      });
  }

  const dialogMaxWidth = computed<number | undefined>(() => {
    if (currentDialogName.value === 'BatchUpdate') return 720;
    if (currentDialogName.value === 'Fillback') return 720;
    return undefined;
  });

  function openDialog(componentName: string, com: any) {
    currentDialogName.value = componentName;
    currentComponent.value = componentMap[componentName];
    setParams(componentName, com);
    dialogVisible.value = true;
  }

  function closeDialog() {
    dialogVisible.value = false;
    currentComponent.value = null;
    currentDialogName.value = null;
  }

  watch(dialogVisible, open => {
    if (!open) {
      currentComponent.value = null;
      currentDialogName.value = null;
    }
  });

  function setParams(compName: string, comp: any) {
    if (compName == 'BatchUpdate') {
      currentParams.value = { tid: selected.value };
      return;
    }
    if (compName == 'Fillback') {
      currentParams.value = { tid: selected.value };
      return;
    }
    // other components are now routed to a detail page; dialog params not required
  }

  function handleRecordUpdate(record: any) {
    if (!record || !record.id) return;
    const idx = table.value.findIndex((r: any) => r.id === record.id);
    if (idx > -1) table.value[idx] = { ...table.value[idx], ...record };
    else table.value.unshift(record);
  }

  function handleBatchUpdate(payload: any) {
    // refresh list after batch update
    _searchCore();
  }

  const doEtl = async (item: any | null) => {
    if (item != null) {
      try {
        await taskService.executeTask(item.id, 300000, 'async');
        taskCenter.addTask({
          id: String(item.id),
          type: '采集',
          target: item.targetTable,
          status: '进行中',
          progress: '已提交，等待后端处理',
          submitTime: new Date().toISOString(),
          result: '',
          extra: { tid: item.id },
        });
        notify('采集任务已提交，可在任务中心查看进展', 'primary');
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        notify('采集任务提交失败: ' + msg, 'error');
      }
      return;
    }

    if (selected.value.length === 0) return;

    const tids = [...selected.value];
    try {
      taskService
        .executeTasksBatch(tids)
        .then(() => {
          notify('批量采集任务已提交，可在任务中心查看进展', 'primary');
        })
        .catch(err => {
          const msg = err instanceof Error ? err.message : String(err);
          notify('批量采集任务提交失败: ' + msg, 'error');
        });
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('批量采集任务提交失败: ' + msg, 'error');
    }
  };

  interface LoadItemsOptions {
    page: number;
    itemsPerPage: number;
    sortBy: any;
  }

  const loadItems = ({ page, itemsPerPage, sortBy }: LoadItemsOptions) => {
    loading.value = true;
    currentPage.value = page;
    currPageSize.value = itemsPerPage;
    const sortParam = createSort(sortBy);
    if (sortParam.sortField != null) {
      currentSortParam.value = sortBy;
    }

    tableService
      .fetchTableList(
        page - 1,
        itemsPerPage,
        search.value,
        runStatuses.value,
        sourceId.value,
        sortParam
      )
      .then(res => {
        table.value = res.content;
        totalItems.value = res.totalElements;
        loading.value = false;
        writeQuery();
      })
      .catch(error => {
        const msg = error instanceof Error ? error.message : String(error);
        notify(`加载失败: ${msg}`, 'error');
        loading.value = false;
      });
  };

  const _searchCore = () => {
    selected.value = [];
    loadItems({ page: 1, itemsPerPage: currPageSize.value, sortBy: currentSortParam.value });
  };
  const searchTable = debounce(_searchCore, 400);

  async function loadSources() {
    try {
      const res = await sourceService.listActiveSources();
      sourceOptions.value = res.map((s: any) => ({ label: `${s.code}_${s.name}`, value: s.id }));
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('加载采集源失败: ' + msg, 'error');
    }
  }

  const openCreateFromQuery = () => {
    const action = Array.isArray(route.query.action) ? route.query.action[0] : route.query.action;
    if (action !== 'create') return;
    router.push('/table/batch-add');
    const query = { ...route.query };
    delete query.action;
    router.replace({ path: route.path, query });
  };

  const viewDetail = (item: any) => {
    const routeLocation = {
      path: `/table/detail/${item.id}`,
      query: { tab: 'info', tblname: `${item.sourceDb}.${item.sourceTable}` },
    };
    // Resolve the router location to an href and open it in a new tab.
    // Prevent the new window from accessing window.opener for security reasons.
    const href = router.resolve(routeLocation).href;
    const url = href.startsWith('http') ? href : `${window.location.origin}${href}`;
    const newWin = window.open(url, '_blank');
    if (newWin) newWin.opener = null;
  };

  onMounted(() => {
    loadSources();
    openCreateFromQuery();
  });

  watch(
    () => route.query.action,
    () => {
      openCreateFromQuery();
    }
  );

  function updateSchema(item: any | null) {
    let params: { mode?: string; tid?: number } = {};
    if (typeof item === 'string' && item === 'all') {
      params.mode = 'all';
    } else if (item && typeof item === 'object' && item.id) {
      params.tid = item.id;
    } else {
      params.mode = 'need';
    }

    tableService
      .updateSchema(params)
      .then(response => {
        const message = response || '表结构更新任务已启动';
        notify(message, 'success', 3000, 'mdi-check-circle');
      })
      .catch(error => {
        const errorMsg = error || '更新失败';
        notify('更新失败: ' + errorMsg, 'error', 4000, 'mdi-alert-circle');
      });
  }

  const deleteDialogVisible = ref(false);
  const itemToDelete = ref(null);
  const isBatchDelete = ref(false);
  const deleteConfirmMessage = ref('');

  function confirmDelete(item) {
    itemToDelete.value = item;
    deleteConfirmMessage.value = '您确定要删除此项吗？';
    isBatchDelete.value = false;
    deleteDialogVisible.value = true;
  }

  function confirmBatchDelete() {
    if (selected.value.length === 0) return;
    deleteConfirmMessage.value = `您确定要删除选中的 ${selected.value.length} 项吗？`;
    isBatchDelete.value = true;
    deleteDialogVisible.value = true;
  }

  function deleteItem() {
    if (itemToDelete.value) {
      const id = itemToDelete.value.id;
      tableService
        .delete(id)
        .then(() => {
          const index = table.value.findIndex(i => i.id === id);
          if (index > -1) {
            table.value.splice(index, 1);
          }
          notify('删除成功', 'success');
          deleteDialogVisible.value = false;
          itemToDelete.value = null;
        })
        .catch(error => {
          notify(`删除失败: ${error}`, 'error');
          deleteDialogVisible.value = false;
        });
    }
  }

  function batchDeleteItems() {
    if (selected.value.length === 0) return;
    const tids = [...selected.value];
    Promise.all(tids.map(tid => tableService.delete(tid)))
      .then(() => {
        notify('批量删除成功', 'success');
        deleteDialogVisible.value = false;
        selected.value = [];
        _searchCore();
      })
      .catch(error => {
        notify(`批量删除时发生错误: ${error}`, 'error');
        deleteDialogVisible.value = false;
        _searchCore();
      });
  }

  function confirmKill(item: any) {
    killConfirm.value = {
      open: true,
      message: `将中止「${item.sourceDb ? item.sourceDb + '.' : ''}${
        item.sourceTable || item.id
      }」正在运行的采集任务，已写入的数据会保留。`,
      itemId: item.id,
    };
  }

  const killConfirm = ref<{ open: boolean; message: string; itemId: number | null }>({
    open: false,
    message: '',
    itemId: null,
  });

  function runKill() {
    const id = killConfirm.value.itemId;
    killConfirm.value.open = false;
    if (id == null) return;
    const target = table.value.find(t => t.id === id);
    if (target) killTask(target);
  }

  async function killTask(item: any) {
    try {
      const res = await taskService.killTask(item.id);
      if (res && (res as any).success) {
        notify(`已发送中止请求：${item.id}`, 'primary');
        const idx = table.value.findIndex(t => t.id === item.id);
        if (idx > -1) table.value[idx].status = 'W';
      } else {
        notify(`中止请求失败：${(res as any)?.message || '未知错误'}`, 'error');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('中止任务失败: ' + msg, 'error');
    }
  }
</script>

<style scoped>
  .table-page {
    min-width: 0;
  }

  .ds-filters {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  .ds-filters__keyword {
    flex: 1 1 420px;
    min-width: 260px;
    max-width: 840px;
  }

  .ds-filters__status {
    flex: 0 0 148px;
    justify-content: flex-start;
    padding-inline: 10px;
    font-size: 13px;
    font-weight: 500;
    text-transform: none;
  }

  .ds-filters__status :deep(.v-btn__content) {
    flex: 1 1 auto;
    min-width: 0;
    justify-content: flex-start;
  }

  .ds-filters__status--active {
    border-color: rgba(var(--v-theme-primary), 0.5);
    color: rgb(var(--v-theme-primary));
  }

  .ds-filters__status-label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    text-align: start;
    font-variant-numeric: tabular-nums;
  }

  .ds-status-menu {
    min-width: 200px;
  }

  .ds-nowrap {
    white-space: nowrap;
  }

  /* Why: fixed layout keeps the short columns at their known sizes and lets the
     name columns share the rest; auto layout sized columns by the longest name */
  .table-card :deep(table) {
    table-layout: fixed;
    min-width: 1160px;
  }

  .ds-filters__source {
    flex: 0 1 220px;
    min-width: 180px;
  }

  .filter-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-left: auto;
  }

  .ds-bulk-bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px 16px;
    margin-top: 12px;
    padding: 8px 12px;
    border: 1px solid rgba(var(--v-theme-primary), 0.24);
    border-radius: 10px;
    background: rgba(var(--v-theme-primary), 0.06);
  }

  .ds-bulk-bar__count {
    color: rgb(var(--v-theme-primary));
    font-size: 13px;
    font-weight: 600;
  }

  .ds-bulk-bar__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-left: auto;
  }

  .table-display-menu {
    min-width: 232px;
  }

  .action-menu-list :deep(.danger-item) {
    color: rgb(var(--v-theme-error));
  }

  @media (max-width: 700px) {
    .ds-filters__keyword,
    .ds-filters__status,
    .ds-filters__source {
      flex: 1 1 100%;
      min-width: 0;
      max-width: none;
    }

    .filter-actions,
    .ds-bulk-bar__actions {
      margin-left: 0;
    }
  }
</style>
<route lang="json">
{
  "meta": {
    "title": "采集表管理",
    "icon": "mdi-table",
    "requiresAuth": false,
    "navGroup": "collect",
    "navOrder": 10
  }
}
</route>
