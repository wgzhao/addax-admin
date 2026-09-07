// Helpers for the reader/writer plugin config fields: pretty-print any stored
// value for editing, and parse/validate the edited text before saving.

export const toJsonText = (value: unknown): string => {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return '';
  }
};

export const parseJsonObjectOrNull = (
  value: string,
  fieldName: string
): Record<string, unknown> | null => {
  const raw = value.trim();
  if (!raw) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error(`${fieldName} 不是合法 JSON`);
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${fieldName} 必须为 JSON 对象`);
  }
  return parsed as Record<string, unknown>;
};

// Vuetify rule: empty is fine, otherwise the value must parse as a JSON object
export const jsonObjectOrEmpty = (v: unknown) => {
  if (v === null || v === undefined || v === '') return true;
  try {
    const parsed = JSON.parse(String(v));
    return (!!parsed && typeof parsed === 'object' && !Array.isArray(parsed)) || '必须为 JSON 对象';
  } catch {
    return '请输入合法 JSON';
  }
};
