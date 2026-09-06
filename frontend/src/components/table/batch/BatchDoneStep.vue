<template>
  <div class="success-shell">
    <v-icon size="56" color="success">mdi-check-circle-outline</v-icon>
    <div class="success-title">成功添加 {{ savedCount }} 张表</div>
    <div class="success-desc">请求已经提交，系统将继续在后端初始化目标表与对应配置。</div>
    <div class="success-meta">
      <v-chip size="small" variant="tonal" color="success">目标库 {{ targetDb || '-' }}</v-chip>
      <v-chip size="small" variant="outlined">表数量 {{ savedCount }}</v-chip>
    </div>
    <div class="success-actions">
      <v-btn variant="text" @click="$emit('back')">返回采集表</v-btn>
      <v-btn color="primary" variant="flat" @click="$emit('restart')">继续新增</v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
  defineProps<{ savedCount: number; targetDb: string }>();
  defineEmits(['restart', 'back']);
</script>

<style scoped>
  .success-shell {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 420px;
    padding: 32px 20px;
    text-align: center;
  }

  .success-title {
    font-size: 1.18rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .success-desc {
    max-width: 560px;
    color: rgba(var(--v-theme-on-surface), 0.66);
    line-height: 1.7;
  }

  .success-meta,
  .success-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    justify-content: center;
  }
</style>
