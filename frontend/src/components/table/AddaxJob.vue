<template>
  <v-card class="mx-auto" max-width="1200" min-width="800">
    <v-card-title class="d-flex align-center">
      <v-icon left class="mr-2">mdi-code-json</v-icon>
      Addax 采集任务配置
    </v-card-title>

    <v-card-text>
      <div v-if="loading" class="d-flex justify-center">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
      </div>

      <div v-else-if="error" class="text-error">
        <v-alert type="error" class="mb-4">
          {{ error }}
        </v-alert>
      </div>

      <div v-else-if="jobContent" class="json-container">
        <div class="d-flex justify-space-between align-center mb-3">
          <v-chip color="primary" size="small">
            <v-icon left size="small">mdi-file-code</v-icon>
            JSON 配置
          </v-chip>
          <div>
            <template v-if="!editing">
              <v-btn size="small" variant="outlined" class="mr-2" @click="copyToClipboard" prepend-icon="mdi-content-copy">
                复制
              </v-btn>
              <v-btn size="small" variant="tonal" class="mr-2" @click="startEdit" prepend-icon="mdi-pencil">
                编辑
              </v-btn>
            </template>
            <template v-else>
              <v-btn size="small" variant="text" class="mr-2" @click="cancelEdit">取消</v-btn>
              <v-btn size="small" color="primary" :disabled="!dirty" @click="saveEdit">保存</v-btn>
            </template>
          </div>
        </div>

        <v-card variant="outlined" class="code-card">
          <div v-if="!editing">
            <pre><code class="language-json hljs" v-html="highlightedCode"></code></pre>
          </div>
          <div v-else>
            <v-textarea
              v-model="editContent"
              auto-grow
              rows="10"
              class="mono-text"
              style="width:100%"
              @input="onEditInput"
            />
          </div>
        </v-card>
      </div>

      <div v-else class="text-center text-medium-emphasis">
        <v-icon size="48" class="mb-2">mdi-file-document-outline</v-icon>
        <p>暂无配置内容</p>
      </div>
    </v-card-text>
  </v-card>
</template>
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import hljs from 'highlight.js/lib/core'
import json from 'highlight.js/lib/languages/json'
// 可以选择以下主题之一：
import 'highlight.js/styles/atom-one-dark.css' // 深色主题，适合暗色界面
// import 'highlight.js/styles/github.css'; // GitHub 风格
// import 'highlight.js/styles/vs.css'; // Visual Studio 风格
// import 'highlight.js/styles/default.css'; // 默认主题
import tableService from '@/service/table-service'
import { useNotifier } from '@/stores/notifier'

// 注册 JSON 语言支持
hljs.registerLanguage('json', json)

const { notify } = useNotifier()

const props = defineProps({
  tid: Number
})

const jobContent = ref('')
const loading = ref(false)
const error = ref('');
const editing = ref(false)
const editContent = ref('')
const dirty = ref(false)

// 高亮后的 HTML 内容
const highlightedCode = computed(() => {
  if (!jobContent.value) return ''
  let text = jobContent.value as unknown as string
  try {
    // 如果后端返回的是对象或可解析的 JSON 字符串，统一美化为字符串
    if (typeof jobContent.value === 'object') {
      text = JSON.stringify(jobContent.value, null, 2)
    } else {
      // 尝试解析以确保缩进一致
      const parsed = JSON.parse(text)
      text = JSON.stringify(parsed, null, 2)
    }
  } catch (_) {
    // 非 JSON 字符串则保持原样
  }
  return hljs.highlight(text, { language: 'json' }).value
})


// 复制到剪贴板，内容为 jobContent.value 字符串
async function copyToClipboard() {
  try {
    let text = jobContent.value
    if (typeof text === 'object') {
      text = JSON.stringify(text, null, 2)
    }
    await navigator.clipboard.writeText(text)
    notify('配置已复制到剪贴板', 'success', 2000, undefined, 'mdi-check')
  } catch (err) {
    notify('复制失败，请手动复制', 'error', 3000, undefined, 'mdi-alert')
  }
}

// 单独封装 job 刷新逻辑
async function reloadJob() {
  if (!props.tid) return
  loading.value = true
  error.value = ''
  try {
    const res = await tableService.fetchAddaxJob(props.tid)
    jobContent.value = res
    if (!jobContent.value) {
      error.value = '获取到的配置内容为空'
    }
  } catch (err) {
    error.value = (err as Error).message || '获取配置失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  reloadJob()
})

function startEdit() {
  editing.value = true
  dirty.value = false
  // ensure editContent is a prettified string
  if (typeof jobContent.value === 'object') {
    editContent.value = JSON.stringify(jobContent.value, null, 2)
  } else {
    try {
      const parsed = JSON.parse(String(jobContent.value))
      editContent.value = JSON.stringify(parsed, null, 2)
    } catch (_err) {
      editContent.value = String(jobContent.value || '')
    }
  }
}

function cancelEdit() {
  editing.value = false
  dirty.value = false
}

function onEditInput() {
  dirty.value = true
}

async function saveEdit() {
  if (!props.tid) return
  try {
    loading.value = true
    // validate JSON where possible
    try {
      JSON.parse(editContent.value)
    } catch (err) {
      notify('JSON 格式错误，请修正后保存', 'error')
      return
    }
    await tableService.updateAddaxJob(props.tid, editContent.value)
    jobContent.value = editContent.value
    editing.value = false
    dirty.value = false
    notify('保存成功', 'success')
  } catch (err) {
    const msg = (err as Error).message || String(err)
    notify('保存失败: ' + msg, 'error')
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.json-container {
  width: 100%;
}

.code-card {
  background-color: #ffffff;
  border: 1px solid #d1d9e0;
  border-radius: 8px;
  overflow: hidden;
}

.code-card pre {
  margin: 0;
  padding: 16px;
  background-color: transparent;
  overflow-x: auto;
  font-family:
    'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.mono-text textarea {
  width: 100% !important;
  min-height: 300px !important;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
}

.code-card code {
  background-color: transparent;
  padding: 0;
  border-radius: 0;
  font-family: inherit;
  font-size: inherit;
}

/* 深色主题适配 */
.v-theme--dark .code-card {
  background-color: #1e1e1e;
  border: 1px solid #3e3e3e;
}

/* 滚动条样式 */
.code-card pre::-webkit-scrollbar {
  height: 8px;
}

.code-card pre::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 4px;
}

.code-card pre::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 4px;
}

.code-card pre::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.5);
}

.v-theme--dark .code-card pre::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.1);
}

.v-theme--dark .code-card pre::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.3);
}

.v-theme--dark .code-card pre::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.5);
}
</style>
