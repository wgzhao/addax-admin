import { ref } from 'vue'

export interface TaskItem {
  id: string
  type: string // 任务类型，如采集、表状态更新等
  target: string // 目标对象，如表名、字段名
  status: '进行中' | '成功' | '失败'
  progress: string // 进度描述
  submitTime: string
  result?: string
  extra?: any
}

const tasks = ref<TaskItem[]>([])

function addTask(task: TaskItem) {
  tasks.value.push(task)
}

function updateTaskStatus(taskId: string, status: string, progress: string, result?: string) {
  const task = tasks.value.find((t) => t.id === taskId)
  if (task) {
    task.status = status as any
    task.progress = progress
    if (result !== undefined) task.result = result
  }
}

function removeTask(taskId: string) {
  tasks.value = tasks.value.filter((t) => t.id !== taskId)
}

function getTasks() {
  return tasks.value
}

export default {
  tasks,
  addTask,
  updateTaskStatus,
  removeTask,
  getTasks
}
