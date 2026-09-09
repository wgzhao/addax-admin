<template>
  <div class="dashboard-container" :class="{ 'dark-mode': isDark }">
    <div class="background-overlay"></div>

    <v-container fluid class="pa-8 dashboard-content">
      <!-- Page Heading -->
      <!-- <div class="header-section mb-6">
        <h1 class="dashboard-title">Dashboard</h1>
      </div> -->

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
            <v-card-title class="stat-title">昨日数据采集 (GiB)</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ lastEtlData }}</span>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon class="stat-icon" size="36">mdi-database-check</v-icon>
            <v-card-title class="stat-title">累计数据采集 (GiB)</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ totalEtlData || 0 }}</span>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" md="4" xl="2" lg="2" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon class="stat-icon" size="36">mdi-timer-outline</v-icon>
            <v-card-title class="stat-title">最近采集总耗时</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ lastCollectTime }}</span>
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
        <v-col cols="6">
          <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
            <v-card-title class="detail-title">项目完成率</v-card-title>
            <v-card-text>
              <div class="progress-stack">
                <div v-for="ratio in ratios" :key="ratio.pct" class="progress-item">
                  <div class="progress-header">
                    <span class="progress-name">{{ ratio.source_name }}</span>
                    <span class="progress-value">{{ ratio.pct }}%</span>
                  </div>
                  <v-progress-linear
                    :model-value="ratio.pct"
                    bg-color="surface-variant"
                    height="10"
                    rounded
                    :color="getProgressColor(ratio.pct)"
                  />
                </div>
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="6">
          <v-row>
            <v-col cols="12">
              <v-card class="chart-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="chart-title">最近12个月累计数据采集量 (GiB)</v-card-title>
                <v-card-text>
                  <div class="chart-container">
                    <LineChart />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="detail-title">数据采集耗时分析</v-card-title>
                <v-card-text>
                  <div class="bar-chart-container">
                    <L5TEtlTimeBar />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="detail-title">数据采集数量分析(MB)</v-card-title>
                <v-card-text>
                  <div class="bar-chart-container">
                    <L5TEtlDataBar />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script setup lang="ts">
  import request from '@/utils/requests';
  import { ref, onMounted, computed } from 'vue';
  import LineChart from '@/components/dashboard/LineChart.vue';
  import L5TEtlTimeBar from '@/components/dashboard/L5TEtlTimeBar.vue';
  import L5TEtlDataBar from '@/components/dashboard/L5TEtlDataBar.vue';
  import { useTheme } from 'vuetify'; // Vuetify 主题钩子

  const vuetifyTheme = useTheme();
  const isDark = computed(() => vuetifyTheme.current.value.dark);

  const ratios = ref([]);
  const lastEtlData = ref(0.0);
  const tableCount = ref(0);
  const allTableCount = ref(0);
  const allDbSourceCount = ref(0);
  const totalEtlData = ref(0.0);
  const totalCollectDays = ref(0);
  const lastCollectTime = ref('');

  // 使用主题色阶，保证明暗主题下都有清晰区分度
  function getProgressColor(prec: number) {
    if (prec >= 100) return 'rgb(var(--v-theme-success))';
    if (prec >= 85) return 'rgb(var(--v-theme-primary))';
    if (prec >= 65) return 'rgb(var(--v-theme-info))';
    if (prec >= 40) return 'rgb(var(--v-theme-warning))';
    return 'rgb(var(--v-theme-error))';
  }

  // Format total seconds of the last collect cycle into a readable duration
  function formatDuration(totalSecs: number): string {
    if (!totalSecs || totalSecs <= 0) return '0 分钟';
    if (totalSecs < 3600) return `${Math.round(totalSecs / 60)} 分钟`;
    if (totalSecs < 86400) return `${(totalSecs / 3600).toFixed(1)} 小时`;
    return `${(totalSecs / 86400).toFixed(1)} 天`;
  }

  function fetchRatio() {
    try {
      // Fetch all dashboard stat cards in one request to reduce round trips
      request.get('/dashboard/summary').then(res => {
        ratios.value = res.ratios;
        allDbSourceCount.value = res.allDbSourceCount;
        tableCount.value = res.tableCount;
        allTableCount.value = res.allTableCount;
        lastEtlData.value = res.lastDataGb;
        totalEtlData.value = res.totalDataGb;
        totalCollectDays.value = res.totalDays;
        lastCollectTime.value = formatDuration(res.totalTimeSecs);
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
    overflow-x: hidden;
    transition: background 0.3s ease;
    background: rgb(var(--v-theme-surface));
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

  .progress-list {
    background: transparent;
  }

  .progress-stack {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .progress-item {
    padding: 6px 10px;
    border-radius: 12px;
    background: rgba(var(--v-theme-on-surface), 0.04);
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
  }

  .progress-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
    gap: 8px;
  }

  .progress-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.85rem;
  }

  .progress-value {
    font-weight: 600;
    color: rgba(var(--v-theme-on-surface), 0.7);
    font-size: 0.8rem;
  }

  .bar-chart-container {
    height: 300px;
  }
</style>
