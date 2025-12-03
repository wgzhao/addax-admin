// 表状态选项配置
export interface StatusOption {
  value: string
  label: string
  text?: string // 向后兼容
}

// 采集表状态选项
export const TABLE_STATUS_OPTIONS: StatusOption[] = [
  { value: 'N', label: 'N - 未采集', text: 'N - 未采集' },
  { value: 'Y', label: 'Y - 已采集', text: 'Y - 已采集' },
  { value: 'X', label: 'X - 不采集', text: 'X - 不采集' },
  { value: 'E', label: 'E - 采集错误', text: 'E - 采集错误' },
  { value: 'R', label: 'R - 正在采集', text: 'R - 正在采集' },
  { value: 'U', label: 'U - 等待更新', text: 'U - 等待更新' }
]

// 批量更新时的状态选项（包含空选项）
export const BATCH_UPDATE_STATUS_OPTIONS: StatusOption[] = [
  { value: '', label: '请选择状态', text: '请选择状态' },
  ...TABLE_STATUS_OPTIONS
]

// 采集模式选项
export const COLLECTION_MODE_OPTIONS = [
  { value: 'A', label: 'A - 盘后采集' },
  { value: 'R', label: 'R - 实时采集' }
]

// HDFS 存储与压缩格式选项（供多个视图复用）
export const HDFS_STORAGE_FORMATS = ['orc', 'parquet', 'avro', 'textfile']
export const HDFS_COMPRESS_FORMATS = ['lz4', 'snappy', 'gzip', 'zstd', 'zlib']
