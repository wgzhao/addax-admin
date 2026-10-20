// Styles
import '@mdi/font/css/materialdesignicons.css';
import 'vuetify/styles';

// Composable
import { createVuetify } from 'vuetify';
import { zhHans } from 'vuetify/locale';
import '@/styles/overrides.scss';
import modernTheme from './modern-theme';
import { aliases, mdi } from 'vuetify/iconsets/mdi';

// https://vuetifyjs.com/en/introduction/why-vuetify/#feature-guides
export default createVuetify({
  // Why: the console is Chinese-only, so the built-in table/pagination copy must follow it
  locale: {
    locale: 'zhHans',
    fallback: 'en',
    messages: { zhHans },
  },
  theme: {
    defaultTheme: 'dark',
    themes: modernTheme,
  },
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: {
      mdi,
    },
  },
  defaults: {
    global: {
      ripple: true,
    },
    VTextField: {
      variant: 'outlined',
    },
  },
});
