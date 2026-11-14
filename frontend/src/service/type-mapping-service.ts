// 源字段类型 → ODS/Hive 类型映射（字典 2011）维护接口
import Requests from '@/utils/requests';

export interface TypeMapping {
  sourceType: string;
  targetType: string;
  remark?: string;
}

const base = '/type-mappings';

const list = (): Promise<TypeMapping[]> => {
  return Requests.get(`${base}`) as unknown as Promise<TypeMapping[]>;
};

const create = (mapping: TypeMapping): Promise<TypeMapping> => {
  return Requests.post(`${base}`, mapping) as unknown as Promise<TypeMapping>;
};

const update = (sourceType: string, mapping: TypeMapping): Promise<TypeMapping> => {
  return Requests.put(`${base}/${encodeURIComponent(sourceType)}`, mapping) as unknown as Promise<
    TypeMapping
  >;
};

const remove = (sourceType: string): Promise<void> => {
  return Requests.delete(`${base}/${encodeURIComponent(sourceType)}`) as unknown as Promise<void>;
};

export default { list, create, update, remove };
