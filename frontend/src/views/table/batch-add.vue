<template>
  <div class="batch-add-page page-shell">
    <PageHeader
      title="批量新增采集表"
      subtitle="选择采集范围，配置目标策略，确认字段映射后再创建"
    />

    <v-card flat class="ds-card batch-add-card">
      <v-card-text class="ds-card__content">
        <BatchAdd tid="-1" />
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import BatchAdd from '@/components/table/BatchAdd.vue';
  import PageHeader from '@/components/PageHeader.vue';
</script>

<style scoped>
  /* Why: the wizard's action bar is sticky, so the wrapping card must not clip it */
  .batch-add-card {
    overflow: visible;
  }
</style>
