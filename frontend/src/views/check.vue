<template>
  <!-- 盘后检查 -->
  <v-card title="采集表记录数异常">
    <v-card-text>
      <v-data-table density="compact" :headers="headers" :items="abnormalRecord"></v-data-table>
    </v-card-text>
  </v-card>
</template>
<script setup lang="ts">
  import { ref, onMounted } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import closeCheckService from '@/service/close-check-service'

  const abnormalRecord = ref([])
  const headers: DataTableHeader[] = [
    { title: '异常级别', key: 'ERR_KIND' },
    { title: '表名', key: 'TBLNAME' },
    { title: 'T-1记录数', key: 'CNT_LTD' },
    { title: 'T日记录数', key: 'CNT_TD' },
    { title: '记录比对差额', key: 'CNT_ERR' }
  ]

  onMounted(() => {
    closeCheckService.fetchAbnormalRecord().then((res) => {
      abnormalRecord.value = res.data
    })
  })
</script>
<route lang="json">
{
  "meta": {
    "title": "盘后检查",
    "icon": "mdi-check",
    "requiresAuth": true
  }
}
</route>
<style></style>
