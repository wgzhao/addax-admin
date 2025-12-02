// src/types/database.ts

// 系统字典表 sys_dict
export interface SysDict {
  code: number // 条目编号
  name?: string // 条目名称
  classification?: string // 分类
  remark?: string // 说明
}

// 字典明细表 sys_item
export interface SysItem {
  dictCode: number // 字典条目编号
  itemKey: string // 明细名称
  itemValue?: string // 明细内容
  remark?: string // 备注
}

// HIVE表结构信息 etl_tbls
export interface EtlTbls {
  dbId?: number
  dbName: string // 数据库名、用户
  dbLocation?: string // 数据库所在HDFS地址
  tblId?: number
  tblName: string // 表名
  tblType?: string // 表类型（管理表、外部表）
  tblLocation?: string // 表所在HDFS地址
  cdId?: number
  colName: string // 字段名
  colType?: string // 字段类型
  colComment?: string // 字段备注
  colIdx?: number // 字段顺序
  tblComment?: string // 表注释
}

// HIVE表结构临时表 tb_imp_etl_tbls_tmp
export interface TbImpEtlTblsTmp {
  dbId?: number
  dbName?: string
  dbLocation?: string
  tblId?: number
  tblName?: string
  tblType?: string
  tblLocation?: string
  cdId?: number
  colName?: string
  colType?: string
  colComment?: string
  colIdx?: number
  tblComment?: string
}

// 采集标志表 tb_imp_flag
export interface TbImpFlag {
  tradedate?: number
  kind: string
  fid: string
  fval?: number
  dwCltDate: Date
}

// 采集日志表 tb_imp_jour
export interface TbImpJour {
  kind?: string
  tradeDate?: number
  status?: string
  keyId?: string
  remark?: string
  updtDate?: Date
}

// 日期参数基表 tb_imp_param0
export interface TbImpParam0 {
  paramSou?: string
  paramKind0?: string
  paramKind?: string
  paramName?: string
  paramRemark?: string
  paramValue?: string
}

// 定时计划表 tb_imp_plan
export interface TbImpPlan {
  pnId: string // 主键，UUID
  pnType?: string // 定时计划类型，参考参数1064
  pnFixed?: string // 定点时间
  pnInterval?: number // 轮询间隔
  pnRange?: string // 轮询时间区间
  flag?: string // 标志
  startTime?: Date
  endTime?: Date
  bexit?: string // 是否跳过切日时间段
  runtime?: number
}

// HIVE表结构信息 tb_imp_tbl_hdp
export interface TbImpTblHdp {
  tid: string
  hiveOwner?: string
  hiveTablename: string
  colName?: string
  colTypeFull?: string
  colType?: string
  colPrecision?: number
  colScale?: number
  colIdx?: number
  tblComment?: string
  colComment?: string
  updtDate?: Date
  cdId?: number
}

// 用户表 users
export interface Users {
  username: string // 用户名，主键
  password: string // 密码
  enabled: boolean // 是否启用
}

// 权限表 authorities
export interface Authorities {
  username: string // 用户名，外键
  authority: string // 权限
}

// 用户组表 groups
export interface Groups {
  id: number // 主键，自增
  groupName: string // 组名
}

// 组权限表 group_authorities
export interface GroupAuthorities {
  groupId: number // 组ID，外键
  authority: string // 权限
}

// 组成员表 group_members
export interface GroupMembers {
  id: number // 主键，自增
  username: string // 用户名
  groupId: number // 组ID，外键
}

// 采集源表信息 etl_soutab
export interface EtlSoutab {
  tid: string // 表ID，主键的一部分
  souDbConn: string // 源数据库连接
  owner: string // 所有者
  tableName: string // 表名
  columnName: string // 字段名，主键的一部分
  dataType?: string // 数据类型
  dataLength?: number // 数据长度
  dataPrecision?: number // 精度
  dataScale?: number // 小数位
  columnId?: number // 字段ID
  tableType?: string // 表类型
  tabComment?: string // 表注释
  colComment?: string // 字段注释
  dwCltDate?: Date // 采集时间
}

// 采集统计表 etl_statistic
export interface EtlStatistic {
  id: number // 逻辑主键
  tid?: number // 采集表主键
  startAt?: Date // 采集开始时间
  endAt?: Date // 采集结束时间
  takeSecs?: number // 采集耗时
  totalBytes?: number // 采集的总字节数
  byteSpeed?: number // 采集速度 字节/秒
  recSpeed?: number // 采集速度 行/秒
  totalRecs?: number // 采集的总行数
  totalErrors?: number // 采集时发生错误的行数
  runDate: Date // 运行时的日期
}

// 采集源表 etl_source
export interface EtlSource {
  id: number // 采集源ID，自增主键
  code: string // 采集编号，一般以两个大写字母作为标志
  name: string // 采集源名称
  url: string // 采集源JDBC连接串
  username?: string // 采集源连接的账号
  pass?: string // 采集源连接的密码
  startAt?: string // 采集的定时启动时间点
  prerequisite?: string // 能否开始采集的先决条件
  preScript?: string // 标志符合条件后的前置脚本
  remark?: string // 系统备注信息
  enabled?: boolean // 是否有效
}

