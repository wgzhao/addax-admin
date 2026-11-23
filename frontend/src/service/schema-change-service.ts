// 表结构变更：字段新增、删除与类型变化
import Requests from '@/utils/requests';

export interface SchemaChangeRow {
  id: number;
  tid: number;
  sourceDb?: string;
  sourceTable?: string;
  columnName?: string;
  changeType?: string;
  oldSourceType?: string | null;
  newSourceType?: string | null;
  oldDataLength?: number | null;
  newDataLength?: number | null;
  oldDataPrecision?: number | null;
  newDataPrecision?: number | null;
  oldDataScale?: number | null;
  newDataScale?: number | null;
  oldColComment?: string | null;
  newColComment?: string | null;
  changeAt?: string;
}

export interface SchemaChangePage {
  content: SchemaChangeRow[];
  totalElements: number;
}

export interface SchemaChangeQuery {
  page: number;
  pageSize: number;
  changeType?: string;
  keyword?: string;
  from?: string;
  to?: string;
  sortField?: string | null;
  sortOrder?: string | null;
}

class SchemaChangeService {
  private prefix = '/schema-changes';

  list(params: SchemaChangeQuery): Promise<SchemaChangePage> {
    return Requests.get(this.prefix, params) as unknown as Promise<SchemaChangePage>;
  }
}

export const schemaChangeService = new SchemaChangeService();
