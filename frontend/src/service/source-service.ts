// 数据源管理接口
import Requests from '@/utils/requests';
import type {
  CollectableTable,
  EsConnectConfig,
  EtlSource,
  MongoConnectConfig,
  SourceTypeDescriptor,
  TableMeta,
} from '@/types/database';

class SourceService {
  prefix: string = '/sources';

  // 查询所有数据源
  list(): Promise<EtlSource[]> {
    return Requests.get(this.prefix) as unknown as Promise<EtlSource[]>;
  }

  // 列出有效源
  listActiveSources(): Promise<EtlSource[]> {
    return Requests.get(`${this.prefix}/enabled`) as unknown as Promise<EtlSource[]>;
  }
  // 查询单个数据源
  get(id: number): Promise<EtlSource> {
    return Requests.get(`${this.prefix}/${id}`) as unknown as Promise<EtlSource>;
  }

  // 新建或更新数据源
  save(source: EtlSource): Promise<EtlSource> {
    if (source.id) {
      // 如果有id，则为更新
      return Requests.put(`${this.prefix}/${source.id}`, source) as unknown as Promise<EtlSource>;
    }
    // 否则为新增
    return Requests.post(this.prefix, source) as unknown as Promise<EtlSource>;
  }

  // 删除数据源
  delete(id: number): Promise<void> {
    return Requests.delete(`${this.prefix}/${id}`) as unknown as Promise<void>;
  }

  // 测试数据源连接
  testConnection(params: {
    url: string;
    username: string;
    password?: string;
    sourceId?: number;
    dbType?: string;
    connectConfig?: MongoConnectConfig | EsConnectConfig | null;
  }): Promise<boolean> {
    return Requests.post(`${this.prefix}/test-connect`, params) as unknown as Promise<boolean>;
  }

  // 采集源类型与能力
  fetchTypes(): Promise<SourceTypeDescriptor[]> {
    return Requests.get(`${this.prefix}/types`) as unknown as Promise<SourceTypeDescriptor[]>;
  }

  // 查询采集源下所有数据库
  fetchDatabasesBySource(sourceId: number): Promise<string[]> {
    return Requests.get(`${this.prefix}/${sourceId}/databases`) as unknown as Promise<string[]>;
  }

  // 查询采集源在目标端上最近使用的目标库，仅作为向导的建议值
  fetchRecentTargetDbs(sourceId: number, targetId: number): Promise<string[]> {
    return Requests.get(`${this.prefix}/${sourceId}/target-databases`, {
      targetId,
    }) as unknown as Promise<string[]>;
  }

  // 查询未采集的表
  fetchUncollectedTables(sourceId: number, dbName: string): Promise<TableMeta[]> {
    return Requests.get(
      `${this.prefix}/${sourceId}/databases/${dbName}/tables/uncollected`,
      undefined,
      { timeout: 300000 }
    ) as unknown as Promise<TableMeta[]>;
  }

  // 查询指定采集路由下可新增或可恢复的源表
  fetchCollectableTables(
    sourceId: number,
    dbName: string,
    targetId: number,
    targetDb: string
  ): Promise<CollectableTable[]> {
    return Requests.get(
      `${this.prefix}/${sourceId}/databases/${dbName}/tables/collectable`,
      { targetId, targetDb },
      { timeout: 300000 }
    ) as unknown as Promise<CollectableTable[]>;
  }
}

export default new SourceService();
