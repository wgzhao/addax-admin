<script setup lang="ts">
  import Topbar from './default/Topbar.vue';
  import Footer from './default/Footer.vue';
  import CommandPalette from '@/components/CommandPalette.vue';
</script>

<template>
  <v-app>
    <!-- Why: Vuetify 4's app bar/footer layout registration lives in
         v-layout, not v-app. Without it, `app` becomes a no-op and the footer
         cannot stay pinned to the viewport bottom. -->
    <v-layout>
      <a class="skip-link" href="#main-content">跳到主内容</a>
      <Topbar />
      <CommandPalette />
      <v-main id="main-content" class="layout-main" tabindex="-1">
        <router-view />
      </v-main>
      <Footer />
    </v-layout>
  </v-app>
</template>

<style scoped>
  .layout-main {
    min-height: 600px;
  }

  .layout-main:focus {
    outline: none;
  }
</style>
