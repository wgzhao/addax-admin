import { defineConfig } from 'vitest/config';
import Vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

// Standalone config on purpose: vite.config.ts plugins (unplugin-vue-router,
// vite-plugin-vuetify autoImport, unplugin-fonts) write dts files and spawn
// watch/subprocess work that unit test runs do not need.
export default defineConfig({
  plugins: [Vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/unit/**/*.spec.ts'],
    setupFiles: ['tests/unit/setup.ts'],
    clearMocks: true,
    restoreMocks: true,
    server: {
      deps: {
        // Vuetify ships per-component .css imports that break under Node's
        // native ESM loader; inlining routes them through vite instead.
        inline: ['vuetify'],
      },
    },
  },
});
