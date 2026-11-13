<template>
  <v-navigation-drawer
    :model-value="modelValue"
    location="right"
    temporary
    width="560"
    class="failure-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="failure-drawer__header">
      <div>
        <div class="failure-drawer__eyebrow">失败详情</div>
        <div class="failure-drawer__title">
          {{ tableLabel || (failure ? `采集表 #${failure.tid}` : '加载中') }}
        </div>
      </div>
      <v-btn icon variant="text" size="small" aria-label="关闭" @click="close">
        <v-icon>mdi-close</v-icon>
      </v-btn>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" />

    <div v-if="error" class="failure-drawer__state">
      <v-icon size="40" color="error">mdi-alert-circle-outline</v-icon>
      <div class="failure-drawer__state-title">加载失败</div>
      <div class="failure-drawer__state-desc">{{ error }}</div>
    </div>

    <div v-else-if="emptyReason" class="failure-drawer__state">
      <v-icon size="44" color="warning">mdi-information-outline</v-icon>
      <div class="failure-drawer__state-title">{{ emptyTitle }}</div>
      <div class="failure-drawer__state-desc">{{ hint || emptyDesc }}</div>
      <div class="failure-drawer__actions">
        <v-btn
          v-if="resolvedTid"
          size="small"
          variant="tonal"
          prepend-icon="mdi-table-eye"
          :to="`/table/detail/${resolvedTid}`"
        >
          打开采集表详情
        </v-btn>
        <v-btn
          v-if="resolvedTid"
          size="small"
          variant="tonal"
          prepend-icon="mdi-format-list-bulleted"
          :to="`/table/detail/${resolvedTid}?tab=failures`"
        >
          查看失败记录
        </v-btn>
      </div>
    </div>

    <template v-else-if="failure">
      <div class="failure-drawer__body">
        <div class="failure-drawer__chips">
          <v-chip size="small" color="error" variant="tonal">
            {{ stageLabel(failure.stage) }}
          </v-chip>
          <v-chip size="small" :color="categoryColor(failure.category)" variant="tonal">
            {{ categoryLabel(failure.category) }}
          </v-chip>
          <v-chip v-if="failure.finalAttempt" size="small" color="warning" variant="tonal">
            最终失败
          </v-chip>
        </div>

        <div class="failure-drawer__summary">{{ failure.summary }}</div>

        <v-alert
          v-if="hint"
          type="info"
          variant="tonal"
          density="compact"
          class="mt-3"
          :text="hint"
        />

        <div class="failure-drawer__meta">
          <div>
            <span>发生时间</span>
            <strong>{{ failure.occurredAt }}</strong>
          </div>
          <div>
            <span>尝试次数</span>
            <strong>{{ attemptLabel }}</strong>
          </div>
          <div>
            <span>业务日期</span>
            <strong>{{ failure.bizDate || '-' }}</strong>
          </div>
          <div>
            <span>运行类型</span>
            <strong>{{ failure.runType === 'FILLBACK' ? '补数' : '常规' }}</strong>
          </div>
          <div>
            <span>错误类</span>
            <strong class="mono">{{ failure.errorClass || '-' }}</strong>
          </div>
          <div>
            <span>错误码</span>
            <strong class="mono">{{ failure.errorCode || '-' }}</strong>
          </div>
        </div>

        <div v-if="occurrences.count > 1" class="failure-drawer__occurrence">
          同类错误共出现 <strong>{{ occurrences.count }}</strong> 次
          <span v-if="occurrences.firstSeenAt">
            ，首次 {{ occurrences.firstSeenAt }}，最近 {{ occurrences.lastSeenAt }}
          </span>
        </div>

        <div class="failure-drawer__section">
          <div class="failure-drawer__section-title">证据</div>

          <div class="evidence-row">
            <v-icon size="18" :color="evidenceColor(evidence.log)">mdi-file-document-outline</v-icon>
            <div class="evidence-row__text">
              <strong>原始日志</strong>
              <span>{{ evidenceText('log') }}</span>
            </div>
            <v-btn
              v-if="evidence.log === 'available'"
              size="small"
              variant="tonal"
              color="primary"
              :loading="logLoading"
              @click="openLog"
            >
              查看
            </v-btn>
          </div>

          <div class="evidence-row">
            <v-icon size="18" :color="evidenceColor(evidence.execution)">mdi-history</v-icon>
            <div class="evidence-row__text">
              <strong>执行快照</strong>
              <span>{{ evidenceText('execution') }}</span>
            </div>
            <v-btn
              v-if="evidence.execution === 'available' && resolvedTid"
              size="small"
              variant="tonal"
              color="primary"
              :to="`/table/detail/${resolvedTid}?tab=executions`"
            >
              查看
            </v-btn>
          </div>

          <div class="evidence-row evidence-row--stack">
            <v-icon size="18" :color="evidenceColor(evidence.detail)">mdi-code-braces</v-icon>
            <div class="evidence-row__text">
              <strong>详细堆栈</strong>
              <span>{{ evidenceText('detail') }}</span>
            </div>
            <v-btn
              v-if="evidence.detail === 'available' && failure.detail"
              size="small"
              variant="text"
              color="primary"
              @click="showDetail = !showDetail"
            >
              {{ showDetail ? '收起' : '展开' }}
            </v-btn>
          </div>
          <pre v-if="showDetail && failure.detail" class="failure-drawer__detail">{{ failure.detail }}</pre>
        </div>
      </div>

      <div class="failure-drawer__footer">
        <v-btn
          v-if="resolvedTid"
          size="small"
          variant="tonal"
          prepend-icon="mdi-format-list-bulleted"
          :to="`/table/detail/${resolvedTid}?tab=failures`"
        >
          失败记录
        </v-btn>
        <v-btn
          size="small"
          variant="text"
          prepend-icon="mdi-filter-outline"
          :to="{ path: '/failures', query: { tid: String(failure.tid) } }"
        >
          在错误中心查看
        </v-btn>
        <v-spacer />
        <v-btn size="small" variant="text" prepend-icon="mdi-content-copy" @click="copySummary">
          复制摘要
        </v-btn>
      </div>
    </template>

    <div v-else-if="!loading" class="failure-drawer__state">
      <v-icon size="40" color="disabled">mdi-help-circle-outline</v-icon>
      <div class="failure-drawer__state-title">没有可显示的信息</div>
    </div>

  </v-navigation-drawer>

  <!-- Keep the log dialog outside the drawer: inside it is positioned relative to the drawer and
       renders as a small floating box instead of a full-width dialog. -->
  <v-dialog v-model="logDialog" attach="body" max-width="1100" scrollable>
    <v-card>
      <v-card-title class="d-flex align-center">
        原始日志
        <v-chip v-if="failure?.logName" size="small" variant="tonal" class="ml-3">
          {{ failure.logName }}
        </v-chip>
        <v-spacer />
        <v-btn icon variant="text" size="small" @click="logDialog = false">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-card-title>
      <v-card-text class="log-dialog-body">
        <v-alert v-if="logError" type="error" variant="tonal">{{ logError }}</v-alert>
        <pre v-else class="log-dialog-content">{{ logContent }}</pre>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import failureService from '@/service/failure-service';
  import logService from '@/service/log-service';
  import { notify } from '@/stores/notifier';
  import {
    failureCategoryColor,
    failureCategoryLabel,
    failureStageLabel,
  } from '@/utils/failure-labels';
  import type {
    EvidenceState,
    Failure,
    FailureDetail,
    FailureEvidence,
    FailureOccurrences,
    LatestFailure,
  } from '@/types/failure';

  const props = defineProps<{
    modelValue: boolean;
    tid?: number | null;
    failureId?: number | null;
  }>();

  const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();

  const loading = ref(false);
  const error = ref('');
  const latest = ref<LatestFailure | null>(null);
  const detail = ref<FailureDetail | null>(null);
  const logDialog = ref(false);
  const logLoading = ref(false);
  const logContent = ref('');
  const logError = ref('');
  const showDetail = ref(false);

  const failure = computed<Failure | null>(() => detail.value?.failure ?? latest.value?.failure ?? null);
  const evidence = computed<FailureEvidence>(
    () => detail.value?.evidence ?? latest.value?.evidence ?? { detail: 'missing', log: 'missing', execution: 'missing' }
  );
  const occurrences = computed<FailureOccurrences>(
    () => detail.value?.occurrences ?? latest.value?.occurrences ?? { count: 0, firstSeenAt: null, lastSeenAt: null }
  );
  const hint = computed(() => detail.value?.hint ?? latest.value?.hint ?? null);
  const resolvedTid = computed(() => props.tid ?? failure.value?.tid ?? null);
  const tableLabel = computed(() => {
    const f = failure.value;
    if (!f) return '';
    const source = [f.sourceDb, f.sourceTable].filter(Boolean).join('.');
    const target = [f.targetDb, f.targetTable].filter(Boolean).join('.');
    return [source, target].filter(Boolean).join(' → ') || `采集表 #${f.tid}`;
  });
  const emptyReason = computed(() => !loading.value && !error.value && !failure.value);
  const emptyTitle = computed(() => {
    if (latest.value?.reason === 'PURGED') return '失败记录已清理';
    if (latest.value?.reason === 'NO_EVIDENCE_FOR_STATE') return '没有对应的失败记录';
    return '暂无失败记录';
  });
  const emptyDesc = computed(() => {
    if (latest.value?.reason === 'PURGED') return '该表没有找到对应的失败记录，可能已按保留策略清理。';
    if (latest.value?.reason === 'NO_EVIDENCE_FOR_STATE') return '状态为失败，但系统没有找到失败事实。';
    return '该采集表当前没有失败记录。';
  });
  const attemptLabel = computed(() => {
    const f = failure.value;
    if (!f || f.attemptNo == null) return '-';
    return f.maxAttempts ? `${f.attemptNo} / ${f.maxAttempts}` : String(f.attemptNo);
  });

  const stageLabel = failureStageLabel;
  const categoryLabel = failureCategoryLabel;
  const categoryColor = failureCategoryColor;

  function evidenceColor(state: EvidenceState) {
    if (state === 'available') return 'success';
    if (state === 'restricted') return 'warning';
    return 'disabled';
  }

  function evidenceText(kind: 'detail' | 'log' | 'execution') {
    const state = evidence.value[kind];
    if (state === 'available') {
      if (kind === 'log') return failure.value?.logName || '可查看';
      if (kind === 'execution') return failure.value?.executionId ? `执行 #${failure.value.executionId}` : '可查看';
      return '可展开查看';
    }
    if (state === 'restricted') return '需要该采集源的操作授权';
    if (state === 'purged') return '原始日志已按保留策略清理';
    if (kind === 'log') return '本次运行没有原始日志';
    if (kind === 'execution') return '没有关联的执行快照';
    return '没有记录详细堆栈';
  }

  async function load() {
    if (!props.modelValue) return;
    loading.value = true;
    error.value = '';
    latest.value = null;
    detail.value = null;
    showDetail.value = false;
    try {
      if (props.failureId != null) {
        detail.value = await failureService.getDetail(props.failureId);
      } else if (props.tid != null) {
        latest.value = await failureService.getLatest(props.tid);
      }
    } catch (e) {
      error.value = (e as Error)?.message || String(e);
    } finally {
      loading.value = false;
    }
  }

  async function openLog() {
    const logId = failure.value?.logId;
    if (!logId) return;
    logLoading.value = true;
    logError.value = '';
    logContent.value = '';
    try {
      logContent.value = await logService.getContent(logId);
      logDialog.value = true;
    } catch (e) {
      const message = (e as Error)?.message || String(e);
      logError.value = `原始日志加载失败：${message}`;
      logDialog.value = true;
    } finally {
      logLoading.value = false;
    }
  }

  function copySummary() {
    if (!failure.value) return;
    navigator.clipboard
      ?.writeText(failure.value.summary)
      .then(() => notify('失败摘要已复制', 'success'))
      .catch(() => notify('复制失败', 'error'));
  }

  function close() {
    emit('update:modelValue', false);
  }

  watch(
    () => [props.modelValue, props.tid, props.failureId],
    () => {
      if (props.modelValue) load();
    },
    { immediate: true }
  );
