<template>
  <v-container fluid class="pa-6 cluster-page">
    <!-- header row -->
    <v-row align="center" class="mb-4">
      <v-col>
        <div class="text-h6 font-weight-medium">集群状态</div>
        <div class="text-caption text-medium-emphasis">节点最近心跳超过 30 秒视为离线</div>
      </v-col>
      <v-col cols="auto">
        <v-chip
          :color="status.totalNodes > 0 ? 'success' : 'warning'"
          variant="tonal"
          size="small"
          class="mr-2"
        >
          在线节点：{{ status.totalNodes }}
        </v-chip>
        <v-btn
          variant="tonal"
          size="small"
          :loading="loading"
          prepend-icon="mdi-refresh"
          @click="refresh"
        >刷新</v-btn>
      </v-col>
    </v-row>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable>{{ error }}</v-alert>

    <!-- node cards -->
    <v-row dense>
      <v-col
        v-for="node in status.nodes"
        :key="node.instanceId"
        cols="12"
        sm="6"
        lg="4"
      >
        <v-card
          :color="node.role.includes('MASTER') ? 'primary' : undefined"
          :variant="node.role.includes('MASTER') ? 'tonal' : 'outlined'"
          class="node-card"
        >
          <!-- card title row -->
          <v-card-title class="d-flex align-center gap-2 pb-1">
            <v-icon :color="node.role.includes('MASTER') ? 'primary' : 'secondary'" size="20">
              {{ node.role.includes('MASTER') ? 'mdi-crown' : 'mdi-server' }}
            </v-icon>
            <span class="text-subtitle-1 font-weight-medium text-truncate" :title="node.instanceId">
              {{ node.host }}
            </span>
            <v-spacer />
            <v-chip
              :color="node.role.includes('MASTER') ? 'primary' : 'default'"
              size="x-small"
              variant="elevated"
            >{{ node.role }}</v-chip>
          </v-card-title>

          <v-card-text class="pt-0">
            <!-- concurrency bar -->
            <div class="d-flex justify-space-between text-caption mb-1">
              <span>并发使用</span>
              <span>{{ node.running }} / {{ node.concurrentLimit }}</span>
            </div>
            <v-progress-linear
              :model-value="node.concurrentLimit > 0 ? (node.running / node.concurrentLimit) * 100 : 0"
              :color="slotColor(node)"
              rounded
              height="6"
              class="mb-3"
            />

            <!-- info items -->
            <v-list density="compact" class="node-info-list pa-0">
              <v-list-item class="px-0 py-0">
                <template #prepend>
                  <v-icon size="14" class="mr-1 text-medium-emphasis">mdi-identifier</v-icon>
                </template>
                <v-list-item-subtitle class="text-caption text-truncate" :title="node.instanceId">
                  {{ node.instanceId }}
                </v-list-item-subtitle>
              </v-list-item>

              <v-list-item class="px-0 py-0">
                <template #prepend>
                  <v-icon size="14" class="mr-1 text-medium-emphasis">mdi-weight</v-icon>
                </template>
                <v-list-item-subtitle class="text-caption">
                  权重：{{ node.weight }}
                </v-list-item-subtitle>
              </v-list-item>

              <v-list-item class="px-0 py-0">
                <template #prepend>
                  <v-icon size="14" class="mr-1 text-medium-emphasis">mdi-slot-machine</v-icon>
                </template>
                <v-list-item-subtitle class="text-caption">
                  可用 Slot：{{ node.availableSlots }}
                </v-list-item-subtitle>
              </v-list-item>

              <v-list-item v-if="sourceRunningEntries(node).length > 0" class="px-0 py-0 mt-1">
                <template #prepend>
                  <v-icon size="14" class="mr-1 text-medium-emphasis">mdi-database-sync</v-icon>
                </template>
                <v-list-item-subtitle class="text-caption">
                  各源并发：{{ sourceRunningEntries(node).map(([k, v]) => `#${k}:${v}`).join('  ') }}
                </v-list-item-subtitle>
              </v-list-item>

              <v-list-item class="px-0 py-0">
                <template #prepend>
                  <v-icon size="14" class="mr-1 text-medium-emphasis">mdi-clock-outline</v-icon>
                </template>
                <v-list-item-subtitle class="text-caption">
                  最近心跳：{{ formatLastSeen(node.lastSeen) }}
                </v-list-item-subtitle>
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>
      </v-col>

      <!-- empty state -->
      <v-col v-if="!loading && status.nodes.length === 0" cols="12">
        <v-empty-state
          icon="mdi-server-off"
          title="暂无在线节点"
          text="没有节点上报心跳，请检查服务是否正常运行。"
        />
      </v-col>
    </v-row>

    <div class="text-caption text-medium-emphasis mt-4 text-right">
      上次刷新：{{ lastRefresh }}
    </div>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { clusterService, type ClusterStatus, type WorkerNode } from '@/service/cluster-service'

const loading = ref(false)
const error = ref('')
const lastRefresh = ref('')
let timer: number | null = null

const status = ref<ClusterStatus>({
  nodes: [],
  masterInstanceId: '',
  totalNodes: 0,
  timestamp: ''
})

const refresh = async () => {
  loading.value = true
  error.value = ''
  try {
    status.value = await clusterService.getNodes()
    lastRefresh.value = new Date().toLocaleTimeString()
  } catch (e: any) {
    error.value = e?.message || '获取集群状态失败'
  } finally {
    loading.value = false
  }
}

const slotColor = (node: WorkerNode) => {
  if (node.concurrentLimit === 0) return 'grey'
  const ratio = node.running / node.concurrentLimit
  if (ratio >= 0.9) return 'error'
  if (ratio >= 0.7) return 'warning'
  return 'success'
}

const sourceRunningEntries = (node: WorkerNode): [string, number][] => {
  return Object.entries(node.sourceRunning ?? {}).filter(([, v]) => v > 0)
}

const formatLastSeen = (lastSeen: string | null): string => {
  if (!lastSeen) return '—'
  const d = new Date(lastSeen)
  if (isNaN(d.getTime())) return lastSeen
  const diffSecs = Math.round((Date.now() - d.getTime()) / 1000)
  if (diffSecs < 60) return `${diffSecs}s 前`
  return `${Math.round(diffSecs / 60)}m 前`
}

onMounted(() => {
  refresh()
  timer = window.setInterval(refresh, 15000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.cluster-page {
  background: rgb(var(--v-theme-surface));
}
.node-card {
  transition: box-shadow 0.15s ease;
}
.node-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}
.node-info-list .v-list-item {
  min-height: 24px !important;
}
</style>

<route lang="json">
{
  "meta": {
    "title": "集群状态",
    "icon": "mdi-server-network",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 40
  }
}
</route>
