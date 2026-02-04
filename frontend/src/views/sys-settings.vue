<template>
  <v-card class="pa-4 sys-settings-card">
    <v-row>
      <v-col cols="12">
        <h2 class="text-h4 mb-4">系统配置</h2>
        <p class="text-body-1 text-medium-emphasis mb-6">
          系统首次安装后，请完成以下必要的配置项以确保系统正常运行。
        </p>
      </v-col>
    </v-row>

    <v-form ref="form" v-model="valid" @submit.prevent="saveSettings">
      <v-row class="section-grid stretch-row">
        <!-- 左列：基础配置 (4/12 = 33%) -->
        <v-col cols="12" md="4" class="col-stack">
          <!-- 基础系统配置 -->
          <v-card class="mb-4 section-card" elevation="0">
            <v-card-title class="section-title">
              <v-icon class="mr-2" color="primary">mdi-cog</v-icon>
              基础系统配置
            </v-card-title>
            <v-divider />
            <v-card-text class="pa-4 section-body">
              <v-row dense>
                <v-col cols="12">
                  <div class="field-label">Addax程序目录</div>
                  <v-text-field
                    v-model="settings['ADDAX']"
                    placeholder="/opt/app/addax"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">HDFS 目录前缀</div>
                  <v-text-field
                    v-model="settings['HDFS_PREFIX']"
                    placeholder="/ods"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">切日时间</div>
                  <v-text-field
                    v-model="settings['SWITCH_TIME']"
                    placeholder="16:30"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required, rules.timeFormat]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">默认HDFS存储格式</div>
                  <v-select
                    v-model="settings['HDFS_STORAGE_FORMAT']"
                    :items="storageFormats"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">默认压缩格式</div>
                  <v-select
                    v-model="settings['HDFS_COMPRESS_FORMAT']"
                    :items="compressFormats"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
              </v-row>
            </v-card-text>
          </v-card>

          <!-- HiveServer2 配置 -->
          <v-card class="mb-4 section-card" elevation="0">
            <v-card-title class="section-title">
              <v-icon class="mr-2" color="primary">mdi-server</v-icon>
              HiveServer2 配置
            </v-card-title>
            <v-divider />
            <v-card-text class="pa-4 section-body">
              <v-row dense>
                <v-col cols="12">
                  <div class="field-label">JDBC连接地址</div>
                  <v-text-field
                    v-model="hiveServer2Config.url"
                    placeholder="jdbc:hive2://<nn01>:10000"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required, rules.jdbcUrl]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">用户名</div>
                  <v-text-field
                    v-model="hiveServer2Config.username"
                    density="compact"
                    placeholder="hive"
                    variant="outlined"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
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
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">驱动类名</div>
                  <v-text-field
                    v-model="hiveServer2Config.driverClassName"
                    placeholder="org.apache.hive.jdbc.HiveDriver"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">驱动路径</div>
                  <v-text-field
                    v-model="hiveServer2Config.driverPath"
                    placeholder="/path/to/hive-jdbc.jar"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required]"
                  />
                </v-col>
                <v-col cols="12">
                  <v-btn
                    color="info"
                    variant="outlined"
                    prepend-icon="mdi-connection"
                    @click="testHiveConnection"
                    :loading="testingConnection"
                  >
                    测试连接
                  </v-btn>
                </v-col>
              </v-row>
            </v-card-text>
          </v-card>

          <!-- 性能配置 -->
          <v-card class="mb-4 section-card" elevation="0">
            <v-card-title class="section-title">
              <v-icon class="mr-2" color="primary">mdi-speedometer</v-icon>
              性能配置
            </v-card-title>
            <v-divider />
            <v-card-text class="pa-4 section-body">
              <v-row dense>
                <v-col cols="12">
                  <div class="field-label">最大采集并发数量</div>
                  <v-text-field
                    v-model.number="settings['CONCURRENT_LIMIT']"
                    type="number"
                    placeholder="30"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required, rules.positiveNumber]"
                  />
                </v-col>
                <v-col cols="12">
                  <div class="field-label">采集队列长度</div>
                  <v-text-field
                    v-model.number="settings['QUEUE_SIZE']"
                    type="number"
                    placeholder="100"
                    variant="outlined"
                    density="compact"
                    :rules="[rules.required, rules.positiveNumber]"
                  />
                </v-col>
              </v-row>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- 右列：作业模板配置 (8/12 = 67%, 使用6来达到4:6比例) -->
        <v-col cols="12" md="8" class="col-stack">
          <!-- 作业模板配置 -->
          <v-card class="mb-2 section-card stretch-card" elevation="0">
            <v-card-title class="section-title">
              <v-icon class="mr-2" color="primary">mdi-file-code</v-icon>
              作业模板配置
            </v-card-title>
            <v-divider />
            <v-card-text class="pa-4 section-body">
              <!-- 采集主模板 -->
              <v-row class="mb-4">
                <v-col cols="12">
                  <div class="field-label">采集主模板</div>
                  <v-textarea
                    v-model="R2HJobTemplate"
                    rows="15"
                    variant="outlined"
                    class="json-editor"
                    hint="变量 ${reader} 指向 RDBMS 读取子模板 / ${writer} 指向 HDFS 写入子模板"
                  />
                </v-col>
              </v-row>

              <!-- RDBMS 读取子模板 -->
              <v-row class="mb-2">
                <v-col cols="12">
                  <div class="field-label">
                    RDBMS 读取子模板(参考
                    <a
                      href="https://wgzhao.github.io/Addax/latest/reader/rdbmsreader/"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      RDBMS 读取插件
                    </a>
                    )
                  </div>
                  <v-textarea
                    v-model="rRJobTemplate"
                    rows="16"
                    variant="outlined"
                    class="json-editor"
                    hint="包含 ${var} 占位符的 JSON；此处保留变量解释区"
                  />
                </v-col>
              </v-row>

              <!-- HDFS 写入子模板 -->
              <v-row class="mb-2">
                <v-col cols="12">
                  <div class="field-label">
                    HDFS 写入子模板(参考
                    <a
                      href="https://wgzhao.github.io/Addax/latest/writer/hdfswriter/"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      HDFS 写入插件
                    </a>
                    )
                  </div>
                  <v-textarea
                    v-model="wHJobTemplate"
                    rows="18"
                    variant="outlined"
                    class="json-editor"
                    hint="包含 ${var} 占位符的 JSON；此处保留变量解释区"
                  />
                </v-col>
              </v-row>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- 操作按钮 -->
      <v-row>
        <v-col cols="12">
          <v-card class="section-card" elevation="0">
            <v-card-actions class="pa-4 action-bar">
              <v-spacer />
              <v-btn
                color="primary"
                variant="tonal"
                type="submit"
                :disabled="!valid"
                :loading="saving"
                prepend-icon="mdi-content-save"
              >
                保存配置
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-col>
      </v-row>
    </v-form>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import { notify } from '@/stores/notifier'
  import settingsService, { type HiveServer2Config } from '@/service/settings-service'
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils/constants'
  import { SysItem } from '@/types/database'

  // 表单引用和状态
  const form = ref<any>(null)
  const valid = ref(false)
  const saving = ref(false)
  const testingConnection = ref(false)
  const showPassword = ref(false)

  // 配置数据
  const settings = ref<any>({})
  // 保存原始的切日时间用于变更检测
  const originalSwitchTime = ref<string>('')

  // 下拉选项（与 BatchAdd.vue 保持一致）
  const storageFormats = HDFS_STORAGE_FORMATS
  const compressFormats = HDFS_COMPRESS_FORMATS

  const hiveServer2Config = ref<HiveServer2Config>({
    url: '',
    username: '',
    password: '',
    driverClassName: '',
    driverPath: ''
  })

  const templates = ref<Map<string, SysItem>>(new Map())
  const rRJobTemplate = ref<string>('')
  const wHJobTemplate = ref<string>('')
  const R2HJobTemplate = ref<string>('')

  // 验证规则
  const rules = {
    required: (value: any) => {
      if (typeof value === 'number')
        return (value !== null && value !== undefined) || '此字段为必填项'
      return !!value || '此字段为必填项'
    },
    positiveNumber: (value: number) => {
      return value > 0 || '请输入大于0的数字'
    },
    timeFormat: (value: string) => {
      if (!value) return '此字段为必填项'
      const timeRegex = /^([0-1][0-9]|2[0-3]):[0-5][0-9]$/
      return timeRegex.test(value) || '时间格式不正确，请使用 HH:mm 格式（如：16:30）'
    },
    jdbcUrl: (value: string) => {
      if (!value) return '此字段为必填项'
      const jdbcRegex = /^jdbc:hive2:\/\/.+$/
      return jdbcRegex.test(value) || 'JDBC地址格式不正确，应以 jdbc:hive2:// 开头'
    },
    jsonFormat: (value: string) => {
      if (!value) return true // 允许为空
      try {
        JSON.parse(value)
        return true
      } catch (error) {
        return 'JSON 格式不正确，请检查语法'
      }
    }
  }

  // 测试Hive连接
  const testHiveConnection = () => {
    testingConnection.value = true
    settingsService
      .testHiveConnection(hiveServer2Config.value)
      .then((res) => {
        console.log(res)
        notify(res.data || '连接成功', 'success')
      })
      .catch((error) => {
        console.error(error)
        notify(error || '连接失败', 'error')
      })
    testingConnection.value = false
  }

  // 保存配置
  const saveSettings = async () => {
    const { valid: isValid } = await form.value.validate()
    if (!isValid) {
      notify('请修正表单中的错误', 'error')
      return
    }

    // 将 hiveServer2Config 的值更新到 settings 中
    settings.value['HIVE_SERVER2'] = JSON.stringify(hiveServer2Config.value)

    saving.value = true
    try {
      const payload: SysItem[] = []
      payload.push({
        dictCode: templates.value['rR'].dictCode,
        itemKey: 'rR',
        itemValue: rRJobTemplate.value,
        remark: templates.value['rR'].remark
      })
      payload.push({
        dictCode: templates.value['wH'].dictCode,
        itemKey: 'wH',
        itemValue: wHJobTemplate.value,
        remark: templates.value['wH'].remark
      })
      payload.push({
        dictCode: templates.value['R2H'].dictCode,
        itemKey: 'R2H',
        itemValue: R2HJobTemplate.value,
        remark: templates.value['R2H'].remark
      })

      await settingsService.saveJobTemplates(payload)

      const result = await settingsService.saveSettings(settings.value)
      if (result) {
        notify('系统配置保存成功', 'success')
        // 如果切日时间发生变化，则调用后端接口重新注册调度任务
        const newSwitchTime = settings.value['SWITCH_TIME']
        if (
          originalSwitchTime.value &&
          newSwitchTime &&
          originalSwitchTime.value !== newSwitchTime
        ) {
          try {
            const resp = await settingsService.rescheduleSwitchTimeTask()
            // 尽量从响应中取信息，否则给出默认成功提示
            const msg = (resp && (resp.message || resp.data)) || '切日调度任务已重新注册'
            notify(msg, 'success')
          } catch (err: any) {
            notify('切日调度任务重新注册失败: ' + (err?.message || err), 'error')
          }
          // 更新原始值为最新保存的值
          originalSwitchTime.value = newSwitchTime
        }
      } else {
        notify('保存配置失败', 'warning')
      }
    } catch (error: any) {
      notify('保存配置失败: ' + (error.message || error), 'error')
    } finally {
      saving.value = false
    }
  }

  // 加载现有配置
  const loadSettings = async () => {
    const loadedSettings = await settingsService.getSettings()
    const loadTemplates = await settingsService.getJobConfig()
    settings.value = loadedSettings
    templates.value = loadTemplates

    rRJobTemplate.value = loadTemplates['rR']?.itemValue || ''
    wHJobTemplate.value = loadTemplates['wH']?.itemValue || ''
    R2HJobTemplate.value = loadTemplates['R2H']?.itemValue || ''

    // 初始化下拉选项默认值（如果未设置）
    if (!settings.value['HDFS_STORAGE_FORMAT']) {
      settings.value['HDFS_STORAGE_FORMAT'] = storageFormats[0]
    }
    if (!settings.value['HDFS_COMPRESS_FORMAT']) {
      settings.value['HDFS_COMPRESS_FORMAT'] = compressFormats[0]
    }

    // 检查 HIVE_SERVER2 是否是字符串，如果是则需要解析
    if (loadedSettings['HIVE_SERVER2']) {
      if (typeof loadedSettings['HIVE_SERVER2'] === 'string') {
        // 如果是 JSON 字符串，需要解析
        try {
          hiveServer2Config.value = JSON.parse(loadedSettings['HIVE_SERVER2'])
        } catch (error) {
          console.error('解析 HIVE_SERVER2 配置失败:', error)
          // 使用默认配置
          hiveServer2Config.value = {
            url: '',
            username: '',
            password: '',
            driverClassName: '',
            driverPath: ''
          }
        }
      } else {
        // 如果已经是对象，直接赋值
        hiveServer2Config.value = loadedSettings['HIVE_SERVER2']
      }
    }

    // 记录初始切日时间
    if (settings.value && typeof settings.value['SWITCH_TIME'] === 'string') {
      originalSwitchTime.value = settings.value['SWITCH_TIME']
    }
  }

  // 组件挂载时加载配置
  onMounted(() => {
    loadSettings()
  })
</script>
<style scoped>
  .v-card-title {
    font-weight: 600;
  }

  .sys-settings-card {
    background: rgb(var(--v-theme-surface));
  }

  .section-grid {
    row-gap: 12px;
  }

  .stretch-row {
    align-items: stretch;
  }

  .col-stack {
    display: flex;
    flex-direction: column;
  }

  .stretch-card {
    flex: 1 1 auto;
  }

  .section-card {
    background: rgb(var(--v-theme-surface-variant));
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
  }

  .section-body {
    background: transparent;
  }

  .action-bar {
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.06);
  }

  .json-preview textarea {
    font-family: 'Courier New', monospace !important;
    font-size: 12px !important;
    line-height: 1.4 !important;
  }

  /* JSON 编辑器样式 */
  .json-editor textarea {
    font-family:
      'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace !important;
    font-size: 13px !important;
    line-height: 1.5 !important;
    tab-size: 2;
  }

  /* 独立标签样式，使用主题颜色并保持轻权重 */
  .field-label {
    color: rgb(var(--v-theme-on-surface));
    opacity: 0.8;
    /* 轻度强调 */
    font-size: 0.9rem;
    margin-bottom: 6px;
  }
</style>
