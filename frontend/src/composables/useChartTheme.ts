import { computed } from 'vue';
import { useTheme } from 'vuetify';
import { CATEGORICAL_DARK, CATEGORICAL_LIGHT } from '@/utils/chart-colors';

// 将 #rgb / #rrggbb 转成 rgba 字符串，非法输入回退到主题蓝
export function hexToRgba(hex: string, alpha: number): string {
  const normalized = hex.replace('#', '').trim();
  const fullHex =
    normalized.length === 3
      ? normalized
          .split('')
          .map(ch => ch + ch)
          .join('')
      : normalized;

  if (!/^[0-9a-fA-F]{6}$/.test(fullHex)) {
    return `rgba(11, 107, 203, ${alpha})`;
  }

  const intValue = Number.parseInt(fullHex, 16);
  const r = (intValue >> 16) & 255;
  const g = (intValue >> 8) & 255;
  const b = intValue & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// 图表主题色统一出口：所有颜色从 Vuetify 主题实时解析，深浅色切换无需刷新
export function useChartTheme() {
  const vuetifyTheme = useTheme();
  const isDark = computed(() => vuetifyTheme.current.value.dark);
  const colors = computed(() => vuetifyTheme.current.value.colors as Record<string, string>);

  const resolve = (token: string, fallback: string): string => colors.value[token] ?? fallback;

  const onSurface = computed(() => resolve('on-surface', '#18212B'));
  const surface = computed(() => resolve('surface', '#FFFFFF'));
  const grid = computed(() => hexToRgba(onSurface.value, isDark.value ? 0.18 : 0.1));

  const palette = computed(() => (isDark.value ? CATEGORICAL_DARK : CATEGORICAL_LIGHT));

  const tooltip = computed(() => ({
    backgroundColor: hexToRgba(surface.value, isDark.value ? 0.95 : 0.98),
    borderColor: hexToRgba(onSurface.value, 0.2),
    borderWidth: 1,
    titleColor: onSurface.value,
    bodyColor: onSurface.value,
  }));

  return { isDark, colors, resolve, onSurface, surface, grid, palette, tooltip, hexToRgba };
}
