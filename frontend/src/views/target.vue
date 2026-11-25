<template>
  <div
    class="target-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible }"
  >
    <PageHeader title="目标端管理" subtitle="统一维护目标端连接、模板键与默认落库策略。">
      <v-btn
        v-if="authStore.canWrite"
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus"
        @click="openCreate"
      >
        新增目标端
      </v-btn>
    </PageHeader>

    <v-card flat class="ds-card toolbar-card">
      <v-card-text class="ds-card__content">
        <v-row density="comfortable" align="center">
          <v-col cols="12" md="5" lg="4">
            <v-text-field
              v-model="searchValue"
              density="compact"
              variant="outlined"
              label="搜索编码 / 名称 / 模板键"
              prepend-inner-icon="mdi-magnify"
              single-line
              hide-details
            />
          </v-col>
          <v-col cols="12" md="7" lg="8" class="d-flex justify-end">
            <v-switch
              v-model="enabledOnly"
              inset
              density="compact"
              color="primary"
              hide-details
              label="仅显示启用"
              @update:model-value="loadTargets"
            />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <AppDataTable
      :headers="headers"
      :items="targets"
      :loading="loading"
      :search="searchValue"
      :custom-filter="targetFilter"
      item-value="id"
      scrollable
      items-per-page="20"
      empty-title="暂无目标端"
      empty-description="当前还没有配置任何目标端。点击下方按钮新增目标端后，即可配置连接、模板键和默认落库策略。"
      :empty-primary="authStore.canWrite ? { label: '新增目标端', icon: 'mdi-plus' } : null"
      @empty-primary="openCreate"
    >
      <template #item.name="{ item }">
        <div class="ds-cell-stack">
          <span class="ds-cell-meta">{{ item.code || '未配置编码' }}</span>
          <span class="ds-cell-primary">{{ item.name || '-' }}</span>
        </div>
      </template>

      <template #item.targetType="{ item }">
        <v-chip size="small" :color="getTargetTypeColor(item.targetType)" variant="tonal">
          {{ item.targetType || '-' }}
        </v-chip>
      </template>

      <template #item.writerTemplateKey="{ item }">
        <span class="ds-cell-primary ds-mono-text">{{ item.writerTemplateKey || '-' }}</span>
      </template>

      <template #item.enabled="{ item }">
        <v-chip
          size="small"
          :color="item.enabled ? 'success' : 'error'"
          :variant="item.enabled ? 'tonal' : 'outlined'"
        >
          {{ item.enabled ? '已启用' : '已停用' }}
        </v-chip>
      </template>

      <template #item.isDefault="{ item }">
        <v-chip
          size="small"
          :color="item.isDefault ? 'primary' : 'default'"
          :variant="item.isDefault ? 'tonal' : 'outlined'"
        >
          {{ item.isDefault ? '默认' : '否' }}
        </v-chip>
      </template>

      <template #item.action="{ item }">
        <div v-if="authStore.canWrite" class="ds-action-inline">
          <v-btn
            size="small"
            variant="text"
            color="primary"
            class="px-2 ds-action-btn-main"
            @click="openEdit(item)"
          >
            <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
          </v-btn>
          <v-btn
            size="small"
            variant="text"
            color="info"
            class="px-2"
            title="仅检测 JDBC 连接；HDFS 写入请进入编辑页点击「校验目标端」"
            @click="testConnect(item)"
          >
            <v-icon size="16" class="mr-1">mdi-connection</v-icon>测试连接
          </v-btn>
          <v-btn size="small" variant="text" color="error" class="px-2" @click="remove(item)">
            <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
          </v-btn>
        </div>
      </template>
    </AppDataTable>

    <v-navigation-drawer
      v-model="dialogVisible"
      location="right"
      temporary
      width="min(640px, 100vw)"
      class="target-editor-drawer"
      scrim="rgba(2, 6, 23, 0.48)"
      :persistent="true"
    >
      <v-card class="target-editor-card">
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-server-network</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">{{ form.id ? '编辑目标端' : '新增目标端' }}</h2>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="dialogVisible = false"
          />
        </div>
        <v-divider />
        <v-card-text class="ds-dialog-body">
          <v-form ref="targetForm" @submit.prevent="save">
            <div class="dialog-grid">
              <section class="form-section">
                <header class="section-head">
                  <h3>基本信息</h3>
                </header>
                <div class="field-grid">
                  <div class="field">
                    <label class="field-label" for="target-code">
                      编码 <span class="field-required" aria-hidden="true">*</span>
                    </label>
                    <v-text-field
                      id="target-code"
                      v-model="form.code"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autofocus
                      autocomplete="off"
                      :rules="[targetRules.required]"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-name">
                      名称 <span class="field-required" aria-hidden="true">*</span>
                    </label>
                    <v-text-field
                      id="target-name"
                      v-model="form.name"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                      :rules="[targetRules.required]"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-type">目标端类型</label>
                    <v-select
                      id="target-type"
                      v-model="form.targetType"
                      :items="targetTypes"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-template">Writer 模板键</label>
                    <v-select
                      id="target-template"
                      v-model="form.writerTemplateKey"
                      :items="writerTemplateChoices"
                      item-title="label"
                      item-value="value"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                </div>
              </section>

              <section class="form-section">
                <header class="section-head">
                  <h3>连接信息</h3>
                  <div class="section-actions">
                    <v-btn
                      v-if="isRdbmsTarget || isDorisTarget"
                      size="x-small"
                      variant="text"
                      prepend-icon="mdi-lan-connect"
                      :loading="testingTarget"
                      @click="testConnectPayload"
                    >
                      测试连接
                    </v-btn>
                    <v-btn
                      size="x-small"
                      variant="text"
                      class="section-toggle"
                      @click="showConnectJson = !showConnectJson"
                    >
                      {{ showConnectJson ? '隐藏连接 JSON' : '查看连接 JSON' }}
                    </v-btn>
                  </div>
                </header>
                <div class="field-grid">
                  <div class="field span-2">
                    <label class="field-label" for="target-url">JDBC URL</label>
                    <v-text-field
                      id="target-url"
                      v-model="connectForm.url"
                      :placeholder="connectionPlaceholder"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="url"
                      :rules="[requiredRule]"
                    />
                  </div>
                  <div
                    v-if="(isRdbmsTarget || isDorisTarget) && detectedDialect"
                    class="field span-2 dialect-hint"
                  >
                    <span class="field-label">识别到的方言</span>
                    <v-chip
                      v-if="detectedDialect.supported"
                      size="small"
                      color="success"
                      variant="tonal"
                    >
                      {{ detectedDialect.dialect }} · {{ detectedDialect.writerPlugin }}
                    </v-chip>
                    <v-alert
                      v-else
                      type="warning"
                      variant="tonal"
                      density="compact"
                      class="dialect-alert"
                    >
                      目标方言 {{ detectedDialect.dialect }} 暂不支持；支持：
                      {{ detectedDialect.supportedDialects }}
                    </v-alert>
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-user">连接用户名</label>
                    <v-text-field
                      id="target-user"
                      v-model="connectForm.username"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="username"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-pass">连接密码</label>
                    <v-text-field
                      id="target-pass"
                      v-model="connectForm.password"
                      :type="showPassword ? 'text' : 'password'"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="new-password"
                    >
                      <template #append-inner>
                        <v-btn
                          icon
                          size="x-small"
                          variant="text"
                          :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                          @click="showPassword = !showPassword"
                        >
                          <v-icon size="18">
                            {{ showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline' }}
                          </v-icon>
                        </v-btn>
                      </template>
                    </v-text-field>
                  </div>
                  <div v-if="showConnectJson" class="field span-2">
                    <label class="field-label" for="target-connect-json">连接配置 JSON</label>
                    <v-textarea
                      id="target-connect-json"
                      :model-value="form.connectConfig"
                      variant="outlined"
                      density="compact"
                      rows="3"
                      hide-details
                      readonly
                      class="config-preview"
                    />
                  </div>
                </div>
              </section>

              <section v-if="isDorisTarget" class="form-section form-section--wide">
                <header class="section-head">
                  <h3>Doris Stream Load 设置</h3>
                </header>
                <div class="field-grid">
                  <div class="field span-2">
                    <label class="field-label" for="target-doris-load-url">
                      loadUrl <span class="field-required" aria-hidden="true">*</span>
                    </label>
                    <v-textarea
                      id="target-doris-load-url"
                      v-model="connectForm.dorisLoadUrl"
                      placeholder="fe1:8030&#10;fe2:8030"
                      hint="每行一个 host:port；Stream Load 的 HTTP 端点，通常为 FE 的 8030 端口，不要带 http:// 前缀"
                      variant="outlined"
                      density="compact"
                      rows="3"
                      hide-details="auto"
                      :rules="[loadUrlRule]"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-doris-batch-size">batchSize</label>
                    <v-text-field
                      id="target-doris-batch-size"
                      v-model.number="connectForm.dorisBatchSize"
                      type="number"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-doris-flush-interval">
                      flushInterval (ms)
                    </label>
                    <v-text-field
                      id="target-doris-flush-interval"
                      v-model.number="connectForm.dorisFlushInterval"
                      type="number"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-doris-flush-queue"
                      >flushQueueLength</label
                    >
                    <v-text-field
                      id="target-doris-flush-queue"
                      v-model.number="connectForm.dorisFlushQueueLength"
                      type="number"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field span-2">
                    <label class="field-label" for="target-doris-load-props"
                      >loadProps (JSON)</label
                    >
                    <v-textarea
                      id="target-doris-load-props"
                      v-model="connectForm.dorisLoadProps"
                      placeholder='{ "format": "csv" }'
                      variant="outlined"
                      density="compact"
                      rows="3"
                      hide-details="auto"
                      :rules="[jsonObjectRule]"
                    />
                  </div>
                </div>
              </section>

              <section v-if="isHdfsTarget" class="form-section form-section--wide">
                <header class="section-head">
                  <h3>HDFS / Hive 设置</h3>
                  <div class="section-actions">
                    <v-btn
                      size="x-small"
                      variant="text"
                      prepend-icon="mdi-lan-connect"
                      :loading="healthCheckRunning"
                      @click="runHealthCheck"
                    >
                      校验目标端
                    </v-btn>
                  </div>
                </header>
                <div
                  v-if="healthCheckItems.length"
                  class="target-check"
                  :class="{
                    'target-check--ok': healthCheckDone && healthCheckSuccess,
                    'target-check--fail': healthCheckDone && !healthCheckSuccess,
                  }"
                >
                  <div class="target-check__head">
                    <v-icon size="18" :color="healthCheckHeadColor">
                      {{ healthCheckHeadIcon }}
                    </v-icon>
                    <span>{{ healthCheckTitle }}</span>
                  </div>
                  <ul class="target-check__list">
                    <li
                      v-for="item in healthCheckItems"
                      :key="item.key"
                      class="target-check__item"
                      :class="`is-${item.status}`"
                    >
                      <v-icon size="16" :color="checkIconColor(item.status)">
                        {{ checkIcon(item.status) }}
                      </v-icon>
                      <div class="target-check__body">
                        <span class="target-check__label">{{ item.label }}</span>
                        <span v-if="item.message" class="target-check__msg">
                          {{ item.message }}
                        </span>
                        <span v-if="item.hint" class="target-check__hint">{{ item.hint }}</span>
                      </div>
                    </li>
                  </ul>
                </div>
                <div class="field-grid">
                  <div class="field span-2">
                    <label class="field-label" for="target-hive-driver">驱动类名（可选）</label>
                    <v-text-field
                      id="target-hive-driver"
                      v-model="connectForm.driverClassName"
                      placeholder="org.apache.hive.jdbc.HiveDriver"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
                  </div>
                  <div class="field span-2">
                    <label class="field-label" for="target-hive-driver-path"
                      >驱动 jar 路径（可选）</label
                    >
                    <v-text-field
                      id="target-hive-driver-path"
                      v-model="connectForm.driverPath"
                      placeholder="仅当驱动无法放入 drivers 目录时填写，例如会与主程序类冲突的 HDP standalone jar。"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
                  </div>
                  <div class="field span-2">
                    <label class="field-label" for="target-default-fs">
                      defaultFS <span class="field-required" aria-hidden="true">*</span>
                    </label>
                    <v-text-field
                      id="target-default-fs"
                      v-model="connectForm.defaultFS"
                      placeholder="hdfs://nameservice1"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                      :rules="[requiredRule]"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-hdfs-prefix">HDFS 目录前缀</label>
                    <v-text-field
                      id="target-hdfs-prefix"
                      v-model="connectForm.hdfsPrefix"
                      placeholder="/ods"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-hdfs-site">hdfs-site.xml 路径</label>
                    <v-text-field
                      id="target-hdfs-site"
                      v-model="connectForm.hdfsSitePath"
                      placeholder="/etc/hadoop/conf/hdfs-site.xml"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-storage">存储格式</label>
                    <v-select
                      id="target-storage"
                      v-model="connectForm.storageFormat"
                      :items="storageFormats"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field">
                    <label class="field-label" for="target-compress">压缩格式</label>
                    <v-select
                      id="target-compress"
                      v-model="connectForm.compressFormat"
                      :items="compressFormats"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                    />
                  </div>
                  <div class="field span-2">
                    <label class="field-label" for="target-hadoop-config"
                      >hadoopConfig (JSON)</label
                    >
                    <v-textarea
                      id="target-hadoop-config"
                      v-model="connectForm.hadoopConfig"
                      placeholder="{}"
                      variant="outlined"
                      density="compact"
                      rows="3"
                      hide-details="auto"
                      :rules="[jsonObjectRule]"
                    />
                  </div>
                  <div class="field">
                    <v-switch
                      v-model="connectForm.enableKerberos"
                      inset
                      density="compact"
                      label="启用 Kerberos"
                      color="primary"
                      hide-details
                    />
                  </div>
                </div>
              </section>

              <section class="form-section form-section--wide">
                <header class="section-head">
                  <h3>其他设置</h3>
                </header>
                <div class="field-grid">
                  <div class="field span-2">
                    <label class="field-label" for="target-remark">备注</label>
                    <v-text-field
                      id="target-remark"
                      v-model="form.remark"
                      placeholder="可选"
                      variant="outlined"
                      density="compact"
                      hide-details
                      autocomplete="off"
                    />
                  </div>
                </div>
                <div class="switch-row">
                  <v-switch
                    v-model="form.enabled"
                    inset
                    density="compact"
                    label="启用"
                    color="primary"
                    hide-details
                  />
                  <v-switch
                    v-model="form.isDefault"
                    inset
                    density="compact"
                    label="设为默认目标端"
                    color="primary"
                    hide-details
                  />
                </div>
              </section>
            </div>
          </v-form>
        </v-card-text>
        <v-card-actions class="ds-dialog-actions">
          <v-spacer />
          <v-btn variant="text" @click="dialogVisible = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="save">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-navigation-drawer>

    <ConfirmDialog
      v-model="confirmState.open"
      :title="confirmState.title"
      :message="confirmState.message"
      confirm-text="删除"
      @confirm="runConfirmed"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import AppDataTable from '@/components/common/AppDataTable.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import PageHeader from '@/components/PageHeader.vue';
  import { notify } from '@/stores/notifier';
  import targetService, { type DialectInfo } from '@/service/target-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils/constants';
  import settingsService from '@/service/settings-service';
  import type { EtlTarget, SysItem } from '@/types/database';
  import type { DataTableHeader } from 'vuetify';
  import { useAuthStore } from '@/stores/auth';

  const route = useRoute();
  const router = useRouter();
  const authStore = useAuthStore();

  const targets = ref<EtlTarget[]>([]);
  const enabledOnly = ref(true);
  const loading = ref(true);
  const dialogVisible = ref(false);
  const targetForm = ref<any>(null);
  const testingTarget = ref(false);
  const showConnectJson = ref(false);
  const showPassword = ref(false);
  const searchValue = ref('');
  const confirmState = ref<{
    open: boolean;
    title: string;
    message: string;
    action: (() => unknown) | null;
  }>({ open: false, title: '', message: '', action: null });

  const runConfirmed = () => {
    const action = confirmState.value.action;
    confirmState.value.open = false;
    action?.();
  };
  // HDFS / RDBMS are families; the concrete RDBMS dialect is inferred from the JDBC URL. Doris is a
  // deliberate exception: its URL is indistinguishable from MySQL, and its writer is Stream Load, so
  // it is an explicit adapter type with its own template key.
  const targetTypes = ['HDFS', 'RDBMS', 'DORIS'];
  const writerTemplateOptions = ref<{ label: string; value: string }[]>([]);

  const headers = computed<DataTableHeader[]>(() => {
    const base: DataTableHeader[] = [
      { title: 'ID', key: 'id', width: 72 },
      { title: '目标端', key: 'name', minWidth: '180px' },
      { title: '类型', key: 'targetType', width: 120 },
      { title: '模板键', key: 'writerTemplateKey', width: 140 },
      { title: '启用', key: 'enabled', width: 90 },
      { title: '默认', key: 'isDefault', width: 90 },
    ];
    if (authStore.canWrite) {
      base.push({ title: '操作', key: 'action', sortable: false, width: 280 });
    }
    return base;
  });

  const blankForm = (): EtlTarget => ({
    code: '',
    name: '',
    targetType: 'HDFS',
    connectConfig: '',
    writerTemplateKey: '',
    enabled: true,
    isDefault: false,
    remark: '',
  });

  const form = ref<EtlTarget>(blankForm());
  const connectForm = ref({
    url: '',
    username: '',
    password: '',
    driverClassName: '',
    driverPath: '',
    defaultFS: '',
    hdfsPrefix: '/ods',
    hdfsSitePath: '',
    enableKerberos: false,
    hadoopConfig: '{}',
    storageFormat: 'orc',
    compressFormat: 'snappy',
    dorisLoadUrl: '',
    dorisBatchSize: 2048,
    dorisFlushInterval: 3000,
    dorisFlushQueueLength: 1,
    dorisLoadProps: '{}',
  });

  const storageFormats = HDFS_STORAGE_FORMATS;
  const compressFormats = HDFS_COMPRESS_FORMATS;

  // The writer key is authoritative, but a legacy row may still carry only target_type. This mirrors
  // TargetTypeResolver: WH/WR/WD first, then the display type.
  const targetKind = computed<'hdfs' | 'rdbms' | 'doris' | null>(() => {
    const key = (form.value.writerTemplateKey || '').trim().toUpperCase();
    if (key === 'WH') return 'hdfs';
    if (key === 'WR') return 'rdbms';
    if (key === 'WD') return 'doris';
    const type = (form.value.targetType || '').trim().toUpperCase();
    if (['HDFS', 'HIVE'].includes(type)) return 'hdfs';
    if (type === 'DORIS') return 'doris';
    if (['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'].includes(type)) return 'rdbms';
    return null;
  });

  const isHdfsTarget = computed(() => targetKind.value === 'hdfs');
  const isRdbmsTarget = computed(() => targetKind.value === 'rdbms');
  const isDorisTarget = computed(() => targetKind.value === 'doris');

  // wD is a Doris-only contract: showing it for an RDBMS/HDFS target lets the operator fill
  // Stream Load fields that a later save would have to discard. The type is the way in; selecting
  // DORIS sets wD automatically.
  const writerTemplateChoices = computed(() =>
    writerTemplateOptions.value.filter(
      option =>
        option.value !== 'wD' ||
        isDorisTarget.value ||
        (form.value.writerTemplateKey || '').trim().toUpperCase() === 'WD'
    )
  );

  // HDFS 校验按清单逐项执行：先列出全部项目（pending），每项完成后立刻回填状态，避免用户
  // 面对一个转圈按钮干等，也避免“结果在可视化区域之外根本看不到”。
  type HealthCheckStatus = 'pending' | 'running' | 'ok' | 'fail' | 'skip';
  interface HealthCheckRow {
    key: string;
    label: string;
    status: HealthCheckStatus;
    message: string;
    hint: string | null;
  }
  const healthCheckItems = ref<HealthCheckRow[]>([]);
  const healthCheckRunning = ref(false);

  const healthCheckDone = computed(
    () =>
      healthCheckItems.value.length > 0 &&
      !healthCheckRunning.value &&
      healthCheckItems.value.every(item => ['ok', 'fail', 'skip'].includes(item.status))
  );
  const healthCheckSuccess = computed(
    () =>
      healthCheckItems.value.length > 0 &&
      healthCheckItems.value.every(item => item.status === 'ok' || item.status === 'skip')
  );
  const healthCheckTitle = computed(() => {
    if (!healthCheckItems.value.length) return '';
    if (healthCheckRunning.value) return '正在校验目标端…';
    if (!healthCheckDone.value) return '待校验';
    return healthCheckSuccess.value ? '目标端校验通过' : '目标端校验未通过';
  });
  const healthCheckHeadIcon = computed(() => {
    if (healthCheckRunning.value) return 'mdi-progress-clock';
    if (!healthCheckDone.value) return 'mdi-clipboard-list-outline';
    return healthCheckSuccess.value ? 'mdi-check-circle' : 'mdi-alert-circle';
  });
  const healthCheckHeadColor = computed(() => {
    if (!healthCheckDone.value) return 'medium-emphasis';
    return healthCheckSuccess.value ? 'success' : 'error';
  });

  const connectionPlaceholder = computed(() =>
    isHdfsTarget.value
      ? 'jdbc:hive2://host:10000/default'
      : isDorisTarget.value
        ? 'jdbc:mysql://fe:9030/db'
        : 'jdbc:postgresql://host:5432/db'
  );

  const requiredRule = (value: any) => !!(value && String(value).trim()) || '必填项';
  const loadUrlRule = (value: string) => {
    const endpoints = parseLoadUrl(value);
    if (!endpoints.length) return '至少配置一个 loadUrl';
    const invalid = endpoints.find(endpoint => {
      if (endpoint.startsWith('http://') || endpoint.startsWith('https://')) return true;
      const colon = endpoint.lastIndexOf(':');
      if (colon <= 0) return true;
      const port = endpoint.slice(colon + 1);
      return !/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535;
    });
    return invalid ? `端点格式不正确: ${invalid}（应为 host:port）` : true;
  };
  const jsonObjectRule = (value: string) => {
    const text = (value || '').trim();
    if (!text) return true;
    try {
      const parsed = JSON.parse(text);
      return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) || '必须是 JSON 对象';
    } catch {
      return 'JSON 格式不正确';
    }
  };

  const parseLoadUrl = (value: string): string[] =>
    (value || '')
      .split(/[,;\n]/)
      .map(item => item.trim())
      .filter(Boolean);

  const parseHadoopConfig = (value: string): Record<string, unknown> => {
    const text = (value || '').trim();
    if (!text) return {};
    try {
      const parsed = JSON.parse(text);
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch {
      return {};
    }
  };

  // The name column renders code + name, so the default search (which inspects displayed values only)
  // cannot match a code. Search the raw endpoint fields explicitly.
  const targetFilter = (_value: unknown, query: string, item?: { raw?: EtlTarget }) => {
    const text = (query || '').trim().toLowerCase();
    if (!text) return true;
    const target = item?.raw;
    return [target?.code, target?.name, target?.targetType, target?.writerTemplateKey]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(text));
  };

  const getTargetTypeColor = (targetType?: string) => {
    const colorMap = {
      HDFS: 'primary',
      RDBMS: 'success',
      DORIS: 'info',
    };
    return colorMap[targetType || ''] || 'secondary';
  };

  const syncConnectConfig = () => {
    const payload: Record<string, unknown> = {
      url: connectForm.value.url || '',
      username: connectForm.value.username || '',
      password: connectForm.value.password || '',
    };
    if (isHdfsTarget.value) {
      payload.driverClassName = connectForm.value.driverClassName || '';
      payload.driverPath = connectForm.value.driverPath || '';
      payload.defaultFS = connectForm.value.defaultFS || '';
      payload.hdfsPrefix = connectForm.value.hdfsPrefix || '/ods';
      payload.hdfsSitePath = connectForm.value.hdfsSitePath || '';
      payload.enableKerberos = !!connectForm.value.enableKerberos;
      payload.hadoopConfig = parseHadoopConfig(connectForm.value.hadoopConfig);
      payload.storageFormat = connectForm.value.storageFormat || 'orc';
      payload.compressFormat = connectForm.value.compressFormat || 'snappy';
    }
    if (isDorisTarget.value) {
      payload.loadUrl = parseLoadUrl(connectForm.value.dorisLoadUrl);
      payload.batchSize = Number(connectForm.value.dorisBatchSize) || 2048;
      payload.flushInterval = Number(connectForm.value.dorisFlushInterval) || 3000;
      payload.flushQueueLength = Number(connectForm.value.dorisFlushQueueLength) || 1;
      payload.loadProps = parseHadoopConfig(connectForm.value.dorisLoadProps);
    }
    form.value.connectConfig = JSON.stringify(payload);
  };

  watch(connectForm, syncConnectConfig, { deep: true });
  // A green tick that no longer describes the visible settings is worse than none: any edit puts
  // the checklist back to "not checked" while keeping the items visible.
  watch(
    connectForm,
    () => {
      if (healthCheckRunning.value) return;
      healthCheckItems.value.forEach(item => {
        item.status = 'pending';
        item.message = '';
        item.hint = null;
      });
    },
    { deep: true }
  );
  watch(
    () => form.value.targetType,
    t => {
      // Follow the family while the key is still one of the derived defaults; a custom writer
      // template key is left alone. Doris is a pair with wD and has no custom-key escape hatch.
      const current = (form.value.writerTemplateKey || '').trim().toUpperCase();
      if (t === 'DORIS') {
        form.value.writerTemplateKey = 'wD';
      } else if (!current || ['WH', 'WR', 'WD'].includes(current)) {
        form.value.writerTemplateKey = t === 'HDFS' ? 'wH' : 'wR';
      }
      syncConnectConfig();
      loadHealthCheckPlan();
    }
  );
  watch(
    () => form.value.writerTemplateKey,
    key => {
      // Keep the pair coherent in both directions. wD is reserved for Doris and the backend rejects
      // a mismatch, so selecting it must move the target type too instead of leaving a form whose
      // visible Doris fields a later save would silently drop.
      const normalized = (key || '').trim().toUpperCase();
      const type = (form.value.targetType || '').trim().toUpperCase();
      if (normalized === 'WD' && type !== 'DORIS') {
        form.value.targetType = 'DORIS';
      } else if (normalized === 'WH' && !['HDFS', 'HIVE'].includes(type)) {
        form.value.targetType = 'HDFS';
      } else if (
        normalized === 'WR' &&
        !['RDBMS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'].includes(type)
      ) {
        form.value.targetType = 'RDBMS';
      }
      syncConnectConfig();
      loadHealthCheckPlan();
    }
  );

  // The concrete RDBMS dialect is derived from the JDBC URL, not chosen; surface the resolved
  // dialect read-only so the operator sees what will actually be used before saving.
  const detectedDialect = ref<DialectInfo | null>(null);
  let detectTimer: ReturnType<typeof setTimeout> | undefined;

  const detectDialect = async () => {
    const url = (connectForm.value.url || '').trim();
    if (!(isRdbmsTarget.value || isDorisTarget.value) || !url.startsWith('jdbc:')) {
      detectedDialect.value = null;
      return;
    }
    try {
      // Doris shares the MySQL URL; the explicit target type is what keeps the hint honest.
      detectedDialect.value = await targetService.detectDialect(url, form.value.targetType);
    } catch {
      detectedDialect.value = null;
    }
  };

  const scheduleDetect = () => {
    clearTimeout(detectTimer);
    detectTimer = setTimeout(detectDialect, 300);
  };

  watch(() => connectForm.value.url, scheduleDetect);
  watch(isRdbmsTarget, scheduleDetect);
  watch(isDorisTarget, scheduleDetect);

  const parseConnectConfig = (target: EtlTarget) => {
    connectForm.value = {
      url: '',
      username: '',
      password: '',
      driverClassName: '',
      driverPath: '',
      defaultFS: '',
      hdfsPrefix: '/ods',
      hdfsSitePath: '',
      enableKerberos: false,
      hadoopConfig: '{}',
      storageFormat: 'orc',
      compressFormat: 'snappy',
      dorisLoadUrl: '',
      dorisBatchSize: 2048,
      dorisFlushInterval: 3000,
      dorisFlushQueueLength: 1,
      dorisLoadProps: '{}',
    };
    if (!target.connectConfig) {
      syncConnectConfig();
      return;
    }
    try {
      const obj = JSON.parse(target.connectConfig);
      connectForm.value.url = obj.url || '';
      connectForm.value.username = obj.username || '';
      connectForm.value.password = obj.password || '';
      connectForm.value.driverClassName = obj.driverClassName || '';
      connectForm.value.driverPath = obj.driverPath || '';
      connectForm.value.defaultFS = obj.defaultFS || '';
      connectForm.value.hdfsPrefix = obj.hdfsPrefix || '/ods';
      connectForm.value.hdfsSitePath = obj.hdfsSitePath || '';
      connectForm.value.enableKerberos = !!obj.enableKerberos;
      connectForm.value.hadoopConfig = obj.hadoopConfig
        ? JSON.stringify(obj.hadoopConfig, null, 2)
        : '{}';
      connectForm.value.storageFormat = obj.storageFormat || 'orc';
      connectForm.value.compressFormat = obj.compressFormat || 'snappy';
      const loadUrl = obj.loadUrl;
      connectForm.value.dorisLoadUrl = Array.isArray(loadUrl)
        ? loadUrl.join('\n')
        : typeof loadUrl === 'string'
          ? loadUrl
          : '';
      connectForm.value.dorisBatchSize = Number(obj.batchSize) || 2048;
      connectForm.value.dorisFlushInterval = Number(obj.flushInterval) || 3000;
      connectForm.value.dorisFlushQueueLength = Number(obj.flushQueueLength) || 1;
      connectForm.value.dorisLoadProps = obj.loadProps
        ? JSON.stringify(obj.loadProps, null, 2)
        : '{}';
    } catch {
      notify('connectConfig 不是有效 JSON，已重置连接表单', 'warning');
    }
    syncConnectConfig();
  };

  const loadTargets = async () => {
    loading.value = true;
    try {
      targets.value = await targetService.list(enabledOnly.value);
    } catch (e: any) {
      notify(`加载目标端失败: ${e.message || e}`, 'error');
    } finally {
      loading.value = false;
    }
  };

  const openCreate = () => {
    form.value = blankForm();
    form.value.writerTemplateKey = form.value.targetType === 'HDFS' ? 'wH' : 'wR';
    parseConnectConfig(form.value);
    showConnectJson.value = false;
    showPassword.value = false;
    dialogVisible.value = true;
    detectDialect();
    loadHealthCheckPlan();
  };

  const openEdit = (item: EtlTarget) => {
    form.value = { ...item };
    parseConnectConfig(form.value);
    showConnectJson.value = false;
    showPassword.value = false;
    dialogVisible.value = true;
    detectDialect();
    loadHealthCheckPlan();
  };

  const save = async () => {
    const { valid } = await targetForm.value.validate();
    if (!valid) return;
    // Keep the adapter type and writer template key a pair; the backend rejects a mismatch.
    // wD is the explicit user intent, so promote the type rather than discarding the key.
    if (
      form.value.targetType === 'DORIS' ||
      (form.value.writerTemplateKey || '').trim().toUpperCase() === 'WD'
    ) {
      form.value.targetType = 'DORIS';
      form.value.writerTemplateKey = 'wD';
    }
    syncConnectConfig();
    try {
      if (form.value.id) {
        await targetService.update(form.value.id, form.value);
      } else {
        await targetService.create(form.value);
      }
      notify('保存成功', 'success');
      dialogVisible.value = false;
      await loadTargets();
    } catch (e: any) {
      notify(`保存失败: ${e.message || e}`, 'error');
    }
  };

  const remove = async (item: EtlTarget) => {
    if (!item.id) return;
    const id = item.id;
    confirmState.value = {
      open: true,
      title: '删除目标端',
      message: `将删除目标端「${item.name}」，引用它的采集表需要重新指定目标端。`,
      action: async () => {
        try {
          await targetService.remove(id);
          notify('删除成功', 'success');
          await loadTargets();
        } catch (e: any) {
          notify(`删除失败: ${e.message || e}`, 'error');
        }
      },
    };
  };

  const testConnect = async (item: EtlTarget) => {
    if (!item.id) return;
    try {
      await targetService.testConnect(item.id);
      notify('连接测试成功', 'success');
    } catch (e: any) {
      notify(`连接测试失败: ${e.message || e}`, 'error');
    }
  };

  const testConnectPayload = async () => {
    syncConnectConfig();
    testingTarget.value = true;
    try {
      await targetService.testConnectPayload(form.value);
      notify('连接测试成功', 'success');
    } catch (e: any) {
      notify(`连接测试失败: ${e.message || e}`, 'error');
    } finally {
      testingTarget.value = false;
    }
  };

  const loadHealthCheckPlan = async () => {
    if (!isHdfsTarget.value) {
      healthCheckItems.value = [];
      return;
    }
    if (healthCheckRunning.value) return;
    try {
      syncConnectConfig();
      const plan = await targetService.healthCheckPlan(form.value);
      healthCheckItems.value = plan.items.map(item => ({
        key: item.key,
        label: item.label,
        status: 'pending' as HealthCheckStatus,
        message: '',
        hint: null,
      }));
    } catch {
      // The checklist is an aid, not a precondition for saving: a failed plan request leaves the
      // section usable and the next click retries it.
      healthCheckItems.value = [];
    }
  };

  // HDFS 目标端的“测试连接”只证明 HiveServer2 可达，证明不了能不能写 HDFS。校验目标端按清单
  // 逐项执行真实的检查（含一次 HDFS 写入探针），每完成一项立即打勾。
  const runHealthCheck = async () => {
    if (healthCheckRunning.value) return;
    syncConnectConfig();
    if (!healthCheckItems.value.length) {
      await loadHealthCheckPlan();
    }
    if (!healthCheckItems.value.length) {
      notify('无法加载校验清单，请重试', 'error');
      return;
    }
    // Snapshot the payload: edits made while the checklist runs must not silently change what the
    // remaining items are checked against.
    const payload: EtlTarget = JSON.parse(JSON.stringify(form.value));
    healthCheckItems.value.forEach(item => {
      item.status = 'pending';
      item.message = '';
      item.hint = null;
    });
    healthCheckRunning.value = true;
    try {
      for (const item of healthCheckItems.value) {
        item.status = 'running';
        try {
          const result = await targetService.healthCheckItem(payload, item.key);
          item.status = result.status;
          item.message = result.message;
          item.hint = result.hint;
        } catch (e: any) {
          item.status = 'fail';
          item.message = String(e.message || e);
          item.hint = '检查请求失败，请重试';
        }
      }
      const failed = healthCheckItems.value.find(item => item.status === 'fail');
      if (failed) {
        notify(`目标端校验未通过: ${failed.label}`, 'error');
      } else {
        notify('目标端校验通过', 'success');
      }
    } finally {
      healthCheckRunning.value = false;
    }
  };

  const checkIcon = (status: string) => {
    switch (status) {
      case 'ok':
        return 'mdi-check-circle';
      case 'fail':
        return 'mdi-alert-circle';
      case 'running':
        return 'mdi-loading';
      case 'pending':
        return 'mdi-circle-outline';
      default:
        return 'mdi-minus-circle-outline';
    }
  };

  const checkIconColor = (status: string) => {
    switch (status) {
      case 'ok':
        return 'success';
      case 'fail':
        return 'error';
      case 'skip':
        return 'warning';
      default:
        return 'medium-emphasis';
    }
  };

  const targetRules = {
    required: (value: any) => !!(value && String(value).trim()) || '必填项',
  };

  const loadWriterTemplateOptions = async () => {
    try {
      const templates = await settingsService.getJobConfig();
      // axios 返回的是普通对象，而非真正的 Map；与 sys-settings 页保持一致地归一化。
      const items: SysItem[] =
        templates instanceof Map
          ? Array.from(templates.values())
          : Object.values((templates as unknown as Record<string, SysItem>) || {});
      writerTemplateOptions.value = items
        .filter(i => i.itemKey && i.itemKey.startsWith('w'))
        .map(i => ({
          label: `${i.itemKey} ${i.remark ? `- ${i.remark}` : ''}`,
          value: i.itemKey,
        }));
    } catch (e: any) {
      notify(`加载 Writer 模板键失败: ${e.message || e}`, 'error');
    }
  };

  const openCreateFromQuery = () => {
    const action = Array.isArray(route.query.action) ? route.query.action[0] : route.query.action;
    if (action !== 'create' || !authStore.canWrite) return;

    openCreate();

    const query = { ...route.query };
    delete query.action;
    router.replace({ path: route.path, query });
  };

  onMounted(async () => {
    await Promise.all([loadTargets(), loadWriterTemplateOptions()]);
    openCreateFromQuery();
  });

  watch(
    () => route.query.action,
    () => {
      openCreateFromQuery();
    }
  );
