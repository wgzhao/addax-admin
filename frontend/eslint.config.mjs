import vue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';
import prettier from 'eslint-config-prettier/flat';
import globals from 'globals';

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'coverage/**',
      'playwright-report/**',
      'test-results/**',
      // unplugin auto-generated and vue-tsc build artifacts
      'src/types/components.d.ts',
      'src/types/vue-router.d.ts',
      'typed-router.d.ts',
      'vite.config.js',
      'vite.config.d.ts',
      'vitest.config.js',
      'vitest.config.d.ts',
    ],
  },
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    // Let TS-aware parsing handle script blocks inside SFCs
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser },
    },
  },
  {
    // Lenient on purpose: vue-tsc (noUnusedLocals) and the TS compiler cover
    // unused vars; strict rules would flood the first baseline with noise.
    files: ['**/*.ts', '**/*.vue'],
    languageOptions: { globals: { ...globals.browser } },
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
      'no-unused-vars': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
      // False positive on Vuetify's dotted data-table slots: the Vue compiler
      // parses v-slot:item.sourceTable as a static slot name "item.sourceTable"
      // (Vue 3 has no slot modifiers at all), while vue-eslint-parser models
      // the dot suffix as a modifier and flags it.
      'vue/valid-v-slot': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
      'vue/require-explicit-emits': 'off',
      'vue/require-default-prop': 'off',
      'vue/require-prop-types': 'off',
      'no-console': 'off',
    },
  },
  {
    // Node-side files: build configs and tests (unit specs run in jsdom)
    files: ['vite.config.ts', 'vitest.config.ts', 'playwright.config.ts', 'eslint.config.mjs'],
    languageOptions: { globals: { ...globals.node } },
  },
  {
    files: ['tests/**/*.ts'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
  prettier,
);
