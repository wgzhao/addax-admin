<template>
  <v-container fluid class="pa-6">
    <v-row dense>
      <!-- 数据源完成情况（占用全宽） -->
      <v-col v-for="t in allItems" cols="12" :md="t.cols">
        <v-card flat :title="t.title" class="mb-4">
          <v-card-text>
            <v-data-table
              :items="data.accomplishList"
              :headers="t.headers"
              density="compact"
              :sort-by="t.sortBy"
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
  import { SortItem, SortProps } from 'vuetify/lib/components/VDataTable/composables/sort.mjs'

  const data = ref({
    accomplishList: [] as Array<Map<string, any>>,
    specialTask: [] as Array<Map<string, any>>,
    rejectTask: [] as Array<Map<string, any>>,
    sysRisk: [] as Array<Map<string, any>>,
    fieldChange: [] as Array<Map<string, any>>,
    smsDetail: [] as Array<Map<string, any>>
  })

  const allItems = [
    {
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
            { title: '运行', key: 'run_cnt' },
            { title: '错误', key: 'fail_cnt' },
            { title: '未执行', key: 'no_run_cnt' },
            { title: '未建表', key: 'no_create_table_cnt' }
          ]
        },
        {
          title: 'T-1 日',
          align: 'center',
          value: '',
          children: [
            { title: '开始时间', key: 'y_begin_at' },
            { title: '结束时间', key: 'y_finish_at' },
            { title: '耗时(秒)', key: 'y_take_secs' }
          ]
        },
        {
          title: 'T 日',
          align: 'center',
          value: '',
          children: [
            { title: '开始时间', key: 't_begin_at' },
            { title: '结束时间', key: 't_finish_at' },
            { title: '耗时', key: 't_take_secs' }
          ]
        }
      ]
    },
    {
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
    },
    {
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
    },
    {
      name: 'sysRisk',
      api: 'sysRisk',
      title: '系统风险检测结果',
      cols: 12,
      headers: <DataTableHeader[]>[
        { title: '类别', key: 'chkKind' },
        { title: '名称', key: 'chkName' },
        { title: '风险提示', key: 'chkContent' },
        { title: '更新时间', key: 'updtDate' }
      ]
    },
    {
      name: 'fieldChange',
      api: 'fieldChange',
      title: '采集源库的字段变更提醒(T-1日结构与T日结构对比)',
      cols: 12,
      headers: <DataTableHeader[]>[
        { title: '连接名', key: 'souDbConn' },
        { title: '用户名', key: 'owner' },
        { title: '表名', key: 'tableName' },
        { title: '字段名  ', key: 'columnName' },
        {
          title: '变更详情',
          value: 'changed',
          align: 'center',
          children: [
            {
              title: '类型',
              key: 'dataType',
              value: (item) => `${item.dataTypeLast} -> ${item.dataType}`
            },
            {
              title: '长度',
              key: 'dataLen',
              value: (item) => `${item.dataLengthLast} -> ${item.dataLength}`
            },
            {
              title: '精度',
              key: 'dataPrec',
              value: (item) => `${item.dataPrecisionLast} -> ${item.dataPrecision}`
            },
            {
              title: '小数位',
              key: 'dataScale',
              value: (item) => `${item.dataScaleLast} -> ${item.dataScale}`
            }
          ]
        },
        { title: '现状更新时间', key: 'dwCltDate' },
        { title: '升级前更新时间', key: 'dwCltDateLast' }
      ]
    },
    {
      name: 'smsDetail',
      api: '/smsDetail',
      title: '短信发送情况',
      cols: 12,
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
