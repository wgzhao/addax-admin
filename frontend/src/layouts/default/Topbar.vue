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
      <!--
      <v-menu v-if="authStore.isLoggedIn" v-model="notifyMenu" offset-y>
        <template v-slot:activator="{ props }">
          <v-badge
            :content="unreadCountValue"
            :model-value="unreadCountValue > 0"
            color="error"
            overlap
          >
            <v-btn v-bind="props" icon :title="'未读消息: ' + unreadCountValue">
              <v-icon>mdi-bell-outline</v-icon>
            </v-btn>
          </v-badge>
        </template>
        <v-card width="380" class="pa-2">
          <v-row align="center" class="px-2">
            <div class="text-subtitle-1 font-weight-medium">消息中心</div>
            <v-spacer />
            <v-btn
              size="small"
              variant="text"
              :disabled="unreadCountValue === 0"
              @click="markAllRead"
            >
              全部已读
            </v-btn>
          </v-row>
          <v-divider class="my-2" />
          <div v-if="visibleNotifications.length === 0" class="px-2 py-3 text-caption">
            暂无消息
          </div>
          <div v-else class="notification-list">
            <div
              v-for="item in visibleNotifications"
              :key="item.id"
              class="notification-bubble"
              :class="{ unread: item.status === 'UNREAD' }"
              @click="markRead(item)"
            >
              <div class="bubble-title">
                <span>{{ item.title }}</span>
                <span class="bubble-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <div class="bubble-content">{{ item.content || item.title }}</div>
            </div>
          </div>
        </v-card>
      </v-menu>
    -->
      <v-menu v-if="authStore.currentUserName" offset-y>
        <template v-slot:activator="{ props }">
          <v-btn v-bind="props" flat>{{ authStore.currentUserName }}</v-btn>
        </template>
        <v-list>
          <v-list-item @click="$router.push('/change-password')">
            <v-list-item-title>修改密码</v-list-item-title>
          </v-list-item>
          <v-list-item @click="logout">
            <v-list-item-title>注销</v-list-item-title>
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from 'vuetify'
import { useRouter } from 'vue-router'
import notificationCenter from '@/stores/notification-center'

const router = useRouter()

// const {global} = useTheme();

const authStore = useAuthStore()
// const notifyMenu = ref(false)
let notifyTimer: number | null = null

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
    title: '首页'
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
    path: '/sys-settings',
    title: '系统配置'
  },
  {
    path: '/logs',
    title: '日志管理'
  },
  {
    path: '/data-insight',
    title: '数据洞察'
  },
  {
    path: '/dict',
    title: '字典维护'
  }
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
  router.replace('/login')
}

const goLogin = () => {
  router.replace('/login')
}

const refreshNotifications = async () => {
  if (!authStore.isLoggedIn) return
  await notificationCenter.refreshUnreadCount()
}
/*
const loadNotificationList = async () => {
  if (!authStore.isLoggedIn) return
  await notificationCenter.refreshList('ALL', 20)
}

const markRead = async (item: any) => {
  if (item?.status === 'READ') return
  await notificationCenter.markRead(item.id)
}

const markAllRead = async () => {
  await notificationCenter.markAllRead()
}

const formatTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString()}`
}

const unreadCountValue = computed(() => notificationCenter.unreadCount.value ?? 0)

const visibleNotifications = computed(() => {
  return notificationCenter.notifications.value.filter((item: any) => {
    const title = typeof item.title === 'string' ? item.title.trim() : ''
    const content = typeof item.content === 'string' ? item.content.trim() : ''
    return title.length > 0 || content.length > 0
  })
})

watch(
  () => notifyMenu.value,
  (open) => {
    if (open) {
      loadNotificationList()
    }
  }
)
*/
watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      refreshNotifications()
      if (!notifyTimer) {
        notifyTimer = window.setInterval(() => {
          refreshNotifications()
        }, 10000)
      }
    } else if (notifyTimer) {
      window.clearInterval(notifyTimer)
      notifyTimer = null
    }
  }
)

onMounted(() => {
  if (authStore.isLoggedIn) {
    refreshNotifications()
    notifyTimer = window.setInterval(() => {
      refreshNotifications()
    }, 10000)
  }
})

onUnmounted(() => {
  if (notifyTimer) {
    window.clearInterval(notifyTimer)
    notifyTimer = null
  }
})
</script>

<style scoped>
.notification-list {
  max-height: 360px;
  overflow-y: auto;
  padding: 4px 8px 8px;
}

.notification-bubble {
  background: #f5f7ff;
  border: 1px solid #d9e1ff;
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease;
}

.notification-bubble:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
}

.notification-bubble.unread {
  background: #fff7e6;
  border-color: #ffd591;
}

.bubble-title {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.bubble-time {
  font-size: 11px;
  color: #6b7280;
  margin-left: 8px;
}

.bubble-content {
  font-size: 12px;
  line-height: 1.5;
  color: #374151;
}
</style>
