<script setup lang="ts">
  import PageHeader from '@/components/PageHeader.vue';

  const tips = [
    '欢迎使用 Addax Admin 数据平台。',
    '这里先提供简要说明，后续内容将逐步补充。',
    '如果需要技术支持，请使用右上角用户菜单中的反馈入口。',
  ];
</script>

<template>
  <div class="page-shell">
    <PageHeader title="帮助文档" subtitle="快速了解平台的主要功能与使用方式。" />
    <v-card flat class="ds-card">
      <v-card-text class="ds-card__content">
        <v-list density="comfortable">
          <v-list-item v-for="(item, index) in tips" :key="index">
            <v-list-item-title>{{ item }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </div>
</template>

<route lang="json">
{
  "meta": {
    "title": "帮助文档",
    "icon": "mdi-book-open-variant",
    "requiresAuth": true,
    "navHidden": true
  }
}
</route>