// 采集表信息 etl_table
export interface EtlTable {
  id: number // 表ID，主键
  sourceDb: string // 采集源库名或schema名称或owner名称
  sourceTable: string // 采集源表名
  targetDb: string // 目标库名，即提供给hive的库名
  targetTable: string // 目标表名，即Hive的表名
  partKind?: string // 分区类型，D - 按每日分区，如果为空，则表示不分区
  partName?: string // 目标表分区字段名称
  partFormat?: string // 目标表分区字段格式
  storageFormat?: string // 目标表存储格式，默认是 orc
  compressFormat?: string // 目标表的压缩格式，默认是 lz4
  filter?: string // 采集过滤条件，即where条件
  status?: string // 当前采集状态：N-还没有采集;W-等待采集;E-采集发生错误;R-正在采集;X-不再采集; U-等待表更新
  kind?: string // 采集类型:A-自动采集(默认值);M-手工采集;R-实时采集
  retryCnt?: number // 采集的重试次数
  startTime?: Date // 本次采集的开始时间
  endTime?: Date // 本次采集的结束时间
  maxRuntime?: number // 采集可持续的最大时间
  sid: number // 采集源ID，对应etl_source表id
  duration?: number // 采集耗时，单位为秒
  tblComment?: string // 目标表的注释
}

export interface TableMeta {
  name: string // 表名
  comment?: string // 表注释
}

export interface VEtlWithSource extends EtlTable {
  code: string // 采集编号，一般以两个大写字母作为标志
  name: string // 采集源名称
  url: string // 采集源JDBC连接串
  username?: string // 采集源连接的账号
  pass?: string // 采集源连接的密码
  startAt?: string // 采集的定时启动时间点
  prerequisite?: string // 能否开始采集的先决条件
  preScript?: string // 标志符合条件后的前置脚本
  remark?: string // 系统备注信息
  enabled?: boolean // 是否有效
}
// 采集表字段信息 etl_column
export interface EtlColumn {
  tid: number // 采集表主键ID，对应etl_table中的id
  columnName?: string // 原表字段名称
  columnId?: number // 列ID，用于排序字段
  sourceType?: string // 源表的数据类型
  dataLength?: number // 数据长度
  dataPrecision?: number // 精度
  dataScale?: number // 小数位
  tblComment?: string // 表注释
  colComment?: string // 字段注释
  targetType: string // 目标表对应的类型
  targetTypeFull?: string // 目标表字段的完整类型，比如decimal(10,3)
  updateAt?: Date // 更新时间
}

// 采集任务模板 etl_job
export interface EtlJob {
  tid: number // 采集表主键
  job: string // addax任务模板
}

// Addax日志表 addax_log
export interface AddaxLog {
  id: number // 主键，自增
  tid: number // 采集表主键
  runAt?: Date // 运行时间
  runDate?: Date // 运行日期
  log?: string // 日志内容
}

// Addax统计表 tb_addax_sta
export interface TbAddaxSta {
  pkid: string // 主键，UUID
  jobname?: string // 作业名称
  startTs?: number // 开始时间戳
  endTs?: number // 结束时间戳
  takeSecs?: number // 耗时秒数
  byteSpeed?: number // 字节速度
  recSpeed?: number // 记录速度
  totalRec?: number // 总记录数
  totalErr?: number // 总错误数
  updtDate?: Date // 更新时间
}

// 消息提醒表 notification
export interface Notification {
  id: number // 自动生成主键
  phone: string // 接收人号码或其他唯一标识，用逗号分隔
  msg: string // 消息内容
  sms?: string // 是否发送短信，发送成功后置为y
  im?: string // 是否发送企微，发送成功后置为y
  call?: string // 是否拨打语音，拨打成功后置为y
  createAt: Date // 消息生成的时间，自动生成
}

export interface DbConnect {
  url: string
  username?: string
  password?: string
}
// ========== 兼容性接口和业务组合接口 ==========

// 原有的业务接口，保持兼容性
export interface ETLAndDS {
  sysKind: string
  sysid: string
  sysName: string
  dbConstr: string
  dbUser: string
}

export interface MainTable {
  id: number
  sysName: string
  souSysid: string
  souOwner: string
  souTable: string
  souFilter: string
  souSplit: string
  destSysid: string
  destOwner: string
  destTable: string
  destSplit: string
  bcreate: string
  bupdate: string
  realtimeInterval: string
  realtimeTaskgroup: string
  realtimeSouOwner: string
  realtimeSouFilter: string
  realtimeFixed: string
  etlKind: string
  realtimeIntervalRange: string
  afterRetryPntype: string
  afterRetryFixed: string
  startTime: string
  endTime: string
  runtime: string
  tid: string
  runtimeAdd: string
  remark: string
}
