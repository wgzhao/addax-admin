<template>
  <div class="sys-settings-page page-shell">
    <PageHeader
      title="系统配置"
      subtitle="运行路径、Hive 连接、执行并发与作业模板。修改后需保存才会生效。"
    >
      <v-chip
        v-if="!loading"
        size="small"
        variant="tonal"
        :color="headerStatus.color"
        :prepend-icon="headerStatus.icon"
      >
        {{ headerStatus.text }}
      </v-chip>
    </PageHeader>

    <v-alert
      v-if="loadError && !loading"
      type="error"
      variant="tonal"
      class="load-error"
      title="系统配置加载失败"
    >
      <div class="load-error__body">
        <span>请检查后端服务与网络后重试，当前页面不会写回任何配置。</span>
        <v-btn size="small" variant="flat" color="error" @click="reload">重新加载</v-btn>
      </div>
    </v-alert>

    <div v-if="loading" class="settings-shell">
      <div class="settings-rail rail-skeleton">
        <div v-for="n in 4" :key="n" class="ds-skeleton" style="height: 54px; margin-bottom: 8px" />
      </div>
      <div class="settings-main">
        <div v-for="n in 4" :key="n" class="ds-card skeleton-card">
          <div class="ds-skeleton" style="height: 20px; width: 30%" />
          <div class="ds-skeleton" style="height: 44px; margin-top: 16px" />
          <div class="ds-skeleton" style="height: 44px; margin-top: 12px" />
        </div>
      </div>
    </div>

    <div v-else class="settings-shell">
      <aside class="settings-rail" aria-label="配置分组">
        <p class="rail-caption">配置分组</p>
        <a
          v-for="item in sections"
          :key="item.id"
          class="rail-link"
          :class="{ 'rail-link--active': activeSection === item.id }"
          :href="`#${item.id}`"
          @click.prevent="jumpTo(item.id)"
        >
          <span class="rail-index">{{ item.index }}</span>
          <span class="rail-text">
            <span class="rail-label">{{ item.label }}</span>
            <span class="rail-meta">{{ item.meta }}</span>
          </span>
          <span v-if="sectionDirty[item.id]" class="rail-dot" aria-label="有未保存修改" />
        </a>
        <p class="rail-note">
          <v-icon size="14">mdi-information-outline</v-icon>
          切日与并发调整属于运行参数，保存后会立即写库。
        </p>
      </aside>

      <v-form ref="form" class="settings-main" @submit.prevent="saveSettings">
        <div class="section-row">
          <section id="cfg-basic" class="ds-card setting-card">
            <header class="setting-head">
              <span class="setting-mark"><v-icon size="20">mdi-tune-variant</v-icon></span>
              <div class="setting-heading">
                <h2>运行基础</h2>
                <p>Addax 安装位置、HDFS 落库默认值，以及业务日切换时刻。</p>
              </div>
            </header>
            <div class="field-grid">
              <div class="field span-2">
                <label class="field-label" for="cfg-addax">Addax 程序目录</label>
                <v-text-field
                  id="cfg-addax"
                  v-model="settings['ADDAX']"
                  placeholder="/opt/app/addax"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  :rules="[rules.required]"
                />
                <p class="field-help">调度进程通过该目录调用 Addax 可执行文件。</p>
              </div>

              <div class="field">
                <label class="field-label" for="cfg-hdfs-prefix">HDFS 目录前缀</label>
                <v-text-field
                  id="cfg-hdfs-prefix"
                  v-model="settings['HDFS_PREFIX']"
                  placeholder="/ods"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  :rules="[rules.required]"
                />
              </div>

              <div class="field">
                <label class="field-label" for="cfg-switch-time">切日时间</label>
                <v-text-field
                  id="cfg-switch-time"
                  v-model="settings['SWITCH_TIME']"
                  placeholder="16:30"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  persistent-placeholder
                  :rules="[rules.required, rules.timeFormat]"
                >
                  <template #append-inner>
                    <v-icon size="18" color="medium-emphasis">mdi-clock-outline</v-icon>
                  </template>
                </v-text-field>
                <p class="field-help">切日之前启动的任务仍计入上一业务日。</p>
              </div>

              <div class="field">
                <label class="field-label" for="cfg-storage">默认 HDFS 存储格式</label>
                <v-select
                  id="cfg-storage"
                  v-model="settings['HDFS_STORAGE_FORMAT']"
                  :items="storageFormats"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  :rules="[rules.required]"
                />
                <p class="field-help">新建采集表时作为默认值。</p>
              </div>

              <div class="field">
                <label class="field-label" for="cfg-compress">默认压缩格式</label>
                <v-select
                  id="cfg-compress"
                  v-model="settings['HDFS_COMPRESS_FORMAT']"
                  :items="compressFormats"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  :rules="[rules.required]"
                />
              </div>
            </div>
          </section>

          <section id="cfg-perf" class="ds-card setting-card">
            <header class="setting-head">
              <span class="setting-mark"><v-icon size="20">mdi-speedometer</v-icon></span>
              <div class="setting-heading">
                <h2>执行并发与队列</h2>
                <p>控制单节点并行度与排队上限，避免瞬时任务压垮目标库。</p>
              </div>
            </header>
            <div class="field-grid field-grid--stack">
              <div class="field">
                <label class="field-label" for="cfg-concurrency">节点最大并发</label>
                <v-text-field
                  id="cfg-concurrency"
                  v-model.number="settings['CONCURRENT_LIMIT']"
                  type="number"
                  min="1"
                  placeholder="30"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  suffix="个"
                  :rules="[rules.required, rules.positiveNumber]"
                />
                <p class="field-help">集群总并发 = 该值 × 在线节点数；修改后需重启所有后端节点。</p>
              </div>

              <div class="field">
                <label class="field-label" for="cfg-queue-size">采集队列长度</label>
                <v-text-field
                  id="cfg-queue-size"
                  v-model.number="settings['QUEUE_SIZE']"
                  type="number"
                  min="1"
                  placeholder="100"
                  variant="outlined"
                  density="comfortable"
                  hide-details="auto"
                  suffix="条"
                  :rules="[rules.required, rules.positiveNumber]"
                />
                <p class="field-help">排队超过该长度时，新任务会等待下一轮调度。</p>
              </div>
            </div>
          </section>
        </div>

        <section id="cfg-hive" class="ds-card setting-card">
          <header class="setting-head">
            <span class="setting-mark"><v-icon size="20">mdi-database-cog-outline</v-icon></span>
            <div class="setting-heading">
              <h2>HiveServer2 连接</h2>
              <p>用于建表、表结构比对与结果校验，连接串变更后建议先测试。</p>
            </div>
            <div class="setting-head-actions">
              <v-chip
                v-if="connectionState"
                size="small"
                :color="connectionState.ok ? 'success' : 'error'"
                variant="tonal"
                :prepend-icon="connectionState.ok ? 'mdi-check-circle' : 'mdi-alert-circle'"
              >
                {{ connectionState.ok ? '连接正常' : '连接失败' }}
              </v-chip>
              <v-btn
                variant="tonal"
                size="small"
                prepend-icon="mdi-lan-connect"
                :loading="testingConnection"
                @click="testHiveConnection"
              >
                测试连接
              </v-btn>
            </div>
          </header>
          <div class="field-grid">
            <div class="field span-2">
              <label class="field-label" for="cfg-hive-url">JDBC 连接地址</label>
              <v-text-field
                id="cfg-hive-url"
                v-model="hiveServer2Config.url"
                placeholder="jdbc:hive2://nn01:10000"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :rules="[rules.required, rules.jdbcUrl]"
              />
            </div>

            <div class="field">
              <label class="field-label" for="cfg-hive-user">用户名</label>
              <v-text-field
                id="cfg-hive-user"
                v-model="hiveServer2Config.username"
                placeholder="hive"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :rules="[rules.required]"
              />
            </div>

            <div class="field">
              <label class="field-label" for="cfg-hive-password">密码</label>
              <v-text-field
                id="cfg-hive-password"
                v-model="hiveServer2Config.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                autocomplete="new-password"
                :append-inner-icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                @click:append-inner="showPassword = !showPassword"
              />
            </div>

            <div class="field">
              <label class="field-label" for="cfg-hive-driver">驱动类名</label>
              <v-text-field
                id="cfg-hive-driver"
                v-model="hiveServer2Config.driverClassName"
                placeholder="org.apache.hive.jdbc.HiveDriver"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :rules="[rules.required]"
              />
            </div>

            <div class="field">
              <label class="field-label" for="cfg-hive-driver-path">驱动路径</label>
              <v-text-field
                id="cfg-hive-driver-path"
                v-model="hiveServer2Config.driverPath"
                placeholder="/opt/app/lib/hive-jdbc.jar"
                variant="outlined"
                density="comfortable"
                hide-details="auto"
                :rules="[rules.required]"
              />
            </div>
          </div>
        </section>

        <section id="cfg-template" class="ds-card setting-card template-card">
          <header class="setting-head">
            <span class="setting-mark"><v-icon size="20">mdi-code-json</v-icon></span>
            <div class="setting-heading">
              <h2>作业模板</h2>
              <p>
                采集任务文件由主模板拼装 RDBMS 读取与 HDFS 写入子模板，支持
                <code>${reader}</code>、<code>${writer}</code> 变量。占位符在执行时由调度器替换，
                校验时按合法插槽处理。
              </p>
            </div>
          </header>

          <div class="editor" :class="{ 'editor--error': !!templateJsonError }">
            <div class="editor-tabs" role="tablist" aria-label="作业模板">
              <button
                v-for="tab in templateTabs"
                :key="tab.key"
                type="button"
                role="tab"
                class="editor-tab"
                :class="{ 'editor-tab--active': activeTemplateKey === tab.key }"
                :aria-selected="activeTemplateKey === tab.key"
                @click="activeTemplateKey = tab.key"
              >
                <span class="tab-dot" :class="`tab-dot--${templateState(tab.key)}`" />
                {{ tab.label }}
                <span v-if="templateDirty(tab.key)" class="tab-dirty">已改</span>
              </button>
              <span class="editor-tabs-spacer" />
              <a
                class="editor-link"
                :href="activeTemplateDocUrl"
                target="_blank"
                rel="noopener noreferrer"
              >
                插件文档
                <v-icon size="14">mdi-open-in-new</v-icon>
              </a>
            </div>

            <textarea
              v-model="activeTemplateText"
              class="editor-input"
              spellcheck="false"
              wrap="off"
              aria-label="作业模板 JSON"
            />

            <div v-if="placeholderTokens.length" class="editor-slots">
              <span class="slots-label">运行时占位符</span>
              <code v-for="token in placeholderTokens" :key="token" class="slot-chip">
                {{ token }}
              </code>
              <span class="slots-hint">保存时原样保留，执行时由调度器替换</span>
            </div>

            <div class="editor-status">
              <span class="editor-metric">
                {{ activeTemplateText.length }} 字符 · {{ templateLines }} 行
              </span>
              <span class="editor-validation" :class="`is-${templateAnalysis.state}`">
                <v-icon size="14">{{ validationIcon }}</v-icon>
                {{ validationText }}
              </span>
              <span class="editor-actions">
                <v-btn size="small" variant="text" :disabled="!canFormat" @click="formatTemplate">
                  格式化
                </v-btn>
                <v-btn
                  size="small"
                  variant="text"
                  :disabled="!activeTemplateText"
                  @click="checkTemplate"
                >
                  校验
                </v-btn>
              </span>
            </div>
            <p v-if="templateJsonError" class="editor-error">{{ templateJsonError }}</p>
          </div>
        </section>

        <div class="save-dock" :class="{ 'save-dock--dirty': dirty }">
          <div class="dock-state">
            <v-icon size="18" :color="dirty ? 'warning' : 'success'">
              {{ dirty ? 'mdi-circle-edit-outline' : 'mdi-check-circle-outline' }}
            </v-icon>
            <span v-if="dirty"
              ><strong>{{ dirtyCount }}</strong> 项修改尚未保存</span
            >
            <span v-else>配置已是最新</span>
            <span v-if="validationFailed" class="dock-error">有字段未通过校验</span>
          </div>
          <div class="dock-actions">
            <v-btn variant="text" :disabled="!dirty || saving" @click="resetChanges"
              >撤销修改</v-btn
            >
            <v-btn
              color="primary"
              variant="flat"
              type="submit"
              prepend-icon="mdi-content-save-outline"
              :loading="saving"
            >
              保存配置
            </v-btn>
          </div>
        </div>
      </v-form>
    </div>

    <ConfirmDialog
      v-model="discardOpen"
      title="放弃未保存的修改"
      :message="`系统配置还有 ${dirtyCount} 项修改未保存，离开后这些修改会丢失。`"
      confirm-text="放弃修改"
      cancel-text="继续编辑"
      @confirm="confirmLeave"
      @cancel="cancelLeave"
    />
  </div>
