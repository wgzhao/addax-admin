<template>
  <v-form fast-fail @submit.prevent="saveOds" ref="formRef" tag="form" class="table-detail-shell">
    <v-card flat class="ds-card hero-card">
      <v-card-text class="hero-card__content">
        <div class="hero-main">
          <div class="hero-kicker">源表</div>
          <div class="hero-title-row">
            <div class="hero-title">{{ sourceIdentity }}</div>
            <v-chip size="small" :color="statusColor" variant="tonal">{{ statusLabel }}</v-chip>
          </div>
        </div>
        <div class="hero-route">
          <div class="hero-route__label">目标映射</div>
          <div class="hero-route__value">{{ targetIdentity }}</div>
        </div>
      </v-card-text>
    </v-card>

    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-label">源系统</span>
        <strong>{{ sourceSystemLabel }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">目标端</span>
        <strong>{{ targetLabel }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">调度时间</span>
        <strong>{{ scheduleLabel }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">最近运行</span>
        <strong>{{ latestRunLabel }}</strong>
      </div>
    </div>

    <v-row dense class="detail-grid">
      <v-col cols="12" lg="7" class="panel-stack">
        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">基础信息</div>
              </div>
            </div>

            <v-row dense>
              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">源系统</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.name"
                    placeholder="请输入源系统"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">采集状态</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.status"
                    :items="statusOptions"
                    item-title="label"
                    item-value="value"
                    placeholder="请选择采集状态"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">源库</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.sourceDb"
                    placeholder="请输入源库"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">剩余次数</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    type="number"
                    v-model="table.retryCnt"
                    placeholder="请输入剩余次数"
                    :rules="[rules.nonNegative]"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12">
                <div class="field-block">
                  <div class="field-label field-label-row">
                    <span>源表</span>
                    <v-btn size="x-small" variant="text" color="info" prepend-icon="mdi-information-outline" @click="showPlaceholderInfoDialog">
                      动态命名说明
                    </v-btn>
                  </div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.sourceTable"
                    placeholder="请输入源表"
                    hide-details="auto"
                  />
                </div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">目标与调度</div>
              </div>
            </div>

            <v-row dense>
              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">目标端</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.targetId"
                    :items="targetOptions"
                    item-title="label"
                    item-value="value"
                    placeholder="请选择目标端"
                    clearable
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">写入模式</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.writeMode"
                    :items="writeModeOptions"
                    item-title="label"
                    item-value="value"
                    placeholder="请选择写入模式"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">目标库</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.targetDb"
                    placeholder="请输入目标库"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">目标表</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.targetTable"
                    placeholder="请输入目标表"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">调度时间 (HH:mm)</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.startAt"
                    placeholder="为空则继承数据源"
                    clearable
                    persistent-hint
                    :hint="`继承值：${inheritedStartAt || '-'}`"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="6">
                <div class="field-block">
                  <div class="field-label">最大运行时 (s)</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.maxRuntime"
                    placeholder="不填默认 2000 秒"
                    hide-details="auto"
                  />
                </div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">抽取与存储配置</div>
              </div>
            </div>

            <v-row dense>
              <v-col cols="12" md="12">
                <div class="field-block">
                  <div class="field-label field-label-row">
                    <span>过滤规则</span>
                    <v-btn size="x-small" variant="text" color="info" prepend-icon="mdi-information-outline" @click="showFilterRuleInfoDialog">
                      规则说明
                    </v-btn>
                  </div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.filter"
                    placeholder="可选，例如 dt='${biz_date_dash}'"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block">
                  <div class="field-label">分区字段</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.partName"
                    placeholder="例如 dt"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block">
                  <div class="field-label">分区格式</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.partFormat"
                    :items="PARTITION_FORMATS"
                    placeholder="请选择分区格式"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block">
                  <div class="field-label">切分字段</div>
                  <v-text-field
                    variant="outlined"
                    density="comfortable"
                    v-model="table.splitPk"
                    placeholder="为空则自动获取"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block">
                  <div class="field-label">存储格式</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.storageFormat"
                    :items="storageOptions"
                    placeholder="请选择存储格式"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block">
                  <div class="field-label">压缩格式</div>
                  <v-select
                    variant="outlined"
                    density="comfortable"
                    v-model="table.compressFormat"
                    :items="compressFormats"
                    placeholder="请选择压缩格式"
                    hide-details="auto"
                  />
                </div>
              </v-col>

              <v-col cols="12" md="4">
                <div class="field-block field-block--switch">
                  <div class="field-label">切分策略</div>
                  <div class="switch-shell">
                    <v-switch
                      v-model="table.autoPk"
                      inset
                      label="自动获取切分字段"
                      :color="table.autoPk ? 'primary' : undefined"
                      hide-details
                      class="mt-0"
                    />
                  </div>
                </div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" lg="5" class="panel-stack">
        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">插件配置</div>
              </div>
            </div>

            <v-row dense>
              <v-col cols="12">
                <div class="field-block">
                  <div class="field-label">读取插件配置 (JSON)</div>
                  <v-textarea
                    variant="outlined"
                    density="comfortable"
                    v-model="readerPluginConfigText"
                    placeholder='例如: {"fetchSize": 50000}'
                    :rules="[rules.jsonObjectOrEmpty]"
                    rows="5"
                    auto-grow
                    hide-details="auto"
                    class="mono-input"
                  />
                </div>
              </v-col>

              <v-col cols="12">
                <div class="field-block">
                  <div class="field-label">写入插件配置 (JSON)</div>
                  <v-textarea
                    variant="outlined"
                    density="comfortable"
                    v-model="writerPluginConfigText"
                    placeholder='例如: {"writeMode": "append"}'
                    :rules="[rules.jsonObjectOrEmpty]"
                    rows="5"
                    auto-grow
                    hide-details="auto"
                    class="mono-input"
                  />
                </div>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">运行快照</div>
              </div>
            </div>

            <div class="snapshot-grid">
              <div class="snapshot-item">
                <span class="snapshot-label">最近采集开始</span>
                <strong>{{ table.startTime || '-' }}</strong>
              </div>
              <div class="snapshot-item">
                <span class="snapshot-label">最近采集结束</span>
                <strong>{{ table.endTime || '-' }}</strong>
              </div>
              <div class="snapshot-item">
                <span class="snapshot-label">继承调度时间</span>
                <strong>{{ inheritedStartAt || '-' }}</strong>
              </div>
              <div class="snapshot-item">
                <span class="snapshot-label">目标映射</span>
                <strong>{{ targetIdentity }}</strong>
              </div>
            </div>
          </v-card-text>
        </v-card>

        <v-card flat class="ds-card section-card">
          <v-card-text class="section-body">
            <div class="section-head">
              <div>
                <div class="section-title">备注与辅助说明</div>
              </div>
            </div>

            <div class="helper-actions">
              <v-btn variant="tonal" color="info" prepend-icon="mdi-lightbulb-outline" @click="showPlaceholderInfoDialog">
                源表动态命名说明
              </v-btn>
              <v-btn variant="tonal" color="info" prepend-icon="mdi-filter-outline" @click="showFilterRuleInfoDialog">
                过滤规则说明
              </v-btn>
            </div>

            <div class="field-block mt-4">
              <div class="field-label">备注</div>
              <v-textarea
                variant="outlined"
                density="comfortable"
                v-model="table.remark"
                placeholder="可选备注"
                rows="4"
                auto-grow
                hide-details="auto"
              />
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-card flat class="ds-card action-card">
      <v-card-actions class="action-bar">
        <v-spacer />
        <v-btn color="primary" variant="flat" @click="saveOds">保存更改</v-btn>
      </v-card-actions>
    </v-card>

    <v-dialog v-model="showPlaceholderInfo" max-width="800">
      <v-card>
        <v-card-title>源表动态命名说明</v-card-title>
        <v-card-text>
          <p>
            系统支持在源表名中使用占位符来拼接运行时变量，例如日期。示例：<strong>t_${biz_date_dash}</strong>，
            实际采集时会解析为 <strong>t_2026-01-27</strong>。
          </p>
          <p>当前支持的变量如下：</p>
          <div class="guide-list">
            <div v-for="v in placeholderVars" :key="v.name" class="guide-item">
              <div class="guide-item__key">{{ v.name }}</div>
              <div class="guide-item__desc">{{ v.desc }}</div>
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showPlaceholderInfo = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="showFilterRuleInfo" max-width="860">
      <v-card>
        <v-card-title>过滤规则说明</v-card-title>
        <v-card-text>
          <p>过滤规则用于控制每次采集读取哪些数据。未填写时，系统按 <strong>1=1</strong> 处理，也就是不过滤。</p>
          <div class="guide-list">
            <div class="guide-item">
              <div class="guide-item__key">1=1</div>
              <div class="guide-item__desc">不添加任何过滤条件，适合作为默认值。</div>
            </div>
            <div class="guide-item">
              <div class="guide-item__key">字段条件</div>
              <div class="guide-item__desc">
                按源表字段编写筛选条件，例如 <code>update_time &gt; '${biz_datetime_dash}'</code>。条件中可以直接复用内置变量。
              </div>
            </div>
            <div class="guide-item">
              <div class="guide-item__key">__max__&lt;field&gt;</div>
              <div class="guide-item__desc">
                用于增量采集。系统会读取上一次采集时 <code>&lt;field&gt;</code> 的最大值 <code>m</code>，并自动转换为
                <code>&lt;field&gt; &gt; m</code>。建议选择数值型、单调递增的字段，通常优先使用主键。
              </div>
            </div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showFilterRuleInfo = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-form>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { notify } from '@/stores/notifier'
import tableService from '@/service/table-service'
import targetService from '@/service/target-service'
import { VEtlWithSource, EtlTable, EtlTarget } from '@/types/database'
import { TABLE_STATUS_OPTIONS, PARTITION_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils'

const route = useRoute()
const router = useRouter()
const tid = computed(() => Number(route.params.tid))

const statusOptions = TABLE_STATUS_OPTIONS
const storageOptions = ['orc', 'parquet', 'text']
const compressFormats = HDFS_COMPRESS_FORMATS || []
const targetOptions = ref<{ label: string; value: number }[]>([])

const writeModeOptions = [
  { label: '覆盖 (overwrite)', value: 'overwrite' },
  { label: '追加 (append)', value: 'append' },
  { label: '无冲突追加 (nonConflict)', value: 'nonConflict' },
]

const rules = {
  required: (v) => !!v || '此字段为必填项',
  nonNegative: (v) => {
    if (v === null || v === undefined || v === '') return true
    const n = Number(v)
    return (!Number.isNaN(n) && n >= 0) || '必须为非负数'
  },
  dateFormat: (v) => PARTITION_FORMATS.includes(v) || '请选择有效的日期格式',
  jsonObjectOrEmpty: (v) => {
    if (v === null || v === undefined || v === '') return true
    try {
      const parsed = JSON.parse(v)
      return (!!parsed && typeof parsed === 'object' && !Array.isArray(parsed)) || '必须为 JSON 对象'
    } catch {
      return '请输入合法 JSON'
    }
  },
}

const table = ref<VEtlWithSource>({} as VEtlWithSource)
const readerPluginConfigText = ref('')
const writerPluginConfigText = ref('')
const showPlaceholderInfo = ref(false)
const showFilterRuleInfo = ref(false)

const placeholderVars = [
  { name: 'biz_date_short', desc: '业务日期(yyyyMMdd)' },
  { name: 'biz_date_dash', desc: '业务日期(yyyy-MM-dd)' },
  { name: 'biz_year', desc: '业务年份(yyyy)' },
  { name: 'biz_month', desc: '业务月份(MM)' },
  { name: 'biz_short_month', desc: '业务月份(M)' },
  { name: 'biz_day', desc: '业务日(dd)' },
  { name: 'biz_short_day', desc: '业务日(d)' },
  { name: 'biz_ym', desc: '业务年月(yyyyMM)' },
  { name: 'biz_short_ym', desc: '业务年月(yyyyM)' },
  { name: 'biz_datetime_short', desc: '业务日期时间(yyyyMMddHHmmss)' },
  { name: 'biz_datetime_dash', desc: '业务日期时间(yyyy-MM-dd HH:mm:ss)' },
  { name: 'biz_datetime_0_dash', desc: '时间为 0 的业务日期时间(yyyy-MM-dd 00:00:00)' },
  { name: 'biz_datetime_0_short', desc: '时间为 0 的业务日期时间(yyyyMMdd000000)' },
  { name: 'curr_date_short', desc: '当前日期(yyyyMMdd)' },
  { name: 'curr_date_dash', desc: '当前日期(yyyy-MM-dd)' },
  { name: 'curr_datetime_short', desc: '当前日期时间(yyyyMMddHHmmss)' },
  { name: 'curr_datetime_dash', desc: '当前日期时间(yyyy-MM-dd HH:mm:ss)' },
  { name: 'curr_datetime_0_dash', desc: '时间为 0 当前日期时间(yyyy-MM-dd 00:00:00)' },
  { name: 'curr_datetime_0_short', desc: '时间为 0 的当前日期时间(yyyyMMdd000000)' },
]

const inheritedStartAt = computed(() => {
  const v = (table.value as any)?.sourceStartAt
  if (!v) return ''
  return typeof v === 'string' && v.length >= 5 ? v.slice(0, 5) : String(v)
})

const sourceIdentity = computed(() => `${table.value.sourceDb || '未配置库'}.${table.value.sourceTable || '未配置表'}`)
const targetIdentity = computed(() => `${table.value.targetDb || '未配置库'}.${table.value.targetTable || '未配置表'}`)
const sourceSystemLabel = computed(() => {
  if (!table.value.name && !table.value.code) return '未配置'
  return table.value.code ? `${table.value.name || '-'} (${table.value.code})` : table.value.name
})
const targetLabel = computed(() => {
  const matched = targetOptions.value.find((item) => item.value === table.value.targetId)
  if (matched) return matched.label
  if (table.value.targetName) return `${table.value.targetName} (${table.value.targetType || '-'})`
  return '未选择'
})
const scheduleLabel = computed(() => table.value.startAt || inheritedStartAt.value || '未设置')
const latestRunLabel = computed(() => table.value.endTime || table.value.startTime || '-')
const statusLabel = computed(() => {
  const matched = statusOptions.find((item) => item.value === table.value.status)
  return matched?.label || table.value.status || '未设置'
})
const statusColor = computed(() => {
  const statusColorMap = {
    N: 'grey-lighten-1',
    R: 'blue',
    Y: 'success',
    E: 'error',
    X: 'warning',
    W: 'amber',
    U: 'grey-darken-1',
  }
  return statusColorMap[table.value.status] || 'grey'
})

const toJsonText = (value: unknown): string => {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return ''
  }
}

const parseJsonObjectOrNull = (value: string, fieldName: string) => {
  const raw = value.trim()
  if (!raw) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    throw new Error(`${fieldName} 不是合法 JSON`)
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${fieldName} 必须为 JSON 对象`)
  }
  return parsed as Record<string, unknown>
}

const syncTableData = (newTable: VEtlWithSource) => {
  table.value = { ...newTable }
  readerPluginConfigText.value = toJsonText(newTable.readerPluginConfig)
  writerPluginConfigText.value = toJsonText(newTable.writerPluginConfig)
}

onMounted(async () => {
  if (!tid.value) return
  try {
    const details = await tableService.fetchTableDetail(tid.value)
    syncTableData(details)
  } catch (e) {
    notify('加载采集表详情失败: ' + ((e as Error)?.message || String(e)), 'error')
  }

  try {
    const targets = await targetService.list(true)
    targetOptions.value = targets
      .filter((t) => t.id !== undefined)
      .map((t: EtlTarget) => ({
        label: `${t.name} (${t.targetType})`,
        value: t.id as number,
      }))
  } catch {
    notify('加载目标端列表失败', 'warning')
  }
})

const saveOds = async () => {
  if (typeof (formRef?.value as any)?.validate === 'function') {
    const valid = await (formRef?.value as any).validate()
    if (!valid.valid) {
      notify('请修正表单错误', 'error')
      return
    }
  }

  let readerPluginConfig: Record<string, unknown> | null
  let writerPluginConfig: Record<string, unknown> | null
  try {
    readerPluginConfig = parseJsonObjectOrNull(readerPluginConfigText.value, '读取插件配置')
    writerPluginConfig = parseJsonObjectOrNull(writerPluginConfigText.value, '写入插件配置')
  } catch (e) {
    notify((e as Error).message, 'error')
    return
  }

  table.value.readerPluginConfig = readerPluginConfig
  table.value.writerPluginConfig = writerPluginConfig

  const etlTableData: Partial<EtlTable> = {
    id: table.value.id,
    sourceDb: table.value.sourceDb,
    sourceTable: table.value.sourceTable,
    targetDb: table.value.targetDb,
    targetTable: table.value.targetTable,
    partKind: table.value.partKind,
    partName: table.value.partName,
    partFormat: table.value.partFormat,
    splitPk: table.value.splitPk,
    autoPk: table.value.autoPk,
    storageFormat: table.value.storageFormat,
    compressFormat: table.value.compressFormat,
    filter: table.value.filter,
    status: table.value.status,
    kind: table.value.kind,
    retryCnt: table.value.retryCnt,
    startTime: table.value.startTime,
    endTime: table.value.endTime,
    maxRuntime: table.value.maxRuntime,
    sid: table.value.sid,
    duration: table.value.duration,
    writeMode: table.value.writeMode || 'overwrite',
    startAt: table.value.startAt || null,
    targetId: table.value.targetId ?? null,
    readerPluginConfig,
    writerPluginConfig,
    createdAt: table.value.createdAt,
    updatedAt: table.value.updatedAt,
  }

  try {
    const updatedRecord = await tableService.save(etlTableData as EtlTable)
    notify('保存成功', 'success')
    syncTableData({ ...table.value, ...updatedRecord } as VEtlWithSource)
  } catch (err) {
    notify('保存失败: ' + ((err as Error)?.message || String(err)), 'error')
  }
}

const showPlaceholderInfoDialog = () => {
  showPlaceholderInfo.value = true
}

const showFilterRuleInfoDialog = () => {
  showFilterRuleInfo.value = true
}

const formRef = ref(null)
</script>

<style scoped>
.table-detail-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-card__content {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.06), transparent 86%);
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-kicker {
  font-size: 0.76rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-primary), 0.9);
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.hero-title {
  font-size: 1.16rem;
  font-weight: 700;
  color: rgb(var(--v-theme-on-surface));
  word-break: break-word;
}

.hero-subtitle {
  margin-top: 10px;
  max-width: 760px;
  color: rgba(var(--v-theme-on-surface), 0.68);
  line-height: 1.7;
}

.hero-route {
  min-width: 260px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  background: rgba(var(--v-theme-surface), 0.86);
}

.hero-route__label {
  font-size: 0.76rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.56);
}

.hero-route__value {
  margin-top: 8px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  font-size: 0.96rem;
  font-weight: 600;
  color: rgb(var(--v-theme-primary));
  word-break: break-word;
}

.hero-route__meta {
  margin-top: 8px;
  color: rgba(var(--v-theme-on-surface), 0.62);
  line-height: 1.55;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.snapshot-item {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  background:
    linear-gradient(180deg, rgba(var(--v-theme-primary), 0.055), rgba(var(--v-theme-primary), 0.01)),
    rgb(var(--v-theme-surface));
}

.summary-label,
.snapshot-label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.76rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.56);
}

.panel-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-size: 1rem;
  font-weight: 700;
  color: rgb(var(--v-theme-on-surface));
}

.section-subtitle {
  margin-top: 4px;
  color: rgba(var(--v-theme-on-surface), 0.64);
  line-height: 1.65;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block--switch {
  height: 100%;
}

.field-label {
  font-size: 0.84rem;
  font-weight: 600;
  color: rgba(var(--v-theme-on-surface), 0.78);
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.switch-shell {
  display: flex;
  align-items: center;
  min-height: 56px;
  padding: 0 12px;
  border-radius: 14px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  background: rgba(var(--v-theme-on-surface), 0.02);
}

.mono-input :deep(textarea) {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  font-size: 0.84rem;
  line-height: 1.7;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.helper-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.action-card {
  position: sticky;
  bottom: 0;
  z-index: 2;
}

.action-bar {
  padding: 14px 18px;
}

.action-copy {
  color: rgba(var(--v-theme-on-surface), 0.62);
}

.guide-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.guide-item {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr);
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  background: rgba(var(--v-theme-on-surface), 0.02);
}

.guide-item__key {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  font-size: 0.84rem;
  color: rgb(var(--v-theme-primary));
  word-break: break-word;
}

.guide-item__desc {
  color: rgba(var(--v-theme-on-surface), 0.68);
  line-height: 1.65;
}

.guide-item__desc code {
  word-break: break-word;
}

@media (max-width: 1180px) {
  .hero-card__content,
  .summary-grid,
  .snapshot-grid {
    grid-template-columns: 1fr;
  }

  .hero-card__content {
    flex-direction: column;
  }

  .hero-route {
    min-width: 0;
  }
}

@media (max-width: 760px) {
  .section-body,
  .hero-card__content,
  .action-bar {
    padding: 16px;
  }

  .summary-grid,
  .snapshot-grid,
  .guide-item {
    grid-template-columns: 1fr;
  }
}
</style>
