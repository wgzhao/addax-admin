import Requests from '@/utils/requests'

class CloseCheckService {
  prefix = '/closeCheck'

  fetchAbnormalRecord() {
    return Requests.get(this.prefix + '/abnormalRecord')
  }
}

export default new CloseCheckService()
