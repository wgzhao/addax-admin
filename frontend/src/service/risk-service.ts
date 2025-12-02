import { ref } from 'vue'
import Requests from '@/utils/requests'

const apiPrefix = ref('/risk')

const sysRisks = () => {
  return Requests.get(apiPrefix.value + '/sysRisk')
}

const fieldsChanges = () => {
  return Requests.get(apiPrefix.value + '/odsFieldChange')
}

const smsDetails = () => {
  return Requests.get(apiPrefix.value + '/smsDetail')
}

export default { sysRisks, fieldsChanges, smsDetails }
