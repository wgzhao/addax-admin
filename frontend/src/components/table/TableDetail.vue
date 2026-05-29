<template>
  <v-card prepend-icon="mdi-table" title="采集表详情" class="table-detail-card" density="comfortable">
    <v-form fast-fail @submit.prevent="saveOds" ref="formRef" tag="form" class="table-detail-form">
      <v-card-text class="pb-2">
        <v-row dense class="section-grid">
          <v-col cols="12">
            <v-sheet class="form-section" rounded="lg" border>
              <div class="section-header">
                <v-icon size="18" color="primary">mdi-database</v-icon>
                <span>基础信息</span>
              </div>
              <v-divider />
              <v-row dense class="section-body">
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">源系统</div>
                    <v-text-field
                      variant="outlined"
                      density="compact"
                      v-model="table.name"
                      placeholder="请输入源系统"
                      hide-details="auto"
                    ></v-text-field>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">源库</div>
                    <v-text-field
                      variant="outlined"
                      density="compact"
                      v-model="table.sourceDb"
                      placeholder="请输入源库"
                      hide-details="auto"
                    ></v-text-field>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">源表</div>
                    <v-text-field
                      variant="outlined"
                      density="compact"
                      v-model="table.sourceTable"
                      placeholder="请输入源表"
                      hide-details="auto"
                    >
                      <template #append>
                        <v-tooltip bottom>
                          <template #activator="{ props }">
                            <v-icon v-bind="props" color="info" size="small" @click="showPlaceholderInfoDialog">
                              mdi-information-outline
                            </v-icon>
                          </template>
                        </v-tooltip>
                      </template>
                    </v-text-field>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">目标端</div>
                    <v-select
                      variant="outlined"
                      density="compact"
                      v-model="table.targetId"
                      :items="targetOptions"
                      item-title="label"
                      item-value="value"
                      placeholder="请选择目标端"
                      clearable
                      hide-details="auto"
                    ></v-select>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">目标库</div>
                    <v-text-field
                      variant="outlined"
                      density="compact"
                      v-model="table.targetDb"
                      placeholder="请输入目标库"
                      hide-details="auto"
                    ></v-text-field>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">目标表</div>
                    <v-text-field
                      variant="outlined"
                      density="compact"
                      v-model="table.targetTable"
                      placeholder="请输入目标表"
                      hide-details="auto"
                    ></v-text-field>
                  </div>
                </v-col>
                <v-col cols="12">
                  <div class="field-stack">
                    <div class="field-label">采集状态</div>
                    <v-select
                      variant="outlined"
                      density="compact"
                      v-model="table.status"
                      :items="statusOptions"
                      item-title="label"
                      item-value="value"
                      placeholder="请选择采集状态"
                      hide-details="auto"
                    ></v-select>
                  </div>
                </v-col>
              </v-row>
            </v-sheet>
          </v-col>

          <v-col cols="12">
            <v-expansion-panels variant="accordion" class="advanced-panel">
              <v-expansion-panel>
                <v-expansion-panel-title class="text-body-2 font-weight-medium">高级参数</v-expansion-panel-title>
                <v-expansion-panel-text>
                  <v-sheet class="form-section" rounded="lg" border>
                    <div class="section-header">
                      <v-icon size="18" color="primary">mdi-tune-vertical</v-icon>
                      <span>高级配置</span>
                    </div>
                    <v-divider />
                    <v-row dense class="section-body">
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">过滤规则</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.filter"
                            placeholder="可选，例如 dt='${biz_date_dash}'"
                            hide-details="auto"
                          >
                            <template #append>
                              <v-tooltip location="bottom">
                                <template #activator="{ props }">
                                  <v-icon v-bind="props" size="small" color="info" @click="showFilterRuleInfoDialog">
                                    mdi-information-outline
                                  </v-icon>
                                </template>
                                <span>查看过滤规则说明</span>
                              </v-tooltip>
                            </template>
                          </v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">分区字段</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.partName"
                            placeholder="例如 dt"
                            hide-details="auto"
                          ></v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">分区格式</div>
                          <v-select
                            variant="outlined"
                            density="compact"
                            v-model="table.partFormat"
                            :items="PARTITION_FORMATS"
                            placeholder="请选择分区格式"
                            hide-details="auto"
                          ></v-select>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">存储格式</div>
                          <v-select
                            variant="outlined"
                            density="compact"
                            v-model="table.storageFormat"
                            :items="storageOptions"
                            placeholder="请选择存储格式"
                            hide-details="auto"
                          ></v-select>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">压缩格式</div>
                          <v-select
                            variant="outlined"
                            density="compact"
                            v-model="table.compressFormat"
                            :items="compressFormats"
                            placeholder="请选择压缩格式"
                            hide-details="auto"
                          ></v-select>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">写入模式</div>
                          <v-select
                            variant="outlined"
                            density="compact"
                            v-model="table.writeMode"
                            :items="writeModeOptions"
                            item-title="label"
                            item-value="value"
                            placeholder="请选择写入模式"
                            hide-details="auto"
                          ></v-select>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack field-stack--textarea">
                          <div class="field-label">读取插件配置(JSON)</div>
                          <v-textarea
                            variant="outlined"
                            density="compact"
                            v-model="readerPluginConfigText"
                            placeholder='例如: {"fetchSize": 50000}'
                            :rules="[rules.jsonObjectOrEmpty]"
                            rows="4"
                            auto-grow
                            hide-details="auto"
                          ></v-textarea>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack field-stack--textarea">
                          <div class="field-label">写入插件配置(JSON)</div>
                          <v-textarea
                            variant="outlined"
                            density="compact"
                            v-model="writerPluginConfigText"
                            placeholder='例如: {"writeMode": "append"}'
                            :rules="[rules.jsonObjectOrEmpty]"
                            rows="4"
                            auto-grow
                            hide-details="auto"
                          ></v-textarea>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">切分字段</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.splitPk"
                            placeholder="为空则自动获取"
                            hide-details="auto"
                          ></v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">切分策略</div>
                          <v-switch
                            v-model="table.autoPk"
                            inset
                            label="自动获取切分字段"
                            :color="table.autoPk ? 'primary' : undefined"
                            density="compact"
                            hide-details
                            class="mt-0"
                          />
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack field-stack--hinted">
                          <div class="field-label">最大运行时(s)</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.maxRuntime"
                            placeholder="不填默认 2000 秒"
                            hint="不填表示默认 2000 秒"
                            persistent-hint
                          ></v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">调度时间(HH:mm)</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.startAt"
                            placeholder="为空则继承数据源"
                            hint="为空=继承数据源；例如 02:30"
                            persistent-hint
                            clearable
                          >
                            <template #append-inner>
                              <v-tooltip location="bottom">
                                <template #activator="{ props }">
                                  <v-icon v-bind="props" size="small" color="info">mdi-information-outline</v-icon>
                                </template>
                                <span>继承值：{{ inheritedStartAt || '-' }}</span>
                              </v-tooltip>
                            </template>
                          </v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">剩余次数</div>
                          <v-text-field
                            variant="outlined"
                            density="compact"
                            v-model="table.retryCnt"
                            placeholder="请输入剩余次数"
                            :rules="[rules.nonNegative]"
                            hide-details="auto"
                          ></v-text-field>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="read-item">
                          <div class="field-label">最近采集开始时间</div>
                          <div class="read-value">{{ table.startTime || '-' }}</div>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="read-item">
                          <div class="field-label">最近采集结束时间</div>
                          <div class="read-value">{{ table.endTime || '-' }}</div>
                        </div>
                      </v-col>
                      <v-col cols="12">
                        <div class="field-stack">
                          <div class="field-label">备注</div>
                          <v-textarea
                            variant="outlined"
                            density="compact"
                            v-model="table.remark"
                            placeholder="可选备注"
                            rows="2"
                            hide-details="auto"
                          ></v-textarea>
                        </div>
                      </v-col>
                    </v-row>
                  </v-sheet>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
          </v-col>
        </v-row>
      </v-card-text>

      <v-card-actions class="action-bar">
        <v-btn
          variant="text"
          color="primary"
          prepend-icon="mdi-history"
          @click="openChangeHistory"
        >
          变更历史
        </v-btn>
        <v-spacer />
        <v-btn color="primary" @click="saveOds">保存</v-btn>
        <v-btn variant="plain" @click="emit('closeDialog')">关闭</v-btn>
      </v-card-actions>
      <v-dialog v-model="showPlaceholderInfo" max-width="800">
        <v-card>
          <v-card-title>源表动态命名说明</v-card-title>
          <v-card-text>
            <p>系统支持在源表名中使用占位符来拼接运行时变量，例如日期。示例：<strong>t_${biz_date_dash}</strong>，实际采集时会解析为 <strong>t_2026-01-27</strong>。</p>
            <p>当前支持的变量如下：</p>
            <v-list dense>
              <v-list-item v-for="v in placeholderVars" :key="v.name">
                <v-list-item-title class="d-flex" style="gap:12px; align-items:center; justify-content:flex-start;">
                  <strong style="width:150px; flex:0 0 200px; display:inline-block; text-align:right;">{{ v.name }}</strong>
                  <span class="text--secondary" style="text-align:left">{{ v.desc }}</span>
                </v-list-item-title>
              </v-list-item>
            </v-list>
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
            <div class="filter-rule-list">
              <div class="filter-rule-item">
                <div class="filter-rule-item__title"><strong>1=1</strong></div>
                <div class="filter-rule-item__desc">不添加任何过滤条件，适合作为默认值。</div>
              </div>
              <div class="filter-rule-item">
                <div class="filter-rule-item__title"><strong>字段条件</strong></div>
                <div class="filter-rule-item__desc">
                  按源表字段编写筛选条件，例如 <code>update_time &gt; '${biz_datetime_dash}'</code>。条件中可以直接复用“源表”右侧说明里的内置变量。
                </div>
              </div>
              <div class="filter-rule-item">
                <div class="filter-rule-item__title"><strong>特殊规则 <code>__max__&lt;field&gt;</code></strong></div>
                <div class="filter-rule-item__desc">
                  用于增量采集。系统会读取上一次采集时 <code>&lt;field&gt;</code> 的最大值 <code>m</code>，并自动转换为 <code>&lt;field&gt; &gt; m</code>。建议 <code>&lt;field&gt;</code> 选择数值型、单调递增的字段，通常优先使用主键。
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
      <v-dialog v-model="showChangeHistory" max-width="980" scrollable>
        <v-card>
          <v-card-title class="history-title">
            <v-icon size="20" color="primary">mdi-history</v-icon>
            <span>变更历史</span>
          </v-card-title>
          <v-card-text>
            <v-text-field
              v-model="changeFieldFilter"
              variant="outlined"
              density="compact"
              clearable
              prepend-inner-icon="mdi-filter-outline"
              placeholder="按字段筛选，例如 filter"
              hide-details
              class="mb-3"
              @keyup.enter="reloadChangeHistory"
              @click:clear="clearChangeFieldFilter"
            />
            <v-table density="compact" class="history-table">
              <thead>
                <tr>
                  <th>变更时间</th>
                  <th>操作者</th>
                  <th>字段</th>
                  <th>旧值</th>
                  <th>新值</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="changeLogLoading">
                  <td colspan="5" class="history-empty">加载中...</td>
                </tr>
                <tr v-else-if="changeLogs.length === 0">
                  <td colspan="5" class="history-empty">暂无记录</td>
                </tr>
                <template v-else>
                  <tr v-for="log in changeLogs" :key="log.id">
                    <td class="history-time">{{ log.changedAt || '-' }}</td>
                    <td>{{ log.changedBy || '-' }}</td>
                    <td>
                      <div class="history-fields">
                        <v-chip
                          v-for="field in normalizeChangedFields(log.changedFields)"
                          :key="`${log.id}-${field}`"
                          size="small"
                          variant="tonal"
                          color="primary"
                        >
                          {{ field }}
                        </v-chip>
                      </div>
                    </td>
                    <td><pre class="history-value">{{ formatChangeValues(log.oldValues) }}</pre></td>
                    <td><pre class="history-value">{{ formatChangeValues(log.newValues) }}</pre></td>
                  </tr>
                </template>
              </tbody>
            </v-table>
            <div class="history-pagination">
              <v-pagination
                v-model="changeLogPage"
                :length="changeLogPageCount"
                density="comfortable"
                size="small"
                @update:model-value="loadChangeHistory"
              />
            </div>
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn variant="text" @click="showChangeHistory = false">关闭</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </v-form>
  </v-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { notify } from '@/stores/notifier';
