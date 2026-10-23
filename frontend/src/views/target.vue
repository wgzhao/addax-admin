<template>
  <div
    class="target-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible }"
  >
    <PageHeader title="目标端管理" subtitle="统一维护目标端连接、模板键与默认落库策略。">
      <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="openCreate">
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

    <v-card flat class="ds-card table-card ds-table-wrap ds-table-scroll">
      <v-card-text class="ds-card__content">
        <v-skeleton-loader v-if="loading" type="table-row@6" class="ds-skeleton" />
        <template v-else-if="targets.length === 0">
          <EmptyState
            title="暂无目标端"
            description="当前还没有配置任何目标端。点击下方按钮新增目标端后，即可配置连接、模板键和默认落库策略。"
            :primary="{ label: '新增目标端', icon: 'mdi-plus' }"
            @primary="openCreate"
          />
        </template>
        <template v-else>
          <v-data-table
            :headers="headers"
            :items="targets"
            :search="searchValue"
            item-value="id"
            density="comfortable"
            items-per-page="20"
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
              <div class="ds-action-inline">
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
                  @click="testConnect(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-connection</v-icon>测试连接
                </v-btn>
                <v-btn size="small" variant="text" color="error" class="px-2" @click="remove(item)">
                  <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
                </v-btn>
              </div>
            </template>
          </v-data-table>
        </template>
      </v-card-text>
    </v-card>

    <v-dialog
      v-model="dialogVisible"
      class="ds-dialog-overlay"
      scrim="rgba(2, 6, 23, 0.62)"
      max-width="880"
      persistent
      scrollable
    >
      <v-card>
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-server-network</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">{{ form.id ? '编辑目标端' : '新增目标端' }}</h2>
            <p class="ds-dialog-subtitle">维护写入模板、连接参数与默认落库策略。</p>
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
                  <span class="section-index">01</span>
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
                      :items="writerTemplateOptions"
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
                  <span class="section-index">02</span>
                  <h3>连接信息</h3>
                  <v-btn
                    size="x-small"
                    variant="text"
                    class="section-toggle"
                    @click="showConnectJson = !showConnectJson"
                  >
                    {{ showConnectJson ? '隐藏连接 JSON' : '查看连接 JSON' }}
                  </v-btn>
                </header>
                <div class="field-grid">
                  <div class="field span-2">
                    <label class="field-label" for="target-url">JDBC URL</label>
                    <v-text-field
                      id="target-url"
                      v-model="connectForm.url"
                      placeholder="jdbc:postgresql://host:5432/db"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="url"
                    />
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
                  <div v-if="form.targetType === 'HDFS'" class="field">
                    <label class="field-label" for="target-driver">驱动类名</label>
                    <v-text-field
                      id="target-driver"
                      v-model="connectForm.driverClassName"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
                  </div>
                  <div v-if="form.targetType === 'HDFS'" class="field">
                    <label class="field-label" for="target-driver-path">驱动路径</label>
                    <v-text-field
                      id="target-driver-path"
                      v-model="connectForm.driverPath"
                      variant="outlined"
                      density="compact"
                      hide-details="auto"
                      autocomplete="off"
                    />
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

              <section class="form-section">
                <header class="section-head">
                  <span class="section-index">03</span>
                  <h3>状态与备注</h3>
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
          <v-btn
            variant="tonal"
            prepend-icon="mdi-lan-connect"
            :loading="testingTarget"
            @click="testConnectPayload"
          >
            测试连接
          </v-btn>
          <v-spacer />
          <v-btn variant="text" @click="dialogVisible = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="save">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

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
  import { onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import EmptyState from '@/components/EmptyState.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import PageHeader from '@/components/PageHeader.vue';
  import { notify } from '@/stores/notifier';
  import targetService from '@/service/target-service';
  import dictService from '@/service/dict-service';
  import type { EtlTarget } from '@/types/database';

  const route = useRoute();
  const router = useRouter();

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
  // Phase 1 only offers dialects that are implemented end to end (writer plugin + dialect-aware DDL).
  const targetTypes = ['HDFS', 'MYSQL', 'POSTGRESQL', 'SQLSERVER'];
  const writerTemplateOptions = ref<{ label: string; value: string }[]>([]);

  const headers = [
    { title: 'ID', key: 'id', width: 72 },
    { title: '目标端', key: 'name', minWidth: '180px' },
    { title: '类型', key: 'targetType', width: 120 },
    { title: '模板键', key: 'writerTemplateKey', width: 140 },
    { title: '启用', key: 'enabled', width: 90 },
    { title: '默认', key: 'isDefault', width: 90 },
    { title: '操作', key: 'action', sortable: false, width: 280 },
  ];

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
  });

  const getTargetTypeColor = (targetType?: string) => {
    const colorMap = {
      HDFS: 'primary',
      MYSQL: 'success',
      POSTGRESQL: 'info',
      SQLSERVER: 'secondary',
    };
    return colorMap[targetType || ''] || 'secondary';
  };

  const syncConnectConfig = () => {
    const payload: Record<string, string> = {
      url: connectForm.value.url || '',
      username: connectForm.value.username || '',
      password: connectForm.value.password || '',
    };
    if (form.value.targetType === 'HDFS') {
      payload.driverClassName = connectForm.value.driverClassName || '';
      payload.driverPath = connectForm.value.driverPath || '';
    }
    form.value.connectConfig = JSON.stringify(payload);
  };

  watch(connectForm, syncConnectConfig, { deep: true });
  watch(
    () => form.value.targetType,
    t => {
      if (!form.value.writerTemplateKey) {
        form.value.writerTemplateKey = t === 'HDFS' ? 'wH' : 'wR';
      }
      syncConnectConfig();
    }
  );

  const parseConnectConfig = (target: EtlTarget) => {
    connectForm.value = {
      url: '',
      username: '',
      password: '',
      driverClassName: '',
      driverPath: '',
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
  };

  const openEdit = (item: EtlTarget) => {
    form.value = { ...item };
    parseConnectConfig(form.value);
    showConnectJson.value = false;
    showPassword.value = false;
    dialogVisible.value = true;
  };

  const save = async () => {
    const { valid } = await targetForm.value.validate();
    if (!valid) return;
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

  const targetRules = {
    required: (value: any) => !!(value && String(value).trim()) || '必填项',
  };

  const loadWriterTemplateOptions = async () => {
    try {
      const items = await dictService.listSysItems(5001);
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
    if (action !== 'create') return;

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

  @media (max-width: 640px) {
    .field-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .field.span-2 {
      grid-column: auto;
    }
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "目标端管理",
    "icon": "mdi-database-export",
    "requiresAuth": true,
    "navGroup": "target",
    "navOrder": 10
  }
}
</route>
