import { mount, MountingOptions } from '@vue/test-utils';
import { createVuetify } from 'vuetify';
import * as vuetifyComponents from 'vuetify/components';

// Mirror the production defaults from src/plugins/vuetify.ts so component
// tests see the same prop defaults the app does.
export function testVuetify() {
  return createVuetify({
    // Vuetify 4 no longer registers built-in components at runtime — the
    // vite-plugin-vuetify build transform handles it in production. Tests
    // must register the full set explicitly.
    components: vuetifyComponents,
    defaults: {
      VTextField: { variant: 'outlined' },
    },
  });
}

export function mountWithVuetify(component: any, options: MountingOptions<any> = {}) {
  return mount(component, {
    ...options,
    global: {
      plugins: [testVuetify()],
      ...(options.global ?? {}),
    },
  });
}
