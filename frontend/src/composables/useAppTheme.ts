import { computed, watch } from 'vue';
import { useTheme } from 'vuetify';
import { useThemeStore } from '@/stores/theme-store';

export const useAppTheme = () => {
  const themeStore = useThemeStore();
  const theme = useTheme();

  const themeName = computed(() => themeStore.theme);
  const isDarkTheme = computed(() => themeStore.theme === 'dark');

  const toggleTheme = () => {
    themeStore.toggleTheme();
  };

  const resetToSystemTheme = () => {
    themeStore.resetToSystemTheme();
  };

  watch(
    () => themeStore.theme,
    value => {
      theme.change(value);
    },
    { immediate: true }
  );

  return {
    themeStore,
    themeName,
    isDarkTheme,
    toggleTheme,
    resetToSystemTheme,
  };
};
