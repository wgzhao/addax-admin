<template>
  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">运行快照</div>
        </div>
      </div>

      <div class="snapshot-grid">
        <div class="snapshot-item">
          <span class="snapshot-label">最近采集开始</span>
          <strong>{{ table.startTime || '-' }}</strong>
        </div>
        <div class="snapshot-item">
          <span class="snapshot-label">最近采集结束</span>
          <strong>{{ table.endTime || '-' }}</strong>
        </div>
        <div class="snapshot-item">
          <span class="snapshot-label">继承调度时间</span>
          <strong>{{ inheritedStartAt || '-' }}</strong>
        </div>
        <div class="snapshot-item">
          <span class="snapshot-label">目标映射</span>
          <strong>{{ targetIdentity }}</strong>
        </div>
      </div>
    </v-card-text>
  </v-card>

  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">备注与辅助说明</div>
        </div>
      </div>

      <div class="helper-actions">
        <v-btn
          variant="tonal"
          color="info"
          prepend-icon="mdi-lightbulb-outline"
          @click="$emit('show-placeholder-help')"
        >
          源表动态命名说明
        </v-btn>
        <v-btn
          variant="tonal"
          color="info"
          prepend-icon="mdi-filter-outline"
          @click="$emit('show-filter-help')"
        >
          过滤规则说明
        </v-btn>
      </div>

      <div class="field-block mt-4">
        <div class="field-label">备注</div>
        <v-textarea
          v-model="table.remark"
          variant="outlined"
          density="comfortable"
          placeholder="可选备注"
          rows="4"
          auto-grow
          hide-details="auto"
        />
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { VEtlWithSource } from '@/types/database';
  import { inheritedStartAt as inheritedStartAtOf, targetIdentity as targetIdentityOf } from './table-labels';

  defineEmits(['show-placeholder-help', 'show-filter-help']);

  // Object-level model: mutate fields only, never assign the whole object
  const table = defineModel<VEtlWithSource>('table', { required: true });

  const inheritedStartAt = computed(() => inheritedStartAtOf(table.value));
  const targetIdentity = computed(() => targetIdentityOf(table.value));
</script>

<style lang="scss" scoped>
  @import './_detail-shared.scss';

  .snapshot-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .snapshot-item {
    padding: 14px 16px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(
        180deg,
        rgba(var(--v-theme-primary), 0.055),
        rgba(var(--v-theme-primary), 0.01)
      ),
      rgb(var(--v-theme-surface));
  }

  .snapshot-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .helper-actions {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
  }

  @media (max-width: 1180px) {
    .snapshot-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 760px) {
    .snapshot-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
