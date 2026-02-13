<template>
  <v-card flat title="采集任务管理">
    <v-card-text>
      <v-data-table
        :items="taskStatus"
        :headers="headers"
        item-value="id"
        density="compact"
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
            :model-value="item.displayProgress ?? item.progress"
            :buffer-value="item.progress"
            height="16"
            color="primary"
            striped
            rounded
            style="min-width: 80px"
          ></v-progress-linear>
        </template>
        <template v-slot:item.node_name="{ item }">
          <span>{{ item.node_name || '-' }}</span>
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

  // 如果需要外部控制间隔，可以使用 prop；页面通常不传，默认 3 秒
  const props = defineProps<{ refreshInterval?: number }>()

  const headers: DataTableHeader[] = [
    { title: '任务ID', key: 'id', align: 'center', width: '10%' },
    { title: '表名', key: 'tbl', align: 'center', width: '18%' },
    { title: '状态', key: 'status', align: 'center', width: '10%' },
    { title: '执行节点', key: 'node_name', align: 'center', width: '20%' },
    { title: '采集时间', key: 'start_time', align: 'center', width: '20%' },
    { title: '进展', key: 'progress', align: 'center', width: '18%' }
  ]

  type TaskItem = {
    id?: string | number
    tbl?: string
    status?: string
    node_name?: string
    start_time?: string
    progress?: number
    displayProgress?: number
    [key: string]: any
  }

  const taskStatus = ref<TaskItem[]>([])

  let refreshTimer: any = null
  let progressAnimator: any = null

  const intervalMs = computed(() => {
    const v = props.refreshInterval ?? 3
    const n = Number(v)
    return Number.isFinite(n) && n > 0 ? Math.floor(n * 1000) : 0
  })

  function mergeTaskStatus(newList: TaskItem[]) {
    const existingById = new Map<string, TaskItem>()
    const seenIds = new Set<string>()

    taskStatus.value.forEach((item) => {
      if (item.id == null) return
      existingById.set(String(item.id), item)
    })

    for (const incoming of newList) {
      if (incoming.id == null) continue
      const key = String(incoming.id)
      seenIds.add(key)
      const existing = existingById.get(key)

      if (existing) {
        // 原地更新，避免整表替换导致闪烁
        Object.assign(existing, incoming)
        if (existing.displayProgress == null) {
          existing.displayProgress = incoming.progress ?? 0
        }
      } else {
        taskStatus.value.push({
          ...incoming,
          displayProgress: incoming.progress ?? 0
        })
      }
    }

    // 后端未返回的任务从列表中移除
    for (let i = taskStatus.value.length - 1; i >= 0; i -= 1) {
      const id = taskStatus.value[i]?.id
      if (id == null) {
        taskStatus.value.splice(i, 1)
        continue
      }
      if (!seenIds.has(String(id))) {
        taskStatus.value.splice(i, 1)
      }
    }
  }

  function refreshTaskStatus() {
    taskService
      .getAllTaskStatus()
      .then((res) => {
        const newList = Array.isArray(res) ? (res as TaskItem[]) : []
        const normalized: TaskItem[] = newList.map((it) => ({
          ...it,
          progress: parseProgress((it as any).progress)
        }))
        mergeTaskStatus(normalized)
      })
      .catch(() => {
        // 静默轮询失败时不打断页面交互
      })
  }

  function startAutoRefresh() {
    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
    // 如果用户选择不刷新（0），则停止任何自动刷新
    if (!intervalMs.value) {
      return
    }
    // 标记为活动并立即刷新一次
    refreshTaskStatus()
    refreshTimer = setInterval(() => {
      refreshTaskStatus()
    }, intervalMs.value)
  }

  function startProgressAnimator() {
    if (progressAnimator) {
      clearInterval(progressAnimator)
      progressAnimator = null
    }
    // 低频平滑追赶，减少进度条跳变感
    progressAnimator = setInterval(() => {
      taskStatus.value.forEach((item) => {
        const target = parseProgress(item.progress)
        const current = parseProgress(item.displayProgress)
        if (current === target) return
        if (current > target) {
          item.displayProgress = target
          return
        }
        const diff = target - current
        const step = Math.max(1, Math.ceil(diff * 0.25))
        item.displayProgress = Math.min(target, current + step)
      })
    }, 120)
  }

  onMounted(() => {
    startAutoRefresh()
    startProgressAnimator()
  })

  watch(intervalMs, () => {
    startAutoRefresh()
  })

  onUnmounted(() => {
    if (refreshTimer) clearInterval(refreshTimer)
    if (progressAnimator) clearInterval(progressAnimator)
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
    if (progress == null) return 0
    const num = Number(progress)
    if (Number.isNaN(num)) return 0
    return Math.max(0, Math.min(100, Math.floor(num)))
  }
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
