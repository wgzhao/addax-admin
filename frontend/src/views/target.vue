<template>
  <v-card flat title="目标端管理">
    <template #text>
      <v-row align="center" justify="space-between">
        <v-col cols="auto">
          <v-btn color="primary" prepend-icon="mdi-plus" @click="openCreate">新增目标端</v-btn>
        </v-col>
        <v-col cols="auto">
          <v-switch
            v-model="enabledOnly"
            inset
            density="compact"
            color="primary"
            label="仅显示启用"
            @update:model-value="loadTargets"
          />
        </v-col>
      </v-row>
    </template>

    <v-data-table :headers="headers" :items="targets" item-value="id" density="compact">
      <template #item.enabled="{ item }">
        <v-chip size="small" :color="item.enabled ? 'success' : 'default'">
          {{ item.enabled ? '启用' : '停用' }}
        </v-chip>
      </template>
      <template #item.isDefault="{ item }">
        <v-chip size="small" :color="item.isDefault ? 'primary' : 'default'">
          {{ item.isDefault ? '是' : '否' }}
        </v-chip>
      </template>
      <template #item.action="{ item }">
        <v-btn size="small" variant="text" color="primary" @click="openEdit(item)">编辑</v-btn>
        <v-btn size="small" variant="text" color="info" @click="testConnect(item)">测试连接</v-btn>
        <v-btn size="small" variant="text" color="error" @click="remove(item)">删除</v-btn>
      </template>
    </v-data-table>
  </v-card>

  <v-dialog v-model="dialogVisible" max-width="760" persistent>
    <v-card>
      <v-card-title>{{ form.id ? '编辑目标端' : '新增目标端' }}</v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="6">
            <v-text-field v-model="form.code" label="编码" variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="6">
            <v-text-field v-model="form.name" label="名称" variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="6">
            <v-select
              v-model="form.targetType"
              :items="targetTypes"
              label="目标端类型"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="6">
            <v-select
              v-model="form.writerTemplateKey"
              label="Writer模板键"
              :items="writerTemplateOptions"
              item-title="label"
              item-value="value"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="6">
            <v-text-field
              v-model="connectForm.url"
              label="JDBC URL"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="connectForm.username"
              label="连接用户名"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field
              v-model="connectForm.password"
              label="连接密码"
              type="password"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="6" v-if="form.targetType === 'HDFS'">
            <v-text-field
              v-model="connectForm.driverClassName"
              label="驱动类名"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12" md="6" v-if="form.targetType === 'HDFS'">
            <v-text-field
              v-model="connectForm.driverPath"
              label="驱动路径"
              variant="outlined"
              density="compact"
            />
          </v-col>
          <v-col cols="12">
            <v-textarea
              :model-value="form.connectConfig"
              label="连接配置(JSON预览)"
              variant="outlined"
              density="compact"
              rows="4"
              readonly
            />
          </v-col>
          <v-col cols="12">
            <v-text-field v-model="form.remark" label="备注" variant="outlined" density="compact" />
          </v-col>
          <v-col cols="12" md="6">
            <v-switch v-model="form.enabled" inset density="compact" label="启用" color="primary" />
          </v-col>
          <v-col cols="12" md="6">
            <v-switch v-model="form.isDefault" inset density="compact" label="设为默认" color="primary" />
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-btn variant="text" color="info" @click="testConnectPayload">测试连接</v-btn>
        <v-spacer />
        <v-btn variant="text" @click="dialogVisible = false">取消</v-btn>
        <v-btn color="primary" @click="save">保存</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { notify } from '@/stores/notifier'
import targetService from '@/service/target-service'
import dictService from '@/service/dict-service'
import type { EtlTarget } from '@/types/database'

const targets = ref<EtlTarget[]>([])
const enabledOnly = ref(true)
const dialogVisible = ref(false)
const targetTypes = ['HDFS', 'MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER', 'CLICKHOUSE']
const writerTemplateOptions = ref<{ label: string; value: string }[]>([])

const headers = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '编码', key: 'code' },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'targetType' },
  { title: '模板键', key: 'writerTemplateKey' },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '默认', key: 'isDefault', width: 90 },
  { title: '操作', key: 'action', sortable: false, width: 220 }
]

const blankForm = (): EtlTarget => ({
  code: '',
  name: '',
  targetType: 'HDFS',
  connectConfig: '',
  writerTemplateKey: '',
  enabled: true,
  isDefault: false,
  remark: ''
})

