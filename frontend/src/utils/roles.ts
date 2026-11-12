export type Role = 'admin' | 'user' | 'guest';

export function normalizeRole(authority?: string): Role {
  let value = (authority || '').trim().toLowerCase();
  if (value.startsWith('role_')) {
    value = value.slice(5);
  }
  if (value === 'admin') return 'admin';
  if (value === 'guest') return 'guest';
  return 'user';
}

export function highestRole(authorities?: string[]): Role {
  const roles = (authorities || []).map(normalizeRole);
  if (roles.includes('admin')) return 'admin';
  if (roles.includes('user')) return 'user';
  return 'guest';
}
