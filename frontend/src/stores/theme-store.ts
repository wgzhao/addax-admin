import { defineStore } from 'pinia'

type ThemeName = 'light' | 'dark'

const STORAGE_KEY = 'theme'

const parseTheme = (value: string | null): ThemeName | null => {
  if (value === 'light' || value === 'dark') return value
  return null
}

const getSystemTheme = (): ThemeName => {
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

export const useThemeStore = defineStore('curTheme', {
  state: () => ({ theme: 'dark' as ThemeName, hasUserPreference: false }),
  actions: {
    initTheme() {
      const savedTheme = parseTheme(localStorage.getItem(STORAGE_KEY))
      if (savedTheme) {
        this.theme = savedTheme
        this.hasUserPreference = true
        return
      }
      this.theme = getSystemTheme()
      this.hasUserPreference = false
    },
    setTheme(theme: ThemeName) {
      this.theme = theme
      this.hasUserPreference = true
      localStorage.setItem(STORAGE_KEY, theme)
    },
    setSystemTheme(theme: ThemeName) {
      this.theme = theme
      this.hasUserPreference = false
    },
    resetToSystemTheme() {
      this.setSystemTheme(getSystemTheme())
      localStorage.removeItem(STORAGE_KEY)
    },
    toggleTheme() {
      this.setTheme(this.theme === 'light' ? 'dark' : 'light')
    },
    getTheme() {
      return this.theme
    },
    getIcon() {
      return this.theme === 'light' ? 'fas fa-sun' : 'fas fa-moon'
    }
  }
})