</script>

<style scoped>
  .failure-drawer {
    background: rgb(var(--v-theme-surface));
  }

  .failure-drawer__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 20px 20px 12px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .failure-drawer__eyebrow {
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 12px;
    letter-spacing: 0.08em;
  }

  .failure-drawer__title {
    margin-top: 4px;
    font-size: 16px;
    font-weight: 600;
    word-break: break-all;
  }

  .failure-drawer__body {
    padding: 16px 20px;
  }

  .failure-drawer__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .failure-drawer__summary {
    margin-top: 12px;
    padding: 12px 14px;
    border-left: 3px solid rgb(var(--v-theme-error));
    background: rgba(var(--v-theme-error), 0.06);
    border-radius: 6px;
    font-size: 14px;
    line-height: 1.55;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .failure-drawer__meta {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 20px;
    margin-top: 16px;
  }

  .failure-drawer__meta span {
    display: block;
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .failure-drawer__meta strong {
    display: block;
    margin-top: 2px;
    font-size: 13px;
    font-weight: 600;
    word-break: break-all;
  }

  .failure-drawer__occurrence {
    margin-top: 16px;
    font-size: 13px;
    color: rgba(var(--v-theme-on-surface), 0.75);
  }

  .failure-drawer__section {
    margin-top: 20px;
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    padding-top: 16px;
  }

  .failure-drawer__section-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 8px;
  }

  .evidence-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 0;
  }

  .evidence-row--stack {
    align-items: flex-start;
  }

  .evidence-row__text {
    flex: 1;
    min-width: 0;
  }

  .evidence-row__text strong {
    display: block;
    font-size: 13px;
  }

  .evidence-row__text span {
    display: block;
    margin-top: 2px;
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
    word-break: break-all;
  }

  .failure-drawer__detail {
    margin-top: 8px;
    max-height: 320px;
    overflow: auto;
    padding: 12px;
    border-radius: 8px;
    background: rgba(var(--v-theme-on-surface), 0.04);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .failure-drawer__footer {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 20px 20px;
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .failure-drawer__state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 64px 32px;
    text-align: center;
  }

  .failure-drawer__state-title {
    font-size: 15px;
    font-weight: 600;
  }

  .failure-drawer__state-desc {
    font-size: 13px;
    color: rgba(var(--v-theme-on-surface), 0.7);
    line-height: 1.55;
  }

  .failure-drawer__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 8px;
    margin-top: 8px;
  }

  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }

  .log-dialog-body {
    max-height: 70vh;
  }

  .log-dialog-content {
    margin: 0;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
  }
</style>
