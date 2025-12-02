<script setup lang="ts">
  import Topbar from './default/Topbar.vue'
  import Footer from './default/Footer.vue'
</script>

<template>
  <v-app>
    <Topbar />
    <v-main class="align-center justify-center" style="min-height: 600px">
      <router-view />
    </v-main>
    <Footer />
  </v-app>
</template>
