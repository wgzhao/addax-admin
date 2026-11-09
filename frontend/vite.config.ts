// Plugins
import Components from 'unplugin-vue-components/vite'
import Vue from '@vitejs/plugin-vue'
import Vuetify, { transformAssetUrls } from 'vite-plugin-vuetify'
import Fonts from 'unplugin-fonts/vite'
import VueRouter from 'unplugin-vue-router/vite'
import Layouts from "vite-plugin-vue-layouts-next";
import { loadEnv } from "vite";
import { readFileSync } from "node:fs";
import { execSync } from "node:child_process";

// Utilities
import { defineConfig } from "vite";
import { fileURLToPath, URL } from "node:url";

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  const isBuild = command === "build";
  const env = loadEnv(mode, process.cwd());
  const pkg = JSON.parse(
    readFileSync(new URL("./package.json", import.meta.url), "utf-8")
  );
  const gitSha = (() => {
    try {
      return execSync("git rev-parse --short HEAD", {
        cwd: fileURLToPath(new URL(".", import.meta.url)),
        stdio: ["ignore", "pipe", "ignore"],
      })
        .toString()
        .trim();
    } catch {
      return "dev";
    }
  })();
  const buildTime = new Date().toISOString();
  return {
    plugins: [
      VueRouter({
        routesFolder: [
          {
            src: "src/views",
            path: "",
            exclude: (exclude) => exclude.concat([
              '**/components/**',
              '**/login.vue',
              '**/change-password.vue',
              '**/not-found.vue',
              '**/forbidden.vue',
            ])
          }
        ],
        dts: "src/types/vue-router.d.ts",
        // Disable chokidar watchers during production builds to avoid hitting
        // OS file watch limits after upgrading the routing/layout plugins.
        watch: !isBuild,
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
        styles: 'sass'
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
    define: {
      "process.env": {},
      "import.meta.env.VITE_APP_VERSION": JSON.stringify(pkg.version || "dev"),
      "import.meta.env.VITE_APP_BUILD_SHA": JSON.stringify(
        env.VITE_APP_BUILD_SHA || gitSha
      ),
      "import.meta.env.VITE_APP_BUILD_TIME": JSON.stringify(
        env.VITE_APP_BUILD_TIME || buildTime
      ),
      "import.meta.env.VITE_APP_ENV": JSON.stringify(env.VITE_APP_ENV || mode),
    },
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url))
      },
      extensions: [
        ".json",
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
