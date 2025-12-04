<template>
  <v-container fluid class="pa-6">
    <v-row dense>
      <!-- 通用多表渲染（每项一个定义） -->
      <v-col v-for="t in allItems" cols="12" :md="t.cols">
        <v-card flat :title="t.title" class="mb-4">
          <v-card-text>
            <v-data-table
              :items="data[t.name]"
              :headers="t.headers"
              density="compact"
              :sort-by="t.sortBy || []"
              class="elevation-1"
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import { monitorService } from '@/service/monitor-service'
  import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort.mjs'

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
    cols: 12,
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
    cols: 6,
    sortBy: <SortItem[]>[{ key: 'RUNTIME', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '任务名', key: 'SPNAME' },
      { title: '状态', key: 'FLAG' },
      { title: '剩余', key: 'RETRY_CNT' },
      {
        title: '耗时',
        key: 'RUNTIME',
        cellProps: ({ value }) => ({
          class: value > 1000 ? 'text-warning' : ''
        })
      },
      { title: '开始时间', key: 'START_TIME' },
      { title: '结束时间', key: 'END_TIME' }
    ]
  }

  const rejectTaskTable = {
    name: 'rejectTask',
    api: 'rejectTask',
    title: '采集拒绝行信息',
    cols: 6,
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
    cols: 12,
    sortBy: <SortItem[]>[],
    headers: <DataTableHeader[]>[
      { title: '类别', key: 'chkKind' },
      { title: '名称', key: 'chkName' },
      { title: '风险提示', key: 'chkContent' },
      { title: '更新时间', key: 'updtDate' }
    ]
  }

  const fieldChangeTable = {
    name: 'fieldChange',
    api: 'fieldChange',
    title: '采集源库的字段变更提醒(T-1日结构与T日结构对比)',
    cols: 12,
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
      {
        title: '备注变化',
        key: 'comment_change',
        value: (item) => {
          const oldC = item.oldColComment
          const newC = item.newColComment
          if (!oldC && !newC) return ''
          return `${oldC ?? '—'} → ${newC ?? '—'}`
        }
      },
      { title: '变更时间', key: 'changeAt' }
    ]
  }

  const smsDetailTable = {
    name: 'smsDetail',
    api: '/smsDetail',
    title: '短信发送情况',
    cols: 12,
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

  const allItems = [
    accomplishListTable,
    specialTaskTable,
    rejectTaskTable,
    sysRiskTable,
    fieldChangeTable,
    smsDetailTable
  ]

  const getData = async () => {
    try {
      const [accomplishList, specialTask, rejectTask, sysRisk, fieldChange, smsDetail] =
        await Promise.all([
          monitorService.fetchAccomplishList(),
          monitorService.fetchSpecialTask(),
          monitorService.fetchRejectTask(),
          monitorService.sysRisks(),
          monitorService.fieldsChanges(),
          monitorService.smsDetail()
        ])
      data.value.accomplishList = accomplishList
      data.value.specialTask = specialTask
      data.value.rejectTask = rejectTask
      data.value.sysRisk = sysRisk
      data.value.fieldChange = fieldChange
      data.value.smsDetail = smsDetail
    } catch (error) {
      console.error('Error fetching data:', error)
    }
  }

  onMounted(() => {
    getData()
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
