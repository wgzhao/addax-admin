<template>
  <v-dialog v-model="open" max-width="520">
    <v-card class="version-dialog-card">
      <v-card-text>
        <div class="version-dialog-body">
          <div class="version-logo-wrap">
            <img class="version-logo" src="/logo.svg" alt="Addax Admin Logo" />
          </div>
          <div class="version-app-name">Addax Admin</div>
          <div class="version-app-meta">Version {{ APP_VERSION }}</div>
          <div class="version-app-copyright">&copy; 2023-{{ new Date().getFullYear() }} wgzhao</div>
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
  import { APP_VERSION } from '@/config/version';

  const open = defineModel<boolean>({ default: false });
</script>

<style scoped>
  .version-dialog-card {
    background: rgb(var(--v-theme-surface));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.2);
  }

  .version-dialog-body {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 12px 8px 6px;
  }

  .version-logo-wrap {
    width: 72px;
    height: 72px;
    border-radius: 20px;
    background: linear-gradient(
      145deg,
      rgba(var(--v-theme-primary), 0.24),
      rgba(var(--v-theme-on-surface), 0.08)
    );
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.24);
  }

  .version-logo {
    width: 56px;
    height: 56px;
    object-fit: contain;
  }

  .version-app-name {
    font-size: 20px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .version-app-meta {
    font-size: 14px;
    color: rgba(var(--v-theme-on-surface), 0.7);
  }

  .version-app-copyright {
    margin-top: 12px;
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.55);
  }
</style>