</template>

<script setup lang="ts">
  import PageHeader from '@/components/PageHeader.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { onBeforeRouteLeave } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import settingsService, { type HiveServer2Config } from '@/service/settings-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils/constants';
  import { SysItem } from '@/types/database';

  type TemplateKey = 'R2H' | 'rR' | 'wH';

  const form = ref<any>(null);
  const loading = ref(true);
  const loadError = ref(false);
  const saving = ref(false);
  const testingConnection = ref(false);
  const showPassword = ref(false);
  const validationFailed = ref(false);

  const settings = ref<any>({});
  const originalSwitchTime = ref<string>('');

  const storageFormats = HDFS_STORAGE_FORMATS;
  const compressFormats = HDFS_COMPRESS_FORMATS;

  const hiveServer2Config = ref<HiveServer2Config>({
    url: '',
    username: '',
    password: '',
    driverClassName: '',
    driverPath: '',
  });

  const templates = ref<Map<string, SysItem>>(new Map());
  const rRJobTemplate = ref<string>('');
  const wHJobTemplate = ref<string>('');
  const R2HJobTemplate = ref<string>('');

  const EMPTY_HIVE: HiveServer2Config = {
    url: '',
    username: '',
    password: '',
    driverClassName: '',
    driverPath: '',
  };

  const templateTabs: Array<{ key: TemplateKey; label: string; doc: string }> = [
    { key: 'R2H', label: '采集主模板', doc: 'https://wgzhao.github.io/Addax/latest/' },
    {
      key: 'rR',
      label: 'RDBMS 读取',
      doc: 'https://wgzhao.github.io/Addax/latest/reader/rdbmsreader/',
    },
    {
      key: 'wH',
      label: 'HDFS 写入',
      doc: 'https://wgzhao.github.io/Addax/latest/writer/hdfswriter/',
    },
  ];

  const templateRefs: Record<TemplateKey, typeof R2HJobTemplate> = {
    R2H: R2HJobTemplate,
    rR: rRJobTemplate,
    wH: wHJobTemplate,
  };

  const activeTemplateKey = ref<TemplateKey>('R2H');
  const activeTemplateText = computed({
    get: () => templateRefs[activeTemplateKey.value].value,
    set: (value: string) => {
      templateRefs[activeTemplateKey.value].value = value;
    },
  });
  const activeTemplateDocUrl = computed(
    () => templateTabs.find(item => item.key === activeTemplateKey.value)?.doc ?? ''
  );

  // Why: a single baseline keeps dirty tracking and "discard" cheap and exact
  const baseline = ref('');
  const EDITABLE_SETTING_KEYS = [
    'ADDAX',
    'HDFS_PREFIX',
    'SWITCH_TIME',
    'HDFS_STORAGE_FORMAT',
    'HDFS_COMPRESS_FORMAT',
    'CONCURRENT_LIMIT',
    'QUEUE_SIZE',
  ];

  const snapshot = () =>
    JSON.stringify({
      settings: Object.fromEntries(
        EDITABLE_SETTING_KEYS.map(key => [key, String(settings.value[key] ?? '')])
      ),
      hive: {
        url: hiveServer2Config.value.url ?? '',
        username: hiveServer2Config.value.username ?? '',
        password: hiveServer2Config.value.password ?? '',
        driverClassName: hiveServer2Config.value.driverClassName ?? '',
        driverPath: hiveServer2Config.value.driverPath ?? '',
      },
      templates: [R2HJobTemplate.value, rRJobTemplate.value, wHJobTemplate.value],
    });

  const currentSnapshot = computed(snapshot);
  const dirty = computed(() => baseline.value !== '' && currentSnapshot.value !== baseline.value);

  const diff = computed(() => {
    if (!baseline.value) return null;
    const before = JSON.parse(baseline.value);
    const after = JSON.parse(currentSnapshot.value);
    return { before, after };
  });

  const dirtyCount = computed(() => {
    if (!diff.value || !dirty.value) return 0;
    const { before, after } = diff.value;
    let count = 0;
    for (const key of EDITABLE_SETTING_KEYS) {
      if (before.settings[key] !== after.settings[key]) count += 1;
    }
    for (const key of Object.keys(after.hive)) {
      if (before.hive[key] !== after.hive[key]) count += 1;
    }
    after.templates.forEach((value: string, index: number) => {
      if (before.templates[index] !== value) count += 1;
    });
    return count;
  });

  const headerStatus = computed(() => {
    if (loadError.value) {
      return { text: '加载失败', color: 'error', icon: 'mdi-alert-circle-outline' };
    }
    if (dirty.value) {
      return {
        text: `${dirtyCount.value} 项待保存`,
        color: undefined,
        icon: 'mdi-shield-check-outline',
      };
    }
    return { text: '配置已同步', color: undefined, icon: 'mdi-shield-check-outline' };
  });

  const sectionDirty = computed<Record<string, boolean>>(() => {
    if (!diff.value) {
      return { 'cfg-basic': false, 'cfg-hive': false, 'cfg-perf': false, 'cfg-template': false };
    }
    const { before, after } = diff.value;
    const changed = (keys: string[]) =>
      keys.some(key => before.settings[key] !== after.settings[key]);
    return {
      'cfg-basic': changed([
        'ADDAX',
        'HDFS_PREFIX',
        'SWITCH_TIME',
        'HDFS_STORAGE_FORMAT',
        'HDFS_COMPRESS_FORMAT',
      ]),
      'cfg-hive': Object.keys(after.hive).some(key => before.hive[key] !== after.hive[key]),
      'cfg-perf': changed(['CONCURRENT_LIMIT', 'QUEUE_SIZE']),
      'cfg-template': after.templates.some(
        (value: string, index: number) => before.templates[index] !== value
      ),
    };
  });

  const hiveHost = computed(() => {
    const match = /^jdbc:hive2:\/\/([^/;]+)/.exec(hiveServer2Config.value.url || '');
    return match?.[1] || '未配置连接串';
  });

  const sections = computed(() => [
    {
      id: 'cfg-basic',
      index: '01',
      label: '运行基础',
      meta: `切日 ${settings.value['SWITCH_TIME'] || '--:--'} · ${
        settings.value['HDFS_STORAGE_FORMAT'] || '默认存储'
      }`,
    },
    {
      id: 'cfg-perf',
      index: '02',
      label: '并发与队列',
      meta: `并发 ${settings.value['CONCURRENT_LIMIT'] ?? '-'} · 队列 ${
        settings.value['QUEUE_SIZE'] ?? '-'
      }`,
    },
    { id: 'cfg-hive', index: '03', label: 'HiveServer2', meta: hiveHost.value },
    {
      id: 'cfg-template',
      index: '04',
      label: '作业模板',
      meta: `${
        [R2HJobTemplate, rRJobTemplate, wHJobTemplate].filter(item => item.value.trim()).length
      } / 3 已配置`,
    },
  ]);

  const activeSection = ref('cfg-basic');

  type TemplateAnalysis = {
    state: 'ok' | 'slot' | 'bad' | 'empty';
    tokens: string[];
    message: string;
  };

  // Why: placeholders like ${column} are runtime fragments, so strict JSON.parse would
  // reject a template that is actually valid for the scheduler
  const maskPlaceholders = (text: string) => {
    let masked = '';
    const tokens: string[] = [];
    const positions: number[] = [];
    let inString = false;
    let i = 0;

    while (i < text.length) {
      const char = text[i];
      if (inString) {
        if (char === '\\') {
          masked += text.slice(i, i + 2);
          positions.push(i, i + 1);
          i += 2;
          continue;
        }
        if (char === '"') inString = false;
        masked += char;
        positions.push(i);
        i += 1;
        continue;
      }

      if (char === '"') {
        inString = true;
        masked += char;
        positions.push(i);
        i += 1;
        continue;
      }

      if (char === '$' && text[i + 1] === '{') {
        const end = text.indexOf('}', i + 2);
        if (end !== -1) {
          const literal = JSON.stringify(`__ADDAX_SLOT_${tokens.length}__`);
          tokens.push(text.slice(i, end + 1));
          for (let k = 0; k < literal.length; k += 1) positions.push(i);
          masked += literal;
          i = end + 1;
          continue;
        }
      }

      masked += char;
      positions.push(i);
      i += 1;
    }

    return { masked, tokens, positions };
  };

  const positionToLineColumn = (text: string, position: number) => {
    const head = text.slice(0, position);
    const line = head.split('\n').length;
    const column = position - head.lastIndexOf('\n');
    return { line, column };
  };

  const analyzeTemplate = (text: string): TemplateAnalysis => {
    if (!text.trim()) return { state: 'empty', tokens: [], message: '' };

    const { masked, tokens, positions } = maskPlaceholders(text);
    try {
      JSON.parse(masked);
      return { state: tokens.length ? 'slot' : 'ok', tokens, message: '' };
    } catch (error: any) {
      const raw = String(error?.message || 'JSON 解析失败');
      const positionMatch = /at position (\d+)/.exec(raw);
      const lineMatch = /line (\d+) column (\d+)/.exec(raw);
      let line = lineMatch ? Number(lineMatch[1]) : 0;
      let column = lineMatch ? Number(lineMatch[2]) : 0;

      // Why: masked placeholders are longer than the original tokens, so map the
      // reported offset back before telling the user where the syntax error is
      if (positionMatch) {
        const original = positions[Number(positionMatch[1])] ?? text.length;
        ({ line, column } = positionToLineColumn(text, original));
      }

      const reason = raw.split(' in JSON')[0];
      const where = line ? `（第 ${line} 行第 ${column} 列）` : '';
      return { state: 'bad', tokens, message: `${reason}${where}` };
    }
  };

  const templateAnalysis = computed(() => analyzeTemplate(activeTemplateText.value));
  const templateJsonError = computed(() =>
    templateAnalysis.value.state === 'bad' ? templateAnalysis.value.message : ''
  );
  const placeholderTokens = computed(() =>
    [...new Set(templateAnalysis.value.tokens)].slice(0, 12)
  );
  const validationText = computed(() => {
    switch (templateAnalysis.value.state) {
      case 'empty':
        return '模板为空';
      case 'bad':
        return '语法错误';
      case 'slot':
        return `结构有效 · ${templateAnalysis.value.tokens.length} 处占位符`;
      default:
        return 'JSON 结构有效';
    }
  });
  const validationIcon = computed(() => {
    switch (templateAnalysis.value.state) {
      case 'empty':
        return 'mdi-code-braces';
      case 'bad':
        return 'mdi-alert-circle-outline';
      case 'slot':
        return 'mdi-code-braces';
      default:
        return 'mdi-check-circle-outline';
    }
  });

  const templateLines = computed(() => activeTemplateText.value.split('\n').length);
  const canFormat = computed(
    () => !!activeTemplateText.value.trim() && templateAnalysis.value.state !== 'bad'
  );

  const templateState = (key: TemplateKey) => {
    return analyzeTemplate(templateRefs[key].value).state;
  };

  const templateDirty = (key: TemplateKey) => {
    if (!diff.value) return false;
    const index = templateTabs.findIndex(item => item.key === key);
    return diff.value.before.templates[index] !== diff.value.after.templates[index];
  };

  const connectionState = ref<{ ok: boolean; message: string } | null>(null);

  const rules = {
    required: (value: any) => {
      if (typeof value === 'number') return value !== null && value !== undefined;
      return !!value || '此字段为必填项';
    },
    positiveNumber: (value: any) => {
      const parsed = Number(value);
      return (Number.isFinite(parsed) && parsed > 0) || '请输入大于 0 的数字';
    },
    timeFormat: (value: string) => {
      if (!value) return '此字段为必填项';
      const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
      return timeRegex.test(value) || '时间格式不正确，请使用 HH:mm 格式（如：16:30）';
    },
    jdbcUrl: (value: string) => {
      if (!value) return '此字段为必填项';
      const jdbcRegex = /^jdbc:hive2:\/\/.+$/;
      return jdbcRegex.test(value) || 'JDBC 地址应以 jdbc:hive2:// 开头';
    },
  };

  const testHiveConnection = async () => {
    testingConnection.value = true;
    connectionState.value = null;
    try {
      const res = await settingsService.testHiveConnection(hiveServer2Config.value);
      connectionState.value = { ok: true, message: res.data || '连接成功' };
      notify(res.data || '连接成功', 'success');
    } catch (error: any) {
      connectionState.value = { ok: false, message: error?.message || '连接失败' };
      notify(error?.message || error || '连接失败', 'error');
    } finally {
      testingConnection.value = false;
    }
  };

  // Why: a stale "connection ok" badge after editing the URL is worse than none
  watch(
    hiveServer2Config,
    () => {
      connectionState.value = null;
    },
    { deep: true }
  );

  const jumpTo = (id: string) => {
    activeSection.value = id;
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const formatTemplate = () => {
    const analysis = templateAnalysis.value;
    if (analysis.state === 'empty') return;
    if (analysis.state === 'bad') {
      notify('无法格式化: ' + analysis.message, 'error');
      return;
    }

    const { masked, tokens } = maskPlaceholders(activeTemplateText.value);
    const parsed = JSON.parse(masked);
    let formatted = JSON.stringify(parsed, null, 2);
    tokens.forEach((token, index) => {
      formatted = formatted.split(`"__ADDAX_SLOT_${index}__"`).join(token);
    });
    activeTemplateText.value = formatted;
    notify('已格式化当前模板，占位符已保留', 'success');
  };

  const checkTemplate = () => {
    const analysis = templateAnalysis.value;
    if (analysis.state === 'bad') {
      notify('模板语法错误: ' + analysis.message, 'error');
      return;
    }
    if (analysis.state === 'empty') {
      notify('当前模板为空', 'warning');
      return;
    }
    notify(
      analysis.state === 'slot'
        ? `模板结构有效，包含 ${analysis.tokens.length} 处运行时占位符`
        : '模板结构有效',
      'success'
    );
  };

  const focusFirstError = async () => {
    await new Promise(resolve => setTimeout(resolve, 60));
    const el = document.querySelector<HTMLElement>('.v-input--error, .v-field--error');
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el?.querySelector<HTMLInputElement>('input, textarea')?.focus();
  };

  const saveSettings = async () => {
    const { valid: isValid } = await form.value.validate();
    validationFailed.value = !isValid;
    if (!isValid) {
      notify('请修正表单中标红的字段', 'error');
      focusFirstError();
      return;
    }

    settings.value['HIVE_SERVER2'] = JSON.stringify(hiveServer2Config.value);

    saving.value = true;
    try {
      const rRTemplate = templates.value.get('rR');
      const wHTemplate = templates.value.get('wH');
      const r2hTemplate = templates.value.get('R2H');

      if (!rRTemplate || !wHTemplate || !r2hTemplate) {
        notify('作业模板配置缺失，请刷新后重试', 'error');
        return;
      }

      const payload: SysItem[] = [
        {
          dictCode: rRTemplate.dictCode,
          itemKey: 'rR',
          itemValue: rRJobTemplate.value,
          remark: rRTemplate.remark,
        },
        {
          dictCode: wHTemplate.dictCode,
          itemKey: 'wH',
          itemValue: wHJobTemplate.value,
          remark: wHTemplate.remark,
        },
        {
          dictCode: r2hTemplate.dictCode,
          itemKey: 'R2H',
          itemValue: R2HJobTemplate.value,
          remark: r2hTemplate.remark,
        },
      ];

      await settingsService.saveJobTemplates(payload);

      const result = await settingsService.saveSettings(settings.value);
      if (result) {
        notify('系统配置保存成功', 'success');
        const newSwitchTime = settings.value['SWITCH_TIME'];
        if (
          originalSwitchTime.value &&
          newSwitchTime &&
          originalSwitchTime.value !== newSwitchTime
        ) {
          try {
            const resp = await settingsService.rescheduleSwitchTimeTask();
            const msg = (resp && (resp.message || resp.data)) || '切日调度任务已重新注册';
            notify(msg, 'success');
          } catch (err: any) {
            notify('切日调度任务重新注册失败: ' + (err?.message || err), 'error');
          }
          originalSwitchTime.value = newSwitchTime;
        }
        baseline.value = snapshot();
        validationFailed.value = false;
      } else {
        notify('保存配置失败', 'warning');
      }
    } catch (error: any) {
      notify('保存配置失败: ' + (error.message || error), 'error');
    } finally {
      saving.value = false;
    }
  };

  const resetChanges = () => {
    if (!baseline.value) return;
    const saved = JSON.parse(baseline.value);
    for (const key of EDITABLE_SETTING_KEYS) {
      settings.value[key] = saved.settings[key];
    }
    hiveServer2Config.value = { ...saved.hive };
    R2HJobTemplate.value = saved.templates[0];
    rRJobTemplate.value = saved.templates[1];
    wHJobTemplate.value = saved.templates[2];
    validationFailed.value = false;
    form.value?.resetValidation?.();
    notify('已撤销未保存的修改', 'info');
  };

  const loadSettings = async () => {
    loading.value = true;
    loadError.value = false;
    try {
      const [loadedSettings, loadedTemplates] = await Promise.all([
        settingsService.getSettings(),
        settingsService.getJobConfig(),
      ]);
      settings.value = loadedSettings;
      templates.value =
        loadedTemplates instanceof Map
          ? loadedTemplates
          : new Map(Object.entries((loadedTemplates as unknown as Record<string, SysItem>) || {}));

      rRJobTemplate.value = templates.value.get('rR')?.itemValue || '';
      wHJobTemplate.value = templates.value.get('wH')?.itemValue || '';
      R2HJobTemplate.value = templates.value.get('R2H')?.itemValue || '';

      if (!settings.value['HDFS_STORAGE_FORMAT']) {
        settings.value['HDFS_STORAGE_FORMAT'] = storageFormats[0];
      }
      if (!settings.value['HDFS_COMPRESS_FORMAT']) {
        settings.value['HDFS_COMPRESS_FORMAT'] = compressFormats[0];
      }

      if (loadedSettings['HIVE_SERVER2']) {
        if (typeof loadedSettings['HIVE_SERVER2'] === 'string') {
          try {
            hiveServer2Config.value = JSON.parse(loadedSettings['HIVE_SERVER2']);
          } catch (error) {
            console.error('解析 HIVE_SERVER2 配置失败:', error);
            hiveServer2Config.value = { ...EMPTY_HIVE };
          }
        } else {
          hiveServer2Config.value = loadedSettings['HIVE_SERVER2'];
        }
      }

      if (typeof settings.value['SWITCH_TIME'] === 'string') {
        originalSwitchTime.value = settings.value['SWITCH_TIME'];
      }

      baseline.value = snapshot();
    } catch (error: any) {
      loadError.value = true;
      notify('加载系统配置失败: ' + (error?.message || error), 'error');
    } finally {
      loading.value = false;
    }
  };

  const reload = () => loadSettings();

  const onScroll = () => {
    const marker = window.scrollY + Math.min(window.innerHeight * 0.35, 360);
    let current = 'cfg-basic';
    for (const item of sections.value) {
      const el = document.getElementById(item.id);
      if (el && el.offsetTop <= marker) current = item.id;
    }
    activeSection.value = current;
  };

  const discardOpen = ref(false);
  let leaveResolver: ((value: boolean) => void) | null = null;

  onBeforeRouteLeave(() => {
    if (!dirty.value) return true;
    discardOpen.value = true;
    return new Promise<boolean>(resolve => {
      leaveResolver = resolve;
    });
  });

  const confirmLeave = () => {
    discardOpen.value = false;
    leaveResolver?.(true);
    leaveResolver = null;
  };

  const cancelLeave = () => {
    discardOpen.value = false;
    leaveResolver?.(false);
    leaveResolver = null;
  };

  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!dirty.value) return;
    event.preventDefault();
    event.returnValue = '';
  };

  onMounted(() => {
    loadSettings();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('beforeunload', onBeforeUnload);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('scroll', onScroll);
    window.removeEventListener('beforeunload', onBeforeUnload);
  });
