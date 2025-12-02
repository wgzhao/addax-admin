import { defineStore } from 'pinia'

export const themeStore = defineStore('curTheme', {
  state: () => ({ theme: 'light' as 'light' | 'dark' }),
  actions: {
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light'
    },
    getTheme() {
      return this.theme
    },
    getIcon() {
      return this.theme === 'light' ? 'fas fa-sun' : 'fas fa-moon'
    }
  }
})
