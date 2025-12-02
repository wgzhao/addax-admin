// Styles
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'

// Composable
import { createVuetify } from 'vuetify'
import '@/styles/overrides.scss'
import customTheme from './theme'
import { aliases, mdi } from 'vuetify/iconsets/mdi'

// https://vuetifyjs.com/en/introduction/why-vuetify/#feature-guides
export default createVuetify({
  theme: {
    defaultTheme: 'dark',
    themes: customTheme
  },
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: {
      mdi
    }
  },
  defaults: {
    global: {
      ripple: true
    },
    VTextField: {
      variant: 'outlined'
    }
  }
})
