<template>
  <v-container fluid class="pa-6">
    <v-row dense>
      <!-- 数据源完成情况 -->
      <v-col cols="12" md="12">
        <v-card flat :title="accomplishListTable.title" class="mb-4">
          <v-card-text>
            <v-data-table
              :items="data.accomplishList"
              :headers="accomplishListTable.headers"
              density="compact"
              :sort-by="accomplishListTable.sortBy"
              class="elevation-1"
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense>
      <!-- 特殊任务提醒 -->
      <v-col cols="12" md="7">
        <v-card flat :title="specialTaskTable.title" class="mb-4">
          <v-card-text>
            <v-data-table
              :items="data.specialTask"
              :headers="specialTaskTable.headers"
              density="compact"
              :sort-by="specialTaskTable.sortBy"
              class="elevation-1"
            />
          </v-card-text>
        </v-card>
      </v-col>

      <!-- 采集拒绝行信息 -->
      <v-col cols="12" md="5">
        <v-row>
          <v-col cols="12" md="12">
            <v-card flat :title="rejectTaskTable.title" class="mb-4">
              <v-card-text>
                <v-data-table
                  :items="data.rejectTask"
                  :headers="rejectTaskTable.headers"
                  density="compact"
                  :sort-by="rejectTaskTable.sortBy"
                  class="elevation-1"
                  hide-no-data
                  hide-default-footer
                />
              </v-card-text>
            </v-card>
          </v-col>
          <!-- 短信发送情况 -->
          <v-col cols="12" md="12">
            <v-card flat :title="smsDetailTable.title" class="mb-4">
              <v-card-text>
                <v-data-table
                  :items="data.smsDetail"
                  :headers="smsDetailTable.headers"
                  density="compact"
                  :sort-by="smsDetailTable.sortBy"
                  class="elevation-1"
                  hide-no-data
                  hide-default-footer
                />
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-col>

      <!-- 字段变更提醒（后端分页） -->
      <v-col cols="12" md="12">
        <v-card flat :title="fieldChangeTable.title" class="mb-4">
          <v-card-text>
            <v-data-table-server
              :items="fieldChangeItems"
              :headers="fieldChangeTable.headers"
              density="compact"
              :items-per-page="fieldChangePageSize"
              :items-length="fieldChangeTotal"
              :loading="fieldChangeLoading"
              @update:options="loadFieldChangeItems"
              class="elevation-1"
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- 系统风险检测结果 -->
    <v-col cols="12" md="12">
      <v-card flat :title="sysRiskTable.title" class="mb-4">
        <v-card-text>
          <v-data-table
            :items="data.sysRisk"
            :headers="sysRiskTable.headers"
            density="compact"
            :sort-by="sysRiskTable.sortBy"
            class="elevation-1"
            hide-no-data
            hide-default-footer
          />
        </v-card-text>
      </v-card>
    </v-col>
  </v-container>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import { monitorService } from '@/service/monitor-service'
  import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort.mjs'
  import { createSort } from '@/utils/'

  const data = ref({
    accomplishList: [] as Array<Map<string, any>>,
    specialTask: [] as Array<Map<string, any>>,
    rejectTask: [] as Array<Map<string, any>>,
    sysRisk: [] as Array<Map<string, any>>,
    fieldChange: [] as Array<Map<string, any>>,
    smsDetail: [] as Array<Map<string, any>>
  })

  // 各表单独定义，便于灵活处理
  const accomplishListTable = {
    name: 'accomplishList',
    api: 'accomplishList',
    title: '数据源完成情况',
    sortBy: <SortItem[]>[{ key: 'overPrec', order: 'asc' }],
    headers: <DataTableHeader[]>[
      {
        title: '启动',
        key: 'start_at'
      },
      { title: '数据源', key: 'sys_name' },
      {
        title: '整体情况',
        align: 'center',
        value: '',
        children: [
          { title: '总数', key: 'total_cnt' },
          { title: '完成数', key: 'succ_cnt' },
          {
            title: '完成率',
            key: 'overPrec',
            value: (item) => `${Math.round((item.succ_cnt / item.total_cnt) * 100)}%`,
            cellProps: ({ value }) => ({
              class: value === '100%' ? 'text-success' : 'text-warning'
            })
          },
          {
            title: '运行/错误/未执行/未建表',
            key: 'run_fail_noRun_noCreate',
            // 使用不间断空格确保渲染时空格可见
            value: (item) =>
              `${item.run_cnt ?? 0}\u00A0/\u00A0${item.fail_cnt ?? 0}\u00A0/\u00A0${item.no_run_cnt ?? 0}\u00A0/\u00A0${item.no_create_table_cnt ?? 0}`,
            align: 'center'
          }
        ]
      },
      {
        title: 'T-1 日',
        align: 'center',
        value: '',
        children: [
          { title: '开始时间', key: 'y_begin_at', align: 'center' },
          { title: '结束时间', key: 'y_finish_at', align: 'center' },
          { title: '耗时', key: 'y_take_secs', align: 'center' }
        ]
      },
      {
        title: 'T 日',
        align: 'center',
        value: '',
        children: [
          { title: '开始时间', key: 't_begin_at', align: 'center' },
          { title: '结束时间', key: 't_finish_at', align: 'center' },
          { title: '耗时', key: 't_take_secs', align: 'center' }
        ]
      }
    ]
  }

  const specialTaskTable = {
    name: 'specialTask',
    api: 'specialTask',
    title: '特殊任务提醒：错误、耗时过长、有重试、有拒绝行',
    sortBy: <SortItem[]>[{ key: 'RUNTIME', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '源库', key: 'sourceDb' },
      { title: '表名', key: 'sourceTable' },
      { title: '状态', key: 'status' },
      { title: '剩余', key: 'retryCnt' },
      {
        title: '耗时',
        key: 'duration',
        cellProps: ({ value }) => ({
          class: value > 1000 ? 'text-warning' : ''
        })
      },
      { title: '开始时间', key: 'startTime' },
      { title: '结束时间', key: 'endTime' }
    ]
  }

  const rejectTaskTable = {
    name: 'rejectTask',
    api: 'rejectTask',
    title: '采集拒绝行信息',
    sortBy: <SortItem[]>[{ key: 'totalErr', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '任务名称', key: 'jobname' },
      { title: '拒绝行', key: 'totalErr' },
      { title: '开始时间', key: 'startTs' },
      { title: '结束时间', key: 'endTs' }
    ]
  }

  const sysRiskTable = {
    name: 'sysRisk',
    api: 'sysRisk',
    title: '系统风险检测结果',
    sortBy: <SortItem[]>[],
    headers: <DataTableHeader[]>[
      { title: '风险来源', key: 'source' },
      { title: '类别', key: 'riskLevel' },
      { title: '风险摘要', key: 'message' },
      { title: '关联表 ID', key: 'tid' },
      { title: '创建时间', key: 'createdAt' }
    ]
  }

  const fieldChangeTable = {
    name: 'fieldChange',
    api: 'fieldChange',
    title: '采集源库的字段变更提醒(T-1日结构与T日结构对比)',
    sortBy: <SortItem[]>[{ key: 'changeAt', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '库名', key: 'sourceDb' },
      { title: '表名', key: 'sourceTable' },
      { title: '字段名', key: 'columnName' },
      { title: '变更类型', key: 'changeType' },
      {
        title: '变更说明',
        key: 'change_summary',
        value: (item) => {
          const parts: string[] = []
          // 类型变化
          if (item.oldSourceType || item.newSourceType) {
            const oldT = item.oldSourceType ?? '—'
            const newT = item.newSourceType ?? '—'
            parts.push(`类型: ${oldT} → ${newT}`)
          }
          // 长度/精度/小数位变化（仅在有值时展示）
          const oldLen = item.oldDataLength
          const newLen = item.newDataLength
          if (oldLen != null || newLen != null)
            parts.push(`长度: ${oldLen ?? '—'} → ${newLen ?? '—'}`)
          const oldPrec = item.oldDataPrecision
          const newPrec = item.newDataPrecision
          if (oldPrec != null || newPrec != null)
            parts.push(`精度: ${oldPrec ?? '—'} → ${newPrec ?? '—'}`)
          const oldScale = item.oldDataScale
          const newScale = item.newDataScale
          if (oldScale != null || newScale != null)
            parts.push(`小数位: ${oldScale ?? '—'} → ${newScale ?? '—'}`)
          return parts.join('； ')
        }
      },
      { title: '变更时间', key: 'changeAt' }
    ]
  }

  const smsDetailTable = {
    name: 'smsDetail',
    api: '/smsDetail',
    title: '短信发送情况',
    sortBy: <SortItem[]>[{ key: 'dwCltDate', order: 'desc' }],
    headers: <DataTableHeader[]>[
      {
        title: '短信内容',
        value: 'msg',
        width: '70%',
        cellProps: ({ value }) => ({
          class: value.includes('失败') ? 'bg-danger' : ''
        })
      },
      { title: '发送时间', value: 'dwCltDate' }
    ]
  }

  // 已按模板拆分为独立区块，移除通用 v-for 渲染

  // ---- 字段变更提醒：服务端分页状态 & 加载逻辑 ----
  const fieldChangeItems = ref<Array<Map<string, any>>>([])
  const fieldChangeTotal = ref(0)
  const fieldChangeLoading = ref(false)
  const fieldChangePageSize = ref(10)
  const fieldChangeCurrentSort = ref<SortItem[]>(fieldChangeTable.sortBy)

  interface LoadItemsOptions {
    page: number
    itemsPerPage: number
    sortBy: SortItem[]
  }

  const loadFieldChangeItems = ({ page, itemsPerPage, sortBy }: LoadItemsOptions) => {
    fieldChangeLoading.value = true
    fieldChangePageSize.value = itemsPerPage
    const sortParam = createSort(sortBy || [])
    if (sortParam.sortField != null) fieldChangeCurrentSort.value = sortBy
    // Vuetify page is 1-based; backend expects 0-based
    monitorService
      .fieldsChangesPaged(page - 1, itemsPerPage, sortParam)
      .then((res) => {
        fieldChangeItems.value = res.content
        fieldChangeTotal.value = res.totalElements
        fieldChangeLoading.value = false
      })
      .catch(() => {
        fieldChangeItems.value = []
        fieldChangeTotal.value = 0
        fieldChangeLoading.value = false
      })
  }

  const getData = async () => {
    try {
      const [accomplishList, specialTask, rejectTask, rawSysRisk, smsDetail] = await Promise.all([
        monitorService.fetchAccomplishList(),
        monitorService.fetchSpecialTask(),
        monitorService.fetchRejectTask(),
        monitorService.sysRisks(),
        monitorService.smsDetail()
      ])
      data.value.accomplishList = accomplishList
      data.value.specialTask = specialTask
      data.value.rejectTask = rejectTask
      data.value.sysRisk = rawSysRisk
      data.value.smsDetail = smsDetail
    } catch (error) {
      console.error('Error fetching data:', error)
    }
  }

  onMounted(() => {
    getData()
    // 触发表格首次加载
    loadFieldChangeItems({
      page: 1,
      itemsPerPage: fieldChangePageSize.value,
      sortBy: fieldChangeCurrentSort.value
    })
  })
</script>
<route lang="json">
{
  "meta": {
    "title": "采集与监控",
    "icon": "mdi-monitor",
    "requiresAuth": false
  }
}
</route>
