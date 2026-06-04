<template>
  <div
    class="table-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible || deleteDialogVisible }"
  >
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">采集表配置</div>
            <div class="page-subtitle">统一维护采集表、批量采集与结构更新。</div>
          </div>
          <div class="ds-actions">
            <v-btn
              color="primary"
              variant="flat"
              prepend-icon="mdi-plus"
              @click="() => router.push('/table/batch-add')"
            >
              新增表
            </v-btn>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card toolbar-card">
      <v-card-text class="ds-card__content">
        <v-row dense align="center">
          <v-col cols="12" md="2">
            <v-text-field
              v-model="search"
              density="compact"
              label="关键字"
              prepend-inner-icon="mdi-magnify"
              single-line
              variant="outlined"
              hide-details
              @keyup.enter="searchTable"
              @click:append-inner="searchTable"
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-select
              v-model="runStatuses"
              :items="TABLE_STATUS_OPTIONS"
              item-title="label"
              item-value="value"
              density="compact"
              variant="outlined"
              multiple
              clearable
              hide-details
              label="状态"
            />
          </v-col>
          <v-col cols="12" md="2" lg="2">
            <v-select
              v-model="sourceId"
              :items="sourceOptions"
              item-title="label"
              item-value="value"
              :item-props="item => ({ title: item.label })"
              density="compact"
              variant="outlined"
              single-line
              hide-details
              clearable
              label="数据源"
            />
          </v-col>
          <v-col cols="12" md="1" lg="1">
            <v-btn color="primary" variant="flat" @click="searchTable">查询</v-btn>
          </v-col>
          <v-spacer />
          <template v-if="selected.length > 0">
            <v-col cols="12" md="4" class="d-flex justify-end">
              <v-btn
                variant="flat"
                color="primary"
                prepend-icon="mdi-database"
                class="mx-1"
                @click="doEtl(null)"
              >
                批量采集
              </v-btn>

              <v-btn
                color="secondary"
                prepend-icon="mdi-pencil"
                class="mx-1"
                @click="openDialog('BatchUpdate', 'BatchUpdate')"
              >
                批量修改
              </v-btn>

              <v-btn
                variant="flat"
                color="error"
                prepend-icon="mdi-delete"
                class="mx-1"
                @click="confirmBatchDelete"
              >
                批量删除
              </v-btn>
              <v-btn color="secondary" variant="tonal" class="mx-1">
                已选择 {{ selected.length }} 行
              </v-btn>
            </v-col>
          </template>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-card ds-table-wrap">
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
            density="comfortable"
            :items="table"
            :headers="headers"
            :items-per-page="currPageSize"
            :items-per-page-options="[15, 20, 25, 30, 50, 100]"
            :items-length="totalItems"
            item-value="id"
            :loading="loading"
            @update:options="loadItems"
            show-select
            v-model="selected"
            :item-class="getRowClass"
          >
            <template v-slot:item.sourceTable="{ item }">
              <div class="ds-cell-stack">
                <span class="ds-cell-meta">{{ item.name }}({{ item.code }})</span>
                <span class="ds-cell-primary ds-white-space-normal">
                  <span class="ds-cell-support">{{ item.sourceDb || '' }}.</span
                  >{{ item.sourceTable || '' }}
                </span>
              </div>
            </template>
            <template v-slot:item.status="{ item }">
              <v-badge dot inline :color="getStatusColor(item.status)" class="mr-2" />
              <span class="text-body-2 font-weight-medium">{{ item.status }}</span>
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

            <template v-slot:item.targetTable="{ item }">
              <div class="d-flex align-center py-1">
                <v-icon size="14" color="disabled" class="mr-2">mdi-arrow-right</v-icon>
                <span class="ds-cell-primary ds-white-space-normal">
                  <span class="ds-cell-support">{{ item.targetDb || '' }}.</span
                  >{{ item.targetTable || '' }}
                </span>
              </div>
            </template>

            <template v-slot:item.action="{ item }">
              <v-btn color="primary" variant="plain" @click="doEtl(item)">
                <v-icon>mdi-play-circle</v-icon>采集
              </v-btn>

              <v-btn color="warning" variant="plain" @click="updateSchema(item)">
                <v-icon>mdi-database-refresh</v-icon>更新
              </v-btn>

              <v-btn variant="plain" color="error" @click="confirmDelete(item)">
                <v-icon>mdi-delete</v-icon>删除
              </v-btn>

              <v-btn
                variant="plain"
                color="secondary"
                aria-label="更多操作"
                @click="
                  router.push({
                    path: `/table/detail/${item.id}`,
                    query: { tab: 'info', tblname: `${item.sourceDb}.${item.sourceTable}` },
                  })
                "
              >
                <v-icon size="16">mdi-open-in-new</v-icon>
              </v-btn>
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
    width="400"
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
</template>

