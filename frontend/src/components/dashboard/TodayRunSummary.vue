<template>
  <v-card flat class="ds-card today-summary">
    <div class="today-summary__main">
      <div class="today-summary__head">
        <span class="today-summary__title">今日运行</span>
        <span class="today-summary__sub">{{ subtitle }}</span>
      </div>
      <div class="today-summary__counts">
        <span class="count">
          <i class="dot dot--done" />已完成
          <b>{{ counts.done }}</b>
        </span>
        <span class="count">
          <i class="dot dot--running" />进行中
          <b>{{ counts.running }}</b>
        </span>
        <span class="count" :class="{ 'count--alert': counts.failed > 0 }">
          <i class="dot dot--failed" />有失败
          <b>{{ counts.failed }}</b>
        </span>
        <span class="count">
          <i class="dot dot--pending" />未开始
          <b>{{ counts.pending }}</b>
        </span>
      </div>
    </div>
    <div class="today-summary__side">
      <span v-if="failedNames" class="today-summary__alert" :title="failedNames">{{
        failedNames
      }}</span>
      <v-btn
        variant="text"
        size="small"
        class="today-summary__link"
        append-icon="mdi-arrow-right"
        @click="emit('open')"
      >
        日切心跳
      </v-btn>
    </div>
  </v-card>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useTodayPulse } from '@/composables/useTodayPulse';

  const emit = defineEmits<{ open: [] }>();
  const { loading, rows, counts, switchLabel } = useTodayPulse();

  const subtitle = computed(() => {
    if (loading.value) return '加载中…';
    const parts = [`${rows.value.length} 个采集源`];
    if (switchLabel.value) parts.push(`切日 ${switchLabel.value}`);
    return parts.join(' · ');
  });

  const failedNames = computed(() => {
    const names = rows.value.filter(row => row.state === 'failed').map(row => row.name);
    if (!names.length) return '';
    const head = names.slice(0, 3).join('、');
    return names.length > 3 ? `${head} 等 ${names.length} 个源有失败` : `${head} 有失败`;
  });
</script>

<style scoped>
  .today-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 20px;
  }

  .today-summary__main {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px 28px;
    min-width: 0;
  }

  .today-summary__head {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }

  .today-summary__title {
    font-size: 15px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .today-summary__sub {
    font-size: 12px;
    color: var(--ds-text-3);
    font-variant-numeric: tabular-nums;
  }

  .today-summary__counts {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .count {
    display: inline-flex;
    align-items: baseline;
    gap: 6px;
  }

  .count b {
    font-size: 15px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
    font-variant-numeric: tabular-nums;
  }

  .count--alert b {
    color: rgb(var(--v-theme-error));
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    align-self: center;
  }

  .dot--done {
    background: rgb(var(--v-theme-success));
  }

  .dot--running {
    background: rgb(var(--v-theme-info));
  }

  .dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .dot--pending {
    background: rgb(var(--v-theme-on-surface-variant));
    opacity: 0.45;
  }

  .today-summary__side {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
  }

  .today-summary__alert {
    max-width: 320px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    font-weight: 600;
    color: rgb(var(--v-theme-error));
  }

  .today-summary__link {
    letter-spacing: 0;
    font-size: 13px;
  }

  @media (max-width: 960px) {
    .today-summary {
      flex-wrap: wrap;
    }

    .today-summary__alert {
      max-width: 220px;
    }
  }
</style>
