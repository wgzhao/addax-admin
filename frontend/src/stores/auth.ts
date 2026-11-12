import { defineStore } from 'pinia';
import { highestRole, type Role } from '@/utils/roles';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '' as string | null, // 存储用户的 Token
    username: '' as string | null, // 存储用户名
    role: 'guest' as Role,
    roleLoaded: false,
  }),
  actions: {
    // 更新 Token
    setToken(token: string) {
      this.token = token;
      localStorage.setItem('authToken', token); // 持久化存储
    },
    setUserName(username: string) {
      this.username = username;
      localStorage.setItem('authUsername', username); // 持久化存储用户名
    },
    // 加载持久化 Token
    loadTokenFromStorage() {
      const token = localStorage.getItem('authToken');
      if (token) {
        this.token = token;
      }
      const username = localStorage.getItem('authUsername');
      if (username) {
        this.username = username;
      }
      // The persisted token says nothing about the current role; always re-read it.
      this.role = 'guest';
      this.roleLoaded = false;
    },
    async loadCurrentUser() {
      if (!this.token) {
        this.role = 'guest';
        this.roleLoaded = true;
        return;
      }
      try {
        // Dynamic import avoids a module cycle: requests -> auth store -> user service -> requests.
        const { default: userService } = await import('@/service/user-service');
        const me = await userService.me();
        this.role = highestRole(me.authorities);
      } catch {
        this.role = 'guest';
      } finally {
        this.roleLoaded = true;
      }
    },
    // 退出登录
    logout() {
      this.token = null;
      this.username = null;
      this.role = 'guest';
      this.roleLoaded = false;
      localStorage.removeItem('authToken');
      localStorage.removeItem('authUsername');
    },

    // 检查 token 是否可能已过期（可选的客户端检查）
    isTokenExpired(): boolean {
      if (!this.token) return true;

      try {
        // 如果使用 JWT token，可以解析其过期时间
        const payload = JSON.parse(atob(this.token.split('.')[1]));
        const currentTime = Date.now() / 1000;
        return payload.exp < currentTime;
      } catch (error) {
        // 如果不是 JWT 或解析失败，假设有效（让服务器验证）
        return false;
      }
    },
  },
  getters: {
    isLoggedIn: state => !!state.token,
    currentUserName: state => state.username,
    isAdmin: state => state.role === 'admin',
    isGuest: state => state.role === 'guest',
    // Phase 0 keeps all business writes admin-only. Phase 1 replaces this with the source grant
    // check once user_source_grant exists.
    canWrite: state => state.role === 'admin',
  },
});
