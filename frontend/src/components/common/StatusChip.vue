<template>
  <v-chip
    :color="meta.token"
    :size="size"
    variant="tonal"
    label
    class="status-chip"
    :title="meta.label"
  >
    <v-icon v-if="showIcon" start size="14">{{ meta.icon }}</v-icon>
    <span v-if="showLabel">{{ meta.label }}</span>
  </v-chip>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { statusMeta } from '@/utils/chart-colors';

  const props = withDefaults(
    defineProps<{
      status: string | null | undefined;
      size?: 'x-small' | 'small' | 'default';
      showIcon?: boolean;
      showLabel?: boolean;
    }>(),
    { size: 'small', showIcon: true, showLabel: true }
  );

  const meta = computed(() => statusMeta(props.status));
</script>

<style scoped>
  .status-chip {
    letter-spacing: 0;
    font-weight: 500;
  }
</style>
