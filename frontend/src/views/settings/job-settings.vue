<template>
    <v-container fluid>
        <v-row>
            <v-col cols="12">
                <h2 class="text-h4 mb-4">Addax 采集模板配置</h2>
                <p class="text-body-1 text-medium-emphasis mb-6">
                    配置 Addax 采集作业的 HDFS/Hive Writer 参数，生成标准的 JSON 配置模板。
                </p>
            </v-col>
        </v-row>

        <v-row>
            <!-- 左侧配置区域 -->
            <v-col cols="12" lg="6">
                <v-form ref="form" v-model="valid" @submit.prevent="generateTemplate">
                    <!-- 基础配置 -->
                    <v-card class="mb-6" elevation="2">
                        <v-card-title class="bg-primary text-white">
                            <v-icon class="mr-2">mdi-cog</v-icon>
                            HDFS Writer 配置
                        </v-card-title>
                        <v-card-text class="pa-6">
                            <v-row>
                                <v-col cols="12" md="6">
                                    <v-text-field small dense v-model="writerConfig.defaultFS" label="HDFS DefaultFS"
                                        placeholder="hdfs://nameservice1" prepend-inner-icon="mdi-server-network"
                                        :rules="[rules.required, rules.hdfsUrl]" persistent-hint />
                                </v-col>
                                <v-col cols="12" md="6">
                                    <v-text-field small dense v-model="writerConfig.hdfsPrefix" label="保存到 HDFS 的路径前缀"
                                        placeholder="/ods" prepend-inner-icon="mdi-server-network"
                                        :rules="[rules.required]" persistent-hint />
                                </v-col>
                                <v-col cols="12" md="6">
                                    <v-switch v-model="writerConfig.enableHA" color="primary" hide-details
                                        density="compact">
                                        <template v-slot:prepend>
                                            <v-icon class="mr-2">mdi-server-plus</v-icon>
                                        </template>
                                        <template v-slot:append>
                                            <v-chip size="small" :color="writerConfig.enableHA ? 'success' : 'default'"
                                                :text="writerConfig.enableHA ? 'HDFS 高可用已启用' : 'HDFS 高可用未启用'"
                                                class="ml-2" />
                                        </template>
                                    </v-switch>
                                </v-col>
                            </v-row>

                            <!-- HDFS 高可用配置 -->
                            <v-expand-transition>
                                <div v-if="writerConfig.enableHA">
                                    <v-row>
                                        <v-col cols="6">
                                            <v-radio-group v-model="hdfsConfigType" hide-details density="compact">
                                                <v-radio label="使用 HDFS Site 配置文件" value="file" color="primary" />
                                                <v-radio label="手动配置 JSON" value="json" color="primary" />
                                            </v-radio-group>
                                        </v-col>
                                        <v-col cols="6">
                                            <v-text-field small dense v-model="writerConfig.hdfsSitePath"
                                                label="HDFS Site 配置文件路径" placeholder="/opt/app/addax/cdh-hdfs-site.xml"
                                                prepend-inner-icon="mdi-file-xml"
                                                :rules="writerConfig.enableHA && hdfsConfigType === 'file' ? [rules.required] : []"
                                                persistent-hint />
                                        </v-col>
                                    </v-row>

                                    <!-- 文件路径配置 -->
                                    <v-row v-if="hdfsConfigType === 'file'">
                                        <v-col cols="12">
                                            <v-text-field small dense v-model="writerConfig.hdfsSitePath"
                                                label="HDFS Site 配置文件路径" placeholder="/opt/app/addax/cdh-hdfs-site.xml"
                                                prepend-inner-icon="mdi-file-xml"
                                                :rules="writerConfig.enableHA && hdfsConfigType === 'file' ? [rules.required] : []"
                                                persistent-hint />
                                        </v-col>
                                    </v-row>

                                    <!-- JSON 配置 -->
                                    <div v-if="hdfsConfigType === 'json'">
                                        <v-row>
                                            <v-col cols="12" md="4">
                                                <v-text-field small dense
                                                    v-model="writerConfig.hadoopConfig.nameservice" label="NameService"
                                                    placeholder="cluster" prepend-inner-icon="mdi-dns"
                                                    :rules="writerConfig.enableHA && hdfsConfigType === 'json' ? [rules.required] : []"
                                                    hint="HDFS 集群的 NameService 名称" persistent-hint
                                                    @input="updateHadoopConfig" />
                                            </v-col>
                                            <v-col cols="12" md="6">
                                                <v-text-field small dense v-model="writerConfig.hadoopConfig.namenodes"
                                                    label="NameNodes" placeholder="nn1,nn2"
                                                    prepend-inner-icon="mdi-server-network-outline"
                                                    :rules="writerConfig.enableHA && hdfsConfigType === 'json' ? [rules.required] : []"
                                                    hint="NameNode 节点名称，用逗号分隔" persistent-hint
                                                    @input="updateHadoopConfig" />
                                            </v-col>
                                        </v-row>

                                        <!-- 动态生成的 NameNode 地址配置 -->
                                        <v-row>
                                            <v-col cols="12" md="6" v-for="(namenode, index) in namenodeList"
                                                :key="namenode">
                                                <v-text-field small dense
                                                    v-model="writerConfig.hadoopConfig.addresses[namenode]"
                                                    :label="`${namenode} RPC 地址`"
                                                    :placeholder="`node${index + 1}.example.com:8020`"
                                                    prepend-inner-icon="mdi-ip-network"
                                                    :rules="writerConfig.enableHA && hdfsConfigType === 'json' ? [rules.required, rules.rpcAddress] : []"
                                                    persistent-hint />
                                            </v-col>
                                        </v-row>
                                    </div>
                                </div>
                            </v-expand-transition>

                            <!-- Kerberos 配置 -->
                            <v-divider class="my-4"></v-divider>
                            <v-row>
                                <v-col cols="12">
                                    <v-switch v-model="writerConfig.haveKerberos" color="primary" hide-details
                                        density="compact">
                                        <template v-slot:prepend>
                                            <v-icon class="mr-2">mdi-shield-key</v-icon>
                                        </template>
                                        <template v-slot:append>
                                            <v-chip size="small"
                                                :color="writerConfig.haveKerberos ? 'success' : 'default'"
                                                :text="writerConfig.haveKerberos ? '已启用 Kerberos' : '未启用 Kerberos'"
                                                class="ml-2" />
                                        </template>
                                    </v-switch>
                                </v-col>
                            </v-row>

                            <!-- Kerberos 详细配置 -->
                            <v-expand-transition>
                                <div v-if="writerConfig.haveKerberos">
                                    <v-row>
                                        <v-col cols="12" md="6">
                                            <v-text-field small dense v-model="writerConfig.kerberosKeytabFilePath"
                                                label="Keytab 文件路径" placeholder="/path/to/user.keytab"
                                                prepend-inner-icon="mdi-key-variant"
                                                :rules="writerConfig.haveKerberos ? [rules.required] : []"
                                                hint="Kerberos Keytab 文件的绝对路径" persistent-hint />
                                        </v-col>
                                        <v-col cols="12" md="6">
                                            <v-text-field small dense v-model="writerConfig.kerberosPrincipal"
                                                label="Kerberos Principal" placeholder="user@REALM.COM"
                                                prepend-inner-icon="mdi-account-key"
                                                :rules="writerConfig.haveKerberos ? [rules.required, rules.principal] : []"
                                                hint="Kerberos 用户主体名称" persistent-hint />
                                        </v-col>
                                    </v-row>
                                </div>
                            </v-expand-transition>
                            <v-row>
                                <v-col cols="12" md="6">
                                    <v-btn color="success" size="large" block :disabled="!valid"
                                        prepend-icon="mdi-content-save" @click="saveConfig" :loading="saving">
                                        保存配置
                                    </v-btn>
                                </v-col>
                            </v-row>
                        </v-card-text>
                    </v-card>
                </v-form>
            </v-col>

            <!-- 右侧预览区域 -->
            <v-col cols="12" lg="6">
                <v-card elevation="2" class="sticky-preview">
                    <v-card-title class="bg-success text-white">
                        <v-icon class="mr-2">mdi-eye</v-icon>
                        配置预览
                        <v-spacer />
                        <v-chip size="small" :color="valid ? 'white' : 'error'" :text="valid ? '配置有效' : '配置无效'" />
                    </v-card-title>
                    <v-card-text class="pa-6">
                        <v-textarea v-model="generatedJson" label="生成的 JSON 配置" readonly rows="30" variant="outlined"
                            class="json-preview" />
                    </v-card-text>
                </v-card>
            </v-col>
        </v-row>
    </v-container>
