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
    <div
      class="text-caption text-disabled"
      style="position: absolute; right: 16px"
    >
      &copy; 2023-{{ new Date().getFullYear() }}
      <span class="d-none d-sm-inline-block">wgzhao</span>
      -
      <a
        class="text-decoration-none on-surface"
        href="https://github.com/wgzhao/addax-ui"
        rel="noopener noreferrer"
        target="_blank"
      >
        MIT License
      </a>
    </div>
  </v-footer>
</template>
<script setup lang="ts">
const items = [
  {
    title: "Addax-UI GitHub",
    icon: `mdi-github`,
    href: "https://github.com/wgzhao/addax-ui"
  }
];
</script>
