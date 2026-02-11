<script setup lang="ts">
  import { useAppTheme } from '@/composables/useAppTheme'
  import Topbar from './default/Topbar.vue'
  import Footer from './default/Footer.vue'

  const { themeName } = useAppTheme()
</script>

<template>
  <v-responsive class="border rounded">
  <v-app :theme="themeName">
    <Topbar />
    <v-main class="align-center justify-center" style="min-height: 600px">
      <router-view />
    </v-main>
    <Footer />
  </v-app>
  </v-responsive>
</template>
