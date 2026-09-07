<template>
  <v-dialog v-model="open" max-width="760">
    <v-card>
      <v-card-title class="text-subtitle-1 font-weight-medium">缺失日期明细</v-card-title>
      <v-card-text>
        <div class="mb-3">目标表：{{ item?.source_db }}.{{ item?.source_table }}</div>
        <div class="mb-3">缺失天数：{{ dates.length }}</div>
        <div class="missing-date-chips">
          <v-chip v-for="date in dates" :key="date" size="small" color="warning" variant="tonal">
            {{ date }}
          </v-chip>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="open = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  defineProps<{ item: any; dates: string[] }>();
  const open = defineModel<boolean>({ default: false });
</script>

<style scoped>
  .missing-date-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    max-height: 320px;
    overflow: auto;
  }
</style>