import tableService from "@/service/table-service";
import targetService from "@/service/target-service";
import { VEtlWithSource, EtlTable, EtlTarget, EtlTableChangeLog } from "@/types/database";
import { TABLE_STATUS_OPTIONS, PARTITION_FORMATS, HDFS_COMPRESS_FORMATS } from "@/utils";

const props = defineProps({
  table: {
    type: Object as () => VEtlWithSource,
    required: true,
  }
});

// 使用公共的状态选项
const statusOptions = TABLE_STATUS_OPTIONS;

// storage options as requested
const storageOptions = ['orc', 'parquet', 'text']

// reuse compress formats from constants
const compressFormats = HDFS_COMPRESS_FORMATS || []
const targetOptions = ref<{ label: string; value: number }[]>([])

// 写入模式选项
const writeModeOptions = [
  { label: '覆盖 (overwrite)', value: 'overwrite' },
  { label: '追加 (append)', value: 'append' },
  { label: '无冲突追加 (nonConflict)', value: 'nonConflict' }
]

// validation rules
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
  }
}

// 创建本地的响应式副本用于编辑
const table = ref<VEtlWithSource>({ ...props.table });
const readerPluginConfigText = ref('')
const writerPluginConfigText = ref('')
const showChangeHistory = ref(false)
const changeLogs = ref<EtlTableChangeLog[]>([])
const changeLogLoading = ref(false)
const changeLogPage = ref(1)
const changeLogPageSize = 10
const changeLogTotal = ref(0)
const changeFieldFilter = ref('')

