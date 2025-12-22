// 采集日志
import Requests from '@/utils/requests'

class LogService {
  prefix: string = '/log'

  getAddaxLogList(page: number, pageSize: number) {
    return Requests.get(`${this.prefix}/addax`, { page, pageSize })
  }
  // find all files matching the pattern
  getLogFiles(tid: string) {
    return Requests.get(`${this.prefix}/addax/${tid}`)
  }
  // get the content of special file
  getContent(id: number): Promise<string> {
    return Requests.get(`${this.prefix}/addax/${id}/content`, { timeout: 120000 }) as Promise<string>
  }

  estimate(table: string, params: Record<string, any>) {
      const qp = new URLSearchParams({ table, ...params })
      return Requests.get(`${this.prefix}/estimate?${qp.toString()}`)
    }
  
  cleanupAddaxLog(before: string) {
      return Requests.post(`${this.prefix}/addax/cleanup?before=${before}`)
    }
  
    getTask(taskId: string) {
      return Requests.get(`${this.prefix}/addax/cleanup/${taskId}`)
    }
  
    getSources(table: string) {
      return Requests.get(`${this.prefix}/sources?table=${encodeURIComponent(table)}`)
    }
}

export default new LogService()
