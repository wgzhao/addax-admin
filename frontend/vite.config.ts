// Plugins
import Components from 'unplugin-vue-components/vite'
import Vue from '@vitejs/plugin-vue'
import Vuetify, { transformAssetUrls } from 'vite-plugin-vuetify'
import Fonts from 'unplugin-fonts/vite'
import VueRouter from 'unplugin-vue-router/vite'
import Layouts from "vite-plugin-vue-layouts-next";
import { loadEnv } from "vite";

// Utilities
import { defineConfig } from "vite";
import { fileURLToPath, URL } from "node:url";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    plugins: [
      VueRouter({
        routesFolder: [
          {
            src: "src/views",
            path: "",
            exclude: (exclude) => exclude.concat(['**/components/**', '**/login.vue','**/personal-settings.vue'])
          }
        ],
        dts: "src/types/vue-router.d.ts",
      }),
      Layouts({
        layoutsDirs: "src/layouts",
        pagesDirs: "src/views",
        defaultLayout: 'Default'
      }),
      Vue({
        template: { transformAssetUrls }
      }),
      // https://github.com/vuetifyjs/vuetify-loader/tree/master/packages/vite-plugin#readme
      Vuetify({
        autoImport: true,
        styles: {
          configFile: "src/styles/settings.scss"
        }
      }),
      Components({
        dts: "src/types/components.d.ts",
      }),
      Fonts({
        fontsource: {
          families: [
            {
              name: 'Roboto',
              weights: [100, 300, 400, 500, 700, 900],
              styles: ['normal', 'italic'],
            },
          ],
        },
      }),
    ],
    optimizeDeps: {
      exclude: [
        'vuetify',
        'vue-router',
        'unplugin-vue-router/runtime',
        'unplugin-vue-router/data-loaders',
        'unplugin-vue-router/data-loaders/basic',
      ],
    },
    define: { "process.env": {} },
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url))
      },
      extensions: [
        ".json",
        ".jsx",
        ".mts",
        ".ts",
        ".vue",
        ".json"
      ]
    },
    server: {
      host: "0.0.0.0",
      port: 3030,
      proxy: {
        [env.VITE_API_BASE_URL]: {
          target: env.VITE_API_HOST,
          changeOrigin: false
        }
      }
    }
  };
});
