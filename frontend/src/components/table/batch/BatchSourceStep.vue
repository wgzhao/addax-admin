<template>
  <div class="step-layout">
    <div class="step-hero">
      <div class="step-kicker">Step 1</div>
      <h2 class="step-title">选择采集源</h2>
      <div class="step-desc">
        先选源系统，再选对应数据库。完成后再进入表选择，避免第二步信息过载。
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
  }>();

  defineEmits(['retry']);

  const selectedSourceId = defineModel<EtlSource | null>('selectedSourceId');
  const selectedDb = defineModel<string | null>('selectedDb');
</script>

<style lang="scss" scoped>
  @use './batch-step-shared' as *;
</style>
