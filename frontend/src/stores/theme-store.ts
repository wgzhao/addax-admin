import { ref } from 'vue'
import { defineStore } from 'pinia'

export const themeStore = defineStore({
  id: 'curTheme',
  state: () => {
    return { theme: 'light' }
  },
  actions: {
    toggleTheme() {
      this.theme = this.theme == 'light' ? 'dark' : 'light'
    },
    getTheme() {
      return this.theme
    },
    getIcon() {
      return this.theme == 'light' ? 'fas fa-sun' : 'fas fa-moon'
    }
  }
})
