<template>
  <div class="batch-add-page page-shell">
    <v-card flat>
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">批量新增采集表</div>
            <div class="page-subtitle">按步骤添加并预览目标表名</div>
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card">
      <v-card-text class="ds-card__content">
        <BatchAdd tid="-1" />
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import BatchAdd from '@/components/table/BatchAdd.vue'
import { useRouter } from 'vue-router'
const router = useRouter()
</script>
