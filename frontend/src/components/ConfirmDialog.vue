<template>
  <v-dialog
    :model-value="modelValue"
    class="ds-dialog-overlay"
    max-width="480"
    :retain-focus="false"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <v-card>
      <v-card-title class="confirm-dialog__title">
        <v-icon :color="toneColor" class="me-2">{{ icon }}</v-icon>
        <span>{{ title }}</span>
      </v-card-title>
      <v-card-text class="confirm-dialog__body">
        <slot>{{ message }}</slot>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="cancel">{{ cancelText }}</v-btn>
        <v-btn :color="toneColor" variant="flat" :loading="loading" @click="emit('confirm')">
          {{ confirmText }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      title: string;
      message?: string;
      confirmText?: string;
      cancelText?: string;
      tone?: 'danger' | 'primary';
      loading?: boolean;
    }>(),
    {
      message: '',
      confirmText: '确认',
      cancelText: '取消',
      tone: 'danger',
      loading: false,
    }
  );

  const emit = defineEmits<{
    'update:modelValue': [value: boolean];
    confirm: [];
    cancel: [];
  }>();

  const toneColor = computed(() => (props.tone === 'danger' ? 'error' : 'primary'));
  const icon = computed(() =>
    props.tone === 'danger' ? 'mdi-alert-circle-outline' : 'mdi-help-circle-outline'
  );

  const cancel = () => {
    emit('cancel');
    emit('update:modelValue', false);
  };
</script>

<style scoped>
  .confirm-dialog__title {
    display: flex;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
    padding-bottom: 4px;
  }

  .confirm-dialog__body {
    font-size: 14px;
    line-height: 1.7;
    color: rgb(var(--v-theme-on-surface-variant));
    word-break: break-word;
  }
</style>
