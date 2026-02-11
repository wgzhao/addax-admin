<script setup lang="ts">
  const tips = [
    '欢迎使用 Addax Admin 数据平台。',
    '这里先提供简要说明，后续内容将逐步补充。',
    '如果需要技术支持，请使用右上角用户菜单中的反馈入口。'
  ]
</script>

<template>
  <v-container class="py-6">
    <v-card elevation="2" class="pa-6">
      <div class="text-h5 font-weight-medium mb-4">帮助文档</div>
      <div class="text-subtitle-2 text-medium-emphasis mb-6">
        快速了解平台的主要功能与使用方式。
      </div>
      <v-list density="compact">
        <v-list-item v-for="(item, index) in tips" :key="index">
          <v-list-item-title>{{ item }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-card>
  </v-container>
</template>