const changeLogPageCount = computed(() => Math.max(1, Math.ceil(changeLogTotal.value / changeLogPageSize)))

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

const normalizeChangedFields = (value: string[] | string) => {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String) : [String(value)]
  } catch {
    return [String(value)]
  }
}

const formatChangeValues = (value: Record<string, unknown> | null | undefined) => {
  if (!value) return '-'
  return JSON.stringify(value, null, 2)
}

const loadChangeHistory = async () => {
  if (!table.value.id) return
  changeLogLoading.value = true
  try {
    const result = await tableService.fetchChangeLogs(
      table.value.id,
      changeLogPage.value - 1,
      changeLogPageSize,
      changeFieldFilter.value || undefined
    )
    changeLogs.value = result.content
    changeLogTotal.value = result.totalElements
  } catch (e) {
    notify(`加载变更历史失败: ${(e as Error).message}`, 'error')
  } finally {
    changeLogLoading.value = false
  }
}

const openChangeHistory = async () => {
  showChangeHistory.value = true
  changeLogPage.value = 1
  await loadChangeHistory()
}

const reloadChangeHistory = async () => {
  changeLogPage.value = 1
  await loadChangeHistory()
}

const clearChangeFieldFilter = async () => {
  changeFieldFilter.value = ''
  await reloadChangeHistory()
}

