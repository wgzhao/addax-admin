// 表状态选项配置
export interface StatusOption {
  value: string;
  label: string;
  text?: string; // 向后兼容
}

// 采集表状态选项
export const TABLE_STATUS_OPTIONS: StatusOption[] = [
  { value: 'N', label: '未采集', text: '未采集' },
  { value: 'Y', label: '已采集', text: '已采集' },
  { value: 'X', label: '已排除', text: '已排除' },
  { value: 'W', label: '等待采集', text: '等待采集' },
  { value: 'E', label: '采集失败', text: '采集失败' },
  { value: 'R', label: '采集中', text: '采集中' },
  { value: 'U', label: '待同步', text: '待同步' },
];

// 采集模式选项
export const COLLECTION_MODE_OPTIONS = [
  { value: 'A', label: 'A - 盘后采集' },
  { value: 'R', label: 'R - 实时采集' },
];

// HDFS 存储与压缩格式选项（供多个视图复用）
export const HDFS_STORAGE_FORMATS = ['orc', 'parquet', 'avro', 'textfile'];
export const HDFS_COMPRESS_FORMATS = ['lz4', 'snappy', 'gzip', 'zstd', 'zlib'];
export const PARTITION_FORMATS = ['yyyyMMdd', 'yyyy-MM-dd', 'yyyy/MM/dd', 'yyyy/M/d'];
