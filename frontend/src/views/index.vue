<template>
  <div class="dashboard-container" :class="{ 'dark-mode': isDark }">
    <div class="background-overlay"></div>

    <v-container fluid class="pa-8 dashboard-content">
      <!-- Page Heading -->
      <!-- <div class="header-section mb-6">
        <h1 class="dashboard-title">Dashboard</h1>
      </div> -->

      <v-tabs v-model="tab" color="primary" density="comfortable" class="dashboard-tabs mb-4">
        <v-tab value="overview">总览</v-tab>
        <v-tab value="today">今日运行</v-tab>
        <v-tab value="health">运行健康</v-tab>
        <v-tab value="quality">数据质量</v-tab>
        <v-tab value="sla">耗时与 SLA</v-tab>
      </v-tabs>

      <!-- Why: the window clips overflow, which would stop the heartbeat axis from sticking -->
      <v-tabs-window v-model="tab" class="dashboard-window" :transition="false">
        <v-tabs-window-item value="overview">
          <TodayRunSummary class="mb-4" @open="tab = 'today'" />
          <!-- Stats Cards Row -->
          <v-row class="stats-row" dense>
            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-database-import</v-icon>
                <v-card-title class="stat-title">采集源/所有源</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">
                    <span class="stat-primary">{{ ratios.length }}</span>
                    <span class="stat-separator">/</span>
                    <span class="stat-secondary">{{ allDbSourceCount }}</span>
                  </span>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-table</v-icon>
                <v-card-title class="stat-title">采集表/所有表</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">
                    <span class="stat-primary">{{ tableCount }}</span>
                    <span class="stat-separator">/</span>
                    <span class="stat-secondary">{{ allTableCount }}</span>
                  </span>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-database-plus</v-icon>
                <v-card-title class="stat-title">昨日数据采集</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">
                    {{ lastData.value }}<span class="stat-unit">{{ lastData.unit }}</span>
                  </span>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-database-check</v-icon>
                <v-card-title class="stat-title">累计数据采集</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">
                    {{ totalData.value }}<span class="stat-unit">{{ totalData.unit }}</span>
                  </span>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-timer-outline</v-icon>
                <v-card-title class="stat-title">最近采集总耗时</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">
                    {{ lastTime.value }}<span class="stat-unit">{{ lastTime.unit }}</span>
                  </span>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
              <v-card class="stat-card pa-4" elevation="0" rounded="lg">
                <v-icon class="stat-icon" size="36">mdi-calendar-check</v-icon>
                <v-card-title class="stat-title">累计采集天数</v-card-title>
                <v-card-text class="text-center">
                  <span class="stat-value">{{ totalCollectDays }}</span>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <!-- Details Row -->
          <v-row class="mt-6">
            <v-col cols="12" md="6">
              <v-card class="detail-card section-card pa-6 mb-6" elevation="0" rounded="lg">
                <v-card-title class="detail-title ratio-title">
                  <span>项目完成率</span>
                  <span class="ratio-actions">
                    <span class="ratio-legend">
                      <span class="legend-item">
                        <i class="legend-dot legend-dot--done" />已完成
                      </span>
                      <span class="legend-item">
                        <i class="legend-dot legend-dot--failed" />失败
                      </span>
                      <span class="legend-item">
                        <i class="legend-dot legend-dot--rest" />未完成
                      </span>
                    </span>
                    <v-btn
                      v-if="ratios.length > RATIO_PREVIEW"
                      variant="text"
                      size="small"
                      class="ratio-more"
                      append-icon="mdi-arrow-right"
                      @click="openRatioDialog"
                    >
                      查看全部 {{ ratios.length }}
                    </v-btn>
                  </span>
                </v-card-title>
                <v-card-text>
                  <SourceRatioList :ratios="ratios" :limit="RATIO_PREVIEW" />
                  <p v-if="hiddenRatioCount > 0" class="ratio-footnote">
                    按完成率升序，仅显示最低的 {{ RATIO_PREVIEW }} 个，另有
                    {{ hiddenRatioCount }} 个已折叠
                  </p>
                </v-card-text>
              </v-card>

              <v-card class="chart-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="chart-title">最近12个月累计数据采集量</v-card-title>
                <v-card-text>
                  <div class="chart-container">
                    <LineChart />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="6">
              <TableStatusDonut class="mb-6" />
              <L5TEtlCompareBar mode="time" class="mb-6" />
              <L5TEtlCompareBar mode="data" />
            </v-col>
          </v-row>

          <v-dialog
            v-model="ratioDialog"
            class="ds-dialog-overlay"
            scrim="rgba(2, 6, 23, 0.62)"
            max-width="720"
          >
            <v-card>
              <div class="ds-dialog-head">
                <span class="ds-dialog-icon"><v-icon size="20">mdi-chart-donut</v-icon></span>
                <div class="ds-dialog-heading">
                  <h2 class="ds-dialog-title">采集源完成率</h2>
                  <p class="ds-dialog-subtitle">
                    共 {{ ratios.length }} 个采集源，按完成率升序排列；{{
                      ratioFailedCount
                    }}
                    个有失败，{{ ratioUnfinishedCount }} 个未完成
                  </p>
                </div>
                <v-btn
                  icon="mdi-close"
                  variant="text"
                  size="small"
                  aria-label="关闭"
                  @click="ratioDialog = false"
                />
              </div>
              <v-divider />
              <v-card-text class="ds-dialog-body ratio-dialog-body">
                <div class="ratio-dialog-tools">
                  <v-btn
                    size="small"
                    variant="text"
                    class="ratio-filter"
                    :class="{ 'ratio-filter--on': ratioOnlyUnfinished }"
                    @click="ratioOnlyUnfinished = !ratioOnlyUnfinished"
                  >
                    {{ ratioOnlyUnfinished ? '显示全部' : '仅看未完成' }}
                  </v-btn>
                  <span class="ratio-dialog-count">{{ dialogRatios.length }} 个源</span>
                </div>
                <div class="ratio-dialog-list">
                  <SourceRatioList :ratios="dialogRatios" empty-text="没有未完成的采集源" />
                </div>
              </v-card-text>
              <div class="ds-dialog-actions">
                <span class="ds-dialog-actions__hint">
                  完成率按已采集表数计算，未完成时向下取整，不会显示成 100%
                </span>
                <v-spacer />
                <v-btn variant="text" @click="ratioDialog = false">关闭</v-btn>
              </div>
            </v-card>
          </v-dialog>
        </v-tabs-window-item>

        <v-tabs-window-item value="today">
          <TodayPulse />
        </v-tabs-window-item>

        <v-tabs-window-item value="health">
          <v-row>
            <v-col cols="12" lg="7">
              <FailTrendBar />
            </v-col>
            <v-col cols="12" lg="5">
              <FailTopTable />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <MissingCollectCalendar />
            </v-col>
          </v-row>
        </v-tabs-window-item>

        <v-tabs-window-item value="quality">
          <v-row>
            <v-col cols="12" lg="6">
              <ErrorRateLine />
            </v-col>
            <v-col cols="12" lg="6">
              <RecsBytesDualLine />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <SourceContributionArea />
            </v-col>
          </v-row>
        </v-tabs-window-item>

        <v-tabs-window-item value="sla">
          <v-row>
            <v-col cols="12" lg="6">
              <SlowTableBar />
            </v-col>
            <v-col cols="12" lg="6">
              <TakeSecsHistogram />
            </v-col>
          </v-row>
        </v-tabs-window-item>
      </v-tabs-window>
    </v-container>
  </div>
