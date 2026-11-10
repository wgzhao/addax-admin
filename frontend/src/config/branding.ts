const pick = (value: string | undefined, fallback: string): string => {
  const trimmed = (value || '').trim();
  return trimmed || fallback;
};

export const APP_NAME = pick(import.meta.env.VITE_APP_NAME, 'Addax Admin');
export const APP_SUBTITLE = pick(import.meta.env.VITE_APP_SUBTITLE, '数据采集管理平台');
export const APP_COMPANY = pick(import.meta.env.VITE_APP_COMPANY, 'Addax Admin');
export const APP_LOGO = pick(import.meta.env.VITE_APP_LOGO, '/brand-mark.svg');
export const APP_LICENSE = pick(import.meta.env.VITE_APP_LICENSE, 'Apache-2.0');
export const APP_EDITION = pick(import.meta.env.VITE_APP_EDITION, 'Community');
export const APP_SUPPORT_URL = pick(
  import.meta.env.VITE_APP_SUPPORT_URL,
  'https://github.com/wgzhao/addax-admin/issues/new'
);
export const APP_DOCS_URL = pick(
  import.meta.env.VITE_APP_DOCS_URL,
  'https://github.com/wgzhao/addax-admin#readme'
);
export const APP_RELEASES_URL = 'https://github.com/wgzhao/addax-admin/releases';
export const APP_IS_DEMO = import.meta.env.VITE_APP_DEMO === 'true';
