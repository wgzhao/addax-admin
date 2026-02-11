<template>
  <v-app :theme="themeName">
    <v-main>
      <v-container fluid>
        <router-view />
        <!-- 登录页面内容将渲染在此 -->
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
  import { useAppTheme } from '@/composables/useAppTheme'

  const { themeName } = useAppTheme()
</script>
