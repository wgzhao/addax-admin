<template>
  <v-card flat class="execution-card">
    <v-card-title class="d-flex align-center">
      <span>执行快照</span>
      <v-spacer />
      <v-btn variant="text" prepend-icon="mdi-refresh" :loading="loading" @click="load">
        刷新
      </v-btn>
    </v-card-title>
    <v-card-text>
      <v-alert v-if="error" type="error" variant="tonal" class="mb-3">{{ error }}</v-alert>
      <v-table v-if="items.length" density="compact">
        <thead>
          <tr>
            <th>执行</th>
            <th>业务日期</th>
            <th>源表快照</th>
            <th>目标表快照</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>作业摘要</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td class="text-no-wrap">#{{ item.id }} / {{ item.attemptNo }}</td>
            <td class="text-no-wrap">{{ item.bizDate }}</td>
            <td>{{ tableLabel(item.sourceDb, item.sourceTable) }}</td>
            <td>{{ tableLabel(item.targetDb, item.targetTable) }}</td>
            <td>
              <v-chip
                size="small"
                :color="statusColor(item.status)"
                :title="item.errorMessage || ''"
              >
                {{ statusLabel(item.status) }}
              </v-chip>
            </td>
            <td class="text-no-wrap">{{ item.startedAt }}</td>
            <td class="text-caption font-monospace" :title="item.jobDigest || ''">
              {{ shortDigest(item.jobDigest) }}
            </td>
          </tr>
        </tbody>
      </v-table>
      <v-alert v-else-if="!loading" type="info" variant="tonal">
        尚无执行快照。新产生的采集执行会自动记录当时的源表、目标表、字段映射和配置摘要。
      </v-alert>
      <div v-if="total > 10" class="d-flex justify-center mt-4">
        <v-pagination
          v-model="page"
          :length="Math.ceil(total / pageSize)"
          :total-visible="5"
          @update:model-value="load"
        />
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import tableService from '@/service/table-service';
  import type { EtlExecution } from '@/types/database';

  const route = useRoute();
  const tid = computed(() => Number((route.params as Record<string, string>).tid));
  const items = ref<EtlExecution[]>([]);
  const total = ref(0);
  const page = ref(1);
  const pageSize = 10;
  const loading = ref(false);
  const error = ref('');

  const tableLabel = (db?: string, table?: string) => [db, table].filter(Boolean).join('.') || '-';
  const shortDigest = (digest?: string | null) =>
    digest ? `${digest.slice(0, 12)}…${digest.slice(-6)}` : '-';
  const statusLabel = (status: string) =>
    ({ running: '执行中', succeeded: '成功', failed: '失败' }[status] || status);
  const statusColor = (status: string) =>
    status === 'succeeded' ? 'success' : status === 'failed' ? 'error' : 'info';

  async function load() {
    if (!tid.value) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await tableService.fetchExecutions(tid.value, page.value - 1, pageSize);
      items.value = result.content;
      total.value = result.totalElements;
    } catch (e) {
      error.value = `加载执行快照失败：${(e as Error)?.message || String(e)}`;
    } finally {
      loading.value = false;
    }
  }

  watch(tid, () => {
    page.value = 1;
    load();
  });
  onMounted(load);
</script>

<style scoped>
  .execution-card {
    margin-top: 8px;
  }

  .font-monospace {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
</style>
