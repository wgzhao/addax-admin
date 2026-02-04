import Requests from '@/utils/requests'

export interface UserNotification {
  id: number
  username: string
  title: string
  content?: string
  type?: string
  status: 'UNREAD' | 'READ'
  refType?: string
  refId?: string
  createdAt?: string
  readAt?: string
}

class NotificationService {
  prefix = '/user-notifications'

  list(status: 'ALL' | 'UNREAD' | 'READ' = 'ALL', limit = 20): Promise<UserNotification[]> {
    return Requests.get(this.prefix, { status, limit }) as unknown as Promise<UserNotification[]>
  }

  unreadCount(): Promise<{ count: number }> {
    return Requests.get(`${this.prefix}/unread-count`) as unknown as Promise<{ count: number }>
  }

  markRead(id: number): Promise<{ success: boolean }> {
    return Requests.post(`${this.prefix}/${id}/read`, {}) as unknown as Promise<{ success: boolean }>
  }

  markAllRead(): Promise<{ success: boolean; count: number }> {
    return Requests.post(`${this.prefix}/read-all`, {}) as unknown as Promise<{
      success: boolean
      count: number
    }>
  }
}

export default new NotificationService()
