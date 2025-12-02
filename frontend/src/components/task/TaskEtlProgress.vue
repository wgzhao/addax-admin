<template>
  <v-dialog :model-value="visible" width="500" persistent @update:modelValue="emitClose">
    <v-card>
      <v-card-title class="text-h6 d-flex align-center">
        <v-icon class="mr-2" color="primary">mdi-database-sync</v-icon>
        {{ title }}
      </v-card-title>
      <v-card-text class="pb-4">
        <div class="mb-4">
          <div class="text-body-1 mb-2">{{ message }}</div>
          <v-progress-linear :indeterminate="inProgress" :model-value="inProgress ? undefined : 100" color="primary"
            height="8" rounded></v-progress-linear>
        </div>
        <div v-if="currentProcessingInfo" class="text-caption text-grey-600">
          {{ currentProcessingInfo }}
        </div>
        <div v-if="results.length > 0" class="mt-3">
          <v-divider class="mb-2"></v-divider>
          <div class="text-subtitle-2 mb-2">执行结果：</div>
          <div class="result-container" style="max-height: 200px; overflow-y: auto">
            <v-chip v-for="(result, index) in results" :key="index" :color="result.success ? 'success' : 'error'"
              size="small" class="mr-1 mb-1">
              <v-icon start :icon="result.success ? 'mdi-check' : 'mdi-alert'" />
              {{ result.message }}
            </v-chip>
          </div>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn v-if="!inProgress" color="primary" @click="$emit('close')">关闭</v-btn>
        <v-btn v-else variant="outlined" disabled>
          <v-progress-circular indeterminate size="16" width="2" class="mr-2" />
          执行中...
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { defineEmits } from 'vue'
const props = defineProps<{
  visible: boolean
  title: string
  message: string
  inProgress: boolean
  results: Array<{ success: boolean; message: string }>
  currentProcessingInfo: string
}>()
const emit = defineEmits(['close'])
function emitClose(val: boolean) {
  if (!val) emit('close')
}
</script>

<style scoped></style>
