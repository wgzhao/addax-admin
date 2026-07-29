<template>
  <div class="sys-settings-page page-shell">
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header">
          <div>
            <div class="page-title">系统配置</div>
            <div class="page-subtitle">
              系统首次安装后，请完成必要配置项，确保采集与调度正常运行。
            </div>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-form ref="form" v-model="valid" @submit.prevent="saveSettings">
      <!-- 上段：三张配置卡片并排 -->
      <v-row class="config-row">
        <v-col cols="12" md="4" class="col-stack">
          <v-card flat class="ds-card section-card h-100">
            <v-card-title class="section-title">
              <v-icon class="section-title-icon" color="primary">mdi-cog</v-icon>
              基础系统配置
            </v-card-title>
            <v-divider />
            <v-card-text class="section-body">
              <div class="field-block">
                <div class="field-label">Addax 程序目录</div>
                <v-text-field
                  v-model="settings['ADDAX']"
                  placeholder="/opt/app/addax"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">HDFS 目录前缀</div>
                <v-text-field
                  v-model="settings['HDFS_PREFIX']"
                  placeholder="/ods"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">切日时间</div>
                <v-text-field
                  v-model="settings['SWITCH_TIME']"
                  placeholder="16:30"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required, rules.timeFormat]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">默认 HDFS 存储格式</div>
                <v-select
                  v-model="settings['HDFS_STORAGE_FORMAT']"
                  :items="storageFormats"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">默认压缩格式</div>
                <v-select
                  v-model="settings['HDFS_COMPRESS_FORMAT']"
                  :items="compressFormats"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" md="4" class="col-stack">
          <v-card flat class="ds-card section-card h-100">
            <v-card-title class="section-title">
              <v-icon class="section-title-icon" color="primary">mdi-server</v-icon>
              HiveServer2 配置
            </v-card-title>
            <v-divider />
            <v-card-text class="section-body">
              <div class="field-block">
                <div class="field-label">JDBC 连接地址</div>
                <v-text-field
                  v-model="hiveServer2Config.url"
                  placeholder="jdbc:hive2://<nn01>:10000"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required, rules.jdbcUrl]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">用户名</div>
                <v-text-field
                  v-model="hiveServer2Config.username"
                  density="compact"
                  placeholder="hive"
                  variant="outlined"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">密码</div>
                <v-text-field
                  v-model="hiveServer2Config.password"
                  density="compact"
                  variant="outlined"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="请输入密码"
                  :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                  @click:append-inner="showPassword = !showPassword"
                  autocomplete="off"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">驱动类名</div>
                <v-text-field
                  v-model="hiveServer2Config.driverClassName"
                  placeholder="org.apache.hive.jdbc.HiveDriver"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <div class="field-block">
                <div class="field-label">驱动路径</div>
                <v-text-field
                  v-model="hiveServer2Config.driverPath"
                  placeholder="/path/to/hive-jdbc.jar"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </div>
              <v-btn
                color="info"
                variant="tonal"
                size="small"
                prepend-icon="mdi-connection"
                @click="testHiveConnection"
                :loading="testingConnection"
                class="mt-2"
              >
                测试连接
              </v-btn>
            </v-card-text>
          </v-card>
        </v-col>

        <v-col cols="12" md="4" class="col-stack">
          <v-card flat class="ds-card section-card h-100">
            <v-card-title class="section-title">
              <v-icon class="section-title-icon" color="primary">mdi-speedometer</v-icon>
              性能配置
            </v-card-title>
            <v-divider />
            <v-card-text class="section-body">
              <div class="field-block">
                <div class="field-label">最大采集并发数量</div>
                <v-text-field
                  v-model.number="settings['CONCURRENT_LIMIT']"
                  type="number"
                  placeholder="30"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required, rules.positiveNumber]"
                  hide-details="auto"
                />
                <ul class="field-hint-list">
                  <li>修改后需重启所有后端节点才生效</li>
                  <li>
                    该值为每个节点的最大并发值。例如设为 20 且有 2 个节点时，集群最多同时发起 40
                    个采集任务。
                  </li>
                </ul>
              </div>
              <div class="field-block">
                <div class="field-label">采集队列长度</div>
                <v-text-field
                  v-model.number="settings['QUEUE_SIZE']"
                  type="number"
                  placeholder="100"
                  variant="outlined"
                  density="compact"
                  :rules="[rules.required, rules.positiveNumber]"
                  hide-details="auto"
                />
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- 下段：作业模板（全宽） -->
      <v-card flat class="ds-card section-card mt-4">
        <v-card-title class="section-title">
          <v-icon class="section-title-icon" color="primary">mdi-file-code</v-icon>
          作业模板配置
        </v-card-title>
        <v-divider />
        <v-card-text class="section-body">
          <div class="field-block">
            <div class="field-label">
              采集主模板
              <span class="field-hint"
                >变量 ${reader} 指向 RDBMS 读取子模板，${writer} 指向 HDFS 写入子模板</span
              >
            </div>
            <v-textarea
              v-model="R2HJobTemplate"
              rows="12"
              variant="outlined"
              class="json-editor"
              hide-details
            />
          </div>
          <v-row class="mt-4">
            <v-col cols="12" md="6">
              <div class="field-block">
                <div class="field-label">
                  RDBMS 读取子模板
                  <a
                    class="field-link"
                    href="https://wgzhao.github.io/Addax/latest/reader/rdbmsreader/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    文档
                  </a>
                </div>
                <v-textarea
                  v-model="rRJobTemplate"
                  rows="12"
                  variant="outlined"
                  class="json-editor"
                  hide-details
                />
              </div>
            </v-col>
            <v-col cols="12" md="6">
              <div class="field-block">
                <div class="field-label">
                  HDFS 写入子模板
                  <a
                    class="field-link"
                    href="https://wgzhao.github.io/Addax/latest/writer/hdfswriter/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    文档
                  </a>
                </div>
                <v-textarea
                  v-model="wHJobTemplate"
                  rows="12"
                  variant="outlined"
                  class="json-editor"
                  hide-details
                />
              </div>
            </v-col>
          </v-row>
        </v-card-text>
        <v-divider />
        <v-card-actions class="px-4 py-3">
          <v-spacer />
          <v-btn
            color="primary"
            variant="flat"
            type="submit"
            :disabled="!valid"
            :loading="saving"
            prepend-icon="mdi-content-save"
          >
            保存配置
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-form>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import { notify } from '@/stores/notifier';
  import settingsService, { type HiveServer2Config } from '@/service/settings-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils/constants';
  import { SysItem } from '@/types/database';

  // 表单引用和状态
  const form = ref<any>(null);
  const valid = ref(false);
  const saving = ref(false);
  const testingConnection = ref(false);
  const showPassword = ref(false);

  // 配置数据
  const settings = ref<any>({});
  // 保存原始的切日时间用于变更检测
  const originalSwitchTime = ref<string>('');

  // 下拉选项（与 BatchAdd.vue 保持一致）
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

  // 验证规则
  const rules = {
    required: (value: any) => {
      if (typeof value === 'number')
        return (value !== null && value !== undefined) || '此字段为必填项';
      return !!value || '此字段为必填项';
    },
    positiveNumber: (value: number) => {
      return value > 0 || '请输入大于0的数字';
    },
    timeFormat: (value: string) => {
      if (!value) return '此字段为必填项';
      const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/;
      return timeRegex.test(value) || '时间格式不正确，请使用 HH:mm 格式（如：16:30）';
    },
    jdbcUrl: (value: string) => {
      if (!value) return '此字段为必填项';
      const jdbcRegex = /^jdbc:hive2:\/\/.+$/;
      return jdbcRegex.test(value) || 'JDBC地址格式不正确，应以 jdbc:hive2:// 开头';
    },
    jsonFormat: (value: string) => {
      if (!value) return true; // 允许为空
      try {
        JSON.parse(value);
        return true;
      } catch (error) {
        return 'JSON 格式不正确，请检查语法';
      }
    },
  };

  // 测试Hive连接
  const testHiveConnection = async () => {
    testingConnection.value = true;
    try {
      const res = await settingsService.testHiveConnection(hiveServer2Config.value);
      notify(res.data || '连接成功', 'success');
    } catch (error: any) {
      notify(error?.message || error || '连接失败', 'error');
    } finally {
      testingConnection.value = false;
    }
  };

  // 保存配置
  const saveSettings = async () => {
    const { valid: isValid } = await form.value.validate();
    if (!isValid) {
      notify('请修正表单中的错误', 'error');
      return;
    }

    // 将 hiveServer2Config 的值更新到 settings 中
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

      const payload: SysItem[] = [];
      payload.push({
        dictCode: rRTemplate.dictCode,
        itemKey: 'rR',
        itemValue: rRJobTemplate.value,
        remark: rRTemplate.remark,
      });
      payload.push({
        dictCode: wHTemplate.dictCode,
        itemKey: 'wH',
        itemValue: wHJobTemplate.value,
        remark: wHTemplate.remark,
      });
      payload.push({
        dictCode: r2hTemplate.dictCode,
        itemKey: 'R2H',
        itemValue: R2HJobTemplate.value,
        remark: r2hTemplate.remark,
      });

      await settingsService.saveJobTemplates(payload);

      const result = await settingsService.saveSettings(settings.value);
      if (result) {
        notify('系统配置保存成功', 'success');
        // 如果切日时间发生变化，则调用后端接口重新注册调度任务
        const newSwitchTime = settings.value['SWITCH_TIME'];
        if (
          originalSwitchTime.value &&
          newSwitchTime &&
          originalSwitchTime.value !== newSwitchTime
        ) {
          try {
            const resp = await settingsService.rescheduleSwitchTimeTask();
            // 尽量从响应中取信息，否则给出默认成功提示
            const msg = (resp && (resp.message || resp.data)) || '切日调度任务已重新注册';
            notify(msg, 'success');
          } catch (err: any) {
            notify('切日调度任务重新注册失败: ' + (err?.message || err), 'error');
          }
          // 更新原始值为最新保存的值
          originalSwitchTime.value = newSwitchTime;
        }
      } else {
        notify('保存配置失败', 'warning');
      }
    } catch (error: any) {
      notify('保存配置失败: ' + (error.message || error), 'error');
    } finally {
      saving.value = false;
    }
  };

  // 加载现有配置
  const loadSettings = async () => {
    const loadedSettings = await settingsService.getSettings();
    const loadTemplates = await settingsService.getJobConfig();
    settings.value = loadedSettings;
    templates.value =
      loadTemplates instanceof Map
        ? loadTemplates
        : new Map(Object.entries((loadTemplates as unknown as Record<string, SysItem>) || {}));

    rRJobTemplate.value = templates.value.get('rR')?.itemValue || '';
    wHJobTemplate.value = templates.value.get('wH')?.itemValue || '';
    R2HJobTemplate.value = templates.value.get('R2H')?.itemValue || '';

    // 初始化下拉选项默认值（如果未设置）
    if (!settings.value['HDFS_STORAGE_FORMAT']) {
      settings.value['HDFS_STORAGE_FORMAT'] = storageFormats[0];
    }
    if (!settings.value['HDFS_COMPRESS_FORMAT']) {
      settings.value['HDFS_COMPRESS_FORMAT'] = compressFormats[0];
    }

    // 检查 HIVE_SERVER2 是否是字符串，如果是则需要解析
    if (loadedSettings['HIVE_SERVER2']) {
      if (typeof loadedSettings['HIVE_SERVER2'] === 'string') {
        // 如果是 JSON 字符串，需要解析
        try {
          hiveServer2Config.value = JSON.parse(loadedSettings['HIVE_SERVER2']);
        } catch (error) {
          console.error('解析 HIVE_SERVER2 配置失败:', error);
          // 使用默认配置
          hiveServer2Config.value = {
            url: '',
            username: '',
            password: '',
            driverClassName: '',
            driverPath: '',
          };
        }
      } else {
        // 如果已经是对象，直接赋值
        hiveServer2Config.value = loadedSettings['HIVE_SERVER2'];
      }
    }

    // 记录初始切日时间
    if (settings.value && typeof settings.value['SWITCH_TIME'] === 'string') {
      originalSwitchTime.value = settings.value['SWITCH_TIME'];
    }
  };

  // 组件挂载时加载配置
  onMounted(() => {
    loadSettings();
  });
</script>
<style scoped>
  .sys-settings-page {
    min-width: 0;
  }

  .config-row {
    row-gap: 16px;
  }

  .col-stack {
    display: flex;
    flex-direction: column;
  }

  .h-100 {
    flex: 1 1 auto;
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-size: 1rem;
  }

  .section-title-icon {
    font-size: 20px;
    opacity: 0.9;
  }

  .section-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 16px !important;
  }

  .field-block {
    display: flex;
    flex-direction: column;
  }

  .field-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
  }

  .field-link {
    font-size: 0.75rem;
    font-weight: 400;
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }
  .field-link:hover {
    text-decoration: underline;
  }

  .field-hint {
    font-size: 0.75rem;
    font-weight: 400;
    opacity: 0.65;
    margin-left: 4px;
  }

  .field-hint-list {
    margin: 6px 0 0;
    padding-left: 1.1em;
    font-size: 0.78rem;
    line-height: 1.6;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }
  .field-hint-list li {
    margin-bottom: 2px;
  }

  .json-editor :deep(textarea) {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace !important;
    font-size: 13px !important;
    line-height: 1.55 !important;
    tab-size: 2;
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
