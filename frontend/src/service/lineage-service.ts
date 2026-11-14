import Requests from '@/utils/requests';

export interface ColumnLineage {
  tid: number;
  sourceName: string;
  sourceDb: string;
  sourceTable: string;
  sourceColumn: string;
  targetName: string;
  targetDb: string;
  targetTable: string;
  targetColumn: string;
}

class LineageService {
  prefix = '/lineage';

  searchColumns(q: string): Promise<ColumnLineage[]> {
    return Requests.get(`${this.prefix}/columns`, { q }) as unknown as Promise<ColumnLineage[]>;
  }
}

export default new LineageService();
