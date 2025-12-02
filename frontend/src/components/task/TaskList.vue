<template>
  <v-row align="center" class="mb-2">
    <v-btn icon @click="refreshTaskStatus" :loading="loading" class="ml-2" title="手动刷新">
      <v-icon>mdi-refresh</v-icon>
    </v-btn>
  </v-row>
  <v-data-table :items="taskStatus" :headers="headers" item-value="id" density="compact" :loading="loading">
    <template v-slot:item.status="{ item }">
      <v-chip :color="getStatusColor(item.status)" size="small" class="font-weight-bold" text-color="white">
        {{ getStatusText(item.status) }}
      </v-chip>
    </template>
    <template v-slot:item.progress="{ item }">
      <v-progress-linear :model-value="parseProgress(item.progress)" height="16" color="primary" striped rounded
        style="min-width: 80px">
        <template #default>
          <span style="font-size: 12px; color: #333">{{ parseProgress(item.progress) }}%</span>
        </template>
      </v-progress-linear>
    </template>
    <template v-slot:item.action="{ item }">
      <v-btn small color="primary" @click="$emit('executeTask', item.id)">采集</v-btn>
      <!-- 可扩展更多任务相关操作按钮 -->
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import taskService from '@/service/task-service';
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import type { DataTableHeader } from 'vuetify'
// const props = defineProps<{ tasks: any[] }>()
// 支持外部传入刷新间隔（单位：秒），默认 3 秒
const props = defineProps<{ refreshInterval?: number }>()

const loading = ref(false)
const headers: DataTableHeader[] = [
  { title: '任务ID', key: 'id', align: 'center', width: '10%' },
  { title: '表名', key: 'tbl', align: 'center', width: '20%' },
  { title: '状态', key: 'status', align: 'center', width: '10%' },
  { title: '采集时间', key: 'start_time', align: 'center', width: '20%' },
  { title: ' 进展', key: 'progress', align: 'center', width: '20%' }
]

// 明确类型，避免 TypeScript 将结果推断为 Map
type TaskItem = {
  id?: string | number
  tbl?: string
  status?: string
  start_time?: string
  progress?: number
  [key: string]: any
}

const taskStatus = ref<TaskItem[]>([])

let localTimer: any = null
let refreshTimer: any = null

const intervalMs = computed(() => {
  const v = props.refreshInterval ?? 3
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? Math.floor(n * 1000) : 3000
})

function refreshTaskStatus() {
  loading.value = true
  taskService.getAllTaskStatus().then((res) => {
    const oldList = taskStatus.value
    const newList = Array.isArray(res) ? (res as TaskItem[]) : []
    const oldMap = new Map<string, { item: TaskItem; idx: number }>()
    oldList.forEach((item, idx) => oldMap.set(String(item.id), { item, idx }))
    // 记录需要追加的新任务
    const toAdd: TaskItem[] = []
    newList.forEach((newItem) => {
      // 规范化新项的 progress 为数字（0-100）
      const normalizedProgress = parseProgress(newItem.progress)
      const normItem: TaskItem = { ...newItem, progress: normalizedProgress }

      const idKey = String(newItem.id)
      const found = oldMap.get(idKey)
      if (found && found.item) {
        // 用后端进度覆盖本地进度（数值）
        found.item.progress = normalizedProgress
        // 也可以更新 status/start_time 等需要同步的字段：
        found.item.status = newItem.status ?? found.item.status
        found.item.start_time = newItem.start_time ?? found.item.start_time
      } else {
        toAdd.push(normItem)
      }
    })
    if (toAdd.length > 0) {
      oldList.splice(oldList.length, 0, ...toAdd)
    }
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function startAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  // 立即执行一次，然后按配置周期刷新
  refreshTaskStatus()
  refreshTimer = setInterval(() => {
    refreshTaskStatus()
  }, intervalMs.value)
}

function startLocalProgress() {
  if (localTimer) clearInterval(localTimer)
  localTimer = setInterval(() => {
    taskStatus.value.forEach(item => {
      // 只对运行中且进度小于100的任务做本地推进
      if (item.status === 'R' && parseProgress(item.progress) < 100) {
        // 每次推进1-2%，可根据需要调整
        let next = parseProgress(item.progress) + Math.floor(Math.random() * 2) + 1
        if (next > 100) next = 100
        item.progress = next
      }
    })
  }, 200)
}

onMounted(() => {
  startAutoRefresh()
  startLocalProgress()
})

// 当外部 prop 改变时重启自动刷新定时器
watch(intervalMs, () => {
  startAutoRefresh()
})


onUnmounted(() => {
  if (localTimer) clearInterval(localTimer)
  if (refreshTimer) clearInterval(refreshTimer)
})

function getStatusColor(status: string) {
  if (status === 'E') return 'red'
  if (status === 'R') return 'blue'
  if (status === 'W') return 'orange'
  return 'grey'
}
function getStatusText(status: string) {
  if (status === 'E') return '错误'
  if (status === 'R') return '运行中'
  if (status === 'W') return '等待'
  return status
}
function parseProgress(progress: any) {
  // 支持字符串或数字，返回 0-100 的整数
  if (progress == null) return 0
  if (typeof progress === 'string') {
    const num = parseInt(progress, 10)
    if (Number.isNaN(num)) return 0
    return Math.max(0, Math.min(100, num))
  }
  if (typeof progress === 'number') return Math.max(0, Math.min(100, Math.floor(progress)))
  return 0
}

</script>

<style scoped></style>
