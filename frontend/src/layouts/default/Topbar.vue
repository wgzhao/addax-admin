<template>
  <v-app-bar app color="primary" dark>
    <template v-slot:default>
      <v-app-bar-title>统一采集管理系统</v-app-bar-title>
      <template v-for="item in urls">
        <v-menu v-if="item.children">
          <template v-slot:activator="{ props }">
            <v-btn v-bind="props" flat>{{ item.title }}</v-btn>
          </template>
          <v-list density="compact" nav v-for="(child, index) in item.children">
            <v-list-item :key="index" :to="{ path: child.path }" class="py-1" style="min-height: 20px">
              <v-list-item-title>{{ child.title }}</v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <div v-else>
          <v-btn flat :to="{ path: item.path }">
            {{ item.title }}
          </v-btn>
        </div>
      </template>
      <v-menu v-if="authStore.currentUserName" offset-y>
        <template v-slot:activator="{ props }">
          <v-btn v-bind="props" flat>{{ authStore.currentUserName }}</v-btn>
        </template>
        <v-list>
          <v-list-item @click="logout">
            <v-list-item-title>注销</v-list-item-title>
          </v-list-item>
          <v-list-item @click="$router.push('/personal-settings')">
            <v-list-item-title>个人设置</v-list-item-title>
          </v-list-item>
          <v-list-item v-if="username === 'admin'">
            <v-list-item-title>系统设置</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
      <v-btn v-if="!authStore.isLoggedIn" @click="goLogin">Login</v-btn>
      <!-- 深色/浅色模式切换按钮 -->
      <v-btn icon @click="toggleTheme" :title="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'">
        <v-icon>mdi-theme-light-dark</v-icon>
      </v-btn>
    </template>
  </v-app-bar>
  <!-- End of Topbar -->
</template>
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from 'vuetify'
import { useRouter } from 'vue-router'

const router = useRouter()

// const {global} = useTheme();

const authStore = useAuthStore()
// 计算属性绑定用户名
const username = computed(() => authStore.username)

// 定义菜单项类型
interface MenuItem {
  path?: string
  name?: string
  title: string
  children?: {
    path: string
    title: string
  }[]
}

const urls = ref<MenuItem[]>([
  {
    path: '/',
    name: 'Home',
    title: 'Home'
  },
  {
    path: '/monitor',
    name: 'ETL',
    title: '监控与告警'
  },
  // {
  //   path: "/system-info",
  //   title: "系统一览"
  // },
  {
    path: '/source',
    title: '数据源管理'
  },
  {
    path: '/table',
    title: '采集表管理'
  },
  {
    path: '/task',
    title: '采集任务管理'
  },
  {
    title: '系统配置',
    children: [
      {
        path: '/settings/sys-settings',
        title: '系统参数配置'
      },
      {
        path: '/settings/job-settings',
        title: 'HDFS 写入模板配置'
      }
    ]
  },
  // {
  //   path: '/param',
  //   title: '参数管理'
  // }
  // {
  //   path: "/check",
  //   title: "盘后检查"
  // },
])

const theme = useTheme()
const isDarkTheme = computed(() => theme.global.name.value === 'dark')

// 切换主题函数
const toggleTheme = () => {
  theme.change(isDarkTheme.value ? 'light' : 'dark')
  console.log('当前主题切换为：', theme.global.name.value)
}

// 如果需要记住用户选择（localStorage，可选）
watch(isDarkTheme, (newValue) => {
  localStorage.setItem('theme', newValue ? 'dark' : 'light')
})

// 在页面加载时初始化主题（从 localStorage 获取用户的选择）
const savedTheme = localStorage.getItem('theme')
if (savedTheme) {
  theme.change(savedTheme)
}

// Logout function
const logout = () => {
  authStore.logout() // Assuming authStore has a logout method
  console.log('用户已注销')
}

const goLogin = () => {
  router.push('/login')
}
</script>