</script>

<style scoped>
  .sys-settings-page {
    min-width: 0;
  }

  .settings-shell {
    display: grid;
    grid-template-columns: 236px minmax(0, 1fr);
    gap: 16px;
    align-items: start;
  }

  .settings-rail {
    position: sticky;
    top: 16px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px;
    background: var(--ds-surface-raised);
    border: 1px solid var(--ds-border-default);
    border-radius: var(--ds-radius-md);
  }

  .rail-caption {
    margin: 2px 8px 8px;
    color: var(--ds-text-3);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .rail-link {
    position: relative;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 10px;
    border-radius: var(--ds-radius-sm);
    color: inherit;
    text-decoration: none;
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  .rail-link:hover {
    background: rgba(var(--v-theme-primary), 0.06);
  }

  .rail-link--active {
    background: rgba(var(--v-theme-primary), 0.1);
  }

  .rail-link--active::before {
    content: '';
    position: absolute;
    left: 0;
    top: 8px;
    bottom: 8px;
    width: 3px;
    border-radius: 0 3px 3px 0;
    background: rgb(var(--v-theme-primary));
  }

  .rail-index {
    min-width: 20px;
    color: var(--ds-text-3);
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .rail-link--active .rail-index {
    color: rgb(var(--v-theme-primary));
  }

  .rail-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
    line-height: 1.35;
  }

  .rail-label {
    font-size: 13px;
    font-weight: 500;
  }

  .rail-meta {
    overflow: hidden;
    color: var(--ds-text-3);
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rail-dot {
    width: 6px;
    height: 6px;
    margin-left: auto;
    border-radius: 50%;
    background: rgb(var(--v-theme-warning));
    flex: none;
  }

  .rail-note {
    display: flex;
    gap: 6px;
    margin: 10px 6px 2px;
    padding-top: 10px;
    border-top: 1px solid var(--ds-border-subtle);
    color: var(--ds-text-3);
    font-size: 11px;
    line-height: 1.5;
  }

  .settings-main {
    display: flex;
    flex-direction: column;
    gap: 16px;
    min-width: 0;
  }

  .section-row {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
    gap: 16px;
    align-items: stretch;
  }

  .setting-card {
    scroll-margin-top: 16px;
    padding: 0;
    overflow: hidden;
  }

  .setting-head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .setting-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
    flex: none;
  }

  .setting-heading {
    min-width: 0;
  }

  .setting-heading h2 {
    margin: 0;
    font-size: var(--ds-fs-section);
    font-weight: 600;
    line-height: 1.4;
  }

  .setting-heading p {
    margin: 2px 0 0;
    color: var(--ds-text-2);
    font-size: 13px;
    line-height: 1.5;
  }

  .setting-heading code {
    padding: 1px 5px;
    border-radius: 4px;
    background: rgba(var(--v-theme-on-surface), 0.06);
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 12px;
  }

  .setting-head-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px 24px;
    padding: 18px 20px 20px;
  }

  .field {
    min-width: 0;
  }

  .field.span-2 {
    grid-column: 1 / -1;
  }

  .field-grid--stack {
    grid-template-columns: minmax(0, 1fr);
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    color: rgb(var(--v-theme-on-surface));
    font-size: 13px;
    font-weight: 500;
  }

  .field-help {
    margin: 6px 0 0;
    color: var(--ds-text-3);
    font-size: 12px;
    line-height: 1.5;
  }

  .template-card .setting-head {
    border-bottom: none;
    padding-bottom: 8px;
  }

  .editor {
    margin: 0 20px 20px;
    border: 1px solid var(--ds-border-default);
    border-radius: var(--ds-radius-sm);
    overflow: hidden;
    background: rgba(var(--v-theme-on-surface), 0.015);
  }

  .editor--error {
    border-color: rgba(var(--v-theme-error), 0.45);
  }

  .editor-tabs {
    display: flex;
    align-items: center;
    gap: 2px;
    padding: 4px 6px;
    border-bottom: 1px solid var(--ds-border-subtle);
    overflow-x: auto;
  }

  .editor-tab {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 7px 12px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--ds-text-2);
    font-family: inherit;
    font-size: 13px;
    white-space: nowrap;
    cursor: pointer;
  }

  .editor-tab:hover {
    background: rgba(var(--v-theme-on-surface), 0.05);
    color: rgb(var(--v-theme-on-surface));
  }

  .editor-tab--active {
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
    font-weight: 500;
  }

  .tab-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--ds-text-3);
  }

  .tab-dot--ok {
    background: rgb(var(--v-theme-success));
  }

  .tab-dot--bad {
    background: rgb(var(--v-theme-error));
  }

  .tab-dot--slot {
    background: rgb(var(--v-theme-primary));
  }

  .tab-dirty {
    padding: 0 5px;
    border-radius: 4px;
    background: rgba(var(--v-theme-warning), 0.16);
    color: rgb(var(--v-theme-warning));
    font-size: 10px;
    line-height: 16px;
  }

  .editor-tabs-spacer {
    flex: 1 1 auto;
  }

  .editor-link {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    padding: 0 8px;
    color: var(--ds-text-2);
    font-size: 12px;
    text-decoration: none;
    white-space: nowrap;
  }

  .editor-link:hover {
    color: rgb(var(--v-theme-primary));
  }

  .editor-input {
    display: block;
    width: 100%;
    height: 380px;
    padding: 14px 16px;
    border: none;
    background: transparent;
    color: rgb(var(--v-theme-on-surface));
    font-family: 'SFMono-Regular', Menlo, Consolas, 'Liberation Mono', monospace;
    font-size: 12.5px;
    line-height: 1.6;
    resize: vertical;
    tab-size: 2;
    outline: none;
  }

  .editor-input:focus-visible {
    box-shadow: inset 0 0 0 2px rgb(var(--v-theme-primary));
  }

  .editor-status {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 6px 10px 6px 16px;
    border-top: 1px solid var(--ds-border-subtle);
    background: rgba(var(--v-theme-on-surface), 0.025);
    color: var(--ds-text-3);
    font-size: 12px;
    flex-wrap: wrap;
  }

  .editor-metric {
    font-variant-numeric: tabular-nums;
  }

  .editor-slots {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 7px 16px;
    border-top: 1px solid var(--ds-border-subtle);
    background: rgba(var(--v-theme-primary), 0.035);
    flex-wrap: wrap;
  }

  .slots-label {
    color: var(--ds-text-3);
    font-size: 11px;
    letter-spacing: 0.02em;
  }

  .slot-chip {
    padding: 1px 6px;
    border: 1px solid rgba(var(--v-theme-primary), 0.25);
    border-radius: 4px;
    background: rgba(var(--v-theme-primary), 0.08);
    color: rgb(var(--v-theme-primary));
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 11px;
    line-height: 18px;
  }

  .slots-hint {
    color: var(--ds-text-3);
    font-size: 11px;
  }

  .editor-validation {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .editor-validation.is-ok {
    color: rgb(var(--v-theme-success));
  }

  .editor-validation.is-slot {
    color: rgb(var(--v-theme-primary));
  }

  .editor-validation.is-bad {
    color: rgb(var(--v-theme-error));
  }

  .editor-validation.is-empty {
    color: var(--ds-text-3);
  }

  .editor-actions {
    display: inline-flex;
    gap: 4px;
    margin-left: auto;
  }

  .editor-error {
    margin: 0;
    padding: 8px 16px;
    border-top: 1px solid rgba(var(--v-theme-error), 0.25);
    background: rgba(var(--v-theme-error), 0.06);
    color: rgb(var(--v-theme-error));
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 12px;
  }

  .save-dock {
    position: sticky;
    bottom: 14px;
    z-index: 3;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px 10px 16px;
    border: 1px solid var(--ds-border-default);
    border-radius: var(--ds-radius-md);
    background: color-mix(in srgb, var(--ds-surface-raised) 88%, transparent);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.1);
    backdrop-filter: blur(10px);
    transition: border-color 0.2s ease, box-shadow 0.2s ease;
  }

  .save-dock--dirty {
    border-color: rgba(var(--v-theme-primary), 0.4);
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
  }

  .dock-state {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--ds-text-2);
    font-size: 13px;
  }

  .dock-state strong {
    color: rgb(var(--v-theme-on-surface));
    font-variant-numeric: tabular-nums;
  }

  .dock-error {
    padding-left: 8px;
    border-left: 1px solid var(--ds-border-default);
    color: rgb(var(--v-theme-error));
  }

  .dock-actions {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;
  }

  .skeleton-card {
    padding: 20px;
  }

  .load-error__body {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .rail-skeleton {
    min-height: 260px;
  }

  @media (max-width: 1199px) {
    .settings-shell {
      grid-template-columns: minmax(0, 1fr);
      gap: 12px;
    }

    .settings-rail {
      position: static;
      flex-direction: row;
      align-items: center;
      gap: 4px;
      padding: 6px;
      overflow-x: auto;
    }

    .section-row {
      grid-template-columns: minmax(0, 1fr);
    }

    .rail-caption,
    .rail-note {
      display: none;
    }

    .rail-link {
      flex: none;
      padding: 7px 10px;
    }

    .rail-link--active::before {
      left: 10px;
      right: 10px;
      top: auto;
      bottom: 0;
      width: auto;
      height: 2px;
      border-radius: 2px 2px 0 0;
    }

    .rail-meta {
      display: none;
    }
  }

  @media (max-width: 900px) {
    .field-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .setting-head {
      flex-wrap: wrap;
    }

    .setting-head-actions {
      width: 100%;
      margin-left: 46px;
      justify-content: flex-start;
    }

    .editor-input {
      height: 320px;
    }
  }

  @media (max-width: 600px) {
    .setting-head {
      padding: 14px 16px;
    }

    .field-grid {
      padding: 16px;
    }

    .editor {
      margin: 0 16px 16px;
    }

    .setting-head-actions {
      margin-left: 0;
    }

    .save-dock {
      flex-wrap: wrap;
    }

    .dock-actions {
      width: 100%;
      margin-left: 0;
      justify-content: flex-end;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "系统配置",
    "icon": "mdi-cog-outline",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 10
  }
}
</route>
