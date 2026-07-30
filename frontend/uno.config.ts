import { defineConfig, presetUno } from 'unocss'
import { presetVuetify } from 'unocss-preset-vuetify'

export default defineConfig({
  presets: [
    presetUno(),
    presetVuetify(),
  ],
  // Align dark mode selector with Vuetify
  dark: '.v-theme--dark',
})
