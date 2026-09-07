import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from '@/stores/auth';

// Build a minimal JWT-shaped token with the given exp (seconds since epoch)
const makeJwt = (exp: number) => {
  const b64 = (obj: unknown) => btoa(JSON.stringify(obj));
  return `${b64({ alg: 'none', typ: 'JWT' })}.${b64({ exp })}.sig`;
};

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('persists token and username to localStorage', () => {
    const store = useAuthStore();
    store.setToken('tok-1');
    store.setUserName('admin');
    expect(store.token).toBe('tok-1');
    expect(localStorage.getItem('authToken')).toBe('tok-1');
    expect(localStorage.getItem('authUsername')).toBe('admin');
  });

  it('loads persisted values from storage', () => {
    localStorage.setItem('authToken', 'tok-2');
    localStorage.setItem('authUsername', 'ops');
    const store = useAuthStore();
    store.loadTokenFromStorage();
    expect(store.token).toBe('tok-2');
    expect(store.username).toBe('ops');
    expect(store.isLoggedIn).toBe(true);
  });

  it('clears state and storage on logout', () => {
    const store = useAuthStore();
    store.setToken('tok-3');
    store.setUserName('admin');
    store.logout();
    expect(store.token).toBeNull();
    expect(store.username).toBeNull();
    expect(store.isLoggedIn).toBe(false);
    expect(localStorage.getItem('authToken')).toBeNull();
  });

  describe('isTokenExpired', () => {
    it('returns true when there is no token', () => {
      const store = useAuthStore();
      expect(store.isTokenExpired()).toBe(true);
    });

    it('returns true for an expired JWT', () => {
      const store = useAuthStore();
      store.setToken(makeJwt(Math.floor(Date.now() / 1000) - 60));
      expect(store.isTokenExpired()).toBe(true);
    });

    it('returns false for a valid JWT', () => {
      const store = useAuthStore();
      store.setToken(makeJwt(Math.floor(Date.now() / 1000) + 3600));
      expect(store.isTokenExpired()).toBe(false);
    });

    it('returns false for non-JWT tokens and lets the server decide', () => {
      const store = useAuthStore();
      store.setToken('opaque-token');
      expect(store.isTokenExpired()).toBe(false);
    });
  });
});
