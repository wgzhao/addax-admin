<template>
  <v-form ref="form" fast-fail class="add-source-form" @submit.prevent="save">
    <v-card class="add-source-card">
      <div class="ds-dialog-head">
        <span class="ds-dialog-icon"><v-icon size="20">mdi-database-cog-outline</v-icon></span>
        <div class="ds-dialog-heading">
          <h2 class="ds-dialog-title">{{ dialogTitle }}</h2>
          <p class="ds-dialog-subtitle">{{ dialogSubtitle }}</p>
        </div>
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="关闭" @click="close" />
      </div>

      <v-divider />

      <v-card-text class="ds-dialog-body add-source-body">
        <div class="dialog-grid">
          <section class="form-section">
            <header class="section-head">
              <span class="section-index">01</span>
              <h3>基本信息</h3>
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

          <section class="form-section">
            <header class="section-head">
              <span class="section-index">02</span>
              <h3>连接信息</h3>
              <v-btn
                variant="tonal"
                size="small"
                prepend-icon="mdi-lan-connect"
                :loading="testingConnection"
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

            <div
              v-if="connectionState"
              class="connection-result"
              :class="connectionState.ok ? 'connection-result--ok' : 'connection-result--fail'"
            >
              <v-icon size="16">
                {{ connectionState.ok ? 'mdi-check-circle-outline' : 'mdi-alert-circle-outline' }}
              </v-icon>
              <span>{{ connectionState.text }}</span>
              <v-btn v-if="!connectionState.ok" size="x-small" variant="text" @click="testConnect">
                重试
              </v-btn>
            </div>
          </section>

          <section class="form-section form-section--wide">
            <header class="section-head">
              <span class="section-index">03</span>
              <h3>备注</h3>
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
        <span class="ds-dialog-actions__hint">{{ dirty ? '有未保存的修改' : '' }}</span>
        <v-spacer />
        <v-btn variant="text" @click="close">取消</v-btn>
        <v-btn
          v-if="mode === 'add' || mode === 'edit'"
          type="submit"
          color="primary"
          variant="flat"
        >
          保存
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
    if (props.mode === 'edit') return '修改连接或调度参数，保存后立即生效。';
    if (props.mode === 'detail') return '查看采集源连接与调度配置。';
    return '配置连接与调度参数，保存后加入调度队列。';
  });

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
  }

  .dialog-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 22px 28px;
  }

  .form-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }

  .form-section--wide {
    grid-column: 1 / -1;
  }

  .section-head {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .section-head h3 {
    margin: 0;
    font-size: var(--ds-fs-body);
    font-weight: 600;
  }

  .section-head .v-btn {
    margin-left: auto;
  }

  .section-index {
    color: var(--ds-text-3);
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 11px;
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
    font-size: 12.5px;
    font-weight: 500;
  }

  .field-help {
    margin: 5px 0 0;
    color: var(--ds-text-3);
    font-size: 11.5px;
    line-height: 1.45;
  }

  .switch-row {
    display: flex;
    align-items: center;
    min-height: 40px;
  }

  .switch-row :deep(.v-switch) {
    margin-top: 0;
  }

  .connection-result {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 10px;
    border-radius: var(--ds-radius-sm);
    font-size: 12px;
  }

  .connection-result--ok {
    background: rgba(var(--v-theme-success), 0.08);
    color: rgb(var(--v-theme-success));
  }

  .connection-result--fail {
    background: rgba(var(--v-theme-error), 0.08);
    color: rgb(var(--v-theme-error));
  }

  @media (max-width: 900px) {
    .dialog-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .form-section--wide {
      grid-column: auto;
    }
  }

  @media (max-width: 640px) {
    .field-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .field.span-2 {
      grid-column: auto;
    }
  }
</style>
