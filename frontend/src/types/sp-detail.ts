// 定义 SP 详情与依赖需求的类型
export interface SpDetail {
  spOwner?: string
  spName?: string
  paramSou?: string
  runFreq?: string
  flag?: string
  retryCnt?: number
  taskGroup?: string
  realtimeTaskgroup?: string
  pnType?: string
  pnFixed?: string
  pnInterval?: string
  pnRange?: string
  remark?: string
  startTime?: string
  endTime?: string
  runtime?: string | number
  spId?: string
}

export interface SpNeedsDetail {
  NEED_SOU?: string
  THROUGH_NEED_SOU?: string
  NEED_SP?: string
  THROUGH_NEED_SP?: string
  SP_ALLTABS?: string
  SP_DEST?: string
  SP_ALLNEXT?: string
}

export default {}
