<template>
  <v-navigation-drawer
    v-if="modelValue"
    :model-value="modelValue"
    temporary
    location="left"
    width="320"
    class="nav-drawer"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <div class="nav-drawer__header">
      <div class="nav-drawer__title">统一采集管理系统</div>
      <v-btn
        variant="text"
        icon="mdi-close"
        size="small"
        aria-label="关闭导航菜单"
        @click="emit('update:modelValue', false)"
      />
    </div>

    <v-list nav density="comfortable" class="nav-drawer__list">
      <template v-for="item in items" :key="item.title">
        <v-list-item
          v-if="!item.children"
          :to="item.path"
          :title="item.title"
          :active="isPathActive(item.path)"
          @click="close"
        />
        <v-list-group v-else :value="item.title">
          <template #activator="{ props }">
            <v-list-item v-bind="props" :title="item.title" :active="isMenuActive(item)" />
          </template>
          <v-list-item
            v-for="child in item.children"
            :key="`${item.title}-${child.title}`"
            :title="child.title"
            :prepend-icon="child.icon || 'mdi-chevron-right'"
            :active="isPathActive(child.path)"
            @click="handleChild(child)"
          />
        </v-list-group>
      </template>
    </v-list>

    <v-divider class="my-2" />

    <v-list nav density="comfortable" class="nav-drawer__list">
      <v-list-subheader>快捷新增</v-list-subheader>
      <v-list-item
        v-for="entry in createItems"
        :key="entry.path"
        :title="entry.title"
        :prepend-icon="entry.icon"
        @click="goCreate(entry.path)"
      />
    </v-list>
  </v-navigation-drawer>
</template>

<script setup lang="ts">
  import { useRoute, useRouter } from 'vue-router';
  import { isMenuActive as isMenuActiveFor, isPathActive as isPathActiveFor } from './build-nav';
  import type { NavChildItem, NavItem } from './build-nav';

  defineProps<{ modelValue: boolean; items: NavItem[] }>();
  const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();

  const route = useRoute();
  const router = useRouter();

  const createItems = [
    { title: '新增采集源', icon: 'mdi-database-cog', path: '/source?action=create' },
    { title: '新增目标端', icon: 'mdi-database-export', path: '/target?action=create' },
    { title: '新增采集表', icon: 'mdi-table-plus', path: '/table/batch-add' },
  ];

  const isPathActive = (path?: string) => isPathActiveFor(route.path, path);
  const isMenuActive = (item: NavItem) => isMenuActiveFor(route.path, item);

  const close = () => emit('update:modelValue', false);

  const handleChild = (child: NavChildItem) => {
    if (child.onClick) {
      child.onClick();
    } else if (child.path) {
      router.push({ path: child.path });
    }
    close();
  };

  const goCreate = (path: string) => {
    router.push(path);
    close();
  };
</script>

<style scoped>
  .nav-drawer__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 12px 12px 16px;
  }

  .nav-drawer__title {
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.2px;
  }

  .nav-drawer__list {
    padding-top: 4px;
  }
</style>
