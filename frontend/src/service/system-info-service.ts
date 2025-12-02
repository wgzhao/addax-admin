import Requests from '@/utils/requests'
import type { LoadItemsOptions } from '@/types'

class SystemInfoService {
  baseUrl = 'systemInfo'
  getEtlAndDs(q?: string) {
    const params = q ? new URLSearchParams({ q: q }) : undefined
    return Requests.get(this.baseUrl + '/etlAndDs', params)
  }

  getEtlInfo(params: LoadItemsOptions) {
    return Requests.get(this.baseUrl + '/etlInfo', params)
  }

  getDsInfo(q?: string) {
    const params = q ? new URLSearchParams({ q: q }) : undefined
    return Requests.get(this.baseUrl + '/dsInfo', params)
  }
}

export default new SystemInfoService()
