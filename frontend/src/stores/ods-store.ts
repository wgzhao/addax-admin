import { defineStore } from 'pinia'

export const useOdsStore = defineStore({
  id: 'ods',
  state: () => ({ item: null }),
  getters: {
    getItem: (state) => state.item
  },
  actions: {
    setItem(item) {
      this.item = item
    }
  }
})
