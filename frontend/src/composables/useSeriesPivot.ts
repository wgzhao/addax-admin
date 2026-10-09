// 长表（label + series + value 三列）转成图表所需的宽表：labels + series[]
// 缺失组合补 0，保证所有 series 的 data 长度与 labels 一致

export interface PivotOptions<T> {
  labelField: keyof T;
  seriesField: keyof T;
  valueField: keyof T;
  seriesOrder?: string[];
  seriesLabels?: Record<string, string>;
}

export interface PivotResult {
  labels: string[];
  series: { key: string; label: string; data: number[] }[];
}

export function pivotSeries<T extends object>(rows: T[], options: PivotOptions<T>): PivotResult {
  const { labelField, seriesField, valueField, seriesOrder, seriesLabels } = options;
  const at = (row: T, field: keyof T): unknown => row[field];
  const labels: string[] = [];
  const seenLabels = new Set<string>();
  const keys = new Set<string>();

  for (const row of rows) {
    const label = String(at(row, labelField) ?? '');
    const key = String(at(row, seriesField) ?? '');
    if (!seenLabels.has(label)) {
      seenLabels.add(label);
      labels.push(label);
    }
    if (key !== '') keys.add(key);
  }

  // 排序：显式顺序优先，其余按字典序追加，保证颜色分配稳定
  const orderedKeys = seriesOrder
    ? [
        ...seriesOrder.filter(k => keys.has(k)),
        ...[...keys].filter(k => !seriesOrder.includes(k)).sort(),
      ]
    : [...keys].sort();

  const labelIndex = new Map(labels.map((label, i) => [label, i]));
  const dataByKey = new Map<string, number[]>(
    orderedKeys.map(key => [key, new Array(labels.length).fill(0)])
  );

  for (const row of rows) {
    const label = String(at(row, labelField) ?? '');
    const key = String(at(row, seriesField) ?? '');
    const value = at(row, valueField);
    const index = labelIndex.get(label);
    const bucket = dataByKey.get(key);
    if (index === undefined || !bucket) continue;
    bucket[index] += typeof value === 'number' ? value : 0;
  }

  return {
    labels,
    series: orderedKeys.map(key => ({
      key,
      label: seriesLabels?.[key] ?? key,
      data: dataByKey.get(key) ?? [],
    })),
  };
}
