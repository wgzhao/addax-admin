<template>
  <HelpDialogShell
    v-model="open"
    title="源表动态命名说明"
    subtitle="在源表名中拼接任务业务日期或实际运行时间，生成按日期切分的表名。"
    icon="mdi-calendar-clock-outline"
    :max-width="900"
  >
    <div class="intro-example">
      <span class="intro-example__label">示例</span>
      <code>t_${biz_date_dash}</code>
      <v-icon size="16">mdi-arrow-right</v-icon>
      <code>t_2026-01-27</code>
    </div>

    <v-alert type="info" variant="tonal" density="compact" class="date-warning">
      <strong>业务日期可能不等于实际运行日期。</strong>
      跨日重试或补数时，<code>biz_*</code> 跟随任务业务日，<code>curr_*</code> 使用真实运行时间。
    </v-alert>

    <div class="search-row">
      <v-text-field
        v-model="search"
        prepend-inner-icon="mdi-magnify"
        placeholder="搜索变量名、含义或日期格式"
        aria-label="搜索动态变量"
        variant="outlined"
        density="compact"
        clearable
        hide-details
      />
      <span class="result-count">找到 {{ filteredCount }} 个变量</span>
    </div>

    <div v-if="filteredGroups.length" class="variable-groups">
      <section v-for="group in filteredGroups" :key="group.key" class="variable-group">
        <div class="group-head">
          <div>
            <h3>{{ group.title }}</h3>
            <p>{{ group.description }}</p>
          </div>
          <v-chip size="x-small" variant="tonal">{{ group.items.length }} 项</v-chip>
        </div>

        <div class="variable-grid">
          <article v-for="item in group.items" :key="item.name" class="variable-card">
            <div class="variable-card__head">
              <code>{{ item.name }}</code>
              <v-btn
                size="x-small"
                variant="text"
                :icon="copied === item.name ? 'mdi-check' : 'mdi-content-copy'"
                :aria-label="copied === item.name ? `已复制 ${item.name}` : `复制 ${item.name}`"
                @click="copyPlaceholder(item.name)"
              />
            </div>
            <div class="variable-card__label">{{ item.label }}</div>
            <div class="variable-meta">
              <span>格式 {{ item.format }}</span>
              <span>示例 {{ item.example }}</span>
            </div>
          </article>
        </div>
      </section>
    </div>

    <div v-else class="empty-result">
      <v-icon size="30">mdi-text-search</v-icon>
      <strong>没有匹配的动态变量</strong>
      <span>尝试搜索变量前缀、日期格式或“业务日期”“当前日期”。</span>
    </div>
  </HelpDialogShell>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import { notify } from '@/stores/notifier';
  import { copyText } from '@/utils/clipboard';
  import HelpDialogShell from './HelpDialogShell.vue';

  const open = defineModel<boolean>({ default: false });
  const search = ref('');
  const copied = ref('');

  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const currentDateShort = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`;
  const currentDateDash = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

  const placeholderVars = [
    {
      name: 'biz_date_short',
      group: 'biz-date',
      label: '任务业务日期，紧凑格式',
      format: 'yyyyMMdd',
      example: '20260127',
    },
    {
      name: 'biz_date_dash',
      group: 'biz-date',
      label: '任务业务日期，短横线格式',
      format: 'yyyy-MM-dd',
      example: '2026-01-27',
    },
    {
      name: 'biz_year',
      group: 'biz-date',
      label: '业务年份',
      format: 'yyyy',
      example: '2026',
    },
    {
      name: 'biz_month',
      group: 'biz-date',
      label: '业务月份，两位数字',
      format: 'MM',
      example: '01',
    },
    {
      name: 'biz_short_month',
      group: 'biz-date',
      label: '业务月份，不补零',
      format: 'M',
      example: '1',
    },
    {
      name: 'biz_day',
      group: 'biz-date',
      label: '业务日，两位数字',
      format: 'dd',
      example: '27',
    },
    {
      name: 'biz_short_day',
      group: 'biz-date',
      label: '业务日，不补零',
      format: 'd',
      example: '27',
    },
    {
      name: 'biz_ym',
      group: 'biz-date',
      label: '业务年月',
      format: 'yyyyMM',
      example: '202601',
    },
    {
      name: 'biz_short_ym',
      group: 'biz-date',
      label: '业务年月，月份不补零',
      format: 'yyyyM',
      example: '20261',
    },
    {
      name: 'biz_datetime_short',
      group: 'biz-datetime',
      label: '业务日期与当前时分秒，紧凑格式',
      format: 'yyyyMMddHHmmss',
      example: '20260127140530',
    },
    {
      name: 'biz_datetime_dash',
      group: 'biz-datetime',
      label: '业务日期与当前时分秒，短横线格式',
      format: 'yyyy-MM-dd HH:mm:ss',
      example: '2026-01-27 14:05:30',
    },
    {
      name: 'biz_datetime_0_dash',
      group: 'biz-datetime',
      label: '业务日期当天 00:00:00，短横线格式',
      format: 'yyyy-MM-dd 00:00:00',
      example: '2026-01-27 00:00:00',
    },
    {
      name: 'biz_datetime_0_short',
      group: 'biz-datetime',
      label: '业务日期当天 00:00:00，紧凑格式',
      format: 'yyyyMMdd000000',
      example: '20260127000000',
    },
    {
      name: 'curr_date_short',
      group: 'curr-date',
      label: '实际运行日期，紧凑格式',
      format: 'yyyyMMdd',
      example: currentDateShort,
    },
    {
      name: 'curr_date_dash',
      group: 'curr-date',
      label: '实际运行日期，短横线格式',
      format: 'yyyy-MM-dd',
      example: currentDateDash,
    },
    {
      name: 'curr_datetime_short',
      group: 'curr-datetime',
      label: '实际运行日期时间，紧凑格式',
      format: 'yyyyMMddHHmmss',
      example: '以实际运行为准',
    },
    {
      name: 'curr_datetime_dash',
      group: 'curr-datetime',
      label: '实际运行日期时间，短横线格式',
      format: 'yyyy-MM-dd HH:mm:ss',
      example: '以实际运行为准',
    },
    {
      name: 'curr_datetime_0_dash',
      group: 'curr-datetime',
      label: '实际运行日当天 00:00:00，短横线格式',
      format: 'yyyy-MM-dd 00:00:00',
      example: '以实际运行为准',
    },
    {
      name: 'curr_datetime_0_short',
      group: 'curr-datetime',
      label: '实际运行日当天 00:00:00，紧凑格式',
      format: 'yyyyMMdd000000',
      example: '以实际运行为准',
    },
  ];

  const groupDefinitions = [
    {
      key: 'biz-date',
      title: '业务日期',
      description: '跟随任务业务日，适合日表、周表、月表的分区或表名。',
    },
    {
      key: 'biz-datetime',
      title: '业务日期时间',
      description: '使用任务业务日，并组合生成任务时的时分秒。',
    },
    {
      key: 'curr-date',
      title: '当前日期',
      description: '使用实际运行时间，跨日重试时可能与业务日期不同。',
    },
    {
      key: 'curr-datetime',
      title: '当前日期时间',
      description: '使用实际运行日期和时间，适合生成运行时快照名称。',
    },
  ];

  const normalizedSearch = computed(() => search.value.trim().toLowerCase());

  const filteredGroups = computed(() =>
    groupDefinitions
      .map(group => ({
        ...group,
        items: placeholderVars.filter(item => {
          if (item.group !== group.key) return false;
          if (!normalizedSearch.value) return true;
          return [item.name, item.label, item.format, item.group, group.title]
            .join(' ')
            .toLowerCase()
            .includes(normalizedSearch.value);
        }),
      }))
      .filter(group => group.items.length > 0)
  );

  const filteredCount = computed(() =>
    filteredGroups.value.reduce((total, group) => total + group.items.length, 0)
  );

  const copyPlaceholder = async (value: string) => {
    try {
      await copyText(value);
      copied.value = value;
      window.setTimeout(() => {
        if (copied.value === value) copied.value = '';
      }, 1600);
    } catch {
      notify('复制失败，请手动复制', 'error');
    }
  };
</script>

<style scoped>
  .intro-example {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px 14px;
    border: 1px solid rgba(var(--v-theme-primary), 0.14);
    border-radius: 12px;
    background: rgba(var(--v-theme-primary), 0.045);
    color: var(--ds-text-2);
  }

  .intro-example__label {
    color: rgb(var(--v-theme-primary));
    font-size: 0.76rem;
    font-weight: 700;
    letter-spacing: 0.05em;
  }

  .intro-example code,
  .variable-card code {
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
  }

  .intro-example code {
    padding: 3px 7px;
    border-radius: 6px;
    background: rgba(var(--v-theme-on-surface), 0.055);
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.84rem;
  }

  .date-warning {
    margin-top: 12px;
    line-height: 1.65;
  }

  .date-warning code {
    padding: 1px 4px;
    border-radius: 4px;
    background: rgba(var(--v-theme-on-surface), 0.08);
  }

  .search-row {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 16px;
  }

  .search-row :deep(.v-input) {
    max-width: 460px;
  }

  .result-count {
    flex: none;
    color: var(--ds-text-3);
    font-size: 0.78rem;
  }

  .variable-groups {
    display: flex;
    flex-direction: column;
    gap: 22px;
    margin-top: 20px;
  }

  .group-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .group-head h3 {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.95rem;
    font-weight: 700;
  }

  .group-head p {
    margin: 3px 0 0;
    color: var(--ds-text-2);
    font-size: 0.8rem;
    line-height: 1.5;
  }

  .variable-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .variable-card {
    min-width: 0;
    padding: 12px 12px 11px 14px;
    border: 1px solid var(--ds-border-default);
    border-radius: 12px;
    background: rgb(var(--v-theme-surface));
  }

  .variable-card__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .variable-card code {
    min-width: 0;
    overflow-wrap: anywhere;
    color: rgb(var(--v-theme-primary));
    font-size: 0.84rem;
    font-weight: 700;
  }

  .variable-card__label {
    margin-top: 5px;
    color: var(--ds-text-2);
    font-size: 0.8rem;
    line-height: 1.5;
  }

  .variable-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px 12px;
    margin-top: 8px;
    color: var(--ds-text-3);
    font-size: 0.74rem;
  }

  .empty-result {
    display: flex;
    align-items: center;
    flex-direction: column;
    gap: 6px;
    padding: 42px 16px;
    color: var(--ds-text-2);
    text-align: center;
  }

  .empty-result strong {
    color: rgb(var(--v-theme-on-surface));
  }

  .empty-result span {
    font-size: 0.8rem;
  }

  @media (max-width: 760px) {
    .search-row {
      align-items: stretch;
      flex-direction: column;
    }

    .search-row :deep(.v-input) {
      max-width: none;
    }

    .variable-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
