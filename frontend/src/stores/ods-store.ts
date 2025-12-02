import { defineStore } from 'pinia'

export const useOdsStore = defineStore('ods', {
  state: () => ({ item: null as any }),
  getters: {
    getItem: (state) => state.item
  },
  actions: {
    setItem(item: any) {
      this.item = item
    }
  }
})
