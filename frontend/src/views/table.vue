<template>
  <v-card flat title="采集表配置">
    <template v-slot:text>
      <v-row justify="center" align-content="center">
        <v-col cols="col-2">
          <v-text-field
            v-model="search"
            density="compact"
            label="Search"
            prepend-inner-icon="mdi-magnify"
            single-line
            variant="outlined"
            hide-details
            @keyup.enter="searchTable"
            @click:append-inner="searchTable"
          >
            <template #prepend>
              <span class="me-2">关键字查询</span>
            </template>
          </v-text-field>
        </v-col>
        <v-col cols="auto">
          <!-- 状态下拉框选择 statusOptions -->
          <v-select
            v-model="runStatus"
            :items="statusOptions"
            item-title="label"
            item-value="value"
            density="compact"
            style="min-width: 150px"
          >
            <template #prepend>
              <span class="me-2">状态</span>
            </template>
          </v-select>
        </v-col>
        <!-- add search button -->
        <v-col cols="auto">
          <v-btn variant="tonal" @click="searchTable">查询</v-btn>
        </v-col>
        <v-spacer />
        <v-col cols="auto">
          <v-btn
            variant="tonal"
            prepend-icon="mdi-plus"
            @click="openDialog('BatchAdd', 'BatchAdd')"
          >
            新增表
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <!-- old function doEtl('source') -->
          <v-btn variant="tonal" prepend-icon="mdi-update" @click="updateSchema(null)">
            更新表信息
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn variant="tonal" prepend-icon="mdi-update" @click="updateSchema('all')">
            强制更新全部表信息
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn
            variant="tonal"
            prepend-icon="mdi-delete"
            :disabled="selected.length === 0"
            @click="confirmBatchDelete"
          >
            批量删除
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn
            variant="tonal"
            prepend-icon="mdi-pencil"
            :disabled="selected.length === 0"
            @click="openDialog('BatchUpdate', 'BatchUpdate')"
          >
            批量修改
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn
            variant="tonal"
            prepend-icon="mdi-database"
            :disabled="selected.length === 0"
            @click="doEtl(null)"
          >
            批量采集
          </v-btn>
        </v-col>
      </v-row>
    </template>
    <v-card-text>
      <v-data-table-server
        density="compact"
        :items="table"
        :headers="headers"
        :items-per-page="currPageSize"
        :items-length="totalItems"
        item-value="id"
        :loading="loading"
        @update:options="loadItems"
        show-select
        v-model="selected"
        :item-class="getRowClass"
      >
        <!-- 顶部插槽：显示当前选中数量 -->
        <template #top>
          <v-row class="mb-2 px-3 py-2" align="center">
            <v-chip v-if="selected.length > 0" color="secondary" variant="tonal" size="small">
              已选择 {{ selected.length }} 行
            </v-chip>
          </v-row>
        </template>
        <template v-slot:item.status="{ item }">
          <v-chip
            :color="getStatusColor(item.status)"
            size="small"
            variant="flat"
            class="font-weight-bold"
          >
              {{ item.status }}
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
                <div
                  style="
                    padding: 12px 16px;
                    max-width: 480px;
                    max-height: 260px;
                    overflow-y: auto;
                    background: #fffbe6;
                    color: #ad6800;
                    border: 1px solid #ffe58f;
                    border-radius: 8px;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
                    font-size: 14px;
                    line-height: 1.7;
                    white-space: pre-line;
                  "
                >
                  <span v-if="errorMsgMap[item.id] !== undefined">
                    {{ errorMsgMap[item.id] || '无详细错误信息' }}
                  </span>
                  <span v-else>加载中...</span>
                </div>
              </v-menu>
            </template>
          </v-chip>
        </template>
        <template v-slot:item.action="{ item }">
          <v-row justify="center" no-gutters>
            <v-btn
              small
              density="compact"
              color="primary"
              class="mr-1"
              @click="openDialog('TableDetail', item)"
            >
              详情
            </v-btn>
            <v-btn
              small
              density="compact"
              color="secondary"
              class="mr-1"
              @click="openDialog('FieldsCompare', item)"
            >
              字段
            </v-btn>
            <v-btn
              small
              density="compact"
              color="info"
              class="mr-1"
              @click="openDialog('AddaxJob', item)"
            >
              模板
            </v-btn>
            <v-btn
              small
              density="compact"
              color="success"
              class="mr-1"
              @click="openDialog('AddaxResult', item)"
            >
              结果
            </v-btn>
            <v-btn
              small
              density="compact"
              color="info"
              class="mr-1"
              @click="openDialog('LogFiles', item)"
            >
              日志
            </v-btn>
            <v-btn small density="compact" color="error" class="mr-1" @click="confirmDelete(item)">
              删除
            </v-btn>
            <v-btn small density="compact" color="info" class="mr-1" @click="doEtl(item)">
              采集
            </v-btn>
            <v-btn small density="compact" color="secondary" @click="updateSchema(item)">
              表更新
            </v-btn>
          </v-row>
        </template>
      </v-data-table-server>
    </v-card-text>
  </v-card>

  <!-- 对话框 -->
  <v-dialog v-model="dialogVisible" :retain-focus="false" scrollable>
    <!-- 动态加载的内容 -->
    <component
      :is="currentComponent"
      v-bind="currentParams"
      @closeDialog="closeDialog"
      @update:record="handleRecordUpdate"
      @update:batch="handleBatchUpdate"
      @refresh-data="searchTable"
    />
  </v-dialog>

  <!-- 删除确认对话框 -->
  <v-dialog v-model="deleteDialogVisible" width="400">
    <v-card>
      <v-card-title class="text-h6">确认删除</v-card-title>
      <v-card-text>{{ deleteConfirmMessage }}</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="deleteDialogVisible = false">取消</v-btn>
        <v-btn color="error" @click="isBatchDelete ? batchDeleteItems() : deleteItem()">确定</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <!-- ETL进度对话框 -->
