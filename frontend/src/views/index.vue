<template>
  <div class="min-h-screen relative overflow-x-hidden" style="background: rgb(var(--v-theme-surface))">
    <div class="background-overlay"></div>

    <v-container fluid class="pa-8 relative" style="z-index: 1">
      <!-- Page Heading -->
      <!-- <div class="header-section mb-6">
        <h1 class="dashboard-title">Dashboard</h1>
      </div> -->

      <!-- Stats Cards Row -->
      <v-row class="stats-row" dense>
        <v-col cols="12" xl="3" lg="3" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon size="36" class="absolute top-4 left-4" color="primary">mdi-database-import</v-icon>
            <v-card-title class="text-center text-sm font-semibold leading-relaxed mb-2" style="color: rgb(var(--v-theme-on-surface))">在用采集数据源/所有数据源</v-card-title>
            <v-card-text class="text-center">
              <span class="inline-block text-4xl font-bold leading-tight" style="color: rgb(var(--v-theme-on-surface))">
                <span style="color: rgb(var(--v-theme-primary))">{{ ratios.length }}</span>
                <span class="mx-1.5 font-medium" style="color: rgba(var(--v-theme-on-surface), 0.5)">/</span>
                <span style="color: rgba(var(--v-theme-on-surface), 0.55)">{{ allDbSourceCount }}</span>
              </span>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" xl="3" lg="3" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon size="36" class="absolute top-4 left-4" color="primary">mdi-table</v-icon>
            <v-card-title class="text-center text-sm font-semibold leading-relaxed mb-2" style="color: rgb(var(--v-theme-on-surface))">采集数据表/所有数据表</v-card-title>
            <v-card-text class="text-center">
              <span class="inline-block text-4xl font-bold leading-tight" style="color: rgb(var(--v-theme-on-surface))">
                <span style="color: rgb(var(--v-theme-primary))">{{ tableCount }}</span>
                <span class="mx-1.5 font-medium" style="color: rgba(var(--v-theme-on-surface), 0.5)">/</span>
                <span style="color: rgba(var(--v-theme-on-surface), 0.55)">{{ allTableCount }}</span>
              </span>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" xl="3" lg="3" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon size="36" class="absolute top-4 left-4" color="primary">mdi-database-plus</v-icon>
            <v-card-title class="text-center text-sm font-semibold leading-relaxed mb-2" style="color: rgb(var(--v-theme-on-surface))">昨日数据采集 (GiB)</v-card-title>
            <v-card-text class="text-center">
              <span class="inline-block text-4xl font-bold leading-tight" style="color: rgb(var(--v-theme-on-surface))">{{ lastEtlData }}</span>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" xl="3" lg="3" class="mb-4">
          <v-card class="stat-card pa-4" elevation="0" rounded="lg">
            <v-icon size="36" class="absolute top-4 left-4" color="primary">mdi-database-check</v-icon>
            <v-card-title class="text-center text-sm font-semibold leading-relaxed mb-2" style="color: rgb(var(--v-theme-on-surface))">累计数据采集 (GiB)</v-card-title>
            <v-card-text class="text-center">
              <span class="inline-block text-4xl font-bold leading-tight" style="color: rgb(var(--v-theme-on-surface))">{{ totalEtlData || 0 }}</span>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- Details Row -->
      <v-row class="mt-6">
        <v-col cols="6">
          <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
            <v-card-title class="text-base font-semibold" style="color: rgb(var(--v-theme-on-surface))">项目完成率</v-card-title>
            <v-card-text>
              <div class="flex flex-col gap-1">
                <div class="progress-item" v-for="ratio in ratios" :key="ratio.pct">
                  <div class="flex items-center justify-between gap-2 mb-1">
                    <span class="font-semibold text-sm" style="color: rgb(var(--v-theme-on-surface))">{{ ratio.source_name }}</span>
                    <span class="font-semibold text-xs" style="color: rgba(var(--v-theme-on-surface), 0.7)">{{ ratio.pct }}%</span>
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
                <v-card-title class="text-lg font-semibold" style="color: rgb(var(--v-theme-on-surface))">最近12个月累计数据采集量 (GiB)</v-card-title>
                <v-card-text>
                  <div class="h-[400px]">
                    <LineChart />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="text-base font-semibold" style="color: rgb(var(--v-theme-on-surface))">数据采集耗时分析</v-card-title>
                <v-card-text>
                  <div class="h-[300px]">
                    <L5TEtlTimeBar />
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
              <v-card class="detail-card section-card pa-6" elevation="0" rounded="lg">
                <v-card-title class="text-base font-semibold" style="color: rgb(var(--v-theme-on-surface))">数据采集数量分析(MB)</v-card-title>
                <v-card-text>
                  <div class="h-[300px]">
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
  import { ref, onMounted } from 'vue';
  import LineChart from '@/components/dashboard/LineChart.vue';
  import L5TEtlTimeBar from '@/components/dashboard/L5TEtlTimeBar.vue';
  import L5TEtlDataBar from '@/components/dashboard/L5TEtlDataBar.vue';

  const ratios = ref([]);
  const lastEtlData = ref(0.0);
  const tableCount = ref(0);
  const allTableCount = ref(0);
  const allDbSourceCount = ref(0);
  const totalEtlData = ref(0.0);

  // 使用主题色阶，保证明暗主题下都有清晰区分度
  function getProgressColor(prec: number) {
    if (prec >= 100) return 'rgb(var(--v-theme-success))';
    if (prec >= 85) return 'rgb(var(--v-theme-primary))';
    if (prec >= 65) return 'rgb(var(--v-theme-info))';
    if (prec >= 40) return 'rgb(var(--v-theme-warning))';
    return 'rgb(var(--v-theme-error))';
  }

  function fetchRatio() {
    try {
      request.get('/dashboard/accomplish-ratio').then(res => (ratios.value = res));
      request.get('/dashboard/last-collect-data').then(res => (lastEtlData.value = res));
      request.get('/dashboard/collect-table-count').then(res => (tableCount.value = res));
      request.get('/dashboard/total-collect-data').then(res => {
        totalEtlData.value = res;
      });
      request.get('/dashboard/all-collect-table-count').then(res => {
        allTableCount.value = res;
      });
      request.get('/dashboard/all-collect-source-count').then(res => {
        allDbSourceCount.value = res;
      });
    } catch (error) {
      console.error('Error fetching ratios:', error);
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
  .background-overlay {
    position: absolute;
    inset: 0;
    z-index: 0;
  }

  .v-theme--dark .background-overlay {
    background: radial-gradient(circle, rgba(148, 163, 184, 0.08) 0%, rgba(15, 23, 42, 0.28) 80%);
  }

  .v-theme--light .background-overlay {
    background: radial-gradient(circle, rgba(15, 23, 42, 0.03) 0%, rgba(255, 255, 255, 0.6) 80%);
  }

  .stat-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }

  .stat-card:hover {
    transform: translateY(-5px);
  }

  .section-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .progress-item {
    padding: 6px 10px;
    border-radius: 12px;
    background: rgba(var(--v-theme-on-surface), 0.04);
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
  }
</style>
