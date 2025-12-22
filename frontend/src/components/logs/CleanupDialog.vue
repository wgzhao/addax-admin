<template>
  <v-dialog v-model="visible" max-width="600">
    <v-card>
      <v-card-title>清理数据 - {{ table }}</v-card-title>
      <v-card-text>
        <v-select v-model="sourceTid" :items="sources" item-text="text" item-value="value" label="采集表ID (sourceTid)" />
        <v-text-field v-model="before" label="清理早于时间 (ISO 格式)" />
        <v-checkbox v-model="dryRun" label="仅预估 (dryRun)" />
        <v-checkbox v-model="async" label="异步执行" />
        <v-text-field v-model="batchSize" label="批次大小" type="number" />
        <v-text-field v-model="maxRows" label="最大删除数量" type="number" />
        <div v-if="estimated !== null">预估将删除: {{ estimated }}</div>
      </v-card-text>
      <v-card-actions>
        <v-btn text @click="onClose">取消</v-btn>
        <v-btn color="primary" @click="onSubmit">确认</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import LogService from '@/service/log-service'

const props = defineProps({ table: String })
const emit = defineEmits(['done'])
const visible = ref(false)
const sourceTid = ref('')
const before = ref('')
const dryRun = ref(true)
const async = ref(false)
const batchSize = ref(1000)
const maxRows = ref(100000)
const estimated = ref<number | null>(null)
const sources = ref<Array<{ value: number | string; text: string }>>([])

async function open() {
  visible.value = true
  estimated.value = null
  // load available sources for this table to help the user choose
  try {
    const res: any = await LogService.getSources(props.table)
    const data = res.data ?? res
    const list = data.items ?? data ?? []
    sources.value = list.map((s: any) => {
      const value = s.tid ?? s.sourceTid ?? s
      const text = s.name ?? String(value)
      return { value, text }
    })
  }
  catch (e) {
    sources.value = []
  }
}

function onClose() {
  visible.value = false
}

async function onSubmit() {
  const payload: any = { table: props.table, dryRun: dryRun.value, async: async.value, batchSize: Number(batchSize.value), maxRows: Number(maxRows.value) }
  if (sourceTid.value) payload.sourceTid = Number(sourceTid.value)
  if (before.value) payload.before = before.value
  if (dryRun.value) {
    const res: any = await LogService.cleanup(payload)
    const result = res.data ?? res
    estimated.value = result.estimated
  }
  else {
    await LogService.cleanup(payload)
    // emit done for parent components
    emit('done')
    // @ts-ignore
    ;(window as any).$notify && (window as any).$notify('删除任务已提交')
    visible.value = false
    // emit custom event for legacy listeners
    // @ts-ignore
    ;(window as any).dispatchEvent(new CustomEvent('logs-cleanup-done'))
  }
}

defineExpose({ open })
</script>
