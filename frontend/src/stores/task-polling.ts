import taskService from '@/service/task-service'
import taskCenter from './task-center'

// 轮询进行中任务状态
let polling = false
let timer: any = null

export function startTaskPolling(interval = 5000) {
  if (polling) return
  polling = true
  timer = setInterval(async () => {
    const tasks = taskCenter.tasks.value.filter((t) => t.status === '进行中')
    for (const task of tasks) {
      try {
        // 从后端获取所有任务状态后匹配当前任务
        const list = await taskService.getAllTaskStatus()
        const found = Array.isArray(list)
          ? (list as Array<Record<string, any>>).find((it) => String(it.id) === String(task.id))
          : undefined
        if (found) {
          const status = found.status ?? task.status
          const progress = found.progress ?? task.progress
          const result = found.result
          if (status !== task.status || progress !== task.progress) {
            taskCenter.updateTaskStatus(task.id, status, progress, result)
          }
        }
      } catch (e) {
        // 可选：处理异常
      }
    }
  }, interval)
}

export function stopTaskPolling() {
  if (timer) clearInterval(timer)
  polling = false
}
