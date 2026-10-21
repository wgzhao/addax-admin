<template>
  <div class="detail-commandbar">
    <div class="command-state" aria-live="polite">
      <v-chip
        size="small"
        :color="retired ? 'grey' : editing ? 'warning' : 'primary'"
        variant="tonal"
      >
        {{ retired ? '已退役' : editing ? '编辑中' : '只读' }}
      </v-chip>
      <span v-if="editing && dirty" class="command-hint command-hint--dirty"> 有未保存的修改 </span>
      <span v-else-if="updatedAt" class="command-hint">最后更新：{{ updatedAt }}</span>
      <span v-else-if="retired" class="command-hint">退役配置仅供查看</span>
    </div>

    <div class="command-actions">
      <template v-if="editing">
        <v-btn variant="text" :disabled="saving" @click="$emit('cancel')">取消</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :disabled="!dirty"
          :loading="saving"
          @click="$emit('save')"
        >
          保存更改
        </v-btn>
      </template>
      <v-btn
        v-else-if="!retired"
        color="primary"
        variant="flat"
        prepend-icon="mdi-pencil-outline"
        @click="$emit('edit')"
      >
        编辑配置
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
  defineProps<{
    editing: boolean;
    dirty: boolean;
    saving: boolean;
    retired: boolean;
    updatedAt?: string | Date;
  }>();

  defineEmits(['edit', 'cancel', 'save']);
</script>

<style scoped>
  .detail-commandbar {
    position: sticky;
    top: 72px;
    z-index: 3;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 12px;
    border: 1px solid var(--ds-border-default);
    border-radius: var(--ds-radius-md);
    background: rgba(var(--v-theme-surface), 0.96);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
    backdrop-filter: blur(12px);
  }

  .command-state,
  .command-actions {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .command-actions {
    flex: none;
  }

  .command-hint {
    color: rgba(var(--v-theme-on-surface), 0.66);
    font-size: 0.82rem;
  }

  .command-hint--dirty {
    color: rgb(var(--v-theme-warning));
    font-weight: 600;
  }

  @media (max-width: 760px) {
    .detail-commandbar {
      align-items: stretch;
      flex-direction: column;
    }

    .command-state {
      justify-content: space-between;
    }

    .command-actions {
      justify-content: flex-end;
    }
  }
</style>
