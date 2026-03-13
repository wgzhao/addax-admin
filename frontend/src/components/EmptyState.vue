<template>
  <v-row justify="center" class="py-12">
    <v-col cols="12" md="8">
      <v-card class="pa-8 text-center empty-card" elevation="1" role="region" aria-label="Empty state">
        <div class="mx-auto mb-4">
          <v-img
            v-if="illustration"
            :src="illustration"
            :alt="title"
            max-width="180"
            class="mx-auto mb-4"
            contain
          />

          <div v-else class="empty-illustration mx-auto mb-4" role="img" aria-hidden="true">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" class="empty-svg" aria-hidden="true">
              <rect x="12" y="20" width="96" height="68" rx="6" fill="none" stroke="currentColor" stroke-width="3" stroke-dasharray="6 6" opacity="0.28"/>
              <path d="M36 46h48M36 62h48" stroke="currentColor" stroke-width="3" stroke-linecap="round" opacity="0.5"/>
              <circle cx="60" cy="92" r="6" fill="currentColor" opacity="0.06"/>
              <path d="M56 88l8 8 8-8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" opacity="0.6"/>
            </svg>
          </div>
        </div>

        <div class="text-h5 font-weight-medium mt-2">{{ title }}</div>

        <div v-if="description" class="text-subtitle-1 my-4" v-html="description" aria-live="polite"></div>

        <div class="d-flex justify-center flex-wrap actions">
          <v-btn
            class="ma-2"
            :color="primary.color || 'primary'"
            large
            @click="$emit('primary')"
          >
            <v-icon left v-if="primary.icon">{{ primary.icon }}</v-icon>
            {{ primary.label }}
          </v-btn>

          <v-btn
            v-if="secondary"
            class="ma-2"
            variant="text"
            @click="$emit('secondary')"
          >
            <v-icon left v-if="secondary.icon">{{ secondary.icon }}</v-icon>
            {{ secondary.label }}
          </v-btn>
        </div>

        <div v-if="actions && actions.length" class="mt-3">
          <v-btn
            v-for="(a, i) in actions"
            :key="i"
            variant="text"
            class="mx-2"
            @click="$emit('action', i)"
          >
            <v-icon left v-if="a.icon">{{ a.icon }}</v-icon>
            {{ a.label }}
          </v-btn>
        </div>

        <slot name="extra"></slot>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
interface Action {
  label: string
  icon?: string
  color?: string
}

const props = defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '当前没有可显示的数据。可通过“创建”或“导入”来快速开始。' },
  illustration: { type: String, default: '' },
  primary: {
    type: Object as () => Action & { color?: string },
    default: () => ({ label: '创建', icon: 'mdi-plus' })
  },
  secondary: { type: Object as () => Action | null, default: null },
  actions: { type: Array as () => Action[], default: () => [] }
})

defineEmits(['primary', 'secondary', 'action'])
</script>

<style scoped>
.empty-card {
  border-radius: 12px;
}
.actions .v-btn {
  min-width: 160px;
}
.empty-card .v-img img {
  object-fit: contain;
}
</style>
