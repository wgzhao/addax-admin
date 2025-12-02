<template>
  <div class="chart-wrapper" :class="{ 'dark-mode': isDark }">
    <Line v-if="chartReady" :data="areaData" :options="areaOptions" />
  </div>
</template>

<script setup lang="ts">
  import request from '@/utils/requests'
  import { ref, computed, onMounted, defineAsyncComponent } from 'vue'
  // 动态组件
  const Line = defineAsyncComponent(async () => {
    const [{ Line }, chart] = await Promise.all([import('vue-chartjs'), import('chart.js')])
    chart.Chart.register(
      chart.CategoryScale,
      chart.LinearScale,
      chart.PointElement,
      chart.LineElement,
      chart.Title,
      chart.Tooltip,
      chart.Legend,
      chart.Filler
    )
    return Line
  })
  import { useTheme } from 'vuetify' // Vuetify 主题钩子

  // 注册行为已在异步加载时执行

  // Vuetify 主题检测
  const vuetifyTheme = useTheme()
  const isDark = computed(() => vuetifyTheme.current.value.dark)

  const last12MonthsEtlData = ref([])
  const chartReady = ref(false)

  // 计算属性：动态生成图表数据
  const areaData = computed(() => ({
    labels: last12MonthsEtlData.value.map((item) => item.month),
    datasets: [
      {
        label: 'Data Collection (GiB)',
        backgroundColor: isDark.value
          ? 'rgba(66, 165, 245, 0.1)' // 暗模式浅蓝填充
          : 'rgba(25, 118, 210, 0.1)', // 亮模式深蓝填充
        borderColor: isDark.value ? '#42a5f5' : '#1976d2', // 动态线条颜色
        pointRadius: 3,
        pointBackgroundColor: isDark.value ? '#42a5f5' : '#1976d2',
        pointBorderColor: isDark.value ? '#42a5f5' : '#1976d2',
        pointHoverRadius: 5,
        pointHoverBackgroundColor: isDark.value ? '#42a5f5' : '#1976d2',
        pointHoverBorderColor: isDark.value ? '#42a5f5' : '#1976d2',
        pointHitRadius: 10,
        pointBorderWidth: 2,
        fill: true,
        data: last12MonthsEtlData.value.map((item) => item.total_gb)
      }
    ]
  }))

  // 图表配置（动态主题）
  const areaOptions = computed(() => ({
    maintainAspectRatio: false,
    layout: {
      padding: {
        left: 10,
        right: 25,
        top: 25,
        bottom: 0
      }
    },
    scales: {
      x: {
        grid: {
          display: false
        },
        ticks: {
          color: isDark.value ? '#ffffff' : '#333333' // 暗模式白色，亮模式深灰
        }
      },
      y: {
        grid: {
          color: isDark.value ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)' // 动态网格线
        },
        ticks: {
          color: isDark.value ? '#ffffff' : '#333333'
        },
        title: {
          display: true,
          text: 'Data (GiB)',
          color: isDark.value ? '#ffffff' : '#333333'
        }
      }
    },
    plugins: {
      legend: {
        display: false, // 显示图例
        labels: {
          color: isDark.value ? '#ffffff' : '#333333'
        }
      },
      tooltip: {
        // 使用官方推荐的 intersect:false + interaction 配置，而不是直接设 mode 避免 TS 类型冲突
        intersect: false,
        backgroundColor: isDark.value ? 'rgba(0,0,0,0.8)' : 'rgba(255,255,255,0.8)',
        titleColor: isDark.value ? '#ffffff' : '#333333',
        bodyColor: isDark.value ? '#ffffff' : '#333333'
      },
      title: {
        display: false,
        text: 'Last 12 Months Data Collection',
        color: isDark.value ? '#ffffff' : '#333333',
        font: {
          size: 16
        }
      }
    }
  }))

  const fetchAccumData = async () => {
    try {
      const res = await request.get('/dashboard/last-12m-collect-data')
      last12MonthsEtlData.value = res
      chartReady.value = true
    } catch (err) {
      console.error('Error fetching data:', err)
    }
  }

  onMounted(() => {
    fetchAccumData()
  })
</script>

<style scoped>
  .chart-wrapper {
    position: relative;
    height: 400px;
    width: 100%;
    transition: background-color 0.3s ease;
  }

  .dark-mode {
    background-color: #1e293b;
    /* 暗模式背景 */
  }

  .chart-wrapper:not(.dark-mode) {
    background-color: #ffffff;
    /* 亮模式背景 */
  }
</style>
