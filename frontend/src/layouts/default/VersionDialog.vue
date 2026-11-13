<template>
  <v-dialog v-model="open" max-width="520">
    <v-card class="version-dialog-card">
      <v-card-text>
        <div class="version-dialog-body">
          <div class="version-logo-wrap">
            <img class="version-logo" :src="APP_LOGO" :alt="`${APP_NAME} Logo`" />
          </div>
          <div class="version-app-name">{{ APP_NAME }}</div>
          <div class="version-app-meta">{{ APP_SUBTITLE }}</div>
          <v-chip size="small" variant="tonal" color="primary" class="version-edition">
            许可 · {{ APP_EDITION }} · {{ APP_LICENSE }}
          </v-chip>

          <div class="version-grid">
            <div class="version-row">
              <span>版本</span>
              <b>{{ APP_VERSION }}</b>
            </div>
            <div class="version-row">
              <span>构建</span>
              <b>{{ APP_BUILD_SHA }}</b>
            </div>
            <div class="version-row">
              <span>环境</span>
              <b>{{ APP_ENV_LABEL }}</b>
            </div>
            <div class="version-row">
              <span>构建时间</span>
              <b>{{ buildTimeLabel }}</b>
            </div>
          </div>

          <div class="version-links">
            <a :href="APP_RELEASES_URL" target="_blank" rel="noopener noreferrer">更新日志</a>
            <a :href="APP_DOCS_URL" target="_blank" rel="noopener noreferrer">安装与部署文档</a>
            <a :href="APP_SUPPORT_URL" target="_blank" rel="noopener noreferrer">联系支持</a>
          </div>
          <div class="version-app-copyright">
            &copy; {{ year }} {{ APP_COMPANY }}
          </div>
        </div>
      </v-card-text>
      <v-card-actions class="pb-4">
        <v-spacer />
        <v-btn variant="text" @click="open = false">关闭</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import {
    APP_COMPANY,
    APP_DOCS_URL,
    APP_EDITION,
    APP_LICENSE,
    APP_LOGO,
    APP_NAME,
    APP_RELEASES_URL,
    APP_SUBTITLE,
    APP_SUPPORT_URL,
  } from '@/config/branding';
  import { APP_BUILD_SHA, APP_BUILD_TIME, APP_ENV_LABEL, APP_VERSION } from '@/config/version';

  const open = defineModel<boolean>({ default: false });
  const year = new Date().getFullYear();
  const buildTimeLabel = computed(() => {
    if (!APP_BUILD_TIME) return '未知';
    const date = new Date(APP_BUILD_TIME);
    return Number.isNaN(date.getTime()) ? APP_BUILD_TIME : date.toLocaleString('zh-CN');
  });
</script>

<style scoped>
  .version-dialog-card {
    background: rgb(var(--v-theme-surface));
    border: 1px solid var(--ds-border-default);
  }

  .version-dialog-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 12px 8px 6px;
  }

  .version-logo-wrap {
    width: 64px;
    height: 64px;
    border-radius: 18px;
    background: rgba(var(--v-theme-primary), 0.08);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .version-logo {
    width: 48px;
    height: 48px;
    object-fit: contain;
  }

  .version-app-name {
    font-size: 20px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .version-app-meta {
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .version-edition {
    margin-top: 2px;
    letter-spacing: 0;
  }

  .version-grid {
    width: 100%;
    margin-top: 14px;
    padding: 12px 14px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .version-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 4px 0;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .version-row b {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .version-links {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 16px;
    margin-top: 8px;
    font-size: 12px;
  }

  .version-links a {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }

  .version-app-copyright {
    margin-top: 6px;
    font-size: 12px;
    color: var(--ds-text-3);
  }
</style>
