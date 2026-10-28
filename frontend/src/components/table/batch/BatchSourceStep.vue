<template>
  <div class="step-layout">
    <div class="step-hero">
      <div class="step-kicker">Step 1</div>
      <h2 class="step-title">选择采集路由</h2>
      <div class="step-desc">
        采集路由由源系统、源库、目标端和目标库共同确定。先确定这四项，下一步才能只列出该路由下可新增的表。
      </div>
    </div>

    <v-alert v-if="sourceLoadError" type="error" variant="tonal" class="step-alert">
      <div class="alert-action">
        <span>{{ sourceLoadError }}</span>
        <v-btn size="small" variant="text" color="error" @click="$emit('retry')">重试</v-btn>
      </div>
    </v-alert>

    <div class="stage-grid stage-grid--2">
      <section class="stage-panel">
        <div class="stage-panel__header">
          <div>
            <div class="stage-panel__title">采集源系统</div>
            <div class="stage-panel__caption">系统会根据所选采集源自动联动可用数据库。</div>
          </div>
        </div>
        <v-select
          v-model="selectedSourceId"
          :items="sourceSystemList"
          :item-props="item => ({ title: `${item.code} — ${item.name}` })"
          item-value="id"
          label="采集源系统"
          density="comfortable"
          variant="outlined"
          return-object
          hide-details="auto"
          prepend-inner-icon="mdi-database-arrow-right"
          :loading="loadingSources"
          :disabled="loadingSources || !!sourceLoadError"
          :required="true"
          no-data-text="暂无可用的采集源"
        />
      </section>

      <section class="stage-panel">
        <div class="stage-panel__header">
          <div>
            <div class="stage-panel__title">源数据库</div>
            <div class="stage-panel__caption">只有在确定采集源后，才会加载对应数据库列表。</div>
          </div>
        </div>
        <v-select
          v-model="selectedDb"
          :items="sourceDbs"
          :disabled="!selectedSourceId || loadingDbs"
          :loading="loadingDbs"
          label="源数据库"
          density="comfortable"
          variant="outlined"
          hide-details="auto"
          prepend-inner-icon="mdi-database"
          :required="true"
          no-data-text="当前采集源下没有可用数据库"
          :placeholder="
            loadingDbs ? '正在加载…' : !selectedSourceId ? '请先选择采集源系统' : '请选择数据库'
          "
        />
      </section>
    </div>

    <div class="stage-grid stage-grid--2">
      <section class="stage-panel">
        <div class="stage-panel__header">
          <div>
            <div class="stage-panel__title">目标端</div>
            <div class="stage-panel__caption">目标端决定 writer 与目标表命名空间。</div>
          </div>
        </div>
        <v-select
          v-model="targetId"
          :items="targets"
          item-title="label"
          item-value="value"
          label="目标端"
          density="comfortable"
          variant="outlined"
          :required="true"
          :loading="loadingTargets"
          :disabled="loadingTargets"
          hide-details="auto"
          prepend-inner-icon="mdi-database-arrow-right-outline"
          no-data-text="暂无启用的目标端"
        />
      </section>

      <section class="stage-panel">
        <div class="stage-panel__header">
          <div>
            <div class="stage-panel__title">目标库</div>
            <div class="stage-panel__caption">同一源表可以在同一目标端的不同目标库各采集一份。</div>
          </div>
        </div>
        <v-text-field
          v-model="targetDb"
          label="目标库名"
          density="comfortable"
          variant="outlined"
          :required="true"
          hide-details="auto"
          autocomplete="off"
          spellcheck="false"
          prepend-inner-icon="mdi-database-arrow-right"
          placeholder="例如 ods_oa"
        />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
  import type { EtlSource } from '@/types/database';

  defineProps<{
    sourceSystemList: EtlSource[];
    sourceDbs: string[];
    loadingDbs: boolean;
    loadingSources: boolean;
    sourceLoadError: string;
    targets: { label: string; value: number; targetType: string }[];
    loadingTargets: boolean;
  }>();

  defineEmits(['retry']);

  const selectedSourceId = defineModel<EtlSource | null>('selectedSourceId');
  const selectedDb = defineModel<string | null>('selectedDb');
  const targetId = defineModel<number | null>('targetId');
  const targetDb = defineModel<string | null>('targetDb');
</script>

<style lang="scss" scoped>
  @use './batch-step-shared' as *;
</style>
