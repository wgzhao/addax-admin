<template>
  <v-dialog v-model="open" max-width="640">
    <v-card>
      <v-card-title class="text-h6">请确认：强制更新全部表信息</v-card-title>
      <v-card-text>
        <div>
          警告：该操作会对所有采集表执行强制结构更新，可能耗时很久并产生破坏性变更（例如覆盖现有表结构）。请确保您已备份数据并明确需要执行此操作。
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" :disabled="loading" @click="open = false">取消</v-btn>
        <v-btn color="error" :loading="loading" @click="confirmUpdateSchemaAll">确认并执行</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import tableService from '@/service/table-service';
  import { notify } from '@/stores/notifier';

  const open = defineModel<boolean>({ default: false });
  const loading = ref(false);

  const updateSchemaAll = async () => {
    try {
      const res = await tableService.updateSchema({ mode: 'all' });
      notify(res || '强制更新全部表信息任务已启动', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify('强制更新失败: ' + msg, 'error');
    }
  };

  const confirmUpdateSchemaAll = async () => {
    loading.value = true;
    try {
      await updateSchemaAll();
      open.value = false;
    } finally {
      loading.value = false;
    }
  };
</script>
