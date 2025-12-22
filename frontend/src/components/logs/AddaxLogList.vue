<template>
  <v-card title="采集日志表">
    <template v-slot:text>
        <v-row justify="center" align-content="center">
          <v-col cols="col-3">
            <v-text-field density="compact" v-model="selectedCleanupDate"  placeholder="1989-09-12"
              clearable >
              <template #prepend>
              <span class="me-2">请输入或者选择要清理的日期(yyyy-MM-dd)</span>
            </template>
            </v-text-field>
            </v-col>
          <v-col cols="col-2">
            <v-btn class="mr-2" variant="tonal" @click="quickSelect(1)">保留1个月</v-btn>
            <v-btn class="mr-2" variant="tonal" @click="quickSelect(3)">保留3个月</v-btn>
            <v-btn class="mr-2" variant="tonal" @click="quickSelect(6)">保留半年</v-btn>
            <v-btn class="mr-2" variant="tonal" @click="quickSelect(12)">保留1年</v-btn>
          </v-col>
          <v-col cols="col-1">
            <v-btn color="error" @click="doCleanup">确认清理</v-btn>
          </v-col>
        </v-row>
    </template>
    <v-card-text>
      <v-data-table-server :items-per-page="currPageSize" :items-length="totalItems" item-value="id" :items="items"
        @update:options="loadItems" :headers="headers">
        <template #item.log="{ item }">
          <pre class="truncated-log" @click="openLog(item)" :title="item.log">{{ previewText(item.log) }}</pre>
        </template>
      </v-data-table-server>
    </v-card-text>
  </v-card>
  <div>

    <v-dialog v-model="logDialog" max-width="100%">
      <v-card>
        <v-card-title>日志内容</v-card-title>
        <v-card-text style="white-space:pre-wrap; word-break:break-word; max-height:60vh; overflow:auto;">
          <pre class="full-log">{{ selectedLog }}</pre>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="logDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="cleanupDialog" max-width="520">
      <v-card>
        <v-card-title>清理日志</v-card-title>
        <v-card-text>
          <div style="margin-bottom:8px">请选择要删除早于指定日期的日志（包括该日期之前）：</div>
          <v-text-field v-model="selectedCleanupDate" label="输入日期" placeholder="1989-09-12" readonly clearable
            @click:clear="selectedCleanupDate = ''" />

          <div style="margin-top:12px">快捷选项：</div>
          <div style="display:flex; gap:8px; margin-top:8px; flex-wrap:wrap">
            <v-btn small outlined @click="quickSelect(1)">保留1个月</v-btn>
            <v-btn small outlined @click="quickSelect(3)">保留3个月</v-btn>
            <v-btn small outlined @click="quickSelect(6)">保留半年</v-btn>
            <v-btn small outlined @click="quickSelect(12)">保留1年</v-btn>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="cleanupDialog = false">取消</v-btn>
          <v-btn color="error" @click="doCleanup">确认清理</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import logService from '@/service/log-service'
import { ref, onMounted } from 'vue'
import { notify } from '@/stores/notifier'

const props = defineProps({ table: String, filters: Object })
const items = ref([])
const currPageSize = ref(10)
const totalItems = ref(0)
const loading = ref(false)
const headers = ref([
  { title: 'ID', value: 'id' },
  { title: '表 ID', value: 'tid' },
  { title: '采集时间', value: 'runAt' },
  { title: '业务日期', value: 'runDate' },
  { title: '消息', value: 'log' }
])

const loadItems = ({ page, itemsPerPage }) => {
  loading.value = true
  currPageSize.value = itemsPerPage

  // v-data-table-server page is 1-based, while backend API is 0-based.
  logService.getAddaxLogList(page - 1, itemsPerPage)
    .then((res) => {
      items.value = res.content
      totalItems.value = res.totalElements
      loading.value = false
    })
    .catch((error) => {
      loading.value = false
    })
}


onMounted(() => {
  loadItems({ page: 1, itemsPerPage: currPageSize.value })
})

const logDialog = ref(false)
const selectedLog = ref('')

// cleanup dialog state
const cleanupDialog = ref(false)
const selectedCleanupDate = ref('')
const menu = ref(false)
const today = new Date().toISOString().slice(0, 10)

function openCleanupDialog() {
  // default to 1 month retention
  quickSelect(1)
  cleanupDialog.value = true
}

function quickSelect(months: number) {
  const d = new Date()
  d.setMonth(d.getMonth() - months)
  selectedCleanupDate.value = formatDate(d)
}

function formatDate(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function doCleanup() {
  if (!selectedCleanupDate.value) {
    notify('请选择一个日期', 'warning')
    return
  }

  logService.cleanupAddaxLog(selectedCleanupDate.value)
    .then(() => {
      notify('清理任务已提交，后台开始执行', 'success')
      cleanupDialog.value = false
    })
    .catch((e) => {
      console.error(e)
      notify('提交清理任务失败', 'error')
    })
}

function openLog(item: any) {
  selectedLog.value = item.log || ''
  logDialog.value = true
}

function previewText(text: any, lines = 10) {
  if (!text) return ''
  const str = String(text)
  const parts = str.split(/\r?\n/)
  const take = parts.length <= lines ? parts : parts.slice(-lines)
  const joined = take.join('\n')
  return parts.length <= lines ? joined : '...\n' + joined
}

</script>

<style scoped>
.truncated-log {
  display: block;
  max-width: 70%;
  max-height: 12em;
  white-space: pre-wrap;
  overflow: hidden;
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, "Roboto Mono", "Helvetica Neue", monospace;
  font-size: 13px;
}

.full-log {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, "Roboto Mono", "Helvetica Neue", monospace;
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
