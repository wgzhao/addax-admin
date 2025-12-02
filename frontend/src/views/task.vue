<template>
  <v-card flat title="采集任务管理">
    <v-card-text>
      <v-row align="center" class="mb-2" style="gap: 12px">
        <v-btn icon @click="refreshTaskStatus" :loading="loading" class="ml-2" title="手动刷新">
          <v-icon>mdi-refresh</v-icon>
        </v-btn>
        <v-select
          v-model="selectedInterval"
          :items="intervalOptions"
          item-title="label"
          item-value="value"
          dense
          hide-details
          style="width: 180px"
          label="自动刷新间隔"
        ></v-select>
        <v-chip small class="ml-2" :color="autoRefreshActive ? 'green' : 'grey'" text-color="white">
          {{ autoRefreshActive ? '自动刷新已开启' : '自动刷新已停止' }}
        </v-chip>
      </v-row>
      <v-data-table
        :items="taskStatus"
        :headers="headers"
        item-value="id"
        density="compact"
        :loading="loading"
      >
        <template v-slot:item.status="{ item }">
          <v-chip
            :color="getStatusColor(item.status)"
            size="small"
            class="font-weight-bold"
            text-color="white"
          >
            {{ getStatusText(item.status) }}
          </v-chip>
        </template>
        <template v-slot:item.progress="{ item }">
          <v-progress-linear
            :model-value="item.progress"
            height="16"
            color="primary"
            striped
            rounded
            style="min-width: 80px"
          ></v-progress-linear>
        </template>
        <template v-slot:item.action="{ item }">
          <v-btn small color="primary" @click="$emit('executeTask', item.id)">采集</v-btn>
          <!-- 可扩展更多任务相关操作按钮 -->
        </template>
      </v-data-table>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import taskService from '@/service/task-service'
  import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import taskCenter from '@/stores/task-center'

  // 如果需要外部控制间隔，可以使用 prop；页面通常不传，默认 3 秒
  const props = defineProps<{ refreshInterval?: number }>()

  const loading = ref(false)
  const headers: DataTableHeader[] = [
    { title: '任务ID', key: 'id', align: 'center', width: '10%' },
    { title: '表名', key: 'tbl', align: 'center', width: '20%' },
    { title: '状态', key: 'status', align: 'center', width: '10%' },
    { title: '采集时间', key: 'start_time', align: 'center', width: '20%' },
    { title: ' 进展', key: 'progress', align: 'center', width: '20%' }
  ]

  type TaskItem = {
    id?: string | number
    tbl?: string
    status?: string
    start_time?: string
    progress?: number
    [key: string]: any
  }

  const taskStatus = ref<TaskItem[]>([])

  let refreshTimer: any = null
  const autoRefreshActive = ref(false)

  // 支持页面内选择刷新间隔；0 表示不自动刷新
  const intervalOptions = [
    { label: '不刷新', value: 0 },
    { label: '3 秒 (默认)', value: 3 },
    { label: '5 秒', value: 5 },
    { label: '10 秒', value: 10 },
    { label: '15 秒', value: 15 },
    { label: '30 秒', value: 30 },
    { label: '60 秒', value: 60 }
  ]

  const selectedInterval = ref<number>(props.refreshInterval ?? 3)

  const intervalMs = computed(() => {
    const v = selectedInterval.value
    const n = Number(v)
    return Number.isFinite(n) && n > 0 ? Math.floor(n * 1000) : 0
  })

  function refreshTaskStatus() {
    loading.value = true
    taskService
      .getAllTaskStatus()
      .then((res) => {
        // 后端返回的是当前要展示的任务列表（完成的任务可能不再返回），
        // 因此直接用后端数据替换本地列表，剔除不再返回的任务。
        const newList = Array.isArray(res) ? (res as TaskItem[]) : []
        const normalized: TaskItem[] = newList.map((it) => ({
          ...it,
          progress: parseProgress((it as any).progress)
        }))
        taskStatus.value = normalized
        loading.value = false
      })
      .catch(() => {
        loading.value = false
      })
  }

  function startAutoRefresh() {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
    // 如果用户选择不刷新（0），则停止任何自动刷新
    if (!intervalMs.value) {
      autoRefreshActive.value = false
      return
    }
    // 标记为活动并立即刷新一次
    autoRefreshActive.value = true
    refreshTaskStatus()
    refreshTimer = setInterval(() => {
      refreshTaskStatus()
    }, intervalMs.value)
  }

  onMounted(() => {
    startAutoRefresh()
  })

  watch(intervalMs, () => {
    startAutoRefresh()
  })

  // 观察用户选择的间隔，0 表示停止自动刷新
  watch(selectedInterval, (v) => {
    if (!v) {
      if (refreshTimer) {
        clearInterval(refreshTimer)
        refreshTimer = null
      }
    } else {
      startAutoRefresh()
    }
  })

  onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
  })

  // Debug: 记录 progress 变化，帮助定位何时被修改
  watch(
    () => taskStatus.value.map((t) => ({ id: t.id, progress: t.progress })),
    (cur, prev) => {
      cur.forEach((c, idx) => {
        const p = prev[idx]
        if (!p) return
        if (c.progress !== p.progress) {
          console.debug(
            `[task.vue] progress changed for ${c.id}: ${p.progress} -> ${c.progress} @ ${new Date().toISOString()}`
          )
        }
      })
    },
    { deep: true }
  )

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
    if (progress == null) return 0
    if (typeof progress === 'string') {
      const num = parseInt(progress, 10)
      if (Number.isNaN(num)) return 0
      return Math.max(0, Math.min(100, num))
    }
    if (typeof progress === 'number') return Math.max(0, Math.min(100, Math.floor(progress)))
    return 0
  }

  // 任务中心 Store 的任务列表（保留，如果需要在页面其他位置展示）
  const tasks = computed(() => taskCenter.tasks.value)
</script>

<route lang="json">
{
  "meta": {
    "title": "采集任务管理",
    "icon": "mdi-database-cog",
    "requiresAuth": true
  }
}
</route>
