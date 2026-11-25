import { defineStore } from 'pinia';

export type ThemeName = 'light' | 'dark';
export type ThemePreference = ThemeName | 'system' | 'default';

const STORAGE_KEY = 'theme';

const parseStoredPreference = (value: string | null): ThemePreference | null => {
  if (value === 'light' || value === 'dark' || value === 'system') return value;
  return null;
};

const getSystemTheme = (): ThemeName => {
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
};

// Module level because the listener outlives any single action call and must be detached again
// when the user stops following the OS.
let systemQuery: MediaQueryList | null = null;
let systemListener: ((event: MediaQueryListEvent) => void) | null = null;

export const useThemeStore = defineStore('curTheme', {
  state: () => ({
    theme: 'light' as ThemeName,
    // Why: `preference` distinguishes "never chose" (default light, the product demo default)
    // from "explicitly follows the OS", which the appearance settings must show truthfully.
    preference: 'default' as ThemePreference,
  }),
  getters: {
    // Explicit light/dark choice. A system preference is not a user preference; it mirrors the OS.
    hasUserPreference: state => state.preference === 'light' || state.preference === 'dark',
  },
  actions: {
    initTheme() {
      const saved = parseStoredPreference(localStorage.getItem(STORAGE_KEY));
      if (saved === 'light' || saved === 'dark') {
        this.preference = saved;
        this.theme = saved;
        this.stopWatchingSystem();
        return;
      }
      if (saved === 'system') {
        this.preference = 'system';
        this.theme = getSystemTheme();
        this.watchSystem();
        return;
      }
      // Why: light-first is the product default for buyer demos; only an
      // explicit user action switches to the OS preference.
      this.preference = 'default';
      this.theme = 'light';
      this.stopWatchingSystem();
    },
    setPreference(preference: ThemePreference) {
      this.preference = preference;
      if (preference === 'system') {
        this.theme = getSystemTheme();
        localStorage.setItem(STORAGE_KEY, 'system');
        this.watchSystem();
        return;
      }
      if (preference === 'default') {
        this.theme = 'light';
        localStorage.removeItem(STORAGE_KEY);
        this.stopWatchingSystem();
        return;
      }
      this.theme = preference;
      localStorage.setItem(STORAGE_KEY, preference);
      this.stopWatchingSystem();
    },
    setTheme(theme: ThemeName) {
      this.setPreference(theme);
    },
    resetToSystemTheme() {
      this.setPreference('system');
    },
    toggleTheme() {
      this.setPreference(this.theme === 'light' ? 'dark' : 'light');
    },
    watchSystem() {
      if (!window.matchMedia) return;
      this.stopWatchingSystem();
      systemQuery = window.matchMedia('(prefers-color-scheme: dark)');
      systemListener = event => {
        if (this.preference === 'system') {
          this.theme = event.matches ? 'dark' : 'light';
        }
      };
      if (systemQuery.addEventListener) {
        systemQuery.addEventListener('change', systemListener);
      } else {
        systemQuery.addListener(systemListener);
      }
    },
    stopWatchingSystem() {
      if (!systemQuery || !systemListener) return;
      if (systemQuery.removeEventListener) {
        systemQuery.removeEventListener('change', systemListener);
      } else {
        systemQuery.removeListener(systemListener);
      }
      systemQuery = null;
      systemListener = null;
    },
  },
});
