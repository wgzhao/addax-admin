<template>
    <v-container>
        <v-row>
            <v-col cols="12">
                <h2 class="text-h4 mb-4">系统配置</h2>
                <p class="text-body-1 text-medium-emphasis mb-6">
                    系统首次安装后，请完成以下必要的配置项以确保系统正常运行。
                </p>
            </v-col>
        </v-row>

        <v-form ref="form" v-model="valid" @submit.prevent="saveSettings">
            <!-- 基础系统配置 -->
            <v-card class="mb-6" elevation="2">
                <v-card-title class="bg-primary text-white">
                    <v-icon class="mr-2">mdi-cog</v-icon>
                    基础系统配置
                </v-card-title>
                <v-card-text class="pa-6">
                    <v-row>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="settings['ADDAX']" label="Addax程序目录" placeholder="/opt/app/addax"
                                prepend-inner-icon="mdi-folder" :rules="[rules.required]" persistent-hint />
                        </v-col>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="settings['HDFS_PREFIX']" label="HDFS 目录前缀" placeholder="/ods"
                                prepend-inner-icon="mdi-folder-network" :rules="[rules.required]" persistent-hint />
                        </v-col>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="settings['SWITCH_TIME']" label="切日时间" placeholder="16:30"
                                prepend-inner-icon="mdi-clock" :rules="[rules.required, rules.timeFormat]"
                                persistent-hint />
                        </v-col>
                    </v-row>
                </v-card-text>
            </v-card>

            <!-- HiveServer2 配置 -->
            <v-card class="mb-6" elevation="2">
                <v-card-title class="bg-success text-white">
                    <v-icon class="mr-2">mdi-server</v-icon>
                    HiveServer2 配置
                </v-card-title>
                <v-card-text class="pa-6">
                    <v-row>
                        <v-col cols="12" md="8">
                            <v-text-field v-model="hiveServer2Config.url" label="JDBC连接地址"
                                placeholder="jdbc:hive2://188.166.1.96:10000" prepend-inner-icon="mdi-link"
                                :rules="[rules.required, rules.jdbcUrl]" hint="HiveServer2的JDBC连接地址" persistent-hint />
                        </v-col>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="hiveServer2Config.driverClassName" label="驱动类名"
                                placeholder="org.apache.hive.jdbc.HiveDriver" prepend-inner-icon="mdi-link"
                                :rules="[rules.required]" hint="HiveServer2的JDBC连接地址" persistent-hint />
                        </v-col>
                    </v-row>
                    <v-row>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="hiveServer2Config.username" label="用户名" placeholder="hive"
                                prepend-inner-icon="mdi-account" :rules="[rules.required]" hint="连接HiveServer2的用户名"
                                persistent-hint />
                        </v-col>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="hiveServer2Config.password"
                                :type="showPassword ? 'text' : 'password'" label="密码" placeholder="请输入密码"
                                prepend-inner-icon="mdi-lock"
                                :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                                @click:append-inner="showPassword = !showPassword" hint="连接HiveServer2的密码"
                                persistent-hint />
                        </v-col>
                        <v-col cols="12" md="4">
                            <v-text-field v-model="hiveServer2Config.driverPath" label="驱动路径"
                                placeholder="/path/to/hive-jdbc.jar" prepend-inner-icon="mdi-file-code"
                                :rules="[rules.required]" hint="Hive JDBC驱动的文件路径" persistent-hint />
                        </v-col>
                    </v-row>
                    <v-row>
                        <v-col cols="12">
                            <v-btn color="info" variant="outlined" prepend-icon="mdi-connection"
                                @click="testHiveConnection" :loading="testingConnection">
                                测试连接
                            </v-btn>
                        </v-col>
                    </v-row>
                </v-card-text>
            </v-card>

            <!-- 性能配置 -->
            <v-card class="mb-6" elevation="2">
                <v-card-title class="bg-warning text-white">
                    <v-icon class="mr-2">mdi-speedometer</v-icon>
                    性能配置
                </v-card-title>
                <v-card-text class="pa-6">
                    <v-row>
                        <v-col cols="12" md="6">
                            <v-text-field v-model.number="settings['CONCURRENT_LIMIT']" label="最大采集并发数量" type="number"
                                placeholder="30" prepend-inner-icon="mdi-flash"
                                :rules="[rules.required, rules.positiveNumber]" persistent-hint />
                        </v-col>
                        <v-col cols="12" md="6">
                            <v-text-field v-model.number="settings['QUEUE_SIZE']" label="采集队列长度" type="number"
                                placeholder="100" prepend-inner-icon="mdi-queue-first-in-last-out"
                                :rules="[rules.required, rules.positiveNumber]" persistent-hint />
                        </v-col>
                    </v-row>
                </v-card-text>
            </v-card>

            <v-card class="mb-6" elevation="2">
                <v-card-title class="bg-info text-white">
                    <v-icon class="mr-2">mdi-json</v-icon>Hadoop配置
                </v-card-title>
                <v-card-text class="pa-6">
                    <v-row>
                        <v-col cols="12">
                            <v-textarea v-model="hdfsConfig" label="Hadoop配置文件内容" rows="10" variant="outlined"
                                class="json-editor" :rules="[rules.jsonFormat]" hint="请输入格式化的 JSON 配置"
                                persistent-hint />
                        </v-col>
                    </v-row>
                </v-card-text>
            </v-card>
            <!-- 操作按钮 -->
            <v-card elevation="2">
                <v-card-actions class="pa-6">
                    <v-spacer />
                    <v-btn color="primary" size="large" type="submit" :disabled="!valid" :loading="saving"
                        prepend-icon="mdi-content-save">
                        保存配置
                    </v-btn>
                </v-card-actions>
            </v-card>
        </v-form>
    </v-container>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { notify } from '@/stores/notifier'
