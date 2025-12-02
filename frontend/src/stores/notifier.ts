import { ref, watch } from 'vue'

export interface Notice {
  show: boolean
  text: string
  color?: string
  timeout?: number
  icon?: string
  title?: string
}

const notice = ref<Notice>({ show: false, text: '', color: 'info', timeout: 3000 })

let timeoutId: number | null = null

watch(notice, (newValue) => {
  if (timeoutId) {
    clearTimeout(timeoutId)
    timeoutId = null
  }
  if (newValue.show) {
    if (newValue.timeout && newValue.timeout > 0) {
      timeoutId = setTimeout(() => {
        notice.value.show = false
      }, newValue.timeout)
    }
  }
})

export function useNotifier() {
  function notify(
    text: string,
    color: string = 'info',
    timeout = 3000,
    title?: string,
    icon?: string
  ) {
    notice.value = { show: true, text, color: color, timeout, title, icon }
  }

  function hide() {
    notice.value.show = false
  }

  return { notice, notify, hide }
}

// 函数式导出，便于在非 setup 场景使用
export function notify(text: string, color: string = 'info', timeout = 3000, icon?: string) {
  notice.value = { show: true, text, color: color, timeout, icon }
}
