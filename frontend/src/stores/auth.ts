import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '' as string | null, // 存储用户的 Token
    username: '' as string | null // 存储用户名
  }),
  actions: {
    // 更新 Token
    setToken(token: string) {
      this.token = token
      localStorage.setItem('authToken', token) // 持久化存储
    },
    setUserName(username: string) {
      this.username = username
      localStorage.setItem('authUsername', username) // 持久化存储用户名
    },
    // 加载持久化 Token
    loadTokenFromStorage() {
      const token = localStorage.getItem('authToken')
      if (token) {
        this.token = token
      }
      const username = localStorage.getItem('authUsername')
      if (username) {
        this.username = username
      }
    },
    // 退出登录
    logout() {
      this.token = null
      this.username = null
      localStorage.removeItem('authToken')
      localStorage.removeItem('authUsername')
    },

    // 检查 token 是否可能已过期（可选的客户端检查）
    isTokenExpired(): boolean {
      if (!this.token) return true

      try {
        // 如果使用 JWT token，可以解析其过期时间
        const payload = JSON.parse(atob(this.token.split('.')[1]))
        const currentTime = Date.now() / 1000
        return payload.exp < currentTime
      } catch (error) {
        // 如果不是 JWT 或解析失败，假设有效（让服务器验证）
        return false
      }
    }
  },
  getters: {
    isLoggedIn: (state) => !!state.token,
    currentUserName: (state) => state.username
  }
})