syncTableData(props.table)

// 表级调度为空时，继承自采集源的调度时间
const inheritedStartAt = computed(() => {
  // 后端 view 字段：sourceStartAt
  const v = (table.value as any)?.sourceStartAt
  if (!v) return ''
  // normalize: HH:mm:ss -> HH:mm
  return typeof v === 'string' && v.length >= 5 ? v.slice(0, 5) : String(v)
})

// 监听props变化，同步更新本地副本
watch(() => props.table, (newTable) => {
  syncTableData(newTable)
}, { deep: true });

// define emit
const emit = defineEmits(["closeDialog", "update:record", "openSchema"]);

// form ref for programmatic validation
const formRef = ref(null)

// dialog visibility for placeholder information
const showPlaceholderInfo = ref(false)

// dialog visibility for filter rule information
const showFilterRuleInfo = ref(false)

// variables supported by backend SystemConfigService#getBizDateValues
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
  { name: 'curr_datetime_0_short', desc: '时间为 0 的当前日期时间(yyyyMMdd000000)' }
]

// 如果用户手动填写了切分字段(splitPk)，自动获取切分字段应被关闭并禁用
watch(
  () => table.value.splitPk,
  (val) => {
    if (val) {
      table.value.autoPk = false
    } else {
      table.value.autoPk = true
    }
  }
)

onMounted(async () => {
  try {
    const targets = await targetService.list(true)
    targetOptions.value = targets
      .filter((t) => t.id !== undefined)
      .map((t: EtlTarget) => ({
        label: `${t.name} (${t.targetType})`,
        value: t.id as number
      }))
  } catch (e) {
    notify('加载目标端列表失败', 'warning')
  }
})

