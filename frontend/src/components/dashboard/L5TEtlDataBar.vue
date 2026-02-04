<template>
  <div class="chart-wrapper" :class="{ 'dark-mode': isDark }">
    <div v-if="!chartReady">Loading...</div>
    <Bar v-else :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue'
  import requests from '@/utils/requests'
  import { defineAsyncComponent } from 'vue'
  // 动态加载 chart.js 相关（首屏不再打包进主 bundle）
  const Bar = defineAsyncComponent(async () => {
    const [{ Bar }, chart] = await Promise.all([import('vue-chartjs'), import('chart.js')])
    chart.Chart.register(
      chart.Title,
      chart.Tooltip,
      chart.Legend,
      chart.BarElement,
      chart.CategoryScale,
      chart.LinearScale
    )
    return Bar
  })
  import { useTheme } from 'vuetify' // Vuetify 主题钩子

  // ChartJS 注册延迟到异步组件加载时完成

  // Vuetify 主题钩子
  const vuetifyTheme = useTheme()
  const isDark = computed(() => vuetifyTheme.current.value.dark)

  const rawData = ref([])
  const chartReady = ref(false)

  // 动态生成图表数据
  const chartData = computed(() => {
    if (!rawData.value || rawData.value.length === 0) {
      return {
        labels: [],
        datasets: []
      }
    }

    // 收集所有唯一的采集源并按时间排序（最新的在前）

    const colors = [
      '#2563EB', // 蓝
      '#22C55E', // 绿
      '#F59E0B', // 橙
      '#EF4444', // 红
      '#A855F7', // 紫
      '#14B8A6', // 青
      '#F97316', // 深橙
      '#0EA5E9' // 天蓝
    ]

    // 为每个 biz_date 创建一个数据集
    const datasets = rawData.value.map((item, index) => {
      return {
        label: item.biz_date,
        data: item.total_bytes,
        backgroundColor: colors[index % colors.length] + '55',
        borderColor: colors[index % colors.length],
        borderWidth: 1,
        borderRadius: 6,
        borderSkipped: false,
        maxBarThickness: 26
      }
    })

    return {
      labels: rawData.value[0].sources,
      datasets: datasets
    }
  })

  // 图表配置（动态主题）
  const chartOptions = computed(() => ({
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        grid: {
          display: false
        },
        ticks: {
          color: isDark.value ? '#E2E8F0' : '#334155',
          font: { size: 11 }
        }
      },
      y: {
        grid: {
          color: isDark.value ? 'rgba(226, 232, 240, 0.12)' : 'rgba(15, 23, 42, 0.08)'
        },
        ticks: {
          color: isDark.value ? '#E2E8F0' : '#334155',
          beginAtZero: true,
          font: { size: 11 }
        },
        title: {
          display: true,
          text: '采集量 (MB)',
          color: isDark.value ? '#E2E8F0' : '#334155',
          font: { size: 12, weight: 600 }
        }
      }
    },
    plugins: {
      legend: {
        display: true,
        position: 'bottom' as const,
        labels: {
          color: isDark.value ? '#E2E8F0' : '#334155',
          usePointStyle: true,
          boxWidth: 8,
          boxHeight: 8,
          padding: 16,
          font: { size: 11 }
        }
      },
      tooltip: {
        backgroundColor: isDark.value ? 'rgba(0,0,0,0.8)' : 'rgba(255,255,255,0.9)',
        titleColor: isDark.value ? '#F8FAFC' : '#0F172A',
        bodyColor: isDark.value ? '#E2E8F0' : '#334155',
        borderColor: isDark.value ? 'rgba(148,163,184,0.2)' : 'rgba(15,23,42,0.1)',
        borderWidth: 1
      },
      title: {
        display: false,
        text: 'Last 5 Days ETL Runtime by FID',
        color: isDark.value ? '#ffffff' : '#333333',
        font: {
          size: 16
        }
      }
    }
  }))

  const fetchData = async () => {
    try {
      const res = await requests.get('/dashboard/last-5d-collect-data')
      rawData.value = res
      chartReady.value = true
    } catch (err) {
      console.error('Error fetching data:', err)
    }
  }

  onMounted(() => {
    fetchData()
  })
</script>

<style scoped>
  .chart-wrapper {
    position: relative;
    height: 300px;
    width: 100%;
    transition: background-color 0.3s ease;
  }

  .dark-mode {
    background-color: transparent;
  }

  .chart-wrapper:not(.dark-mode) {
    background-color: transparent;
  }
</style>
