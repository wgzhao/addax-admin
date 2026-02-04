import { ref } from 'vue'
import notificationService, { type UserNotification } from '@/service/notification-service'

const notifications = ref<UserNotification[]>([])
const unreadCount = ref(0)
const loading = ref(false)

async function refreshList(status: 'ALL' | 'UNREAD' | 'READ' = 'ALL', limit = 20) {
  loading.value = true
  try {
    const list = await notificationService.list(status, limit)
    notifications.value = (list || [])
      .map((n) => ({
        ...n,
        title: typeof n.title === 'string' ? n.title.trim() : '',
        content: typeof n.content === 'string' ? n.content.trim() : ''
      }))
      .filter((n) => (n.title && n.title.length > 0) || (n.content && n.content.length > 0))
  } finally {
    loading.value = false
  }
}

async function refreshUnreadCount() {
  const res = await notificationService.unreadCount()
  unreadCount.value = res?.count ?? 0
}

async function markRead(id: number) {
  await notificationService.markRead(id)
  const target = notifications.value.find((n) => n.id === id)
  if (target) target.status = 'READ'
  await refreshUnreadCount()
}

async function markAllRead() {
  await notificationService.markAllRead()
  notifications.value = notifications.value.map((n) => ({ ...n, status: 'READ' }))
  unreadCount.value = 0
}

export default {
  notifications,
  unreadCount,
  loading,
  refreshList,
  refreshUnreadCount,
  markRead,
  markAllRead
}
