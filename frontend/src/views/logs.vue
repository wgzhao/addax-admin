<template>
  <div class="p-4">
    <h2>日志管理</h2>
    <div class="mb-4">
      <v-sheet elevation="4">
        <v-tabs color="primary">
          <v-tab value="addax_log">采集日志表</v-tab>
          <v-tab value="etl_jour">采集流水表</v-tab>
          <v-tab value="etl_statistic">统计表</v-tab>
        </v-tabs>

        <v-divider></v-divider>

        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="addax_log">
            <v-sheet class="pa-5" color="primary">
              <AddaxLogList table="addax_log" />
            </v-sheet>
          </v-tabs-window-item>
          <v-tabs-window-item value="etl_jour">
            <v-sheet class="pa-5" color="secondary">Two</v-sheet>
          </v-tabs-window-item>
          <v-tabs-window-item value="etl_statistic">
            <v-sheet class="pa-5" color="brown">Three</v-sheet>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-sheet>

      <!-- <v-text-field v-model="sourceTid" label="采集表ID (sourceTid 可选)" dense outlined />
      <v-btn color="primary" @click="openCleanup">清理数据</v-btn> -->
    </div>

    <!-- <LogTable :table="table" :filters="filters" ref="logTableRef" /> -->

    <!-- <CleanupDialog ref="cleanupDialog" :table="table" @done="onCleanupDone" /> -->
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AddaxLogList from '@/components/logs/AddaxLogList.vue';
const tab = ref('addax_log')
</script>

