export const APP_VERSION: string = import.meta.env.VITE_APP_VERSION || 'dev';
export const APP_BUILD_SHA: string = import.meta.env.VITE_APP_BUILD_SHA || 'dev';
export const APP_BUILD_TIME: string = import.meta.env.VITE_APP_BUILD_TIME || '';
export const APP_ENV: string =
  import.meta.env.VITE_APP_ENV || (import.meta.env.DEV ? 'development' : 'production');

const ENV_LABELS: Record<string, string> = {
  production: '生产',
  staging: '预发',
  test: '测试',
  development: '开发',
};

export const APP_ENV_LABEL = ENV_LABELS[APP_ENV] || APP_ENV;

export const buildSummary = () =>
  [APP_VERSION, APP_BUILD_SHA && APP_BUILD_SHA !== 'dev' ? APP_BUILD_SHA : '']
    .filter(Boolean)
    .join(' · ');
