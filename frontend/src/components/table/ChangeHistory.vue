<template>
  <v-card density="comfortable">
    <v-card-text>
      <v-text-field
        v-model="changeFieldFilter"
        variant="outlined"
        clearable
        prepend-inner-icon="mdi-filter-outline"
        placeholder="按字段筛选，例如 filter"
        hide-details
        class="mb-3"
        @keyup.enter="reloadChangeHistory"
        @click:clear="clearChangeFieldFilter"
      />
      <v-table class="history-table">
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
              <td>
                <pre class="history-value">{{ formatChangeValues(log.oldValues) }}</pre>
              </td>
              <td>
                <pre class="history-value">{{ formatChangeValues(log.newValues) }}</pre>
              </td>
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
  </v-card>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue';
  import { useRoute } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import type { EtlTableChangeLog } from '@/types/database';

  const route = useRoute();
  const tid = Number(route.params.tid);

  const changeLogs = ref<EtlTableChangeLog[]>([]);
  const changeLogLoading = ref(false);
  const changeLogPage = ref(1);
  const changeLogPageSize = 10;
  const changeLogTotal = ref(0);
  const changeFieldFilter = ref('');

  const changeLogPageCount = computed(() =>
    Math.max(1, Math.ceil(changeLogTotal.value / changeLogPageSize))
  );

  const normalizeChangedFields = (value: string[] | string) => {
    if (Array.isArray(value)) return value;
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.map(String) : [String(value)];
    } catch {
      return [String(value)];
    }
  };

  const formatChangeValues = (value: Record<string, unknown> | null | undefined) => {
    if (!value) return '-';
    return JSON.stringify(value, null, 2);
  };

  const loadChangeHistory = async () => {
    if (!tid) return;
    changeLogLoading.value = true;
    try {
      const result = await tableService.fetchChangeLogs(
        tid,
        changeLogPage.value - 1,
        changeLogPageSize,
        changeFieldFilter.value || undefined
      );
      changeLogs.value = result.content;
      changeLogTotal.value = result.totalElements;
    } catch (e) {
      notify(`加载变更历史失败: ${(e as Error).message}`, 'error');
    } finally {
      changeLogLoading.value = false;
    }
  };

  const reloadChangeHistory = async () => {
    changeLogPage.value = 1;
    await loadChangeHistory();
  };

  const clearChangeFieldFilter = async () => {
    changeFieldFilter.value = '';
    await reloadChangeHistory();
  };

  onMounted(() => {
    loadChangeHistory();
  });
</script>

<style scoped>
  .change-history-card {
    background: rgb(var(--v-theme-surface));
  }

  .history-table {
    border: 1px solid rgba(var(--v-theme-on-surface), 0.1);
    border-radius: 8px;
  }

  .history-empty {
    height: 72px;
    text-align: center;
    color: rgb(var(--v-theme-on-surface-variant));
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

  .history-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
</style>