</template>

<script setup lang="ts">
  import { ref, shallowRef, defineAsyncComponent } from 'vue'
  import { debounce } from '@/utils/debounce'
  import { createSort } from '@/utils/'
  import { BATCH_UPDATE_STATUS_OPTIONS } from '@/utils'
  import tableService from '@/service/table-service'
  import taskService from '@/service/task-service'
  import { notify } from '@/stores/notifier'
  import type { DataTableHeader } from 'vuetify'
  // 异步按需加载组件，减轻首屏体积
  const TableDetail = defineAsyncComponent(() => import('@/components/table/TableDetail.vue'))
  const FieldsCompare = defineAsyncComponent(() => import('@/components/table/FieldsCompare.vue'))
  const AddaxJob = defineAsyncComponent(() => import('@/components/table/AddaxJob.vue'))
  const AddaxResult = defineAsyncComponent(() => import('@/components/table/AddaxResult.vue'))
  const BatchAdd = defineAsyncComponent(() => import('@/components/table/BatchAdd.vue'))
  const LogFiles = defineAsyncComponent(() => import('@/components/table/LogFiles.vue'))
  const BatchUpdate = defineAsyncComponent(() => import('@/components/table/BatchUpdate.vue'))

  const table = ref([])
  const search = ref('')
  const selected = ref([])
  const currPageSize = ref(10)
  const totalItems = ref(0)
  const loading = ref(true)
  const dialogVisible = ref(false)
  // 当前要显示的组件（默认值为 null，表示对话框无内容）
  // shallowRef 用于定义浅层响应式的引用，它不会递归地将对象或组件内部的数据设为响应式，因此适用于组件这种复杂对象。
  const currentComponent = shallowRef(null)
  // 传递给子组件的参数
  const currentParams = ref({})
  const currentSortParam = ref([
    {
      sortField: null,
      sortOrder: null
    }
  ])
  const componentMap = {
    TableDetail,
    FieldsCompare,
    AddaxJob,
    AddaxResult,
    BatchAdd,
    LogFiles,
    BatchUpdate
  }

  function getRowClass(item: any) {
    return selected.value.includes(item.id) ? 'selected-row' : ''
  }

  // 根据状态返回对应的颜色
  function getStatusColor(status: string) {
    const statusColorMap = {
      N: 'grey', // N_未运行 - 灰色
      R: 'blue', // R_正在运行 - 蓝色
      Y: 'success', // Y_运行结束 - 绿色
      E: 'error', // E_运行错误 - 红色
      X: 'warning', // X_禁用 - 橙色
      W: 'purple', // W_等待 - 紫色
      U: 'blue-grey' // U_等待表结构更新 - 蓝灰色
    }
    return statusColorMap[status] || 'grey'
  }

  // 使用公共的批量更新状态选项
  const statusOptions = BATCH_UPDATE_STATUS_OPTIONS

  const runStatus = ref<string>()

  const headers: DataTableHeader[] = [
    {
      title: '#',
      key: 'id',
      align: 'center' as const,
      width: '3%'
    },
    {
      title: '系统名称及代码',
      key: 'name',
      align: 'center' as const,
      sortable: true,
      width: '12%',
      value: (item) => `${item.name} (${item.code})`
    },
    { title: '源库表', key: 'sourceTableCombined', align: 'start' as const, sortable: true, width: '12%', value: (item) => `${item.sourceDb || ''}.${item.sourceTable || ''}` },
    { title: '目标库表', key: 'targetTableCombined', align: 'start' as const, sortable: true, width: '18%', value: (item) => `${item.targetDb || ''}.${item.targetTable || ''}` },
    { title: '状态', key: 'status', align: 'center' as const, sortable: true, width: '3%' },
    { title: '剩余', key: 'retryCnt', align: 'center' as const, sortable: true, width: '2%' },
    { title: '耗时', key: 'duration', align: 'center' as const, sortable: true, width: '3%' },
    { title: '最后采集', key: 'startTime', align: 'center' as const, sortable: true, width: '12%' },
    { title: '操作', key: 'action', align: 'center' as const, sortable: false, width: '40%' }
  ]
  // 已使用全局 Notifier 替换旧的 alertMsg

  const errorMsgMap = ref<Record<number, string>>({})

  function fetchErrorMsg(tid: number) {
    errorMsgMap.value[tid] = ''

    taskService
      .getLastError(tid)
      .then((res) => {
        errorMsgMap.value[tid] = res || '无详细错误信息'
      })
      .catch((err) => {
        const msg = err instanceof Error ? err.message : String(err)
        errorMsgMap.value[tid] = '获取错误信息失败: ' + msg
      })
  }
  // 打开对话框并加载相应的组件
  function openDialog(componentName: string, com: any) {
    currentComponent.value = componentMap[componentName]
    setParams(componentName, com)
    dialogVisible.value = true // 打开对话框
  }

  // 关闭对话框
  function closeDialog() {
    dialogVisible.value = false
    currentComponent.value = null // 清空内容
  }

  function setParams(compName: string, comp: any) {
    if (compName == 'BatchUpdate') {
      // 批量修改
      currentParams.value = {
        tid: selected.value
      }
      return
    }
    if (compName == 'TableDetail') {
      currentParams.value = {
        table: comp
      }
      return
    }
    if (compName == 'FieldsCompare' || compName == 'AddaxJob') {
      currentParams.value = {
        tid: comp.id
      }
    } else if (compName == 'TableUsed') {
      currentParams.value = {
        tid: comp.targetDb + '.' + comp.targetTable + '|' + comp.sid
      }
    } else if (compName == 'AddaxResult') {
      currentParams.value = {
        tid: comp.id
      }
    } else if (compName === 'BatchAdd') {
      currentParams.value = {
        tid: '-1'
      }
    } else if (compName === 'LogFiles') {
      currentParams.value = {
        tid: comp.id
      }
    } else {
      currentParams.value = {
        tid: comp.id
      }
    }
  }

  import taskCenter from '@/stores/task-center'

  const doEtl = async (item: any | null) => {
    if (item != null) {
      // 单个采集，直接异步提交任务中心
      try {
        await taskService.executeTask(item.id, 300000, 'async')
        taskCenter.addTask({
          id: String(item.id),
          type: '采集',
          target: item.targetTable,
          status: '进行中',
          progress: '已提交，等待后端处理',
          submitTime: new Date().toISOString(),
          result: '',
          extra: { tid: item.id }
        })
        notify('采集任务已提交，可在任务中心查看进展', 'primary')
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err)
        notify('采集任务提交失败: ' + msg, 'error')
      }
      return
    }

    if (selected.value.length === 0) return

    // 并行批量采集，异步提交
    const tids = [...selected.value]
    try {
      taskService
        .executeTasksBatch(tids)
        .then(() => {
          // 所有任务提交成功
          notify('采集任务已提交，可在任务中心查看进展', 'primary')
        })
        .catch((err) => {
          const msg = err instanceof Error ? err.message : String(err)
          notify('批量采集任务提交失败: ' + msg, 'error')
        })
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      notify('批量采集任务提交失败: ' + msg, 'error')
    }
  }

  const handleRecordUpdate = (newRecord) => {
    const index = table.value.findIndex((item) => item.id === newRecord.id)
    if (index > -1) {
      table.value.splice(index, 1, newRecord) // 响应式替换单个记录
    }
  }

  /**
*
* @param payload
* const payload = {
tids: props.tid,
status: status.value,
retryCnt: retryCnt.value
};
*/
  const handleBatchUpdate = (payload) => {
    payload.tids.forEach((tid) => {
      const index = table.value.findIndex((item) => item.id === tid)
      if (index > -1) {
        table.value[index].status = payload.status
        table.value[index].retryCnt = payload.retryCnt
      }
    })
  }

  interface LoadItemsOptions {
    page: number
    itemsPerPage: number
    sortBy: any
  }

  const loadItems = ({ page, itemsPerPage, sortBy }: LoadItemsOptions) => {
    loading.value = true
    currPageSize.value = itemsPerPage
    const sortParam = createSort(sortBy)
    if (sortParam.sortField != null) {
      currentSortParam.value = sortBy
    }
    // v-data-table-server page is 1-based, while backend API is 0-based.
    tableService
      .fetchTableList(page - 1, itemsPerPage, search.value, runStatus.value, sortParam)
      .then((res) => {
        table.value = res.content
        totalItems.value = res.totalElements
        loading.value = false
      })
      .catch((error) => {
        const msg = error instanceof Error ? error.message : String(error)
        notify(`加载失败: ${msg}`, 'error')
        loading.value = false
      })
  }

  const _searchCore = () => {
    selected.value = []
    loadItems({
      page: 0,
      itemsPerPage: currPageSize.value,
      sortBy: currentSortParam.value
    })
  }

  const searchTable = debounce(_searchCore, 400)

  function updateSchema(item: any | null) {
    let params: { mode?: string; tid?: number } = {}
    if (typeof item === 'string' && item === 'all') {
      params.mode = 'all'
    } else if (item && typeof item === 'object' && item.id) {
      params.tid = item.id
    } else {
      params.mode = 'need'
    }

    tableService
      .updateSchema(params) // 10分钟超时
      .then((response) => {
        const message = response || '表结构更新任务已启动'
        notify(message, 'success', 3000, 'mdi-check-circle')
      })
      .catch((error) => {
        const errorMsg = error || '更新失败'
        notify('更新失败: ' + errorMsg, 'error', 4000, 'mdi-alert-circle')
      })
  }

  const deleteDialogVisible = ref(false)
  const itemToDelete = ref(null)
  const isBatchDelete = ref(false)
  const deleteConfirmMessage = ref('')

  function confirmDelete(item) {
    itemToDelete.value = item
    deleteConfirmMessage.value = '您确定要删除此项吗？'
    isBatchDelete.value = false
    deleteDialogVisible.value = true
  }

  function confirmBatchDelete() {
    if (selected.value.length === 0) return
    deleteConfirmMessage.value = `您确定要删除选中的 ${selected.value.length} 项吗？`
    isBatchDelete.value = true
    deleteDialogVisible.value = true
  }

  function deleteItem() {
    if (itemToDelete.value) {
      const id = itemToDelete.value.id
      tableService
        .delete(id)
        .then(() => {
          const index = table.value.findIndex((i) => i.id === id)
          if (index > -1) {
            table.value.splice(index, 1) // 删除项
          }
          notify('删除成功', 'success')
          deleteDialogVisible.value = false
          itemToDelete.value = null
        })
        .catch((error) => {
          notify(`删除失败: ${error}`, 'error')
          deleteDialogVisible.value = false
        })
    }
  }

  function batchDeleteItems() {
    if (selected.value.length === 0) return
    const tids = [...selected.value]
    // 并发删除所有选中项
    Promise.all(tids.map((tid) => tableService.delete(tid)))
      .then(() => {
        notify('批量删除成功', 'success')
        // 关闭确认框 & 清空选中
        deleteDialogVisible.value = false
        selected.value = []
        // 重新加载数据，防止当前页面空白
        // 直接调用核心加载函数，避免 debounce 延迟
        _searchCore()
      })
      .catch((error) => {
        notify(`批量删除时发生错误: ${error}`, 'error')
        deleteDialogVisible.value = false
        _searchCore()
      })
  }

  // Confirm and trigger kill for a running task
  function confirmKill(item: any) {
    // simple browser confirm – replace with nicer dialog if desired
    const ok = window.confirm(`确定要中止任务 ${item.id} 吗？`)
    if (!ok) return
    killTask(item)
  }

  async function killTask(item: any) {
    try {
      const res = await taskService.killTask(item.id)
      if (res && (res as any).success) {
        notify(`已发送中止请求：${item.id}`, 'primary')
        // optimistically update status to indicate kill requested
        const idx = table.value.findIndex((t) => t.id === item.id)
        if (idx > -1) table.value[idx].status = 'W'
      } else {
        notify(`中止请求失败：${(res as any)?.message || '未知错误'}`, 'error')
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      notify('中止任务失败: ' + msg, 'error')
    }
  }
</script>

<route lang="json">
{
  "meta": {
    "title": "采集表管理",
    "icon": "mdi-table",
    "requiresAuth": false
  }
}
</route>
