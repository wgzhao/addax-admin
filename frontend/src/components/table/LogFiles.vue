<template>
  <!-- 调度和命令日志 -->
  <!-- <dialog-comp v-mode="dialog" title="调度/命令日志"> -->
  <v-card prepend-icon="mdi-file-document-outline" title="调度/命令日志" class="log-card" density="comfortable">
    <v-card-text class="log-body">
      <v-row dense>
        <v-col cols="12">
          <v-sheet class="form-section" rounded="lg" border>
            <div class="section-header">
              <v-icon size="18" color="primary">mdi-format-list-bulleted</v-icon>
              <span>日志列表</span>
              <v-spacer />
              <span class="chip-hint">共 {{ logList.length }} 条</span>
            </div>
            <v-divider />
            <div class="chips-wrap">
              <v-chip v-for="item in logList" :key="item.id" :color="selectedLogId === item.id ? 'primary' : 'default'"
                :variant="selectedLogId === item.id ? 'elevated' : 'outlined'" clickable size="small"
                @click="getContent(item.id)">
                <v-icon start size="small">mdi-file-document-outline</v-icon>
                {{ item.runAt }}
              </v-chip>
            </div>
          </v-sheet>
        </v-col>
      </v-row>
      <v-row dense>
        <!-- 展示日志内容-->
        <v-col cols="12">
          <v-sheet class="form-section" rounded="lg" border>
            <div class="section-header">
              <v-icon size="18" color="primary">mdi-text-box-outline</v-icon>
              <span>日志内容</span>
              <v-spacer />
              <div class="header-actions">
                <v-btn
                  size="small"
                  variant="tonal"
                  color="primary"
                  :disabled="!fContent"
                  @click="copyLog"
                  prepend-icon="mdi-content-copy"
                >
                  复制
                </v-btn>
                <v-btn
                  size="small"
                  variant="tonal"
                  color="secondary"
                  :disabled="!fContent"
                  @click="downloadLog"
                  prepend-icon="mdi-download"
                >
                  下载
                </v-btn>
              </div>
            </div>
            <v-divider></v-divider>
            <div class="log-content-wrap">
              <!-- 空状态 (没有选择任何日志) -->
              <div v-if="!selectedLogId" class="empty-state">
                <v-icon size="64" color="grey-lighten-2" class="mb-4">mdi-file-document-outline</v-icon>
                <div class="text-h6 text-grey-darken-1 mb-2">No log selected</div>
                <div class="text-body-2 text-grey">Please select a log file from above to view its content.</div>
              </div>
              <!-- 日志内容区域 -->
              <div v-else class="position-relative">
                <!-- 加载状态覆盖层 -->
                <v-overlay v-model="loading" contained class="d-flex align-center justify-center">
                  <div class="text-center">
                    <v-progress-circular indeterminate color="primary" class="mb-4"></v-progress-circular>
                    <div class="text-body-1">Loading log content...</div>
                  </div>
                </v-overlay>
                <!-- 日志内容 -->
                <div v-if="fContent" class="text-body-2 log-content" v-html="fContent"></div>
                <!-- 加载失败状态 -->
                <div v-else-if="!loading" class="empty-state">
                  <v-icon size="48" color="error" class="mb-4">mdi-alert-circle</v-icon>
                  <div class="text-h6 text-error mb-2">Failed to load log</div>
                  <div class="text-body-2 text-grey">Please try selecting the log file again.</div>
                </div>
              </div>
            </div>
          </v-sheet>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { notify } from "@/stores/notifier";
import logService from "@/service/log-service";

// const dialog = defineModel({ required: true, default: true });
const props = defineProps({ tid: String });

const fContent = ref();

const selectedLogId = ref<number | null>(null);
const loading = ref(false);

const logList = ref([]);

const emit = defineEmits(["closeDialog"]);


const getContent = (id: number) => {
  // 如果点击的是当前已选中的日志，不需要重新加载
  if (selectedLogId.value === id && fContent.value) {
    return;
  }

  selectedLogId.value = id;
  loading.value = true;
  // 不要立即清空内容，避免闪烁

  logService.getContent(id)
    .then((res: null | string) => {
      // 只有在请求成功后才更新内容
      fContent.value = res;
    })
    .catch((error) => {
      console.error('Failed to load log content:', error);
      // 加载失败时清空内容并显示错误信息
      fContent.value = null;
    })
    .finally(() => {
      loading.value = false;
    });
};

onMounted(() => {
  logService.getLogFiles(props.tid).then(res => {
    logList.value = res.data;
    // 如果有日志文件，自动选择并加载最新的一条（第一条）
    if (res.data && res.data.length > 0) {
      const latestLog = res.data[0];
      getContent(latestLog.id);
    }
  });
});

const copyLog = async () => {
  if (!fContent.value) return;
  try {
    await navigator.clipboard.writeText(String(fContent.value));
    notify('日志已复制到剪贴板', 'success');
  } catch (err) {
    notify('复制失败，请手动复制', 'error');
  }
};

const downloadLog = () => {
  if (!fContent.value) return;
  const fileName = selectedLogId.value ? `log-${selectedLogId.value}.txt` : 'log.txt';
  const blob = new Blob([String(fContent.value)], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  notify('日志已开始下载', 'success');
};
</script>
<style scoped>
.log-card {
  background: rgb(var(--v-theme-surface));
}

.log-body {
  background: transparent;
}

.form-section {
  background: rgb(var(--v-theme-surface-variant));
  border-color: rgba(var(--v-theme-on-surface), 0.08);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.form-section:hover {
  border-color: rgba(var(--v-theme-primary), 0.2);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-weight: 600;
  color: rgb(var(--v-theme-on-surface));
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.chips-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px 14px;
}

.chip-hint {
  font-size: 12px;
  color: rgba(var(--v-theme-on-surface), 0.6);
}

.log-content-wrap {
  padding: 12px 14px 14px;
}

.log-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  border-radius: 10px;
  max-height: 60vh;
  overflow-y: auto;
  font-size: 0.875rem;
  min-height: 200px;
  /* 确保有足够的高度显示加载状态 */
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
}

.log-content::-webkit-scrollbar {
  width: 8px;
}

.log-content::-webkit-scrollbar-track {
  background: rgba(var(--v-theme-on-surface), 0.1);
  border-radius: 4px;
}

.log-content::-webkit-scrollbar-thumb {
  background: rgba(var(--v-theme-on-surface), 0.3);
  border-radius: 4px;
}

.log-content::-webkit-scrollbar-thumb:hover {
  background: rgba(var(--v-theme-on-surface), 0.5);
}

/* 确保相对定位的容器有最小高度 */
.position-relative {
  min-height: 200px;
}

.empty-state {
  text-align: center;
  padding: 32px 8px;
}

/* 改进空状态的图标动画 */
.v-icon.mdi-file-document-outline {
  transition: transform 0.3s ease;
}

.v-icon.mdi-file-document-outline:hover {
  transform: scale(1.1);
}

/* 响应式改进 */
@media (max-width: 600px) {
  .log-content {
    max-height: 50vh;
    font-size: 0.75rem;
  }

  .v-card-title {
    padding: 12px 16px;
  }

  .position-relative {
    min-height: 150px;
  }
}

/* chip样式优化 */
.v-chip {
  transition: all 0.2s ease;
}

.v-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 选中状态的chip样式 */
.v-chip[aria-pressed="true"] {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