</template>

<script setup lang="ts">
  import request from '@/utils/requests';
  import { ref, onMounted, computed } from 'vue';
  import LineChart from '@/components/dashboard/LineChart.vue';
  import L5TEtlCompareBar from '@/components/dashboard/L5TEtlCompareBar.vue';
  import TableStatusDonut from '@/components/dashboard/TableStatusDonut.vue';
  import FailTrendBar from '@/components/dashboard/FailTrendBar.vue';
  import FailTopTable from '@/components/dashboard/FailTopTable.vue';
  import MissingCollectCalendar from '@/components/dashboard/MissingCollectCalendar.vue';
  import ErrorRateLine from '@/components/dashboard/ErrorRateLine.vue';
  import RecsBytesDualLine from '@/components/dashboard/RecsBytesDualLine.vue';
  import SourceContributionArea from '@/components/dashboard/SourceContributionArea.vue';
  import SlowTableBar from '@/components/dashboard/SlowTableBar.vue';
  import TodayPulse from '@/components/dashboard/TodayPulse.vue';
  import TodayRunSummary from '@/components/dashboard/TodayRunSummary.vue';
  import SourceRatioList from '@/components/dashboard/SourceRatioList.vue';
  import TakeSecsHistogram from '@/components/dashboard/TakeSecsHistogram.vue';
  import { useTheme } from 'vuetify'; // Vuetify 主题钩子
  import { formatBytes, formatDuration } from '@/utils/format';

  const vuetifyTheme = useTheme();
  const isDark = computed(() => vuetifyTheme.current.value.dark);
  const tab = ref('overview');

  type SourceRatio = {
    source_name: string;
    pct?: number;
    total?: number;
    done?: number;
    failed?: number;
    running?: number;
    pending?: number;
  };

  const ratios = ref<SourceRatio[]>([]);
  const lastDataBytes = ref(0);
  const tableCount = ref(0);
  const allTableCount = ref(0);
  const allDbSourceCount = ref(0);
  const totalDataBytes = ref(0);
  const totalCollectDays = ref(0);
  const lastCollectSecs = ref(0);

  const lastData = computed(() => formatBytes(lastDataBytes.value));
  const totalData = computed(() => formatBytes(totalDataBytes.value));
  const lastTime = computed(() => formatDuration(lastCollectSecs.value));

  // Why: the overview keeps the worst sources visible without letting 37 rows
  // stretch the card past the chart column next to it
  const RATIO_PREVIEW = 8;
  const ratioDialog = ref(false);
  const ratioOnlyUnfinished = ref(false);

  const ratioFailedCount = computed(
    () => ratios.value.filter(ratio => Number(ratio.failed ?? 0) > 0).length
  );
  const ratioUnfinishedCount = computed(
    () => ratios.value.filter(ratio => Number(ratio.done ?? 0) < Number(ratio.total ?? 0)).length
  );
  const hiddenRatioCount = computed(() => Math.max(ratios.value.length - RATIO_PREVIEW, 0));
  const dialogRatios = computed(() =>
    ratioOnlyUnfinished.value
      ? ratios.value.filter(ratio => Number(ratio.done ?? 0) < Number(ratio.total ?? 0))
      : ratios.value
  );

  // Why: reopen always starts from the full list so the header count and the rows agree
  const openRatioDialog = () => {
    ratioOnlyUnfinished.value = false;
    ratioDialog.value = true;
  };

  function fetchRatio() {
    try {
      // Fetch all dashboard stat cards in one request to reduce round trips
      request.get('/dashboard/summary').then(res => {
        ratios.value = res.ratios || [];
        allDbSourceCount.value = res.allDbSourceCount;
        tableCount.value = res.tableCount;
        allTableCount.value = res.allTableCount;
        lastDataBytes.value = res.lastDataBytes;
        totalDataBytes.value = res.totalDataBytes;
        totalCollectDays.value = res.totalDays;
        lastCollectSecs.value = res.totalTimeSecs;
      });
    } catch (error) {
      console.error('Error fetching dashboard summary:', error);
    }
  }

  onMounted(() => {
    fetchRatio();
  });
