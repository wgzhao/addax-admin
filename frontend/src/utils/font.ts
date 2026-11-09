// Why: Chart.js renders into its own canvas, so it does not inherit the app's
// CJK font stack; every chart loader imports this single value.
export const APP_FONT_FAMILY =
  "'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei UI', 'Microsoft YaHei', 'Noto Sans CJK SC', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif";
