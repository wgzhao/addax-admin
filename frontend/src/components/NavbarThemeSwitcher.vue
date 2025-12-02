<script setup lang="ts">
  import { useTheme } from 'vuetify'
  import { useCycleList } from '@vueuse/core'
  import { watch } from 'vue'

  const themes = [
    {
      name: 'light',
      icon: 'fa-regular fa-sun'
    },
    {
      name: 'dark',
      icon: 'fa-regular fa-moon'
    }
  ]

  const IconBtn = {
    icon: true,
    size: 'small',
    color: 'default',
    variant: 'text',
    VIcon: {
      size: 24
    }
  }
  const VTooltip = {
    // set v-tooltip default location to top
    location: 'top'
  }

  // const props = defineProps({
  //   themes: {
  //     type: Array,
  //     required: true,
  //   },
  // })

  const { name: themeName, global: globalTheme } = useTheme()

  const {
    state: currentThemeName,
    next: getNextThemeName,
    index: currentThemeIndex
  } = useCycleList(
    themes.map((t) => t.name),
    { initialValue: themeName }
  )

  const changeTheme = () => {
    globalTheme.name.value = getNextThemeName()
  }

  // Update icon if theme is changed from other sources
  watch(
    () => globalTheme.name.value,
    (val) => {
      currentThemeName.value = val
    }
  )
</script>

<template>
  <IconBtn @click="changeTheme">
    <VIcon :icon="themes[currentThemeIndex].icon" />
    <VTooltip activator="parent" open-delay="1000" scroll-strategy="close">
      <span class="text-capitalize">{{ currentThemeName }} theme</span>
    </VTooltip>
  </IconBtn>
</template>