import settingsService, {
    type HiveServer2Config,
    type SystemConfig
} from '@/service/settings-service'

// 表单引用和状态
const form = ref<any>(null)
const valid = ref(false)
const saving = ref(false)
const testingConnection = ref(false)
const showPassword = ref(false)

// 配置数据
const settings = ref<any>({})

const hiveServer2Config = ref<HiveServer2Config>({
    url: '',
    username: '',
    password: '',
    driverClassName: '',
    driverPath: ''
})

// HDFS 配置（用于展示格式化的 JSON）
const hdfsConfig = ref<string>('')

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
    // 假设 response 结构为 { status: 200/500, data: 消息字符串 }
    //   if (response && response.status === 200) {
    //     notify(response.data || '连接成功', 'success')
    //   } else if (response && response.status === 500) {
    //     notify(response.data || '连接失败', 'error')
    //   } else {
    //     notify('未知错误', 'error')
    //   }
    // } catch (error: any) {
    //   notify(error?.response?.data || '连接失败', 'error')
    // } finally {
    //   testingConnection.value = false
    // }
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

    // 处理 HDFS_CONFIG，如果是有效的 JSON 则压缩存储，否则直接存储
    try {
        if (hdfsConfig.value.trim()) {
            // 验证并压缩 JSON
            const parsed = JSON.parse(hdfsConfig.value)
            settings.value['HDFS_CONFIG'] = JSON.stringify(parsed)
        } else {
            settings.value['HDFS_CONFIG'] = ''
        }
    } catch (error) {
        console.error('HDFS_CONFIG JSON 格式错误:', error)
        notify('HDFS配置格式错误，请检查 JSON 格式', 'error')
        return
    }

    saving.value = true
    try {
        const result = await settingsService.saveSettings(settings.value)
        if (result) {
            notify('系统配置保存成功', 'success')
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
    settings.value = loadedSettings

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

    // 检查 HDFS_CONFIG 是否是字符串，如果是则需要格式化显示
    if (loadedSettings['HDFS_CONFIG']) {
        if (typeof loadedSettings['HDFS_CONFIG'] === 'string') {
            try {
                // 尝试解析 JSON 并格式化
                const parsed = JSON.parse(loadedSettings['HDFS_CONFIG'])
                hdfsConfig.value = JSON.stringify(parsed, null, 2)
            } catch (error) {
                console.error('解析 HDFS_CONFIG 配置失败，使用原始字符串:', error)
                // 如果不是有效的 JSON，直接使用原始字符串
                hdfsConfig.value = loadedSettings['HDFS_CONFIG']
            }
        } else {
            // 如果已经是对象，格式化为字符串
            hdfsConfig.value = JSON.stringify(loadedSettings['HDFS_CONFIG'], null, 2)
        }
    } else {
        // 如果没有配置，使用空字符串
        hdfsConfig.value = ''
    }

    console.log("loaded config:", loadedSettings)
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

/* 使用主题适配的表面颜色 */
.v-card-text {
    background-color: rgb(var(--v-theme-surface));
}

/* 深色模式下的优化 */
.v-theme--dark .v-card-text {
    background-color: #2b2c40;
    /* 使用主题中定义的深色表面颜色 */
}

/* 深色模式下输入框的背景色优化 */
.v-theme--dark .v-text-field .v-field__field {
    background-color: #444463 !important;
    /* 使用主题中的 grey-100 */
}

.v-theme--dark .v-select .v-field__field {
    background-color: #444463 !important;
    /* 使用主题中的 grey-100 */
}

.v-theme--dark .v-textarea .v-field__field {
    background-color: #444463 !important;
    /* 使用主题中的 grey-100 */
}

/* 深色模式下输入框边框颜色 */
.v-theme--dark .v-text-field .v-field__outline {
    color: #5e6692 !important;
    /* 使用主题中的 grey-300 */
}

.v-theme--dark .v-select .v-field__outline {
    color: #5e6692 !important;
    /* 使用主题中的 grey-300 */
}

/* 深色模式下文本颜色 */
.v-theme--dark .v-text-field input,
.v-theme--dark .v-select .v-field__input,
.v-theme--dark .v-textarea textarea {
    color: #dbdbeb !important;
    /* 使用主题中的 on-surface 颜色 */
}

/* 浅色模式下保持合适的背景色 */
.v-theme--light .v-card-text {
    background-color: #fafafa;
    /* 使用主题中的 grey-50 */
}

/* 提示文字颜色优化 */
.v-theme--dark .v-messages__message {
    color: #8692d0 !important;
    /* 使用主题中的 grey-500 */
}

/* 标签颜色优化 */
.v-theme--dark .v-label {
    color: #aab3de !important;
    /* 使用主题中的 grey-600 */
}

.json-preview textarea {
    font-family: 'Courier New', monospace !important;
    font-size: 12px !important;
    line-height: 1.4 !important;
}

/* JSON 编辑器样式 */
.json-editor textarea {
    font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace !important;
    font-size: 13px !important;
    line-height: 1.5 !important;
    tab-size: 2;
}
</style>
