<template>
  <div class="notice-stack" role="region" aria-label="系统通知">
    <transition-group name="notice">
      <v-alert
        v-for="item in notices"
        :key="item.id"
        :color="item.color"
        variant="flat"
        density="comfortable"
        closable
        class="notice-item"
        @click:close="dismiss(item.id)"
      >
        <div class="notice-body">
          <v-icon v-if="iconFor(item)" class="notice-icon">{{ iconFor(item) }}</v-icon>
          <div class="notice-copy">
            <div v-if="item.title" class="notice-title">{{ item.title }}</div>
            <div class="notice-text">{{ item.text }}</div>
          </div>
        </div>
      </v-alert>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
  import { useNotifier } from '@/stores/notifier';
  import type { Notice } from '@/stores/notifier';

  const { notices, dismiss } = useNotifier();

  const iconFor = (item: Notice) =>
    item.icon ||
    {
      success: 'mdi-check-circle-outline',
      error: 'mdi-alert-circle-outline',
      warning: 'mdi-alert-outline',
      info: 'mdi-information-outline',
      primary: 'mdi-information-outline',
    }[item.color] ||
    'mdi-information-outline';
</script>

<style scoped>
  .notice-stack {
    position: fixed;
    top: 76px;
    inset-inline-end: 16px;
    z-index: 2200;
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: min(380px, calc(100vw - 32px));
    pointer-events: none;
  }

  .notice-item {
    pointer-events: auto;
    box-shadow: 0 8px 24px rgba(2, 6, 23, 0.18);
    border-radius: 10px;
  }

  .notice-body {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .notice-icon {
    margin-top: 1px;
    flex: 0 0 auto;
  }

  .notice-title {
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 2px;
  }

  .notice-text {
    font-size: 14px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .notice-enter-active,
  .notice-leave-active {
    transition: opacity 0.16s ease-out, transform 0.16s ease-out;
  }

  .notice-enter-from,
  .notice-leave-to {
    opacity: 0;
    transform: translateY(-6px);
  }

  @media (prefers-reduced-motion: reduce) {
    .notice-enter-active,
    .notice-leave-active {
      transition: none;
    }
  }
</style>
