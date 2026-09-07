<template>
  <div class="insight-page page-shell">
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header">
          <div>
            <div class="page-title">数据洞察</div>
          </div>
          <v-chip size="small" variant="outlined" color="secondary"
            >近 {{ filters.days }} 天</v-chip
          >
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card toolbar-card filter-card">
      <v-card-text class="ds-card__content">
        <v-row density="comfortable" class="filter-row">
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
            <v-btn color="primary" variant="flat" :loading="loading" @click="loadInsights">
              查询
            </v-btn>
            <v-btn variant="text" :disabled="loading" @click="resetFilters"> 重置 </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card toolbar-card segment-card">
      <v-card-text class="segment-card-body">
        <v-btn-toggle
          v-model="activePanel"
          mandatory
          divided
          class="segment-toggle"
          color="primary"
        >
          <v-btn value="noChange"
            >无变化 <span class="segment-count">{{ filteredByKey.noChange.length }}</span></v-btn
          >
          <v-btn value="lowChange"
            >低变化 <span class="segment-count">{{ filteredByKey.lowChange.length }}</span></v-btn
          >
          <v-btn value="highChange"
            >高变化 <span class="segment-count">{{ filteredByKey.highChange.length }}</span></v-btn
          >
          <v-btn value="timeChange"
            >耗时异常
            <span class="segment-count">{{ filteredByKey.timeChange.length }}</span></v-btn
          >
          <v-btn value="missingCollect"
            >缺采集
            <span class="segment-count">{{ filteredByKey.missingCollect.length }}</span></v-btn
          >
        </v-btn-toggle>
      </v-card-text>
    </v-card>

    <InsightPanelTable
      v-for="panel in panels"
      :key="panel.key"
      v-show="activePanel === panel.key"
      :title="panel.title"
      :headers="panel.headers"
      :sort-by="panel.sortBy"
      :items="filteredByKey[panel.key]"
      :export-name="panel.exportName"
    >
      <template #item.actions="{ item }">
        <v-btn size="small" color="error" variant="text" @click="disableTable(item)">
          禁用采集
        </v-btn>
      </template>
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
    </InsightPanelTable>

    <ConfirmDisableDialog v-model="confirmOpen" :item="confirmItem" @confirmed="loadInsights" />
    <MissingDatesDialog v-model="missingOpen" :item="missingItem" :dates="missingDates" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed, watch } from 'vue';
  import { monitorService } from '@/service/monitor-service';
  import InsightPanelTable from '@/components/insight/InsightPanelTable.vue';
  import ConfirmDisableDialog from '@/components/insight/ConfirmDisableDialog.vue';
  import MissingDatesDialog from '@/components/insight/MissingDatesDialog.vue';
  import {
    createInsightPanels,
    filterByKeyword,
    parseMissingDates,
    compactMissingDates,
    hasMissingDates,
  } from '@/components/insight/insight-panels';
  import type { InsightFilters, InsightPanel } from '@/components/insight/insight-panels';

  const defaultFilters: InsightFilters = {
    days: 15,
    lowRate: 2,
    highRate: 40,
    timeRate: 40,
    keyword: '',
  };

  const filters = ref({ ...defaultFilters });
  const loading = ref(false);
  const activePanel = ref<InsightPanel['key']>('noChange');

  const data = ref<Record<InsightPanel['key'], Array<Map<string, any>>>>({
    noChange: [],
    lowChange: [],
    highChange: [],
    timeChange: [],
    missingCollect: [],
  });

  const panels = computed(() => createInsightPanels(filters.value));

  const filteredByKey = computed(() => ({
    noChange: filterByKeyword(data.value.noChange, filters.value.keyword),
    lowChange: filterByKeyword(data.value.lowChange, filters.value.keyword),
    highChange: filterByKeyword(data.value.highChange, filters.value.keyword),
    timeChange: filterByKeyword(data.value.timeChange, filters.value.keyword),
    missingCollect: filterByKeyword(data.value.missingCollect, filters.value.keyword),
  }));

  const loadInsights = async () => {
    loading.value = true;
    try {
      const params = {
        days: filters.value.days,
      };
      const [noChange, lowChange, highChange, timeChange, missingCollect] = await Promise.all([
        monitorService.insightNoChange(params),
        monitorService.insightLowChange({ ...params, threshold: filters.value.lowRate }),
        monitorService.insightHighChange({ ...params, threshold: filters.value.highRate }),
        monitorService.insightTimeChange({ ...params, threshold: filters.value.timeRate }),
        monitorService.insightMissingCollect(params),
      ]);
      data.value.noChange = noChange ?? [];
      data.value.lowChange = lowChange ?? [];
      data.value.highChange = highChange ?? [];
      data.value.timeChange = timeChange ?? [];
      data.value.missingCollect = missingCollect ?? [];
    } catch (error) {
      console.error('Failed to load insight data:', error);
    } finally {
      loading.value = false;
    }
  };

  const resetFilters = () => {
    filters.value = { ...defaultFilters };
    loadInsights();
  };

  onMounted(() => {
    loadInsights();
  });

  const confirmOpen = ref(false);
  const confirmItem = ref<any>(null);

  // Keep parity with the previous inline dialog: clear the item when closed
  watch(confirmOpen, value => {
    if (!value) confirmItem.value = null;
  });

  const disableTable = (item: any) => {
    if (!item?.tid) return;
    confirmItem.value = item;
    confirmOpen.value = true;
  };

  const missingOpen = ref(false);
  const missingItem = ref<any>(null);
  const missingDates = ref<string[]>([]);

  watch(missingOpen, value => {
    if (!value) {
      missingItem.value = null;
      missingDates.value = [];
    }
  });

  const openMissingDatesDialog = (item: any) => {
    missingItem.value = item;
    missingDates.value = parseMissingDates(item?.missing_dates);
    missingOpen.value = true;
  };
</script>

<route lang="json">
{
  "meta": {
    "title": "数据洞察",
    "icon": "mdi-chart-box-outline",
    "requiresAuth": true,
    "navGroup": "data",
    "navOrder": 10
  }
}
</route>

<style scoped>
  .insight-page {
    min-width: 0;
  }

  .segment-card-body {
    padding: 10px 12px;
  }

  .segment-toggle {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: 6px;
  }

  .segment-count {
    margin-left: 4px;
    opacity: 0.72;
    font-variant-numeric: tabular-nums;
  }

  .filter-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    justify-content: flex-end;
  }

  .filter-row {
    flex-wrap: wrap;
    align-items: center;
    row-gap: 8px;
  }

  .filter-keyword {
    min-width: 220px;
    flex: 1 1 260px;
  }

  .missing-dates-compact {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
  }

  @media (max-width: 960px) {
    .filter-actions {
      width: 100%;
      justify-content: flex-start;
    }

    .filter-keyword {
      min-width: 100%;
    }
  }
</style>
