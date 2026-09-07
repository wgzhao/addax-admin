import type { DataTableHeader } from 'vuetify';

// Build a CSV string from Vuetify table headers and rows. Headers carrying a
// `value` function evaluate it per row; plain keys are read from the row.
export function buildCsv(headers: DataTableHeader[], items: any[]): string {
  const exportHeaders = headers.filter(h => h.key && h.key !== 'actions');
  const titleRow = exportHeaders.map(h => h.title);
  const rows = items.map((row: any) =>
    exportHeaders
      .map((header: any) => {
        if (typeof header.value === 'function') return header.value(row);
        const key = String(header.key);
        return row?.[key] ?? '';
      })
      .join(',')
  );
  return [titleRow.join(','), ...rows].join('\n');
}

export function downloadCsv(filename: string, csv: string) {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${filename}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