const saveOds = async () => {
  if (formRef.value && typeof formRef.value.validate === 'function') {
    const valid = await formRef.value.validate()
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
  };

  tableService.save(etlTableData as EtlTable)
    .then((updatedRecord) => {
      notify('保存成功', 'success');
      emit('closeDialog');
      emit('update:record', { ...table.value, ...updatedRecord });
    })
    .catch(err => {
      notify('保存失败: ' + err, 'error');
    });
};

const showPlaceholderInfoDialog = () => {
  showPlaceholderInfo.value = true
}

const showFilterRuleInfoDialog = () => {
  showFilterRuleInfo.value = true
};
</script>

<style scoped>
.table-title {
  margin: 0;
  font-weight: 600;
}

.meta {
  color: rgb(var(--v-theme-on-surface-variant));
  font-size: 0.9rem;
}

.table-detail-card {
  background: rgb(var(--v-theme-surface, 255, 255, 255));
  width: min(100%, 920px);
  margin: 0 auto;
  /* Allow the card to fill the scrollable dialog and constrain its own height */
  display: flex;
  flex-direction: column;
  max-height: min(90vh, 900px);
}

.table-detail-form {
  background: rgb(var(--v-theme-surface, 255, 255, 255));
  /* The <form> tag sits between v-card and v-card-text, breaking Vuetify's
     scrollable dialog selector; replicate the same flex chain manually */
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.table-detail-form :deep(.v-card-text) {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.section-grid {
  row-gap: 12px;
}

.form-section {
  background: rgb(var(--v-theme-surface));
  border-color: rgba(var(--v-theme-on-surface), 0.08);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-weight: 600;
  color: rgb(var(--v-theme-on-surface));
}

.section-body {
  padding: 12px 14px 8px;
  max-width: 740px;
  margin: 0 auto;
}

.field-stack {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  column-gap: 16px;
  align-items: center;
}

.field-stack--textarea {
  align-items: start;
}

.field-label {
  font-size: 0.82rem;
  font-weight: 600;
  line-height: 1.2;
  text-align: right;
  white-space: nowrap;
  color: rgb(var(--v-theme-on-surface-variant));
}

.field-stack :deep(.v-input) {
  width: 100%;
}

.field-stack :deep(.v-selection-control) {
  margin-top: 0;
}

.advanced-panel {
  margin-top: 2px;
}

.advanced-panel :deep(.v-expansion-panel) {
  background: rgb(var(--v-theme-surface));
  border: 1px dashed rgba(var(--v-theme-on-surface), 0.18);
}

.advanced-panel :deep(.v-expansion-panel-title) {
  min-height: 42px;
}

.read-item {
  min-height: 46px;
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  column-gap: 16px;
  align-items: center;
  border-radius: 10px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  background: rgb(var(--v-theme-surface));
  padding: 8px 12px;
}

.read-value {
  margin-top: 0;
  color: rgb(var(--v-theme-on-surface));
  word-break: break-word;
}

.filter-rule-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 8px;
}

.filter-rule-item {
  border-radius: 10px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  background: rgb(var(--v-theme-surface));
  padding: 12px 14px;
}

.filter-rule-item__title {
  margin-bottom: 6px;
  font-size: 0.95rem;
  color: rgb(var(--v-theme-on-surface));
}

.filter-rule-item__desc {
  color: rgb(var(--v-theme-on-surface-variant));
  line-height: 1.7;
  white-space: normal;
  word-break: break-word;
}

.filter-rule-item__desc code,
.filter-rule-item__title code {
  white-space: normal;
  word-break: break-word;
}

@media (max-width: 960px) {
  .section-body {
    max-width: 100%;
  }

  .field-stack,
  .read-item {
    grid-template-columns: 90px minmax(0, 1fr);
  }
}

.action-bar {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding-top: 12px;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-table {
  border: 1px solid rgba(var(--v-theme-on-surface), 0.1);
  border-radius: 8px;
}

.history-table th {
  white-space: nowrap;
  font-weight: 600;
}

.history-time {
  min-width: 150px;
  white-space: nowrap;
}

.history-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 140px;
}

.history-value {
  max-width: 280px;
  max-height: 180px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.78rem;
  line-height: 1.45;
  color: rgb(var(--v-theme-on-surface));
}

.history-empty {
  height: 72px;
  text-align: center;
  color: rgb(var(--v-theme-on-surface-variant));
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
