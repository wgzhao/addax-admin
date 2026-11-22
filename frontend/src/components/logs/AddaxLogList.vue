<template>
  <AppDataTable
    mode="server"
    title="采集日志表"
    :items-per-page="currPageSize"
    :total="totalItems"
    item-value="id"
    :items="items"
    :headers="headers"
    scrollable
    empty-title="暂无采集日志"
    empty-description="该采集表还没有生成日志，或已被清理。"
    @update:options="loadItems"
  >
    <template #header>
      <div v-if="authStore.isAdmin" class="log-cleanup">
        <v-text-field
          v-model="selectedCleanupDate"
          density="compact"
          variant="outlined"
          placeholder="1989-09-12"
          label="清理早于该日期"
          clearable
          hide-details
          class="log-cleanup__date"
        />
        <div class="log-cleanup__quick">
          <v-btn size="small" variant="tonal" @click="quickSelect(1)">保留1个月</v-btn>
          <v-btn size="small" variant="tonal" @click="quickSelect(3)">保留3个月</v-btn>
          <v-btn size="small" variant="tonal" @click="quickSelect(6)">保留半年</v-btn>
          <v-btn size="small" variant="tonal" @click="quickSelect(12)">保留1年</v-btn>
        </div>
        <v-spacer />
        <v-btn size="small" color="error" variant="flat" @click="doCleanup">确认清理</v-btn>
      </div>
    </template>

    <template #item.log="{ item }">
      <pre class="truncated-log" :title="item.log" @click="openLog(item)">{{
        previewText(item.log)
      }}</pre>
    </template>
  </AppDataTable>
  <div>
    <v-dialog v-model="logDialog" max-width="100%">
      <v-card>
        <v-card-title>日志内容</v-card-title>
        <v-card-text
          style="white-space: pre-wrap; word-break: break-word; max-height: 60vh; overflow: auto"
        >
          <pre class="full-log">{{ selectedLog }}</pre>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="logDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-if="authStore.isAdmin" v-model="cleanupDialog" max-width="520">
      <v-card>
        <v-card-title>清理日志</v-card-title>
        <v-card-text>
          <div style="margin-bottom: 8px">请选择要删除早于指定日期的日志（包括该日期之前）：</div>
          <v-text-field
            v-model="selectedCleanupDate"
            label="输入日期"
            placeholder="1989-09-12"
            readonly
            clearable
            @click:clear="selectedCleanupDate = ''"
          />

          <div style="margin-top: 12px">快捷选项：</div>
          <div style="display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap">
            <v-btn small outlined @click="quickSelect(1)">保留1个月</v-btn>
            <v-btn small outlined @click="quickSelect(3)">保留3个月</v-btn>
            <v-btn small outlined @click="quickSelect(6)">保留半年</v-btn>
            <v-btn small outlined @click="quickSelect(12)">保留1年</v-btn>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="cleanupDialog = false">取消</v-btn>
          <v-btn color="error" @click="doCleanup">确认清理</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import AppDataTable from '@/components/common/AppDataTable.vue';
  import logService from '@/service/log-service';
  import { ref, onMounted } from 'vue';
  import { notify } from '@/stores/notifier';
  import { useAuthStore } from '@/stores/auth';

  const authStore = useAuthStore();

  const props = defineProps({ table: String, filters: Object });
  const items = ref([]);
  const currPageSize = ref(10);
  const totalItems = ref(0);
  const loading = ref(false);
  const headers = ref([
    { title: '表 ID', key: 'tid', width: '5%' },
    { title: '采集时间', key: 'runAt', width: '35%' },
    { title: '业务日期', key: 'runDate', width: '12%' },
    { title: '消息', key: 'log', width: '40%' },
  ]);

  const loadItems = ({ page, itemsPerPage }) => {
    loading.value = true;
    currPageSize.value = itemsPerPage;

    // v-data-table-server page is 1-based, while backend API is 0-based.
    logService
      .getAddaxLogList(page - 1, itemsPerPage)
      .then(res => {
        items.value = res.content;
        totalItems.value = res.totalElements;
        loading.value = false;
      })
      .catch(error => {
        loading.value = false;
      });
  };

  onMounted(() => {
    loadItems({ page: 1, itemsPerPage: currPageSize.value });
  });

  const logDialog = ref(false);
  const selectedLog = ref('');

  // cleanup dialog state
  const cleanupDialog = ref(false);
  const selectedCleanupDate = ref('');

  function quickSelect(months: number) {
    const d = new Date();
    d.setMonth(d.getMonth() - months);
    selectedCleanupDate.value = formatDate(d);
  }

  function formatDate(d: Date) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  function doCleanup() {
    if (!selectedCleanupDate.value) {
      notify('请选择一个日期', 'warning');
      return;
    }

    logService
      .cleanupAddaxLog(selectedCleanupDate.value)
      .then(() => {
        notify('清理任务已提交，后台开始执行', 'success');
        cleanupDialog.value = false;
      })
      .catch(e => {
        console.error(e);
        notify('提交清理任务失败', 'error');
      });
  }

  function openLog(item: any) {
    selectedLog.value = item.log || '';
    logDialog.value = true;
  }

  function previewText(text: any, lines = 10) {
    if (!text) return '';
    const str = String(text);
    const parts = str.split(/\r?\n/);
    const take = parts.length <= lines ? parts : parts.slice(-lines);
    const joined = take.join('\n');
    return parts.length <= lines ? joined : '...\n' + joined;
  }
</script>

<style scoped>
  .log-cleanup {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 14px;
  }

  .log-cleanup__date {
    max-width: 220px;
  }

  .log-cleanup__quick {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .truncated-log {
    display: block;
    max-width: 70%;
    max-height: 12em;
    white-space: pre-wrap;
    overflow: hidden;
    cursor: pointer;
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, 'Roboto Mono', 'Helvetica Neue', monospace;
    font-size: 13px;
  }

  .full-log {
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, 'Roboto Mono', 'Helvetica Neue', monospace;
    font-size: 13px;
    white-space: pre-wrap;
  }
</style>
