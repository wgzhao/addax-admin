<template>
  <v-footer app height="40" class="app-footer">
    <div class="footer-links">
      <router-link to="/help" class="footer-link">帮助中心</router-link>
      <router-link to="/status" class="footer-link">系统状态</router-link>
      <a
        href="https://github.com/wgzhao/addax-admin"
        target="_blank"
        rel="noopener noreferrer"
        class="footer-link"
      >
        GitHub
      </a>
    </div>
    <v-spacer />
    <span class="footer-meta">© {{ year }} {{ APP_COMPANY }} · {{ APP_LICENSE }}</span>
    <span class="footer-meta footer-meta--version" :title="buildTitle">
      {{ versionLabel }}
    </span>
  </v-footer>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { APP_COMPANY, APP_LICENSE } from '@/config/branding';
  import { APP_BUILD_SHA, APP_BUILD_TIME, APP_ENV_LABEL, APP_VERSION } from '@/config/version';

  const year = new Date().getFullYear();
  const versionLabel = computed(() =>
    APP_BUILD_SHA && APP_BUILD_SHA !== 'dev'
      ? `v${APP_VERSION} · ${APP_BUILD_SHA}`
      : `v${APP_VERSION}`
  );
  const buildTitle = computed(
    () => `环境：${APP_ENV_LABEL}\n构建：${APP_BUILD_SHA}\n时间：${APP_BUILD_TIME || '未知'}`
  );
</script>

<style scoped>
  .app-footer {
    position: fixed !important;
    bottom: 0 !important;
    left: 0;
    right: 0;
    gap: 12px;
    min-height: 40px;
    padding-inline: 16px;
    z-index: 1005;
    border-top: 1px solid var(--ds-border-subtle);
    background: rgb(var(--v-theme-surface));
    color: var(--ds-text-3);
    font-size: 12px;
  }

  .footer-links {
    display: inline-flex;
    align-items: center;
    gap: 14px;
  }

  .footer-link {
    color: var(--ds-text-2);
    text-decoration: none;
    font-size: 12px;
  }

  .footer-link:hover {
    color: rgb(var(--v-theme-primary));
  }

  .footer-meta {
    font-size: 12px;
    color: var(--ds-text-3);
    white-space: nowrap;
  }

  .footer-meta--version {
    margin-inline-start: 14px;
    font-variant-numeric: tabular-nums;
  }

  @media (max-width: 760px) {
    .footer-links {
      gap: 10px;
    }

    /* Why: a 40px strip cannot hold links plus build metadata on phones; the
       version and environment stay reachable from 账号 → 系统版本 */
    .footer-meta {
      display: none;
    }
  }
</style>
