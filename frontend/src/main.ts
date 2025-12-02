/**
 * main.ts
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Plugins
import { registerPlugins } from "@/plugins";
import { useAuthStore } from '@/stores/auth';

// Components
import App from "./App.vue";

// Composables
import { createApp } from "vue";

const app = createApp(App);

registerPlugins(app);

// 在应用初始化时加载本地存储中的 Token
const authStore = useAuthStore();
authStore.loadTokenFromStorage();

app.mount("#app");