</template>

<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { notify } from '@/stores/notifier'
import settingsService from '@/service/settings-service'

// 表单引用和状态
const form = ref<any>(null)
const valid = ref(false)
const saving = ref(false)

// Writer 配置接口定义
interface WriterConfig {
    hdfsPrefix: string
    defaultFS: string
    haveKerberos: boolean
    kerberosKeytabFilePath?: string
    kerberosPrincipal?: string
    createPath: boolean
    hdfsSitePath?: string
    enableHA: boolean
    hadoopConfig?: Record<string, any>
}

const nameservice = ref('nameservice1')


// 配置数据
const writerConfig = ref<WriterConfig>({
    hdfsPrefix: '/ods',
    defaultFS: 'hdfs://nameservice1',
    haveKerberos: false,
    createPath: true,
    hdfsSitePath: '/opt/app/addax/cdh-hdfs-site.xml',
    enableHA: false,
    kerberosKeytabFilePath: '',
    kerberosPrincipal: '',
    hadoopConfig: {} as Record<string, any>
})

// HDFS 高可用配置类型：file（配置文件）或 json（手动配置）
const hdfsConfigType = ref<'file' | 'json'>('file')


// 计算 namenode 列表
const namenodeList = computed(() => {
    if (!writerConfig.value.hadoopConfig?.namenodes) return []
    return writerConfig.value.hadoopConfig.namenodes.split(',').map(name => name.trim()).filter(Boolean)
})

