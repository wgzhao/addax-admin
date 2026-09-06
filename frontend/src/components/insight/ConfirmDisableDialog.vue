<template>
  <v-dialog v-model="open" max-width="520">
    <v-card>
      <v-card-title class="text-subtitle-1 font-weight-medium">确认禁用采集</v-card-title>
      <v-card-text>
        <v-alert type="warning" variant="tonal" border="start" class="mb-4">
          禁用后该表采集状态将置为 X，后续采集将被跳过。
        </v-alert>
        <div>目标表：{{ item?.source_db }}.{{ item?.source_table }}</div>
        <div>表 ID：{{ item?.tid }}</div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" :disabled="loading" @click="open = false"> 取消 </v-btn>
        <v-btn color="error" variant="flat" :loading="loading" @click="confirmDisable">
          确认禁用
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import tableService from '@/service/table-service';

  const props = defineProps<{ item: any }>();
  const open = defineModel<boolean>({ default: false });
  const emit = defineEmits(['confirmed']);

  const loading = ref(false);

  const confirmDisable = async () => {
    if (!props.item?.tid) return;
    loading.value = true;
    try {
      await tableService.batchUpdateStatus({ tids: [props.item.tid], status: 'X' });
      open.value = false;
      emit('confirmed');
    } catch (error) {
      console.error('Failed to disable table:', error);
    } finally {
      loading.value = false;
    }
  };
</script>
