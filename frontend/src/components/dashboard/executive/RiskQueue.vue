<template>
  <SectionCard title="风险与待办" subtitle="按严重级别排序，点击可直接下钻" icon="mdi-shield-alert-outline">
    <template #actions>
      <v-btn size="small" variant="text" append-icon="mdi-arrow-right" to="/monitor">
        全部
      </v-btn>
    </template>

    <SkeletonBlock v-if="loading" :height="180" />
    <ErrorState v-else-if="error && items.length === 0" :message="error" @retry="$emit('retry')" />
    <div v-else-if="items.length === 0" class="risk-empty">
      <v-icon size="22" color="success">mdi-shield-check-outline</v-icon>
      <div>
        <div class="risk-empty-title">当前没有需要处理的风险</div>
        <div class="risk-empty-sub">失败、缺失与慢表都会出现在这里</div>
      </div>
    </div>
    <ul v-else class="risk-list">
      <li v-for="item in items" :key="item.id">
        <component
          :is="item.to ? 'router-link' : 'div'"
          v-bind="item.to ? { to: item.to } : {}"
          class="risk-item"
          :class="`risk-item--${item.severity}`"
        >
          <span class="risk-icon">
            <v-icon size="16">{{ iconFor(item.severity) }}</v-icon>
          </span>
          <span class="risk-copy">
            <span class="risk-title">{{ item.title }}</span>
            <span class="risk-detail">{{ item.detail }}</span>
          </span>
          <v-icon v-if="item.to" size="16" class="risk-arrow">mdi-chevron-right</v-icon>
        </component>
      </li>
    </ul>

    <div v-if="error && items.length > 0" class="risk-warning">
      <v-icon size="14">mdi-alert-circle-outline</v-icon>
      <span>{{ error }}</span>
      <v-btn size="x-small" variant="text" @click="$emit('retry')">重试</v-btn>
    </div>
  </SectionCard>
</template>

<script setup lang="ts">
  import SectionCard from '@/components/common/SectionCard.vue';
  import SkeletonBlock from '@/components/common/SkeletonBlock.vue';
  import ErrorState from '@/components/common/ErrorState.vue';
  import type { RiskItem } from '@/composables/useExecutiveDashboard';

  withDefaults(
    defineProps<{
      items: RiskItem[];
      loading?: boolean;
      error?: string | null;
    }>(),
    { loading: false, error: null }
  );

  defineEmits<{ retry: [] }>();

  const iconFor = (severity: RiskItem['severity']) => {
    if (severity === 'critical') return 'mdi-alert-circle-outline';
    if (severity === 'warning') return 'mdi-alert-outline';
    return 'mdi-information-outline';
  };
</script>

<style scoped>
  .risk-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .risk-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 9px 10px;
    border-radius: 8px;
    color: inherit;
    text-decoration: none;
    transition: background var(--ds-duration-fast) var(--ds-ease);
  }

  .risk-item:hover {
    background: rgba(var(--v-theme-on-surface), 0.04);
  }

  .risk-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: 7px;
    flex: none;
    margin-top: 1px;
  }

  .risk-item--critical .risk-icon {
    background: rgba(var(--v-theme-error), 0.12);
    color: rgb(var(--v-theme-error));
  }

  .risk-item--warning .risk-icon {
    background: rgba(var(--v-theme-warning), 0.14);
    color: rgb(var(--v-theme-warning));
  }

  .risk-item--info .risk-icon {
    background: rgba(var(--v-theme-info), 0.12);
    color: rgb(var(--v-theme-info));
  }

  .risk-copy {
    display: flex;
    flex-direction: column;
    min-width: 0;
    flex: 1 1 auto;
  }

  .risk-title {
    font-size: 13px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .risk-detail {
    margin-top: 1px;
    font-size: 11px;
    line-height: 1.45;
    color: var(--ds-text-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .risk-arrow {
    color: var(--ds-text-3);
    margin-top: 4px;
    flex: none;
  }

  .risk-warning {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 8px;
    padding: 6px 8px;
    border-radius: 7px;
    background: rgba(var(--v-theme-warning), 0.08);
    color: rgb(var(--v-theme-warning));
    font-size: 11px;
  }

  .risk-warning span {
    flex: 1 1 auto;
    min-width: 0;
  }

  .risk-empty {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 22px 8px;
  }

  .risk-empty-title {
    font-size: 13px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .risk-empty-sub {
    margin-top: 2px;
    font-size: 11px;
    color: var(--ds-text-3);
  }
</style>