// 验证规则
const rules = {
    required: (value: any) => !!value || '此字段为必填项',
    hdfsUrl: (value: string) => {
        if (!value) return '此字段为必填项'
        const hdfsRegex = /^hdfs:\/\/.+$/
        return hdfsRegex.test(value) || 'HDFS URL 格式不正确，应以 hdfs:// 开头'
    },
    principal: (value: string) => {
        if (!value) return '此字段为必填项'
        const principalRegex = /^.+@.+$/
        return principalRegex.test(value) || 'Principal 格式不正确，应包含 @ 符号'
    },
    rpcAddress: (value: string) => {
        if (!value) return '此字段为必填项'
        const rpcRegex = /^.+:\d+$/
        return rpcRegex.test(value) || 'RPC 地址格式不正确，应为 host:port 格式'
    }
}

// 更新 Hadoop 配置
const updateHadoopConfig = () => {
    // 当 namenode 列表发生变化时，更新地址配置
    const newAddresses: Record<string, string> = {}
    const ns = writerConfig.value.hadoopConfig?.nameservice || 'nameservice1'
    namenodeList.value.forEach(namenode => {
        newAddresses[`dfs.namenode.rpc-address.${ns}.${namenode}`] = writerConfig.value.hadoopConfig?.addresses[namenode] || ''
    })
    if (writerConfig.value.hadoopConfig) {
        writerConfig.value.hadoopConfig.addresses = newAddresses
    }
}

