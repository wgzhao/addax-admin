import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useTheme } from 'vuetify'
import { useThemeStore } from '@/stores/theme-store'

export const useAppTheme = () => {
  const themeStore = useThemeStore()
  const theme = useTheme()

  const themeName = computed(() => themeStore.theme)
  const isDarkTheme = computed(() => themeStore.theme === 'dark')

  const toggleTheme = () => {
    themeStore.toggleTheme()
  }

  const resetToSystemTheme = () => {
    themeStore.resetToSystemTheme()
  }

  watch(
    () => themeStore.theme,
    (value) => {
      theme.global.name.value = value
    },
    { immediate: true }
  )

  let mediaQuery: MediaQueryList | null = null
  const handleSystemThemeChange = (event: MediaQueryListEvent) => {
    if (themeStore.hasUserPreference) return
    themeStore.setSystemTheme(event.matches ? 'dark' : 'light')
  }

  onMounted(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

    if (!themeStore.hasUserPreference) {
      themeStore.setSystemTheme(mediaQuery.matches ? 'dark' : 'light')
    }

    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleSystemThemeChange)
    } else {
      mediaQuery.addListener(handleSystemThemeChange)
    }
  })

  onUnmounted(() => {
    if (!mediaQuery) return
    if (mediaQuery.removeEventListener) {
      mediaQuery.removeEventListener('change', handleSystemThemeChange)
    } else {
      mediaQuery.removeListener(handleSystemThemeChange)
    }
  })

  return {
    themeStore,
    themeName,
    isDarkTheme,
    toggleTheme,
    resetToSystemTheme
  }
}
