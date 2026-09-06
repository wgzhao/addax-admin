<template>
  <v-row dense class="section-grid">
    <v-col cols="12" md="12">
      <v-card flat class="ds-card table-card section-card">
        <v-card-text class="section-body">
          <div class="section-header">
            <div class="section-title">{{ title }}</div>
            <div class="header-actions">
              <v-btn
                size="small"
                variant="outlined"
                color="secondary"
                prepend-icon="mdi-download"
                :disabled="!items.length"
                @click="exportCsv"
              >
                导出
              </v-btn>
            </div>
          </div>
          <v-data-table
            :items="items"
            :headers="headers"
            density="default"
            :sort-by="sortBy"
            class="insight-table"
            hide-no-data
          >
            <template #item.actions="{ item }">
              <slot name="item.actions" :item="item" />
            </template>
            <template #item.missing_dates="{ item }">
              <slot name="item.missing_dates" :item="item" />
            </template>
          </v-data-table>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
  import type { DataTableHeader } from 'vuetify';
  import { buildCsv, downloadCsv } from '@/utils/csv';

  const props = defineProps<{
    title: string;
    headers: DataTableHeader[];
    sortBy: any[];
    items: any[];
    exportName: string;
  }>();

  const exportCsv = () => {
    if (!props.items.length) return;
    downloadCsv(props.exportName, buildCsv(props.headers, props.items));
  };
</script>

<style scoped>
  .section-grid {
    margin-bottom: 8px;
  }

  .section-body {
    padding-top: 8px;
  }

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 4px 0 12px;
    font-weight: 600;
  }

  .section-title {
    font-size: 16px;
  }

  .header-actions {
    display: inline-flex;
    gap: 8px;
  }

  .insight-table {
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    overflow: hidden;
  }

  @media (max-width: 960px) {
    .header-actions {
      width: 100%;
      justify-content: flex-start;
    }
  }
</style>
