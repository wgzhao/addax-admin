<template>
  <v-form ref="form" fast-fail class="add-source-form" @submit.prevent="save">
    <v-card class="add-source-card">
      <div class="ds-dialog-head source-dialog-head">
        <span class="ds-dialog-icon"><v-icon size="20">mdi-database-cog-outline</v-icon></span>
        <div class="ds-dialog-heading">
          <h2 class="ds-dialog-title">{{ dialogTitle }}</h2>
          <p class="ds-dialog-subtitle">{{ dialogSubtitle }}</p>
          <div v-if="showContext" class="source-context" aria-label="采集源摘要">
            <span class="source-context__code">{{ sourceItem.code }}</span>
            <span class="source-context__divider" aria-hidden="true" />
            <span
              class="source-context__state"
              :class="{ 'source-context__state--off': sourceItem.enabled === false }"
            >
              <span class="source-context__dot" aria-hidden="true" />
              {{ sourceItem.enabled === false ? '调度已停用' : '调度已启用' }}
            </span>
            <span class="source-context__item">
              <v-icon size="14">mdi-clock-outline</v-icon>
              {{ scheduleSummary }}
            </span>
          </div>
        </div>
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="关闭" @click="close" />
      </div>

      <v-divider />

      <v-card-text class="ds-dialog-body add-source-body">
        <div class="dialog-grid">
          <section class="source-section">
            <header class="section-head">
              <span class="source-section__icon">
                <v-icon size="16">mdi-card-account-details-outline</v-icon>
              </span>
              <div class="section-heading">
                <h3>基本信息</h3>
                <p>标识、调度日历与运行并发</p>
              </div>
            </header>

            <div class="field-grid">
              <div class="field">
                <label class="field-label" for="src-code">采集编号</label>
                <v-text-field
                  id="src-code"
                  v-model="sourceItem.code"
                  placeholder="两位大写字母"
                  autocomplete="off"
                  :disabled="mode === 'edit'"
                  :rules="[rules.required, rules.codeExistsRule]"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                />
                <p class="field-help">
                  {{
                    mode === 'edit' ? '编号保存后不可修改' : '建议使用两位大写字母，保存后不可修改'
                  }}
                </p>
              </div>

              <div class="field">
                <label class="field-label" for="src-name">采集名称</label>
                <v-text-field
                  id="src-name"
                  v-model="sourceItem.name"
                  placeholder="例如：巨源资讯"
                  :rules="[rules.required]"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                />
              </div>

              <div class="field">
                <label class="field-label" for="src-start">采集时间</label>
                <v-text-field
                  id="src-start"
                  v-model="sourceItem.startAt"
                  placeholder="HH:mm 或 HH:mm:ss"
                  :rules="[timeFormatRule]"
                  :error-messages="timeError"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                />
                <p class="field-help">留空时使用系统默认调度时间</p>
              </div>

              <div class="field">
                <label class="field-label" for="src-calendar">采集日历</label>
                <v-select
                  id="src-calendar"
                  v-model="sourceItem.calendar"
                  :items="calendarOptions"
                  item-title="label"
                  item-value="value"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                  placeholder="自然日"
                />
                <p class="field-help">决定哪些日期触发采集，默认自然日</p>
              </div>

              <div class="field">
                <label class="field-label" for="src-concurrency">最大并发数</label>
                <v-text-field
                  id="src-concurrency"
                  v-model.number="sourceItem.maxConcurrency"
                  type="number"
                  min="1"
                  placeholder="默认 10"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                />
                <p class="field-help">留空则使用系统默认值</p>
              </div>

              <div class="field">
                <span class="field-label">启用状态</span>
                <div class="switch-row">
                  <v-switch
                    v-model="sourceItem.enabled"
                    color="primary"
                    hide-details
                    density="compact"
                    inset
                    :label="sourceItem.enabled ? '已启用调度' : '已禁用调度'"
                  />
                </div>
                <p class="field-help">禁用后不再参与调度，历史配置保留</p>
              </div>
            </div>
          </section>

          <section class="source-section source-section--connection">
            <header class="section-head">
              <span class="source-section__icon">
                <v-icon size="16">mdi-lan-connect</v-icon>
              </span>
              <div class="section-heading">
                <h3>连接信息</h3>
                <p>数据库地址与访问凭据</p>
              </div>
              <v-chip
                v-if="connectionFamily"
                size="x-small"
                variant="tonal"
                class="connection-family"
              >
                {{ connectionFamily }}
              </v-chip>
              <v-btn
                class="connection-test"
                variant="tonal"
                size="small"
                prepend-icon="mdi-lan-connect"
                :loading="testingConnection"
                :aria-busy="testingConnection"
                :disabled="mode === 'detail' || !sourceItem.url"
                @click="testConnect"
              >
                测试连接
              </v-btn>
            </header>

            <div class="field-grid">
              <div class="field span-2">
                <label class="field-label" for="src-url">JDBC 连接地址</label>
                <v-text-field
                  id="src-url"
                  v-model="sourceItem.url"
                  placeholder="jdbc:hive2://host:port"
                  :rules="[rules.required]"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                />
              </div>

              <div class="field">
                <label class="field-label" for="src-user">用户名</label>
                <v-text-field
                  id="src-user"
                  v-model="sourceItem.username"
                  variant="outlined"
                  density="compact"
                  autocomplete="off"
                  hide-details="auto"
                />
              </div>

              <div class="field">
                <label class="field-label" for="src-pass">密码</label>
                <v-text-field
                  id="src-pass"
                  v-model="sourceItem.pass"
                  :type="showPassword ? 'text' : 'password'"
                  variant="outlined"
                  density="compact"
                  autocomplete="new-password"
                  :append-inner-icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                  hide-details="auto"
                  @click:append-inner="showPassword = !showPassword"
                />
              </div>
            </div>

            <div class="connection-support">
              <div
                v-if="connectionState"
                class="connection-result"
                :class="connectionState.ok ? 'connection-result--ok' : 'connection-result--fail'"
                role="status"
                aria-live="polite"
              >
                <v-icon size="16">
                  {{ connectionState.ok ? 'mdi-check-circle-outline' : 'mdi-alert-circle-outline' }}
                </v-icon>
                <span>{{ connectionState.text }}</span>
                <v-btn
                  v-if="!connectionState.ok"
                  size="x-small"
                  variant="text"
                  @click="testConnect"
                >
                  重试
                </v-btn>
              </div>
              <div class="connection-footnote">
                <v-icon size="15">mdi-shield-key-outline</v-icon>
                <span>连接测试使用当前表单值，密码不会在列表中显示。</span>
              </div>
            </div>
          </section>

          <section class="source-section source-section--wide">
            <header class="section-head">
              <span class="source-section__icon">
                <v-icon size="16">mdi-text-box-outline</v-icon>
              </span>
              <div class="section-heading">
                <h3>备注</h3>
                <p>记录用途、负责人或维护注意事项</p>
              </div>
            </header>
            <v-textarea
              v-model="sourceItem.remark"
              placeholder="补充该采集源的用途、负责人或注意事项"
              auto-grow
              rows="2"
              variant="outlined"
              density="compact"
              hide-details
            />
          </section>
        </div>
      </v-card-text>

      <v-card-actions class="ds-dialog-actions">
        <span class="ds-dialog-actions__state" :class="{ 'is-dirty': dirty }">
          <span class="ds-dialog-actions__dot" aria-hidden="true" />
          {{ actionStateText }}
        </span>
        <v-spacer />
        <v-btn variant="text" @click="close">取消</v-btn>
        <v-btn
          v-if="mode === 'add' || mode === 'edit'"
          type="submit"
          color="primary"
          variant="flat"
          :disabled="submitDisabled"
        >
          {{ submitLabel }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-form>
</template>

<script setup lang="ts">
  import { ref, computed, watch, onMounted } from 'vue';
  import { notify } from '@/stores/notifier';
  import sourceService from '@/service/source-service';
  import { EtlSource } from '@/types/database';

  const props = defineProps({
    sid: Number,
    mode: String,
    cloneData: Object,
  });

  const form = ref<any>(null);
  const showPassword = ref(false);
  const testingConnection = ref(false);
  const connectionState = ref<{ ok: boolean; text: string } | null>(null);
  const dirty = ref(false);

  const defaultSourceItem: EtlSource = {
    id: 0,
    code: '',
    name: '',
    url: '',
    username: '',
    pass: '',
    startAt: '',
    calendar: '',
    prerequisite: '',
    preScript: '',
    remark: '',
    enabled: true,
    maxConcurrency: 10,
  };

  const sourceItem = ref<EtlSource>({ ...defaultSourceItem });

  const dialogTitle = computed(() => {
    if (props.mode === 'edit') return '编辑采集源';
    if (props.mode === 'detail') return '采集源详情';
    return '新增采集源';
  });

  const dialogSubtitle = computed(() => {
    if (props.mode === 'edit') return '调整运行参数与数据库连接，保存后立即生效。';
    if (props.mode === 'detail') return '查看采集源连接与调度配置。';
    return '先定义采集源身份，再配置连接与运行策略。';
  });

  const showContext = computed(() => props.mode !== 'add' && sourceItem.value.id > 0);

  const calendarLabel = computed(() => {
    if (sourceItem.value.calendar === 'WORK_DAY') return '工作日';
    if (sourceItem.value.calendar === 'TRADE_DAY') return '交易日';
    return '自然日';
  });

  const scheduleSummary = computed(
    () => `${sourceItem.value.startAt || '系统时间'} · ${calendarLabel.value}`
  );

  const connectionFamily = computed(() => {
    const url = String(sourceItem.value.url || '').toLowerCase();
    if (url.startsWith('jdbc:postgresql:')) return 'PostgreSQL';
    if (url.startsWith('jdbc:mysql:')) return 'MySQL';
    if (url.startsWith('jdbc:hive2:')) return 'Hive';
    if (url.startsWith('jdbc:oracle:')) return 'Oracle';
    if (url.startsWith('jdbc:sqlserver:')) return 'SQL Server';
    return url.startsWith('jdbc:') ? 'JDBC' : '';
  });

  const actionStateText = computed(() => {
    if (dirty.value) return '有未保存的修改';
    if (props.mode === 'add') return '填写完成后创建采集源';
    if (props.mode === 'detail') return '当前配置仅供查看';
    return '修改后保存立即生效';
  });

  const submitDisabled = computed(() => props.mode === 'edit' && !dirty.value);

  const submitLabel = computed(() => (props.mode === 'edit' ? '保存修改' : '创建采集源'));

  // Why: the source form is long; leaving by Esc/backdrop must not silently drop edits
  const initialSnapshot = ref('');
  const isDirty = () =>
    initialSnapshot.value !== '' && JSON.stringify(sourceItem.value) !== initialSnapshot.value;

  const markClean = () => {
    initialSnapshot.value = JSON.stringify(sourceItem.value);
    dirty.value = false;
  };

  watch(
    sourceItem,
    () => {
      dirty.value = isDirty();
    },
    { deep: true }
  );

  defineExpose({ isDirty });

  // 自然日不需要字典数据，所以是“空值即默认”，工作日/交易日各自对应一份日期字典。
  const calendarOptions = [
    { label: '自然日', value: '' },
    { label: '工作日（含调休）', value: 'WORK_DAY' },
    { label: '交易日', value: 'TRADE_DAY' },
  ];

  const emit = defineEmits(['closeDialog', 'save']);

  const timeFormatRule = (value: string) => {
    if (!value) return true;
    const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$/;
    return timeRegex.test(value) || '时间格式不正确，请使用 HH:mm 或 HH:mm:ss 格式（如：08:30）';
  };

  const timeError = computed(() => {
    if (!sourceItem.value.startAt) return [];
    const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?$/;
    if (!timeRegex.test(sourceItem.value.startAt)) {
      return ['时间格式不正确，请使用 HH:mm 或 HH:mm:ss 格式（如：08:30）'];
    }
    return [];
  });

  const formatTimeInput = (value: string) => {
    if (!value) return value;
    const timeWithoutSeconds = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
    if (timeWithoutSeconds.test(value)) {
      return value + ':00';
    }
    return value;
  };

  const rules = {
    required: (value: string) => !!value || '必填项',
    codeExistsRule: async (value: string) => {
      if (props.mode !== 'add' || !value) {
        return true;
      }
      try {
        const exists = await sourceService.checkCode(value);
        return !exists || '编号已存在';
      } catch (error) {
        notify('检查编号失败: ' + ((error as Error).message || error), 'error');
        return '编号校验失败，请稍后重试';
      }
    },
  };

  const save = async () => {
    if (props.mode !== 'add' && props.mode !== 'edit') return;

    const { valid } = await form.value.validate();
    if (!valid) {
      notify('请修正表单中的错误', 'error');
      await new Promise(resolve => setTimeout(resolve, 60));
      const firstInvalid = document.querySelector<HTMLElement>(
        '.add-source-body .v-input--error, .add-source-body .v-field--error'
      );
      firstInvalid?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      firstInvalid?.querySelector<HTMLInputElement>('input, textarea')?.focus();
      return;
    }

    if (sourceItem.value.startAt && timeError.value.length > 0) {
      notify('请检查启动时间格式', 'error');
      return;
    }

    if (sourceItem.value.startAt) {
      sourceItem.value.startAt = formatTimeInput(sourceItem.value.startAt);
    }

    sourceService
      .save(sourceItem.value)
      .then(() => {
        markClean();
        notify('保存成功', 'success');
        emit('save');
        emit('closeDialog');
      })
      .catch(error => {
        notify('保存失败: ' + error, 'error');
      });
  };

  const close = () => {
    emit('closeDialog');
  };

  const testConnect = async () => {
    if (!sourceItem.value.url) {
      notify('请先填写 JDBC 连接地址', 'warning');
      return;
    }
    testingConnection.value = true;
    connectionState.value = null;
    try {
      const resp = await sourceService.testConnection({
        url: sourceItem.value.url,
        username: sourceItem.value.username,
        password: sourceItem.value.pass,
        sourceId: sourceItem.value.id > 0 ? sourceItem.value.id : undefined,
      });
      connectionState.value = resp
        ? { ok: true, text: '连接成功，可以保存配置' }
        : { ok: false, text: '连接失败，请检查地址与账号密码' };
    } catch (error: any) {
      connectionState.value = { ok: false, text: '连接失败：' + (error?.message || error) };
    } finally {
      testingConnection.value = false;
    }
  };

  watch(
    () => [sourceItem.value.url, sourceItem.value.username, sourceItem.value.pass],
    () => {
      connectionState.value = null;
    }
  );

  const applyCloneData = () => {
    const cloneData = props.cloneData as Partial<EtlSource> | undefined;
    if (!cloneData) return;
    sourceItem.value = {
      ...defaultSourceItem,
      ...cloneData,
      calendar: cloneData.calendar ?? '',
      id: 0,
      code: '',
    };
  };

  const initializeForm = () => {
    if (props.mode === 'add') {
      sourceItem.value = { ...defaultSourceItem };
      applyCloneData();
      markClean();
      return;
    }

    if (props.sid != -1) {
      sourceService
        .get(Number(props.sid))
        .then(resp => {
          // null 表示自然日，转成空串才能命中下拉项，否则会显示成原始值
          sourceItem.value = { ...resp, calendar: resp.calendar ?? '' };
          markClean();
        })
        .catch(error => {
          console.log(error);
          notify(`加载采集源失败: ${error}`, 'error');
        });
    }
  };

  watch(
    () => [props.sid, props.mode, props.cloneData],
    () => {
      initializeForm();
    },
    { immediate: true }
  );

  onMounted(() => {
    if (!initialSnapshot.value) markClean();
  });
</script>

<style scoped>
  .add-source-card {
    background: var(--ds-surface-raised);
    border: 1px solid var(--ds-border-default);
    box-shadow: 0 24px 70px rgba(15, 23, 42, 0.22);
    overflow: hidden;
  }

  .source-dialog-head {
    position: relative;
    padding: 18px 20px 16px;
    background: linear-gradient(118deg, rgba(var(--v-theme-primary), 0.12), transparent 48%),
      rgba(var(--v-theme-surface-variant), 0.34);
  }

  .source-context {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 9px;
    color: var(--ds-text-2);
    font-size: var(--ds-fs-caption);
  }

  .source-context__code {
    display: inline-flex;
    align-items: center;
    min-height: 22px;
    padding: 1px 7px;
    border: 1px solid rgba(var(--v-theme-primary), 0.2);
    border-radius: 6px;
    background: rgba(var(--v-theme-primary), 0.08);
    color: rgb(var(--v-theme-primary));
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
  }

  .source-context__divider {
    width: 1px;
    height: 12px;
    background: var(--ds-border-default);
  }

  .source-context__state,
  .source-context__item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  .source-context__state {
    color: rgb(var(--v-theme-success));
    font-weight: 600;
  }

  .source-context__state--off {
    color: var(--ds-text-3);
  }

  .source-context__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 0 3px rgba(var(--v-theme-success), 0.12);
  }

  .source-context__state--off .source-context__dot {
    box-shadow: none;
  }

  .dialog-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: stretch;
    gap: 16px;
  }

  .source-section {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 14px;
    min-width: 0;
    padding: 14px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: var(--ds-radius-md);
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.025), transparent 42%),
      rgba(var(--v-theme-surface-variant), 0.3);
    animation: source-section-enter 0.26s ease-out both;
  }

  .source-section:nth-child(2) {
    animation-delay: 0.04s;
  }

  .source-section:nth-child(3) {
    animation-delay: 0.08s;
  }

  .source-section--wide {
    grid-column: 1 / -1;
  }

  .section-head {
    display: flex;
    align-items: center;
    gap: 9px;
    flex-wrap: wrap;
    padding-bottom: 11px;
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .source-section__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    flex: none;
    border-radius: 8px;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
  }

  .section-heading {
    flex: 1 1 160px;
    min-width: 0;
  }

  .section-heading h3 {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: var(--ds-fs-section);
    font-weight: 600;
    line-height: 1.35;
  }

  .section-heading p {
    margin: 1px 0 0;
    color: var(--ds-text-3);
    font-size: 11.5px;
    line-height: 1.4;
  }

  .connection-family {
    flex: none;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
    letter-spacing: 0;
  }

  .connection-test {
    margin-left: auto;
  }

  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 16px;
  }

  .field {
    min-width: 0;
  }

  .field.span-2 {
    grid-column: 1 / -1;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    color: rgb(var(--v-theme-on-surface));
    font-size: var(--ds-fs-caption);
    font-weight: 600;
    letter-spacing: 0.01em;
  }

  .field-help {
    margin: 5px 0 0;
    color: var(--ds-text-3);
    font-size: 11px;
    line-height: 1.45;
  }

  .switch-row {
    display: flex;
    align-items: center;
    min-height: 40px;
    padding: 1px 10px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: var(--ds-radius-sm);
    background: rgba(var(--v-theme-surface), 0.72);
  }

  .switch-row :deep(.v-switch) {
    margin-top: 0;
  }

  .connection-support {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: auto;
  }

  .connection-result {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 9px 10px;
    border: 1px solid transparent;
    border-radius: var(--ds-radius-sm);
    font-size: var(--ds-fs-caption);
    line-height: 1.45;
  }

  .connection-result--ok {
    background: rgba(var(--v-theme-success), 0.08);
    border-color: rgba(var(--v-theme-success), 0.14);
    color: rgb(var(--v-theme-success));
  }

  .connection-result--fail {
    background: rgba(var(--v-theme-error), 0.08);
    border-color: rgba(var(--v-theme-error), 0.14);
    color: rgb(var(--v-theme-error));
  }

  .connection-footnote {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    padding-top: 2px;
    color: var(--ds-text-3);
    font-size: 11px;
    line-height: 1.45;
  }

  .ds-dialog-actions__state {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--ds-text-3);
    font-size: var(--ds-fs-caption);
  }

  .ds-dialog-actions__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: rgba(var(--v-theme-on-surface), 0.28);
  }

  .ds-dialog-actions__state.is-dirty {
    color: rgb(var(--v-theme-warning));
    font-weight: 600;
  }

  .ds-dialog-actions__state.is-dirty .ds-dialog-actions__dot {
    background: rgb(var(--v-theme-warning));
    box-shadow: 0 0 0 3px rgba(var(--v-theme-warning), 0.12);
  }

  @keyframes source-section-enter {
    from {
      opacity: 0;
      transform: translateY(5px);
    }

    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  @media (max-width: 900px) {
    .dialog-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .source-section--wide {
      grid-column: auto;
    }
  }

  @media (max-width: 640px) {
    .source-dialog-head {
      padding: 16px 14px 14px;
    }

    .add-source-body {
      padding: 14px !important;
    }

    .source-context {
      gap: 6px 8px;
    }

    .source-context__item {
      flex-basis: 100%;
    }

    .source-section {
      padding: 13px;
    }

    .field-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .field.span-2 {
      grid-column: auto;
    }

    .connection-test {
      margin-left: 37px;
    }

    .ds-dialog-actions {
      flex-wrap: wrap;
      padding: 10px 14px;
      row-gap: 8px;
    }

    .ds-dialog-actions__state {
      flex: 1 0 100%;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .source-section {
      animation: none;
    }
  }
</style>
