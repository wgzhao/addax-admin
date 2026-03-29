<template>
  <v-container fluid class="pa-6 insight-page">
    <v-card flat class="mb-4 filter-card">
      <v-card-text>
        <v-row dense class="filter-row">
          <v-col cols="auto">
            <v-text-field
              v-model.number="filters.days"
              type="number"
              min="1"
              label="天数"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="auto">
            <v-text-field
              v-model.number="filters.lowRate"
              type="number"
              min="0"
              label="低变化率阈值(%)"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="auto">
            <v-text-field
              v-model.number="filters.highRate"
              type="number"
              min="0"
              label="高变化率阈值(%)"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="auto">
            <v-text-field
              v-model.number="filters.timeRate"
              type="number"
              min="0"
              label="耗时变动率阈值(%)"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="auto" class="filter-keyword">
            <v-text-field
              v-model="filters.keyword"
              label="筛选(源库/表名)"
              density="compact"
              clearable
              hide-details
            />
          </v-col>
          <v-col cols="auto" class="filter-actions">
            <v-btn color="primary" variant="tonal" :loading="loading" @click="loadInsights">
              查询
            </v-btn>
            <v-btn variant="text" :disabled="loading" @click="resetFilters">
              重置
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-row dense class="section-grid">
      <v-col cols="12" md="12">
        <v-card flat class="mb-4 section-card">
          <v-card-text class="section-body">
            <div class="section-header">
              <div class="section-title">{{ noChangeTable.title }}</div>
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  prepend-icon="mdi-download"
                  :disabled="!filteredNoChange.length"
                  @click="exportCsv(noChangeTable.headers, filteredNoChange, 'insight-no-change')"
                >
                  导出
                </v-btn>
              </div>
            </div>
            <v-data-table
              :items="filteredNoChange"
              :headers="noChangeTable.headers"
              density="compact"
              :sort-by="noChangeTable.sortBy"
              class="elevation-1"
              hide-no-data
            >
              <template #item.actions="{ item }">
                <v-btn
                  size="small"
                  color="error"
                  variant="tonal"
                  @click="disableTable(item)"
                >
                  禁用采集
                </v-btn>
              </template>
            </v-data-table>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="section-grid">
      <v-col cols="12" md="12">
        <v-card flat class="mb-4 section-card">
          <v-card-text class="section-body">
            <div class="section-header">
              <div class="section-title">{{ lowChangeTable.title }}</div>
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  prepend-icon="mdi-download"
                  :disabled="!filteredLowChange.length"
                  @click="exportCsv(lowChangeTable.headers, filteredLowChange, 'insight-low-change')"
                >
                  导出
                </v-btn>
              </div>
            </div>
            <v-data-table
              :items="filteredLowChange"
              :headers="lowChangeTable.headers"
              density="compact"
              :sort-by="lowChangeTable.sortBy"
              class="elevation-1"
              hide-no-data
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="section-grid">
      <v-col cols="12" md="12">
        <v-card flat class="mb-4 section-card">
          <v-card-text class="section-body">
            <div class="section-header">
              <div class="section-title">{{ highChangeTable.title }}</div>
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  prepend-icon="mdi-download"
                  :disabled="!filteredHighChange.length"
                  @click="exportCsv(highChangeTable.headers, filteredHighChange, 'insight-high-change')"
                >
                  导出
                </v-btn>
              </div>
            </div>
            <v-data-table
              :items="filteredHighChange"
              :headers="highChangeTable.headers"
              density="compact"
              :sort-by="highChangeTable.sortBy"
              class="elevation-1"
              hide-no-data
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="section-grid">
      <v-col cols="12" md="12">
        <v-card flat class="mb-4 section-card">
          <v-card-text class="section-body">
            <div class="section-header">
              <div class="section-title">{{ timeChangeTable.title }}</div>
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  prepend-icon="mdi-download"
                  :disabled="!filteredTimeChange.length"
                  @click="exportCsv(timeChangeTable.headers, filteredTimeChange, 'insight-time-change')"
                >
                  导出
                </v-btn>
              </div>
            </div>
            <v-data-table
              :items="filteredTimeChange"
              :headers="timeChangeTable.headers"
              density="compact"
              :sort-by="timeChangeTable.sortBy"
              class="elevation-1"
              hide-no-data
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row dense class="section-grid">
      <v-col cols="12" md="12">
        <v-card flat class="mb-4 section-card">
          <v-card-text class="section-body">
            <div class="section-header">
              <div class="section-title">{{ missingCollectTable.title }}</div>
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  prepend-icon="mdi-download"
                  :disabled="!filteredMissingCollect.length"
                  @click="
                    exportCsv(
                      missingCollectTable.headers,
                      filteredMissingCollect,
                      'insight-missing-collect'
                    )
                  "
                >
                  导出
                </v-btn>
              </div>
            </div>
            <v-data-table
              :items="filteredMissingCollect"
              :headers="missingCollectTable.headers"
              density="compact"
              :sort-by="missingCollectTable.sortBy"
              class="elevation-1"
              hide-no-data
            >
              <template #item.missing_dates="{ item }">
                <div class="missing-dates-compact">
                  <span class="text-caption text-medium-emphasis">
                    {{ compactMissingDates(item) }}
                  </span>
                  <v-btn
                    size="x-small"
                    variant="text"
                    color="primary"
                    :disabled="!hasMissingDates(item)"
                    @click="openMissingDatesDialog(item)"
                  >
                    查看明细
                  </v-btn>
                </div>
              </template>
            </v-data-table>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="confirmDialog.open" max-width="520">
      <v-card>
        <v-card-title class="text-subtitle-1 font-weight-medium">确认禁用采集</v-card-title>
        <v-card-text>
          <v-alert type="warning" variant="tonal" border="start" class="mb-4">
            禁用后该表采集状态将置为 X，后续采集将被跳过。
          </v-alert>
          <div>
            目标表：{{ confirmDialog.item?.source_db }}.{{ confirmDialog.item?.source_table }}
          </div>
          <div>表 ID：{{ confirmDialog.item?.tid }}</div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="confirmDialog.loading" @click="closeConfirm">
            取消
          </v-btn>
          <v-btn
            color="error"
            variant="tonal"
            :loading="confirmDialog.loading"
            @click="confirmDisable"
          >
            确认禁用
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="missingDatesDialog.open" max-width="760">
      <v-card>
        <v-card-title class="text-subtitle-1 font-weight-medium">缺失日期明细</v-card-title>
        <v-card-text>
          <div class="mb-3">
            目标表：{{ missingDatesDialog.item?.source_db }}.{{ missingDatesDialog.item?.source_table }}
          </div>
          <div class="mb-3">缺失天数：{{ missingDatesDialog.dates.length }}</div>
          <div class="missing-date-chips">
            <v-chip
              v-for="date in missingDatesDialog.dates"
              :key="date"
              size="small"
              color="warning"
              variant="tonal"
            >
              {{ date }}
            </v-chip>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="closeMissingDatesDialog">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import { monitorService } from '@/service/monitor-service'
  import tableService from '@/service/table-service'
  import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort.mjs'

  const defaultFilters = {
    days: 15,
    lowRate: 2,
    highRate: 40,
    timeRate: 40,
    keyword: ''
  }

  const filters = ref({ ...defaultFilters })
  const loading = ref(false)

  const data = ref({
    noChange: [] as Array<Map<string, any>>,
    lowChange: [] as Array<Map<string, any>>,
    highChange: [] as Array<Map<string, any>>,
    timeChange: [] as Array<Map<string, any>>,
    missingCollect: [] as Array<Map<string, any>>
  })

  const noChangeTable = computed(() => ({
    title: `近 ${filters.value.days} 天内，数据量无变化的表`,
    sortBy: <SortItem[]>[{ key: 'total_recs', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
      { title: '源库', key: 'source_db' },
      { title: '表名', key: 'source_table' },
      { title: '目标库表', key: 'target_table_full', value: (item) => formatTargetTable(item) },
      { title: '记录数', key: 'total_recs', align: 'end' },
      { title: '开始日期', key: 'start_date' },
      { title: '结束日期', key: 'end_date' },
      { title: '天数', key: 'day_count', align: 'end' },
      { title: '操作', key: 'actions', sortable: false, align: 'center' }
    ]
  }))

  const lowChangeTable = computed(() => ({
    title: `近 ${filters.value.days} 天内，数据变化率小于 ${filters.value.lowRate}% 的表`,
    sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'asc' }],
    headers: <DataTableHeader[]>[
      { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
      { title: '源库', key: 'source_db' },
      { title: '表名', key: 'source_table' },
      { title: '目标库表', key: 'target_table_full', value: (item) => formatTargetTable(item) },
      { title: '最小记录数', key: 'min_recs', align: 'end' },
      { title: '最大记录数', key: 'max_recs', align: 'end' },
      {
        title: '变化率',
        key: 'change_rate_pct',
        align: 'end',
        value: (item) => `${item.change_rate_pct ?? 0}%`
      },
      { title: '开始日期', key: 'start_date' },
      { title: '结束日期', key: 'end_date' },
      { title: '天数', key: 'day_count', align: 'end' }
    ]
  }))

  const highChangeTable = computed(() => ({
    title: `近 ${filters.value.days} 天内，数据变化率超过 ${filters.value.highRate}% 的表`,
    sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
      { title: '源库', key: 'source_db' },
      { title: '表名', key: 'source_table' },
      { title: '目标库表', key: 'target_table_full', value: (item) => formatTargetTable(item) },
      { title: '最小记录数', key: 'min_recs', align: 'end' },
      { title: '最大记录数', key: 'max_recs', align: 'end' },
      {
        title: '变化率',
        key: 'change_rate_pct',
        align: 'end',
        value: (item) => `${item.change_rate_pct ?? 0}%`
      },
      { title: '开始日期', key: 'start_date' },
      { title: '结束日期', key: 'end_date' },
      { title: '天数', key: 'day_count', align: 'end' }
    ]
  }))

  const timeChangeTable = computed(() => ({
    title: `近 ${filters.value.days} 天内，采集耗时变动率超过 ${filters.value.timeRate}% 的表`,
    sortBy: <SortItem[]>[{ key: 'change_rate_pct', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
      { title: '源库', key: 'source_db' },
      { title: '表名', key: 'source_table' },
      { title: '目标库表', key: 'target_table_full', value: (item) => formatTargetTable(item) },
      { title: '最小耗时(秒)', key: 'min_secs', align: 'end' },
      { title: '最大耗时(秒)', key: 'max_secs', align: 'end' },
      {
        title: '变动率',
        key: 'change_rate_pct',
        align: 'end',
        value: (item) => `${item.change_rate_pct ?? 0}%`
      },
      { title: '开始日期', key: 'start_date' },
      { title: '结束日期', key: 'end_date' },
      { title: '天数', key: 'day_count', align: 'end' }
    ]
  }))

  const missingCollectTable = computed(() => ({
    title: `近 ${filters.value.days} 天内，缺失采集记录的有效表`,
    sortBy: <SortItem[]>[{ key: 'missing_days', order: 'desc' }],
    headers: <DataTableHeader[]>[
      { title: '表 ID', key: 'tid', align: 'end', width: '64px' },
      { title: '源库', key: 'source_db' },
      { title: '表名', key: 'source_table' },
      { title: '目标库表', key: 'target_table_full', value: (item) => formatTargetTable(item) },
      { title: '应采集天数', key: 'expected_days', align: 'end' },
      { title: '实际采集天数', key: 'actual_days', align: 'end' },
      { title: '缺失天数', key: 'missing_days', align: 'end' },
      { title: '首次缺失日期', key: 'first_missing_date' },
      { title: '最近缺失日期', key: 'last_missing_date' },
      { title: '最近采集日期', key: 'last_collect_date' },
      { title: '缺失日期', key: 'missing_dates', sortable: false, width: '280px' }
    ]
  }))

  const normalizeKeyword = (value: string) => value.trim().toLowerCase()

  const formatTargetTable = (item: any) => {
    const targetDb = String(item?.target_db ?? '').trim()
    const targetTable = String(item?.target_table ?? '').trim()
    if (!targetDb && !targetTable) return ''
    if (!targetDb) return targetTable
    if (!targetTable) return targetDb
    return `${targetDb}.${targetTable}`
  }

  const filterByKeyword = (items: Array<Map<string, any>>) => {
    const keyword = normalizeKeyword(filters.value.keyword || '')
    if (!keyword) return items
    return items.filter((item: any) => {
      const sourceDb = String(item?.source_db ?? '').toLowerCase()
      const sourceTable = String(item?.source_table ?? '').toLowerCase()
      return sourceDb.includes(keyword) || sourceTable.includes(keyword)
    })
  }

  const filteredNoChange = computed(() => filterByKeyword(data.value.noChange))
  const filteredLowChange = computed(() => filterByKeyword(data.value.lowChange))
  const filteredHighChange = computed(() => filterByKeyword(data.value.highChange))
  const filteredTimeChange = computed(() => filterByKeyword(data.value.timeChange))
  const filteredMissingCollect = computed(() => filterByKeyword(data.value.missingCollect))

  const loadInsights = async () => {
    loading.value = true
    try {
      const params = {
        days: filters.value.days
      }
      const [noChange, lowChange, highChange, timeChange, missingCollect] = await Promise.all([
        monitorService.insightNoChange(params),
        monitorService.insightLowChange({ ...params, threshold: filters.value.lowRate }),
        monitorService.insightHighChange({ ...params, threshold: filters.value.highRate }),
        monitorService.insightTimeChange({ ...params, threshold: filters.value.timeRate }),
        monitorService.insightMissingCollect(params)
      ])
      data.value.noChange = noChange ?? []
      data.value.lowChange = lowChange ?? []
      data.value.highChange = highChange ?? []
      data.value.timeChange = timeChange ?? []
      data.value.missingCollect = missingCollect ?? []
    } catch (error) {
      console.error('Failed to load insight data:', error)
    } finally {
      loading.value = false
    }
  }

  const resetFilters = () => {
    filters.value = { ...defaultFilters }
    loadInsights()
  }

  const exportCsv = (headers: DataTableHeader[], items: Array<Map<string, any>>, name: string) => {
    if (!items.length) return
    const exportHeaders = headers.filter((h) => h.key && h.key !== 'actions')
    const titleRow = exportHeaders.map((h) => h.title)
    const rows = items.map((row: any) =>
      exportHeaders
        .map((header: any) => {
          if (typeof header.value === 'function') return header.value(row)
          const key = String(header.key)
          return row?.[key] ?? ''
        })
        .join(',')
    )
    const csv = [titleRow.join(','), ...rows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${name}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  onMounted(() => {
    loadInsights()
  })

  const confirmDialog = ref({
    open: false,
    loading: false,
    item: null as any
  })

  const disableTable = (item: any) => {
    if (!item?.tid) return
    confirmDialog.value.item = item
    confirmDialog.value.open = true
  }

  const closeConfirm = () => {
    confirmDialog.value.open = false
    confirmDialog.value.item = null
  }

  const confirmDisable = async () => {
    const item = confirmDialog.value.item
    if (!item?.tid) return
    confirmDialog.value.loading = true
    try {
      await tableService.batchUpdateStatus({ tids: [item.tid], status: 'X' })
      await loadInsights()
      closeConfirm()
    } catch (error) {
      console.error('Failed to disable table:', error)
    } finally {
      confirmDialog.value.loading = false
    }
  }

  const parseMissingDates = (value: string) =>
    String(value ?? '')
      .split('|')
      .map((d) => d.trim())
      .filter(Boolean)

  const compactMissingDates = (item: any) => {
    const dates = parseMissingDates(item?.missing_dates)
    if (!dates.length) return '-'
    if (dates.length <= 2) return dates.join('、')
    return `${dates[0]} ~ ${dates[dates.length - 1]}（共 ${dates.length} 天）`
  }

  const hasMissingDates = (item: any) => parseMissingDates(item?.missing_dates).length > 0

  const missingDatesDialog = ref({
    open: false,
    item: null as any,
    dates: [] as string[]
  })

  const openMissingDatesDialog = (item: any) => {
    missingDatesDialog.value.item = item
    missingDatesDialog.value.dates = parseMissingDates(item?.missing_dates)
    missingDatesDialog.value.open = true
  }

  const closeMissingDatesDialog = () => {
    missingDatesDialog.value.open = false
    missingDatesDialog.value.item = null
    missingDatesDialog.value.dates = []
  }
</script>

<route lang="json">
{
  "meta": {
    "title": "数据洞察",
    "icon": "mdi-chart-box-outline",
    "requiresAuth": true
  }
}
</route>

<style scoped>
  .insight-page {
    background: rgb(var(--v-theme-surface));
  }

  .section-grid {
    margin-bottom: 8px;
  }

  .section-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    border-radius: 12px;
  }

  .filter-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }


  .section-body {
    padding-top: 8px;
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 4px 0 12px;
    font-weight: 600;
  }

  .section-title {
    font-size: 16px;
  }

  .header-actions {
    display: inline-flex;
    gap: 8px;
  }

  .filter-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .filter-row {
    flex-wrap: nowrap;
    align-items: center;
    overflow-x: auto;
    row-gap: 0;
  }

  .filter-keyword {
    min-width: 220px;
  }

  .missing-dates-compact {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
  }

  .missing-date-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    max-height: 320px;
    overflow: auto;
  }
</style>