// 生成的 JSON 配置
const generatedJson = computed(() => {

    return JSON.stringify(writerConfig.value, null, 2)
})

// 保存配置到后端
const saveConfig = async () => {
    const { valid: isValid } = await form.value.validate()
    if (!isValid) {
        notify('请修正表单中的错误', 'error')
        return
    }

    saving.value = true

    try {
        console.log('保存的配置数据:', generatedJson.value)
        // 调用后端API保存配置
        await settingsService.saveHdfsWriterConfig(generatedJson.value)

        notify('配置保存成功！', 'success')
    } catch (error) {
        console.error('保存配置失败:', error)
        notify('保存配置失败，请稍后重试', 'error')
    } finally {
        saving.value = false
    }
}

// 生成配置模板
const generateTemplate = async () => {
    const { valid: isValid } = await form.value.validate()
    if (!isValid) {
        notify('请修正表单中的错误', 'error')
        return
    }

    notify('配置已更新', 'success')
}

// 监听配置变化，自动更新预览
watch(writerConfig, () => {
    // 自动更新预览，无需手动点击刷新
}, { deep: true })

// 监听 namenode 列表变化，自动更新地址配置
watch(() => writerConfig.value.hadoopConfig?.namenodes, () => {
    updateHadoopConfig()
})

// 监听 HDFS HA 开关变化
watch(() => writerConfig.value.enableHA, (newVal) => {
    if (newVal && hdfsConfigType.value === 'json') {
        // 启用 HDFS HA 且使用 JSON 配置时，初始化地址配置
        updateHadoopConfig()
    }
})

// 加载保存的配置
const loadSavedConfig = async () => {
    try {
        const savedConfigResponse = await settingsService.getHdfsWriterConfig()

        // 处理后端返回的 JSON 字符串
        let savedConfig
        if (typeof savedConfigResponse === 'string') {
            writerConfig.value = JSON.parse(savedConfigResponse)
        } else {
            writerConfig.value = savedConfigResponse
        }
        if (writerConfig.value.hdfsSitePath != "") {
            hdfsConfigType.value = 'file'
        }
        if (writerConfig.value.hadoopConfig) {
            hdfsConfigType.value = 'json'
        }
        console.log('加载的保存配置:', writerConfig.value);
    } catch (error) {
        // 如果没有保存的配置或加载失败，使用默认配置
        console.log('未找到保存的配置，使用默认配置:', error)
    }
}

// 组件挂载时的初始化
onMounted(() => {
    loadSavedConfig()
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
    background-color: #2B2C40;
}

/* 深色模式下输入框的背景色优化 */
.v-theme--dark .v-text-field small dense .v-field__field {
    background-color: #444463 !important;
}

.v-theme--dark .v-select .v-field__field {
    background-color: #444463 !important;
}

.v-theme--dark .v-textarea .v-field__field {
    background-color: #444463 !important;
}

/* 浅色模式下保持合适的背景色 */
.v-theme--light .v-card-text {
    background-color: #FAFAFA;
}

/* JSON 预览区域样式 */
.json-preview textarea {
    font-family: 'Courier New', monospace !important;
    font-size: 12px !important;
    line-height: 1.4 !important;
}

/* 右侧预览区域固定位置 */
.sticky-preview {
    position: sticky;
    top: 20px;
    max-height: calc(100vh - 40px);
    overflow-y: auto;
}

/* 大屏幕下的间距优化 */
@media (min-width: 1280px) {
    .sticky-preview {
        top: 80px;
        max-height: calc(100vh - 100px);
    }
}

/* 确保预览区域的文本区域填满可用空间 */
.sticky-preview .v-textarea {
    height: 100%;
}

.sticky-preview .v-textarea .v-field {
    height: 100%;
}

.sticky-preview .v-textarea textarea {
    height: 100% !important;
    resize: none !important;
}
</style>