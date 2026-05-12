<template>
  <v-footer height="40" app>
    <a
      v-for="item in items"
      :key="item.title"
      :href="item.href"
      :title="item.title"
      class="d-inline-block mx-2 text-decoration-none"
      ref="noopener noreferrer"
      target="_blank"
    >
      <v-icon :icon="item.icon" />
    </a>
    <v-spacer></v-spacer>

    <v-btn variant="plain" small class="mr-2">
      &copy; 2023-{{ new Date().getFullYear() }} &nbsp;wgzhao
    </v-btn>
     <v-btn variant="plain" small class="mr-2">
    Version: {{ APP_VERSION }}
    </v-btn>
  </v-footer>
</template>
<script setup lang="ts">

import { APP_VERSION } from '@/config/version'

const items = [
  {
    title: "Addax-Admin GitHub",
    icon: `mdi-github`,
    href: "https://github.com/wgzhao/addax-admin",
  },
];
</script>
