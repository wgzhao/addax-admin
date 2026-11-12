import { defineStore } from 'pinia';
import { highestRole, type Role } from '@/utils/roles';

export type GrantLevel = 'VIEW' | 'OPERATE';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '' as string | null, // 存储用户的 Token
    username: '' as string | null, // 存储用户名
    role: 'guest' as Role,
    grants: {} as Record<number, GrantLevel>,
    roleLoaded: false,
  }),
  actions: {
    // 更新 Token
    setToken(token: string) {
      this.token = token;
      // The old role belongs to the previous session; reset it so the router guard re-reads
      // /users/me before deciding where the new session may go.
      this.role = 'guest';
      this.grants = {};
      this.roleLoaded = false;
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
      this.grants = {};
      this.roleLoaded = false;
    },
    async loadCurrentUser() {
      if (!this.token) {
        this.role = 'guest';
        this.grants = {};
        this.roleLoaded = true;
        return;
      }
      try {
        // Dynamic import avoids a module cycle: requests -> auth store -> user service -> requests.
        const { default: userService } = await import('@/service/user-service');
        const me = await userService.me();
        this.role = highestRole(me.authorities);
        this.grants = Object.fromEntries(
          (me.grants || []).map(grant => [grant.sourceId, grant.accessLevel])
        ) as Record<number, GrantLevel>;
      } catch {
        this.role = 'guest';
        this.grants = {};
      } finally {
        this.roleLoaded = true;
      }
    },
    // 退出登录
    logout() {
      this.token = null;
      this.username = null;
      this.role = 'guest';
      this.grants = {};
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
    // Admin-only actions: target/config management and source creation/deletion.
    canWrite: state => state.role === 'admin',
    // All authenticated roles may read source/table metadata; `user` is guest read access plus
    // management of granted sources.
    canReadSource: () => (sourceId?: number | null) => true,
    canOperateSource: state => (sourceId?: number | null) => {
      if (state.role === 'admin') return true;
      if (state.role === 'guest' || sourceId == null) return false;
      return state.grants[sourceId] === 'OPERATE';
    },
    hasOperateSources: state =>
      state.role === 'admin' || Object.values(state.grants).includes('OPERATE'),
  },
});