<script setup lang="ts">
  import { ref, shallowRef, defineAsyncComponent, onMounted, watch, computed } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import EmptyState from '@/components/EmptyState.vue';
  import { debounce } from '@/utils/debounce';
  import { createSort } from '@/utils/';
  import { TABLE_STATUS_OPTIONS } from '@/utils';
  import tableService from '@/service/table-service';
  import taskService from '@/service/task-service';
  import sourceService from '@/service/source-service';
  import { notify } from '@/stores/notifier';
  import taskCenter from '@/stores/task-center';
  import type { DataTableHeader } from 'vuetify';
  // 异步按需加载组件，减轻首屏体积（仅保留批量修改对话框）
  const BatchUpdate = defineAsyncComponent(() => import('@/components/table/BatchUpdate.vue'));

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
  };

  function getRowClass(item: any) {
    return selected.value.includes(item.id) ? 'selected-row' : '';
  }

  // 映射 Vuetify 的标准状态色（支持 Badge 点）
  function getStatusColor(status: string) {
    const statusColorMap = {
      N: 'grey-lighten-1',
      R: 'blue', // 运行中使用干净的蓝色
      Y: 'success', // 成功绿色
      E: 'error', // 错误红色
      X: 'warning', // 警告橙色
      W: 'amber',
      U: 'grey-darken-1',
    };
    return statusColorMap[status] || 'grey';
  }

  const runStatuses = ref<string[]>([]);
  const sourceId = ref<number | null>(null);
  const sourceOptions = ref<any[]>([]);

  // 优化后的 Headers 配置：精简列数，合并表达
  const headers: DataTableHeader[] = [
    { title: '#', key: 'id', align: 'center', width: '1%' },
    { title: '源库表映射路径', key: 'sourceTable', align: 'start', sortable: true, width: '25%' },
    { title: '目标库表', key: 'targetTable', align: 'start', sortable: true, width: '25%' },
    { title: '过滤规则', key: 'filter', align: 'start', sortable: true, width: '10%' },
    { title: '状态', key: 'status', align: 'start', sortable: true, width: '5%' },
    { title: '耗时(s)', key: 'duration', align: 'center', sortable: true, width: '6%' },
    { title: '完成时间', key: 'endTime', align: 'center', sortable: true, width: '12%' },
    { title: '操作', key: 'action', align: 'center', sortable: false, width: '15%' },
  ];

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
      })
      .catch(error => {
        const msg = error instanceof Error ? error.message : String(error);
        notify(`加载失败: ${msg}`, 'error');
        loading.value = false;
      });
  };

  const _searchCore = () => {
    selected.value = [];
    loadItems({ page: 0, itemsPerPage: currPageSize.value, sortBy: currentSortParam.value });
  };
  const searchTable = debounce(_searchCore, 400);

  async function loadSources() {
    try {
      const res = await sourceService.listActiveSources();
      sourceOptions.value = res.map((s: any) => ({ label: `${s.code}_${s.name}`, value: s.id }));
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('加载数据源失败: ' + msg, 'error');
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
    const ok = window.confirm(`确定要中止任务 ${item.id} 吗？`);
    if (!ok) return;
    killTask(item);
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

  .query-actions {
    flex-wrap: wrap;
  }

  .batch-toolbar {
    row-gap: 8px;
  }

  .selection-bar {
    margin-bottom: 8px;
    padding: 0 4px;
    gap: 8px;
  }

  .action-menu-list :deep(.danger-item) {
    color: rgb(var(--v-theme-error));
  }

  .status-cell-wrap {
    height: 32px;
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
