<template>
  <div class="dashboard-container" :class="{ 'dark-mode': isDark }">
    <!-- Dynamic Background -->
    <div class="background-overlay"></div>

    <v-container fluid class="pa-8">
      <!-- Page Heading -->
      <!-- <div class="header-section mb-6">
        <h1 class="dashboard-title">Dashboard</h1>
      </div> -->

      <!-- Stats Cards Row -->
      <v-row class="stats-row">
        <v-col cols="12" xl="4" lg="4" class="mb-4">
          <v-card class="stat-card pa-4" elevation="12" rounded="lg">
            <v-icon class="stat-icon" size="36">mdi-database</v-icon>
            <v-card-title class="stat-title">采集数据源</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ ratios.length }}</span>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" xl="4" lg="4" class="mb-4">
          <v-card class="stat-card pa-4" elevation="12" rounded="lg">
            <v-icon class="stat-icon" size="36">mdi-table</v-icon>
            <v-card-title class="stat-title">采集数据表</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ tableCount }}</span>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" xl="4" lg="4" class="mb-4">
          <v-card class="stat-card pa-4" elevation="12" rounded="lg">
            <v-icon class="stat-icon" size="36">mdi-database-sync</v-icon>
            <v-card-title class="stat-title">昨日数据采集 (GiB)</v-card-title>
            <v-card-text class="text-center">
              <span class="stat-value">{{ lastEtlData }}</span>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- Chart Row -->
      <v-row>
        <v-col cols="12">
          <v-card class="chart-card pa-6" elevation="12" rounded="lg">
            <v-card-title class="chart-title">最近12个月累计数据采集量 (GiB)</v-card-title>
            <v-card-text>
              <div class="chart-container">
                <LineChart />
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- Details Row -->
      <v-row class="mt-6">
        <v-col cols="6">
          <v-card class="detail-card pa-6" elevation="12" rounded="lg">
            <v-card-title class="detail-title">项目完成率</v-card-title>
            <v-card-text>
              <v-list class="progress-list" dense>
                <v-list-item v-for="ratio in ratios" :key="ratio.over_prec_str">
                  <v-progress-linear
                    :model-value="ratio.over_prec_str"
                    :class="ratio.bg_color"
                    height="20"
                    rounded
                    :color="isDark ? 'cyan' : 'blue'"
                    :bg-color="isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'"
                  >
                    <template v-slot:default="{ value }">
                      <span class="progress-text">
                        {{ ratio.source_name }} - {{ ratio.over_prec_str }}
                      </span>
                    </template>
                  </v-progress-linear>
                </v-list-item>
              </v-list>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="6">
          <v-card class="detail-card pa-6" elevation="12" rounded="lg">
            <v-card-title class="detail-title">数据采集分析</v-card-title>
            <v-card-text>
              <div class="bar-chart-container">
                <L5TEtlBar />
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script setup lang="ts">
  import request from '@/utils/requests'
  import { ref, onMounted, computed } from 'vue'
  import LineChart from '@/components/dashboard/LineChart.vue'
  import L5TEtlBar from '@/components/dashboard/L5TEtlBar.vue'
  import { useTheme } from 'vuetify' // Vuetify 主题钩子

  const vuetifyTheme = useTheme()
  const isDark = computed(() => vuetifyTheme.current.value.dark)

  const ratios = ref([])
  const lastEtlData = ref(0.0)
  const tableCount = ref(0)

  function fetchRatio() {
    try {
      request.get('/dashboard/accomplish-ratio').then((res) => (ratios.value = res))
      request.get('/dashboard/last-collect-data').then((res) => (lastEtlData.value = res))
      request.get('/dashboard/collect-table-count').then((res) => (tableCount.value = res))
    } catch (error) {
      console.error('Error fetching ratios:', error)
    }
  }

  onMounted(() => {
    fetchRatio()
  })
</script>
<route lang="json">
{
  "meta": {
    "title": "Home",
    "icon": "mdi-home",
    "requiresAuth": true
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
  }

  .dark-mode {
    background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
  }

  .dashboard-container:not(.dark-mode) {
    background: linear-gradient(135deg, #f5f7fa 0%, #e4e7eb 100%);
  }

  .background-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 0;
  }

  .dark-mode .background-overlay {
    background: radial-gradient(circle, rgba(255, 255, 255, 0.05) 0%, rgba(0, 0, 0, 0.8) 80%);
  }

  .dashboard-container:not(.dark-mode) .background-overlay {
    background: radial-gradient(circle, rgba(0, 0, 0, 0.05) 0%, rgba(255, 255, 255, 0.8) 80%);
  }

  .header-section {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .dashboard-title {
    font-size: 2.5rem;
    font-weight: 700;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .dark-mode .dashboard-title {
    color: #ffffff;
  }

  .dashboard-container:not(.dark-mode) .dashboard-title {
    color: #1a237e;
  }

  .stat-card {
    transition: transform 0.3s ease;
  }

  .dark-mode .stat-card {
    background: linear-gradient(145deg, #1e40af 0%, #1e1e5f 100%);
  }

  .dashboard-container:not(.dark-mode) .stat-card {
    background: linear-gradient(145deg, #42a5f5 0%, #1976d2 100%);
  }

  .stat-card:hover {
    transform: translateY(-5px);
  }

  .stat-icon {
    opacity: 0.8;
    position: absolute;
    top: 16px;
    left: 16px;
  }

  .dark-mode .stat-icon {
    color: #ffffff;
  }

  .dashboard-container:not(.dark-mode) .stat-icon {
    color: #ffffff;
  }

  .stat-title {
    font-size: 1.2rem;
    font-weight: 500;
    text-align: center;
  }

  .dark-mode .stat-title {
    color: #e0f2fe;
  }

  .dashboard-container:not(.dark-mode) .stat-title {
    color: #ffffff;
  }

  .stat-value {
    font-size: 3rem;
    font-weight: 800;
    line-height: 1;
  }

  .dark-mode .stat-value {
    color: #ffffff;
  }

  .dashboard-container:not(.dark-mode) .stat-value {
    color: #ffffff;
  }

  .chart-card {
    backdrop-filter: blur(10px);
  }

  .dark-mode .chart-card {
    background: rgba(30, 64, 175, 0.2);
    border: 1px solid rgba(255, 255, 255, 0.1);
  }

  .dashboard-container:not(.dark-mode) .chart-card {
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid rgba(0, 0, 0, 0.1);
  }

  .chart-title {
    font-size: 1.5rem;
    font-weight: 600;
  }

  .dark-mode .chart-title {
    color: #bae6fd;
  }

  .dashboard-container:not(.dark-mode) .chart-title {
    color: #1976d2;
  }

  .chart-container {
    height: 400px;
  }

  .detail-card {
    backdrop-filter: blur(10px);
  }

  .dark-mode .detail-card {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
  }

  .dashboard-container:not(.dark-mode) .detail-card {
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid rgba(0, 0, 0, 0.1);
  }

  .detail-title {
    font-size: 1.4rem;
    font-weight: 600;
  }

  .dark-mode .detail-title {
    color: #bae6fd;
  }

  .dashboard-container:not(.dark-mode) .detail-title {
    color: #1976d2;
  }

  .progress-list {
    background: transparent;
  }

  .progress-text {
    font-weight: 500;
    padding-left: 8px;
  }

  .dark-mode .progress-text {
    color: #ffffff;
  }

  .dashboard-container:not(.dark-mode) .progress-text {
    color: #333333;
  }

  .bar-chart-container {
    height: 300px;
  }
</style>
