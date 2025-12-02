import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as EnumService from '@/service/enum-service'

export const useDictStore = defineStore('dict', () => {
  const jourKind = ref<Array<{ code: string | number; desc: string; name?: string }>>([])
  const tableStatus = ref<Array<{ code: string | number; desc: string; name?: string }>>([])
  const taskStatus = ref<Array<{ code: string | number; desc: string; name?: string }>>([])

  async function loadAll() {
    try {
      const [jk, ts, ks] = await Promise.all([
        EnumService.getJourKind(),
        EnumService.getTableStatus(),
        EnumService.getTaskStatus()
      ])
      jourKind.value = jk || []
      tableStatus.value = ts || []
      taskStatus.value = ks || []
    } catch (e) {
      console.error('Failed to load enums', e)
    }
  }

  function findTableStatus(code: string | number) {
    return tableStatus.value.find((r) => String(r.code) === String(code))
  }

  function findTaskStatus(code: string | number) {
    return taskStatus.value.find((r) => String(r.code) === String(code))
  }

  return {
    jourKind,
    tableStatus,
    taskStatus,
    loadAll,
    findTableStatus,
    findTaskStatus
  }
})
