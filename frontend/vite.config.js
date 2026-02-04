// Plugins
import Components from 'unplugin-vue-components/vite';
import Vue from '@vitejs/plugin-vue';
import Vuetify, { transformAssetUrls } from 'vite-plugin-vuetify';
import Fonts from 'unplugin-fonts/vite';
import VueRouter from 'unplugin-vue-router/vite';
import Layouts from "vite-plugin-vue-layouts-next";
import { loadEnv } from "vite";
import { readFileSync } from "node:fs";
// Utilities
import { defineConfig } from "vite";
import { fileURLToPath, URL } from "node:url";
// https://vitejs.dev/config/
export default defineConfig(function (_a) {
    var _b;
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd());
    var pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf-8"));
    return {
        plugins: [
            VueRouter({
                routesFolder: [
                    {
                        src: "src/views",
                        path: "",
                        exclude: function (exclude) { return exclude.concat(['**/components/**', '**/login.vue', '**/change-password.vue']); }
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
                template: { transformAssetUrls: transformAssetUrls }
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
        define: {
            "process.env": {},
            "import.meta.env.VITE_APP_VERSION": JSON.stringify(pkg.version || "dev"),
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
            proxy: (_b = {},
                _b[env.VITE_API_BASE_URL] = {
                    target: env.VITE_API_HOST,
                    changeOrigin: false
                },
                _b)
        }
    };
});
