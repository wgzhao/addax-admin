<template>
  <div class="top-nav-cluster">
    <template v-for="item in items" :key="item.title">
      <v-menu
        v-if="item.children"
        :min-width="0"
        open-on-hover
        open-on-click
        :open-delay="80"
        :close-delay="120"
      >
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            class="top-nav-btn"
            :class="{ 'top-nav-btn--active': isMenuActive(item) }"
            :variant="isMenuActive(item) ? 'flat' : 'text'"
            :color="isMenuActive(item) ? 'primary' : undefined"
            append-icon="mdi-chevron-down"
          >
            {{ item.title }}
          </v-btn>
        </template>
        <v-list density="compact" nav class="top-nav-menu-list">
          <v-list-item
            v-for="(child, index) in item.children"
            :key="`${item.title}-${index}`"
            class="top-nav-menu-item"
            :prepend-icon="child.icon || 'mdi-chevron-right'"
            :to="child.path ? { path: child.path } : undefined"
            slim
          >
            <v-list-item-title>{{ child.title }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
      <v-btn
        v-else
        class="top-nav-btn"
        :class="{ 'top-nav-btn--active': isPathActive(item.path) }"
        :variant="isPathActive(item.path) ? 'flat' : 'text'"
        :color="isPathActive(item.path) ? 'primary' : undefined"
        :to="{ path: item.path }"
      >
        {{ item.title }}
      </v-btn>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { isMenuActive as isMenuActiveFor, isPathActive as isPathActiveFor } from '@/config/navigation';
  import type { NavItem } from '@/config/navigation';

  defineProps<{ items: NavItem[] }>();

  const route = useRoute();

  const isPathActive = (path?: string) => isPathActiveFor(route.path, path);
  const isMenuActive = (item: NavItem) => isMenuActiveFor(route.path, item);
</script>

<style lang="scss" scoped>
  @use './topbar-menu-shared' as *;

  .top-nav-cluster {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  @media (max-width: 1280px) {
    .top-nav-cluster {
      gap: 4px;
    }
  }
</style>
