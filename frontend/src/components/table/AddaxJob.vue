<template>
  <v-card class="mx-auto" max-width="100%">
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
            <v-btn size="small" variant="outlined" class="mr-2" @click="copyToClipboard"
              prepend-icon="mdi-content-copy">
              复制
            </v-btn>
          </div>
        </div>

        <v-card variant="outlined" class="code-card">
          <pre><code>{{ jobContent }}</code></pre>
          <!-- <pre><code
            ref="codeElement"
            class="language-json hljs"
            v-html="highlightedCode"
          ></code></pre> -->
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
import { ref, computed, onMounted } from 'vue'
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
const error = ref('')
const codeElement = ref<HTMLElement | null>(null)

// 计算属性：高亮后的代码
const highlightedCode = computed(() => {
  if (!jobContent.value) return ''

  try {
    // 尝试格式化 JSON
    const formatted = JSON.stringify(JSON.parse(jobContent.value), null, 2)
    // 应用语法高亮
    return hljs.highlight(formatted, { language: 'json' }).value
  } catch (e) {
    // 如果不是有效的 JSON，直接高亮原始内容
    return hljs.highlight(jobContent.value, { language: 'json' }).value
  }
})


// 复制到剪贴板，内容为 jobContent.value 字符串
async function copyToClipboard() {
  try {
    let text = jobContent.value
    if (typeof text === 'object') {
      text = JSON.stringify(text, null, 2)
    }
    await navigator.clipboard.writeText(text)
    notify('配置已复制到剪贴板', 'success', 2000, 'mdi-check')
  } catch (err) {
    notify('复制失败，请手动复制', 'error', 3000, 'mdi-alert')
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
    error.value = err || '获取配置失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  reloadJob()
})
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
