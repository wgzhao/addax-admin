<template>
  <section v-if="visible" class="onboarding" aria-label="首次使用引导">
    <div class="onboarding-head">
      <div>
        <div class="onboarding-title">快速开始</div>
        <div class="onboarding-sub">{{ completed }} / {{ steps.length }} 步已完成</div>
      </div>
      <v-btn icon size="small" variant="text" aria-label="关闭引导" @click="dismiss">
        <v-icon size="18">mdi-close</v-icon>
      </v-btn>
    </div>

    <v-progress-linear
      :model-value="(completed / steps.length) * 100"
      color="primary"
      height="4"
      rounded
      class="onboarding-progress"
    />

    <ol class="onboarding-steps">
      <li v-for="step in steps" :key="step.key" :class="{ 'is-done': step.done }">
        <router-link :to="step.to" class="onboarding-step">
          <span class="step-dot">
            <v-icon size="14">{{ step.done ? 'mdi-check' : step.icon }}</v-icon>
          </span>
          <span class="step-copy">
            <span class="step-title">{{ step.title }}</span>
            <span class="step-desc">{{ step.description }}</span>
          </span>
          <v-icon size="14" class="step-arrow">mdi-chevron-right</v-icon>
        </router-link>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import targetService from '@/service/target-service';

  const props = withDefaults(
    defineProps<{
      sourceCount: number;
      tableCount: number;
      doneCount: number;
      loading?: boolean;
    }>(),
    { loading: false }
  );

  const STORAGE_KEY = 'addax.onboarding.dismissed';
  const dismissed = ref(localStorage.getItem(STORAGE_KEY) === '1');
  const targetCount = ref(0);
  const targetsLoaded = ref(false);

  const steps = computed(() => [
    {
      key: 'source',
      title: '连接采集源',
      description: '配置第一个数据库连接',
      icon: 'mdi-database-plus-outline',
      to: '/source?action=create',
      done: props.sourceCount > 0,
    },
    {
      key: 'target',
      title: '配置目标端',
      description: '选择 Hive 或关系型数据库',
      icon: 'mdi-database-export-outline',
      to: '/target?action=create',
      done: targetCount.value > 0,
    },
    {
      key: 'table',
      title: '新增采集表',
      description: '从源库选择要采集的表',
      icon: 'mdi-table-plus',
      to: '/table/batch-add',
      done: props.tableCount > 0,
    },
    {
      key: 'run',
      title: '查看首次运行',
      description: '确认调度与执行结果',
      icon: 'mdi-play-circle-outline',
      to: '/task',
      done: props.doneCount > 0,
    },
  ]);

  const completed = computed(() => steps.value.filter(step => step.done).length);
  const visible = computed(
    () =>
      !dismissed.value &&
      targetsLoaded.value &&
      !props.loading &&
      completed.value < steps.value.length
  );

  const dismiss = () => {
    dismissed.value = true;
    localStorage.setItem(STORAGE_KEY, '1');
  };

  onMounted(async () => {
    try {
      const targets = await targetService.list(false);
      targetCount.value = targets?.length ?? 0;
    } catch {
      targetCount.value = 0;
    } finally {
      targetsLoaded.value = true;
    }
  });
</script>

<style scoped>
  .onboarding {
    padding: 14px 16px 12px;
    border: 1px solid rgba(var(--v-theme-primary), 0.18);
    border-radius: 12px;
    background: rgba(var(--v-theme-primary), 0.035);
  }

  .onboarding-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .onboarding-title {
    font-size: 14px;
    font-weight: 650;
    color: rgb(var(--v-theme-on-surface));
  }

  .onboarding-sub {
    margin-top: 2px;
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .onboarding-progress {
    margin: 10px 0 12px;
  }

  .onboarding-steps {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .onboarding-step {
    display: flex;
    align-items: center;
    gap: 9px;
    min-width: 0;
    padding: 9px 10px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 9px;
    color: inherit;
    text-decoration: none;
    background: var(--ds-surface-raised);
    transition: border-color var(--ds-duration-fast) var(--ds-ease);
  }

  .onboarding-step:hover {
    border-color: rgba(var(--v-theme-primary), 0.35);
  }

  .step-dot {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    flex: none;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
  }

  .is-done .step-dot {
    background: rgba(var(--v-theme-success), 0.12);
    color: rgb(var(--v-theme-success));
  }

  .step-copy {
    display: flex;
    flex-direction: column;
    min-width: 0;
    flex: 1 1 auto;
  }

  .step-title {
    font-size: 12px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .step-desc {
    margin-top: 1px;
    font-size: 10px;
    color: var(--ds-text-3);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .step-arrow {
    color: var(--ds-text-3);
    flex: none;
  }

  @media (max-width: 1100px) {
    .onboarding-steps {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 600px) {
    .onboarding-steps {
      grid-template-columns: 1fr;
    }
  }
</style>
