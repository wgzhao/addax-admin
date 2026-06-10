<template>
  <v-card flat class="ds-card table-panel addax-job-panel">
    <v-card-text class="table-panel__content">
      <div class="panel-head">
        <div>
          <div class="panel-title-row">
            <div class="panel-title">采集作业配置</div>
            <v-chip size="small" variant="tonal" color="primary">JSON</v-chip>
            <v-chip v-if="editing" size="small" variant="outlined" color="warning">编辑中</v-chip>
          </div>
        </div>
        <div class="panel-actions">
          <template v-if="!editing">
            <v-btn
              size="small"
              variant="tonal"
              @click="copyToClipboard"
              prepend-icon="mdi-content-copy"
            >
              复制
            </v-btn>
            <v-btn
              size="small"
              variant="flat"
              color="primary"
              @click="startEdit"
              prepend-icon="mdi-pencil"
            >
              编辑
            </v-btn>
          </template>
          <template v-else>
            <v-btn size="small" variant="text" @click="cancelEdit">取消</v-btn>
            <v-btn size="small" color="primary" variant="flat" :disabled="!dirty" @click="saveEdit">
              保存
            </v-btn>
          </template>
        </div>
      </div>

      <div v-if="loading" class="panel-state">
        <v-progress-circular indeterminate color="primary" size="42" width="4" />
        <div class="panel-state__title">正在加载作业配置</div>
        <div class="panel-state__desc">系统正在读取当前采集表对应的 Addax JSON 内容。</div>
      </div>

      <div v-else-if="error" class="panel-state panel-state--error">
        <v-icon size="44" color="error">mdi-alert-circle-outline</v-icon>
        <div class="panel-state__title">配置加载失败</div>
        <v-alert type="error" variant="tonal" class="mt-3">
          {{ error }}
        </v-alert>
      </div>

      <div v-else-if="jobContent" class="code-shell">
        <div class="code-shell__body" :class="{ 'code-shell__body--editing': editing }">
          <template v-if="!editing">
            <pre><code class="language-json hljs" v-html="highlightedCode"></code></pre>
          </template>
          <template v-else>
            <v-textarea
              v-model="editContent"
              auto-grow
              rows="16"
              variant="solo-filled"
              flat
              class="mono-text"
              @input="onEditInput"
            />
          </template>
        </div>
      </div>

      <div v-else class="panel-state">
        <v-icon size="48" class="panel-state__icon">mdi-file-document-outline</v-icon>
        <div class="panel-state__title">暂无配置内容</div>
        <div class="panel-state__desc">当前采集表还没有生成可展示的 Addax 作业配置。</div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue';
  import hljs from 'highlight.js/lib/core';
  import json from 'highlight.js/lib/languages/json';
  import 'highlight.js/styles/atom-one-dark.css';
  import tableService from '@/service/table-service';
  import { useRoute } from 'vue-router';
  import { useNotifier } from '@/stores/notifier';

  hljs.registerLanguage('json', json);

  const { notify } = useNotifier();

  // Why: read tid from route instead of props for page usage
  const route = useRoute();
  const tid = Number(route.params.tid);

  const jobContent = ref('');
  const loading = ref(false);
  const error = ref('');
  const editing = ref(false);
  const editContent = ref('');
  const dirty = ref(false);

  const displayText = computed(() => {
    if (!jobContent.value) return '';
    if (typeof jobContent.value === 'object') {
      return JSON.stringify(jobContent.value, null, 2);
    }

    const raw = String(jobContent.value);
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  });

  const highlightedCode = computed(() => {
    if (!displayText.value) return '';
    return hljs.highlight(displayText.value, { language: 'json' }).value;
  });

  async function copyToClipboard() {
    try {
      await navigator.clipboard.writeText(displayText.value);
      notify('配置已复制到剪贴板', 'success', 2000, undefined, 'mdi-check');
    } catch {
      notify('复制失败，请手动复制', 'error', 3000, undefined, 'mdi-alert');
    }
  }

  async function reloadJob() {
    if (!tid) return;
    loading.value = true;
    error.value = '';
    try {
      const res = await tableService.fetchAddaxJob(tid);
      jobContent.value = res;
      if (!jobContent.value) {
        error.value = '获取到的配置内容为空';
      }
    } catch (err) {
      error.value = (err as Error).message || '获取配置失败';
    } finally {
      loading.value = false;
    }
  }

  onMounted(() => {
    reloadJob();
  });

  function startEdit() {
    editing.value = true;
    dirty.value = false;
    editContent.value = displayText.value;
  }

  function cancelEdit() {
    editing.value = false;
    dirty.value = false;
  }

  function onEditInput() {
    dirty.value = true;
  }

  async function saveEdit() {
    if (!tid) return;
    try {
      loading.value = true;
      try {
        JSON.parse(editContent.value);
      } catch {
        notify('JSON 格式错误，请修正后保存', 'error');
        return;
      }
      await tableService.updateAddaxJob(tid, editContent.value);
      jobContent.value = editContent.value;
      editing.value = false;
      dirty.value = false;
      notify('保存成功', 'success');
    } catch (err) {
      const msg = (err as Error).message || String(err);
      notify(`保存失败: ${msg}`, 'error');
    } finally {
      loading.value = false;
    }
  }
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
    max-width: 680px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .panel-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .summary-strip {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }

  .summary-item {
    padding: 12px 14px;
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

  .code-shell {
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.035), transparent 84%),
      rgb(var(--v-theme-surface));
    overflow: hidden;
  }

  .code-shell__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .code-shell__title-wrap {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .code-shell__title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .code-shell__caption {
    margin-top: 2px;
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .code-shell__body {
    padding: 16px;
    background: #0f172a;
  }

  .code-shell__body--editing {
    background: rgba(var(--v-theme-primary), 0.03);
  }

  .code-shell__body pre {
    margin: 0;
    overflow: auto;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
    font-size: 13px;
    line-height: 1.72;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .code-shell__body code {
    font-family: inherit;
  }

  .mono-text :deep(textarea) {
    min-height: 360px !important;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
    font-size: 13px;
    line-height: 1.72;
  }

  .mono-text :deep(.v-field) {
    border-radius: 14px;
    background: rgba(var(--v-theme-surface), 0.96);
  }

  .panel-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 260px;
    padding: 24px;
    text-align: center;
    border-radius: 18px;
    border: 1px dashed rgba(var(--v-theme-on-surface), 0.16);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .panel-state--error {
    border-style: solid;
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
    max-width: 520px;
    color: rgba(var(--v-theme-on-surface), 0.64);
    line-height: 1.6;
  }

  @media (max-width: 960px) {
    .summary-strip {
      grid-template-columns: 1fr;
    }

    .table-panel__content {
      padding: 16px;
    }

    .code-shell__header {
      align-items: flex-start;
      flex-direction: column;
    }
  }
</style>
