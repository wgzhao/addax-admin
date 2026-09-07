import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useThemeStore } from '@/stores/theme-store';

const stubSystemTheme = (dark: boolean) => {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: dark && query.includes('prefers-color-scheme'),
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
};

describe('theme store', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    stubSystemTheme(false);
  });

  it('falls back to the system theme without a saved preference', () => {
    const store = useThemeStore();
    store.initTheme();
    expect(store.theme).toBe('light');
    expect(store.hasUserPreference).toBe(false);
  });

  it('follows a dark system theme when nothing is saved', () => {
    stubSystemTheme(true);
    const store = useThemeStore();
    store.initTheme();
    expect(store.theme).toBe('dark');
  });

  it('restores a saved preference over the system theme', () => {
    localStorage.setItem('theme', 'dark');
    stubSystemTheme(false);
    const store = useThemeStore();
    store.initTheme();
    expect(store.theme).toBe('dark');
    expect(store.hasUserPreference).toBe(true);
  });

  it('ignores invalid saved values', () => {
    localStorage.setItem('theme', 'blue');
    const store = useThemeStore();
    store.initTheme();
    expect(store.theme).toBe('light');
    expect(store.hasUserPreference).toBe(false);
  });

  it('persists the preference on setTheme and flips on toggleTheme', () => {
    const store = useThemeStore();
    store.initTheme();
    store.setTheme('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    store.toggleTheme();
    expect(store.theme).toBe('light');
    expect(store.hasUserPreference).toBe(true);
  });

  it('resets to system theme and clears the saved key', () => {
    localStorage.setItem('theme', 'dark');
    stubSystemTheme(true);
    const store = useThemeStore();
    store.initTheme();
    store.resetToSystemTheme();
    expect(store.theme).toBe('dark');
    expect(store.hasUserPreference).toBe(false);
    expect(localStorage.getItem('theme')).toBeNull();
  });
});