const form = ref<EtlTarget>(blankForm())
const connectForm = ref({
  url: '',
  username: '',
  password: '',
  driverClassName: '',
  driverPath: ''
})

const syncConnectConfig = () => {
  const payload: Record<string, string> = {
    url: connectForm.value.url || '',
    username: connectForm.value.username || '',
    password: connectForm.value.password || ''
  }
  if (form.value.targetType === 'HDFS') {
    payload.driverClassName = connectForm.value.driverClassName || ''
    payload.driverPath = connectForm.value.driverPath || ''
  }
  form.value.connectConfig = JSON.stringify(payload)
}

watch(connectForm, syncConnectConfig, { deep: true })
watch(() => form.value.targetType, (t) => {
  if (!form.value.writerTemplateKey) {
    form.value.writerTemplateKey = t === 'HDFS' ? 'wH' : 'wR'
  }
  syncConnectConfig()
})

const parseConnectConfig = (target: EtlTarget) => {
  connectForm.value = { url: '', username: '', password: '', driverClassName: '', driverPath: '' }
  if (!target.connectConfig) {
    syncConnectConfig()
    return
  }
  try {
    const obj = JSON.parse(target.connectConfig)
    connectForm.value.url = obj.url || ''
    connectForm.value.username = obj.username || ''
    connectForm.value.password = obj.password || ''
    connectForm.value.driverClassName = obj.driverClassName || ''
    connectForm.value.driverPath = obj.driverPath || ''
  } catch {
    notify('connectConfig 不是有效 JSON，已重置连接表单', 'warning')
  }
  syncConnectConfig()
}

const loadTargets = async () => {
  try {
    targets.value = await targetService.list(enabledOnly.value)
  } catch (e: any) {
    notify(`加载目标端失败: ${e.message || e}`, 'error')
  }
}

const openCreate = () => {
  form.value = blankForm()
  form.value.writerTemplateKey = form.value.targetType === 'HDFS' ? 'wH' : 'wR'
  parseConnectConfig(form.value)
  dialogVisible.value = true
}

const openEdit = (item: EtlTarget) => {
  form.value = { ...item }
  parseConnectConfig(form.value)
  dialogVisible.value = true
}

const save = async () => {
  syncConnectConfig()
  try {
    if (form.value.id) {
      await targetService.update(form.value.id, form.value)
    } else {
      await targetService.create(form.value)
    }
    notify('保存成功', 'success')
    dialogVisible.value = false
    await loadTargets()
  } catch (e: any) {
    notify(`保存失败: ${e.message || e}`, 'error')
  }
}

const remove = async (item: EtlTarget) => {
  if (!item.id) return
  if (!window.confirm(`确认删除目标端 ${item.name} 吗？`)) return
  try {
    await targetService.remove(item.id)
    notify('删除成功', 'success')
    await loadTargets()
  } catch (e: any) {
    notify(`删除失败: ${e.message || e}`, 'error')
  }
}

const testConnect = async (item: EtlTarget) => {
  if (!item.id) return
  try {
    await targetService.testConnect(item.id)
    notify('连接测试成功', 'success')
  } catch (e: any) {
    notify(`连接测试失败: ${e.message || e}`, 'error')
  }
}

const testConnectPayload = async () => {
  syncConnectConfig()
  try {
    await targetService.testConnectPayload(form.value)
    notify('连接测试成功', 'success')
  } catch (e: any) {
    notify(`连接测试失败: ${e.message || e}`, 'error')
  }
}

const loadWriterTemplateOptions = async () => {
  try {
    const items = await dictService.listSysItems(5001)
    writerTemplateOptions.value = items
      .filter((i) => i.itemKey && i.itemKey.startsWith('w'))
      .map((i) => ({
        label: `${i.itemKey} ${i.remark ? `- ${i.remark}` : ''}`,
        value: i.itemKey
      }))
  } catch (e: any) {
    notify(`加载 Writer 模板键失败: ${e.message || e}`, 'error')
  }
}

onMounted(async () => {
  await Promise.all([loadTargets(), loadWriterTemplateOptions()])
})
</script>

<route lang="json">
{
  "meta": {
    "title": "目标端管理",
    "icon": "mdi-database-cog",
    "requiresAuth": true
  }
}
</route>
