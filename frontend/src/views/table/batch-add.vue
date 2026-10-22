<template>
  <div class="batch-add-page page-shell">
    <v-card flat>
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">批量新增采集表</div>
            <div class="page-subtitle">选择采集范围，配置目标策略，确认字段映射后再创建</div>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card batch-add-card">
      <v-card-text class="ds-card__content">
        <BatchAdd tid="-1" />
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import BatchAdd from '@/components/table/BatchAdd.vue';
</script>

<style scoped>
  /* Why: the wizard's action bar is sticky, so the wrapping card must not clip it */
  .batch-add-card {
    overflow: visible;
  }
</style>
