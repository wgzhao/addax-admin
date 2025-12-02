// 采集日志
import Requests from '@/utils/requests'

class LogService {
  prefix: string = '/log'

  // find all files matching the pattern
  getLogFiles(tid: string) {
    return Requests.get(`${this.prefix}/${tid}`)
  }
  // get the content of special file
  getContent(id: number): Promise<string> {
    return Requests.get(`${this.prefix}/${id}/content`, { timeout: 120000 }) as Promise<string>
  }
}

export default new LogService()
