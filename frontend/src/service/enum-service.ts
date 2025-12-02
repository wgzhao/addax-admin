import Requests from '@/utils/requests'

export interface EnumEntry {
  code: string | number
  desc: string
  name?: string
}

class EnumService {
  prefix: string = '/enums'

  getJourKind(): Promise<EnumEntry[]> {
    return Requests.get(`${this.prefix}/jour-kind`) as unknown as Promise<EnumEntry[]>
  }

  getTableStatus(): Promise<EnumEntry[]> {
    return Requests.get(`${this.prefix}/table-status`) as unknown as Promise<EnumEntry[]>
  }

  getTaskStatus(): Promise<EnumEntry[]> {
    return Requests.get(`${this.prefix}/task-status`) as unknown as Promise<EnumEntry[]>
  }
}

export default new EnumService()
