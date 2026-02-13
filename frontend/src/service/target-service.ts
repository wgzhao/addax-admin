import Requests from '@/utils/requests'
import type { EtlTarget } from '@/types/database'

class TargetService {
  prefix = '/targets'

  list(enabledOnly = true): Promise<EtlTarget[]> {
    return Requests.get(this.prefix, { enabledOnly }) as unknown as Promise<EtlTarget[]>
  }

  create(data: EtlTarget): Promise<EtlTarget> {
    return Requests.post(this.prefix, data) as unknown as Promise<EtlTarget>
  }

  update(id: number, data: EtlTarget): Promise<EtlTarget> {
    return Requests.put(`${this.prefix}/${id}`, data) as unknown as Promise<EtlTarget>
  }

  remove(id: number): Promise<void> {
    return Requests.delete(`${this.prefix}/${id}`) as unknown as Promise<void>
  }

  testConnect(id: number): Promise<void> {
    return Requests.post(`${this.prefix}/${id}/test-connect`, {}) as unknown as Promise<void>
  }

  testConnectPayload(data: EtlTarget): Promise<void> {
    return Requests.post(`${this.prefix}/test-connect`, data) as unknown as Promise<void>
  }
}

export default new TargetService()
