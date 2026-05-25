<script setup lang="ts">
  import Topbar from './default/Topbar.vue'
  import Footer from './default/Footer.vue'
</script>

<template>
  <v-app>
    <Topbar />
    <v-main class="layout-main">
      <router-view />
    </v-main>
    <Footer />
  </v-app>
</template>

<style scoped>
  .layout-main {
    min-height: 600px;
  }
</style>