</script>

<style scoped>
  .target-page {
    min-width: 0;
  }

  .dialog-grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }

  .form-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
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

  .section-head .v-chip,
  .section-head .section-toggle {
    margin-left: auto;
  }

  .section-head .section-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
  }

  .section-head .section-actions .section-toggle {
    margin-left: 0;
  }

  .section-index {
    color: var(--ds-text-3);
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 11px;
  }

  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 18px;
  }

  .field {
    min-width: 0;
  }

  .field.span-2 {
    grid-column: 1 / -1;
  }

  .dialect-hint {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .dialect-hint .field-label {
    margin-bottom: 0;
  }

  .dialect-alert {
    margin: 0;
    width: 100%;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    color: rgb(var(--v-theme-on-surface));
    font-size: 12.5px;
    font-weight: 500;
  }

  .field-required {
    color: rgb(var(--v-theme-error));
  }

  .switch-row {
    display: flex;
    align-items: center;
    gap: 24px;
    flex-wrap: wrap;
  }

  .switch-row :deep(.v-switch) {
    margin-top: 0;
    flex: none;
  }

  .config-preview :deep(textarea) {
    font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .target-check {
    margin-top: 4px;
    padding: 12px 14px;
    border: 1px solid var(--ds-border-default);
    border-radius: var(--ds-radius-sm);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .target-check--ok {
    border-color: rgba(var(--v-theme-success), 0.4);
  }

  .target-check--fail {
    border-color: rgba(var(--v-theme-error), 0.4);
  }

  .target-check__head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 600;
  }

  .target-check__list {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .target-check__item {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  .target-check__body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .target-check__label {
    color: rgb(var(--v-theme-on-surface));
    font-size: 12.5px;
    font-weight: 500;
  }

  .target-check__msg {
    color: var(--ds-text-2);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  .target-check__hint {
    color: rgb(var(--v-theme-error));
    font-size: 11.5px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  .target-check__item.is-fail .target-check__label {
    color: rgb(var(--v-theme-error));
  }

  .target-check__item.is-pending .target-check__label {
    color: var(--ds-text-3);
  }

  .target-check__item.is-running .target-check__label {
    color: rgb(var(--v-theme-primary));
  }

  .target-check__item.is-running .v-icon {
    animation: target-check-spin 1s linear infinite;
  }

  @keyframes target-check-spin {
    to {
      transform: rotate(360deg);
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

  /* The target editor is a focused form surface: supporting status belongs to the list or result views. */
  .target-editor-card {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 100%;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .target-editor-card .ds-dialog-head {
    flex: none;
    padding: 16px 22px 14px;
    background: var(--ds-surface-raised);
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .target-editor-card .ds-dialog-body {
    flex: 1 1 auto;
    min-height: 0;
    padding: 22px 24px 26px !important;
    overflow: auto;
  }

  .target-editor-card .dialog-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px 20px;
  }

  .target-editor-drawer :deep(.v-navigation-drawer__content) {
    overflow: hidden;
  }

  .target-editor-drawer .target-editor-card {
    width: 100%;
  }

  .target-editor-drawer .dialog-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .target-editor-drawer .form-section--wide {
    grid-column: auto;
  }

  .target-editor-card .form-section--wide {
    grid-column: 1 / -1;
  }

  .target-editor-card .section-head {
    padding-bottom: 10px;
  }

  .target-editor-card .ds-dialog-actions {
    flex: none;
    min-height: 58px;
    padding: 10px 22px;
    background: var(--ds-surface-raised);
  }

  @media (max-width: 760px) {
    .target-editor-card {
      height: calc(100vh - 16px);
      max-height: calc(100vh - 16px);
    }

    .target-editor-card .ds-dialog-head {
      padding: 14px;
    }

    .target-editor-card .ds-dialog-body {
      padding: 14px !important;
    }

    .target-editor-card .dialog-grid {
      display: block;
    }

    .target-editor-card .form-section {
      margin-bottom: 18px;
    }

    .target-editor-card .form-section:last-child {
      margin-bottom: 0;
    }

    .target-editor-card .ds-dialog-actions {
      padding: 9px 14px;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "目标端管理",
    "icon": "mdi-database-export",
    "requiresAuth": true,
    "navGroup": "collectConfig",
    "navOrder": 20
  }
}
</route>