</script>
<route lang="json">
{
  "meta": {
    "title": "首页",
    "icon": "mdi-home",
    "requiresAuth": true,
    "navOrder": 0
  }
}
</route>
<style scoped>
  .dashboard-container {
    min-height: 100vh;
    width: 100vw;
    position: relative;
    /* Why: clip keeps the horizontal guard without creating a scroll container */
    overflow-x: clip;
    transition: background 0.3s ease;
    background: rgb(var(--v-theme-surface));
  }

  .dashboard-window {
    overflow: visible;
  }

  .dark-mode {
    background: rgb(var(--v-theme-surface));
  }

  .dashboard-container:not(.dark-mode) {
    background: rgb(var(--v-theme-surface));
  }

  .background-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;
  }

  .dashboard-content {
    position: relative;
    z-index: 1;
  }

  .dark-mode .background-overlay {
    background: radial-gradient(circle, rgba(148, 163, 184, 0.08) 0%, rgba(15, 23, 42, 0.28) 80%);
  }

  .dashboard-container:not(.dark-mode) .background-overlay {
    background: radial-gradient(circle, rgba(15, 23, 42, 0.03) 0%, rgba(255, 255, 255, 0.6) 80%);
  }

  .header-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .dashboard-title {
    font-size: 2.5rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
    text-shadow: 0 2px 8px rgba(var(--v-theme-on-surface), 0.12);
  }

  .stat-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  .stat-card:hover {
    transform: translateY(-5px);
  }

  .stat-icon {
    opacity: 0.9;
    position: absolute;
    top: 16px;
    left: 16px;
    color: rgb(var(--v-theme-primary));
  }

  .stat-title {
    font-size: 0.95rem;
    font-weight: 600;
    line-height: 1.5;
    text-align: center;
    margin-bottom: 10px;
    color: rgb(var(--v-theme-on-surface));
  }

  .stat-value {
    display: inline-block;
    font-size: 2.4rem;
    font-weight: 700;
    line-height: 1.24;
    color: rgb(var(--v-theme-on-surface));
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  /* Why: the unit carries the 1024x magnitude, so it stays legible instead of
     fading into the background; hierarchy comes from size and weight only */
  .stat-unit {
    margin-left: 0.18em;
    font-size: 0.55em;
    font-weight: 600;
    color: rgba(var(--v-theme-on-surface), 0.8);
  }

  .stat-primary {
    color: rgb(var(--v-theme-primary));
  }

  .stat-secondary {
    color: rgba(var(--v-theme-on-surface), 0.55);
  }

  .stat-separator {
    margin: 0 6px;
    color: rgba(var(--v-theme-on-surface), 0.5);
    font-weight: 500;
  }

  .section-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .chart-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .chart-container {
    height: 400px;
  }

  .detail-title {
    font-size: 1rem;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .ratio-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .ratio-actions {
    display: inline-flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .ratio-more {
    letter-spacing: 0;
    font-size: 12px;
  }

  .ratio-footnote {
    margin: 10px 0 0;
    color: var(--ds-text-3);
    font-size: 11px;
  }

  .ratio-dialog-body {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .ratio-dialog-tools {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .ratio-filter {
    letter-spacing: 0;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .ratio-filter--on {
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .ratio-dialog-count {
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .ratio-dialog-list {
    max-height: 58vh;
    overflow-y: auto;
    padding-right: 4px;
  }

  .ratio-legend {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    color: var(--ds-text-3);
    font-size: 11px;
    font-weight: 400;
  }

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .legend-dot {
    width: 6px;
    height: 6px;
    border-radius: 2px;
  }

  .legend-dot--done {
    background: rgb(var(--v-theme-success));
  }

  .legend-dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .legend-dot--rest {
    background: rgba(var(--v-theme-on-surface), 0.2);
  }

  .bar-chart-container {
    height: 300px;
  }

  .dashboard-tabs {
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }
</style>
