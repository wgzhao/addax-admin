<template>
  <v-card flat class="batch-update-card">
    <div class="batch-update-head">
      <div>
        <div class="batch-update-title-row">
          <div class="batch-update-title">批量修改采集表</div>
          <v-chip size="small" variant="tonal" color="primary">{{ selectedCount }} 条记录</v-chip>
        </div>
        <div class="batch-update-subtitle">
          将本次批量操作聚焦为两个关键字段：采集状态与剩余运行次数，减少误操作负担。
        </div>
      </div>
    </div>

    <div class="impact-strip">
      <div class="impact-item">
        <span class="impact-label">影响范围</span>
        <strong>{{ selectedCount }} 条采集表记录</strong>
      </div>
      <div class="impact-item">
        <span class="impact-label">修改字段</span>
        <strong>状态 / 剩余运行次数</strong>
      </div>
    </div>

    <v-alert type="info" variant="tonal" density="comfortable" class="mx-6 mt-2 mb-0">
      提交后会覆盖选中记录的对应字段，请确认本次批量修改的口径一致。
    </v-alert>

    <v-form fast-fail @submit.prevent="updateItem">
      <div class="form-grid">
        <div class="field-card">
          <div class="field-card__head">
            <div class="field-card__title">采集状态</div>
            <div class="field-card__desc">统一设置选中记录的运行状态。</div>
          </div>
          <v-select
            v-model="status"
            :items="statusOptions"
            item-title="label"
            item-value="value"
            density="comfortable"
            variant="outlined"
            label="状态"
            hint="选择要写入的状态值"
            persistent-hint
          />
        </div>

        <div class="field-card">
          <div class="field-card__head">
            <div class="field-card__title">剩余运行次数</div>
            <div class="field-card__desc">适合对重试配额做统一回填或收缩。</div>
          </div>
          <v-text-field
            v-model="retryCnt"
            label="剩余运行次数"
            type="number"
            min="0"
            max="99"
            density="comfortable"
            variant="outlined"
            hint="请输入 0 到 99 之间的非负整数"
            persistent-hint
          />
        </div>
      </div>

      <v-card-actions class="batch-update-actions">
        <v-spacer />
        <v-btn type="button" variant="text" @click="emit('closeDialog')">取消</v-btn>
        <v-btn type="submit" color="primary" variant="flat" :disabled="!isValid" :loading="loading">
          应用修改
        </v-btn>
      </v-card-actions>
    </v-form>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import { BATCH_UPDATE_STATUS_OPTIONS } from '@/utils';

  const props = defineProps({
    tid: {
      type: Array,
      required: true,
    },
  });

  const emit = defineEmits(['closeDialog', 'update:batch']);

  const status = ref('N');
  const retryCnt = ref(3);
  const loading = ref(false);

  const selectedCount = computed(() => (props.tid ? props.tid.length : 0));
  const isValid = computed(
    () => !!status.value || (retryCnt.value !== null && retryCnt.value !== undefined)
  );
  const statusOptions = BATCH_UPDATE_STATUS_OPTIONS;

  async function updateItem() {
    if (!isValid.value) return;

    loading.value = true;
    const payload = {
      tids: props.tid,
      status: status.value,
      retryCnt: retryCnt.value,
    };

    try {
      const count = await tableService.batchUpdateStatus(payload);
      notify(`批量更新成功 ${count} 条记录`, 'success');
      emit('closeDialog');
      emit('update:batch', payload);
    } catch (error) {
      notify(`批量更新失败: ${error}`, 'error');
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .batch-update-card {
    background: rgb(var(--v-theme-surface));
  }

  .batch-update-head {
    padding: 22px 24px 14px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.06), transparent 95%);
  }

  .batch-update-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .batch-update-title {
    font-size: 1.05rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .batch-update-subtitle {
    margin-top: 6px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .impact-strip {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 18px 24px 0;
  }

  .impact-item {
    padding: 14px 16px;
    border-radius: 14px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .impact-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    padding: 18px 24px 10px;
  }

  .field-card {
    padding: 18px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-on-surface), 0.015);
  }

  .field-card__head {
    margin-bottom: 14px;
  }

  .field-card__title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .field-card__desc {
    margin-top: 4px;
    font-size: 0.84rem;
    line-height: 1.6;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .batch-update-actions {
    padding: 14px 24px 20px;
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  @media (max-width: 760px) {
    .impact-strip,
    .form-grid {
      grid-template-columns: 1fr;
    }

    .batch-update-head,
    .impact-strip,
    .form-grid,
    .batch-update-actions {
      padding-left: 16px;
      padding-right: 16px;
    }
  }
</style>
