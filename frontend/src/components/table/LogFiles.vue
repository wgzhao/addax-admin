<template>
  <v-card flat class="ds-card table-panel log-files-panel">
    <v-card-text class="table-panel__content">
      <v-row dense class="content-grid">
        <v-col cols="12" md="4" lg="3">
          <div class="log-list-shell">
            <div class="shell-header">
              <div>
                <div class="shell-title">日志列表</div>
                <div class="shell-caption">按运行时间快速定位最近一次执行记录。</div>
              </div>
            </div>

            <div v-if="listLoading" class="panel-state panel-state--compact">
              <v-progress-circular indeterminate color="primary" size="34" width="4" />
              <div class="panel-state__desc">正在加载日志列表</div>
            </div>

            <div v-else-if="logList.length" class="log-list-wrap">
              <v-list density="comfortable" nav class="log-list">
                <v-list-item
                  v-for="item in logList"
                  :key="item.id"
                  :active="selectedLogId === item.id"
                  rounded="lg"
                  @click="getContent(item.id)"
                >
                  <template #prepend>
                    <v-avatar size="30" color="primary" variant="tonal">
                      <v-icon size="16">mdi-text-box-search-outline</v-icon>
                    </v-avatar>
                  </template>

                  <v-list-item-title class="log-item-title">
                    {{ item.runAt || `日志 #${item.id}` }}
                  </v-list-item-title>

                  <template #append>
                    <v-chip
                      v-if="logList[0]?.id === item.id"
                      size="x-small"
                      variant="tonal"
                      color="primary"
                    >
                      最新
                    </v-chip>
                  </template>
                </v-list-item>
              </v-list>
            </div>

            <div v-else class="panel-state panel-state--compact">
              <v-icon size="42" class="panel-state__icon">mdi-file-remove-outline</v-icon>
              <div class="panel-state__title">暂无日志文件</div>
            </div>
          </div>
        </v-col>

        <v-col cols="12" md="8" lg="9">
          <div class="log-content-shell">
            <div class="shell-header shell-header--content">
              <div>
                <div class="shell-title">日志正文</div>
                <div class="shell-caption">
                  {{
                    selectedLog?.runAt
                      ? `当前日志：${selectedLog.runAt}`
                      : '请选择左侧日志文件查看详细内容'
                  }}
                </div>
              </div>
              <div>
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  :disabled="!fContent"
                  @click="copyLog"
                  prepend-icon="mdi-content-copy"
                >
                  复制
                </v-btn>
                &nbsp;&nbsp;
                <v-btn
                  size="small"
                  variant="tonal"
                  color="secondary"
                  :disabled="!fContent"
                  @click="downloadLog"
                  prepend-icon="mdi-download"
                  style="mx-2"
                >
                  下载
                </v-btn>
              </div>
            </div>

            <div class="log-content-wrap">
              <div v-if="!selectedLogId" class="panel-state panel-state--content-empty">
                <v-icon size="52" class="panel-state__icon">mdi-file-document-outline</v-icon>
                <div class="panel-state__title">尚未选择日志</div>
                <div class="panel-state__desc">
                  请从左侧选择一条日志记录，系统将在右侧显示完整内容。
                </div>
              </div>

              <div v-else-if="loading" class="panel-state panel-state--content-empty">
                <v-progress-circular indeterminate color="primary" size="42" width="4" />
                <div class="panel-state__title">正在加载日志内容</div>
              </div>

              <div v-else-if="fContent" class="log-body-shell">
                <pre class="log-content">{{ fContent }}</pre>
              </div>

              <div v-else class="panel-state panel-state--content-empty panel-state--error-soft">
                <v-icon size="46" color="error">mdi-alert-circle-outline</v-icon>
                <div class="panel-state__title">日志内容加载失败</div>
                <div class="panel-state__desc">请重新选择日志，或稍后再次尝试。</div>
              </div>
            </div>
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue';
  import { useRoute } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import logService from '@/service/log-service';
  import type { AddaxLog } from '@/types/database';

  const route = useRoute();
  const tid = String(route.params.tid);

  const fContent = ref('');
  const selectedLogId = ref<number | null>(null);
  const loading = ref(false);
  const listLoading = ref(false);
  const logList = ref<AddaxLog[]>([]);

  const selectedLog = computed(
    () => logList.value.find(item => item.id === selectedLogId.value) || null
  );

  const getContent = async (id: number) => {
    if (selectedLogId.value === id && fContent.value) {
      return;
    }

    selectedLogId.value = id;
    loading.value = true;

    try {
      fContent.value = await logService.getContent(id);
    } catch (error) {
      console.error('Failed to load log content:', error);
      fContent.value = '';
    } finally {
      loading.value = false;
    }
  };

  onMounted(async () => {
    listLoading.value = true;
    try {
      const res = await logService.getLogFiles(tid);
      logList.value = res.data || [];
      if (logList.value.length > 0) {
        await getContent(logList.value[0].id);
      }
    } catch (error) {
      notify(`加载日志列表失败: ${error}`, 'error');
    } finally {
      listLoading.value = false;
    }
  });

  const copyLog = async () => {
    if (!fContent.value) return;
    try {
      await navigator.clipboard.writeText(String(fContent.value));
      notify('日志已复制到剪贴板', 'success');
    } catch {
      notify('复制失败，请手动复制', 'error');
    }
  };

  const downloadLog = () => {
    if (!fContent.value) return;
    const fileName = selectedLogId.value ? `log-${selectedLogId.value}.txt` : 'log.txt';
    const blob = new Blob([String(fContent.value)], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    notify('日志已开始下载', 'success');
  };
</script>

<style scoped>
  .table-panel__content {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 18px;
  }

  .panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .panel-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .panel-title {
    font-size: 1rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-subtitle {
    margin-top: 6px;
    max-width: 720px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .panel-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }

  .summary-card {
    padding: 14px 16px;
    border-radius: 14px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(
        180deg,
        rgba(var(--v-theme-primary), 0.06),
        rgba(var(--v-theme-primary), 0.01)
      ),
      rgb(var(--v-theme-surface));
  }

  .summary-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .content-grid {
    margin: 0;
  }

  .log-list-shell,
  .log-content-shell {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 520px;
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgb(var(--v-theme-surface));
    overflow: hidden;
  }

  .shell-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .shell-header--content {
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.04), transparent 96%);
  }

  .shell-title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .shell-caption {
    margin-top: 2px;
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .log-list-wrap {
    flex: 1;
    overflow: auto;
    padding: 12px;
  }

  .log-list :deep(.v-list-item) {
    margin-bottom: 8px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
    transition: all 0.2s ease;
  }

  .log-list :deep(.v-list-item:hover) {
    border-color: rgba(var(--v-theme-primary), 0.18);
    background: rgba(var(--v-theme-primary), 0.04);
  }

  .log-list :deep(.v-list-item--active) {
    background: rgba(var(--v-theme-primary), 0.08);
    border-color: rgba(var(--v-theme-primary), 0.24);
  }

  .log-item-title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .log-item-subtitle {
    margin-top: 2px;
  }

  .log-content-wrap {
    flex: 1;
    min-height: 0;
    padding: 16px;
    background: rgba(var(--v-theme-on-surface), 0.015);
  }

  .log-body-shell {
    height: 100%;
    min-height: 420px;
    border-radius: 16px;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }

  .log-content {
    height: 100%;
    min-height: 420px;
    margin: 0;
    padding: 18px;
    overflow: auto;
    color: #dbe4ff;
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.72;
    font-size: 0.83rem;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
  }

  .log-content::-webkit-scrollbar,
  .log-list-wrap::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  .log-content::-webkit-scrollbar-thumb,
  .log-list-wrap::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: rgba(var(--v-theme-on-surface), 0.24);
  }

  .panel-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 220px;
    padding: 24px;
    text-align: center;
  }

  .panel-state--compact {
    min-height: 180px;
  }

  .panel-state--content-empty {
    height: 100%;
    border-radius: 16px;
    border: 1px dashed rgba(var(--v-theme-on-surface), 0.14);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .panel-state--error-soft {
    background: rgba(var(--v-theme-error), 0.04);
  }

  .panel-state__icon {
    color: rgba(var(--v-theme-on-surface), 0.28);
  }

  .panel-state__title {
    font-size: 1rem;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-state__desc {
    max-width: 320px;
    color: rgba(var(--v-theme-on-surface), 0.64);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .table-panel__content {
      padding: 16px;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }

    .log-list-shell,
    .log-content-shell {
      min-height: auto;
    }

    .log-body-shell,
    .log-content {
      min-height: 320px;
    }
  }
</style>
