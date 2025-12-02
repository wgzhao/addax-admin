import taskService from '@/service/task-service'
import { ref } from 'vue'
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
        // 这里假设主任务id即为后端任务id
        const res = await taskService.getAllTaskStatus(task.id)
        if (res.status && res.status !== task.status) {
          taskCenter.updateTaskStatus(task.id, res.status, res.progress, res.result)
        } else if (res.progress) {
          taskCenter.updateTaskStatus(task.id, task.status, res.progress, res.result)
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
