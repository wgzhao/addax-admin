<template>
  <div v-if="loading" data-testid="detail-loading" class="detail-loading" aria-live="polite">
    <v-skeleton-loader type="card" />
    <div class="loading-grid">
      <v-skeleton-loader type="list-item-three-line@2" />
      <v-skeleton-loader type="list-item-three-line@2" />
    </div>
    <span class="sr-only">正在加载采集表详情</span>
  </div>

  <v-alert v-else type="error" variant="tonal" class="load-error">
    <div class="load-error__body">
      <div>
        <div class="load-error__title">采集表详情加载失败</div>
        <div>{{ error }}</div>
      </div>
      <v-btn variant="flat" color="error" prepend-icon="mdi-refresh" @click="$emit('retry')">
        重新加载
      </v-btn>
    </div>
  </v-alert>
</template>

<script setup lang="ts">
  defineProps<{
    loading: boolean;
    error: string;
  }>();

  defineEmits(['retry']);
</script>

<style scoped>
  .detail-loading {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .loading-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .load-error__body {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .load-error__title {
    margin-bottom: 4px;
    font-weight: 700;
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  @media (max-width: 760px) {
    .load-error__body {
      align-items: stretch;
      flex-direction: column;
    }

    .loading-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
