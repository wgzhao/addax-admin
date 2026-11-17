<template>
  <div
    class="source-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': isShow || deleteDialog }"
  >
    <PageHeader title="采集源管理" subtitle="统一维护采集源、连接配置与启停状态。">
      <v-btn
        v-if="authStore.canWrite"
        color="primary"
        variant="flat"
        prepend-icon="mdi-plus"
        @click="doAction(-1, 'add')"
      >
        新增采集源
      </v-btn>
    </PageHeader>

    <v-card flat class="ds-card toolbar-card">
      <v-card-text class="ds-card__content">
        <v-row density="comfortable" align="center">
          <v-col cols="12" md="5" lg="4">
            <v-text-field
              v-model="searchValue"
              density="compact"
              variant="outlined"
              label="搜索名称 / 采集代码 / 连接串"
              prepend-inner-icon="mdi-magnify"
              single-line
              hide-details
            />
          </v-col>
          <v-col cols="12" md="7" lg="8" class="d-flex justify-end">
            <v-switch
              v-model="showDisabled"
              density="compact"
              color="primary"
              hide-details
              :label="showDisabled ? '显示全部源' : '仅显示启用源'"
            />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-card ds-table-wrap ds-table-scroll">
      <v-card-text class="ds-card__content">
        <v-skeleton-loader v-if="loading" type="table-row@6" class="ds-skeleton" />
        <template v-else-if="sources.length === 0">
          <EmptyState
            title="暂无采集源"
            description="当前还没有配置任何采集源。点击下方按钮新增采集源后，即可继续维护连接与采集配置。"
            :primary="authStore.canWrite ? { label: '新增采集源', icon: 'mdi-plus' } : undefined"
            @primary="() => doAction(-1, 'add')"
          />
        </template>
        <template v-else>
          <v-data-table
            :items="sources"
            :headers="headers"
            :search="searchValue"
            class="source-table"
            density="comfortable"
            items-per-page="20"
          >
            <template #item.dbType="{ item }">
              <v-chip size="small" color="primary" variant="tonal">{{
                typeLabel(item.dbType)
              }}</v-chip>
            </template>

            <template #item.url="{ item }">
              <span class="ds-cell-primary ds-mono-text ds-white-space-normal table-url">{{
                item.url || '-'
              }}</span>
            </template>

            <template #item.enabled="{ item }">
              <v-chip size="small" :color="item.enabled ? 'success' : 'grey'" variant="tonal">
                {{ item.enabled ? '已启用' : '已禁用' }}
              </v-chip>
            </template>

            <template #item.startAt="{ item }">
              <span class="ds-cell-support">{{ item.startAt || '-' }}</span>
            </template>

            <template #item.calendar="{ item }">
              <span class="ds-cell-support">{{ calendarLabel(item.calendar) }}</span>
            </template>

            <template #item.actions="{ item }">
              <div
                v-if="authStore.canWrite || authStore.canOperateSource(item.id)"
                class="ds-action-inline"
              >
                <v-btn
                  v-if="authStore.canOperateSource(item.id)"
                  variant="text"
                  color="primary"
                  size="small"
                  class="px-2 ds-action-btn-main"
                  @click="doAction(item.id, 'edit')"
                >
                  <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
                </v-btn>
                <v-btn
                  v-if="authStore.canWrite"
                  variant="text"
                  color="info"
                  size="small"
                  class="px-2"
                  @click="cloneSource(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-content-copy</v-icon>克隆
                </v-btn>
                <v-btn
                  v-if="authStore.canWrite"
                  variant="text"
                  color="error"
                  size="small"
                  class="px-2"
                  @click="openDeleteDialog(item.id, item.name)"
                >
                  <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
                </v-btn>
              </div>
            </template>
          </v-data-table>
        </template>
      </v-card-text>
    </v-card>
  </div>

  <v-dialog
    v-model="isShow"
    class="ds-dialog-overlay"
    scrim="rgba(2, 6, 23, 0.62)"
    max-width="1120"
    width="min(1120px, calc(100vw - 32px))"
    scrollable
    :persistent="isSourceDirty"
  >
    <AddDataSource
      ref="addSourceRef"
      v-bind="params"
      @save="handleSave"
      @close-dialog="requestClose"
    />
  </v-dialog>

  <ConfirmDialog
    v-model="discardOpen"
    title="放弃未保存的修改"
    message="采集源表单还有未保存的修改，关闭后这些修改会丢失。"
    confirm-text="放弃修改"
    @confirm="forceClose"
  />

  <v-dialog
    v-model="deleteDialog"
    class="ds-dialog-overlay"
    scrim="rgba(2, 6, 23, 0.62)"
    max-width="480"
  >
    <v-card>
      <v-card-title class="headline">确认删除</v-card-title>
      <v-card-text>您确定要删除采集源 {{ itemNameToDelete }} 吗？</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="deleteDialog = false">取消</v-btn>
        <v-btn color="error" variant="flat" @click="confirmDelete">确认删除</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, useTemplateRef, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import type { DataTableHeader } from 'vuetify';
  import sourceService from '@/service/source-service';
  import AddDataSource from '@/components/source/AddSource.vue';
  import EmptyState from '@/components/EmptyState.vue';
  import PageHeader from '@/components/PageHeader.vue';
  import { notify } from '@/stores/notifier';
  import { useAuthStore } from '@/stores/auth';

  const route = useRoute();
  const router = useRouter();
  const authStore = useAuthStore();

  const sources = ref([]);
  const isShow = ref(false);
  const addSourceRef = useTemplateRef<{ isDirty?: () => boolean }>('addSourceRef');
  const discardOpen = ref(false);
  const isSourceDirty = computed(() => addSourceRef.value?.isDirty?.() ?? false);
  const deleteDialog = ref(false);
  const loading = ref(true);
  const itemIdToDelete = ref<number | null>(null);
  const itemNameToDelete = ref<string | null>(null);
  const showDisabled = ref(false);
  const mode = ref('show');
  const searchValue = ref('');

  const headers = computed<DataTableHeader[]>(() => {
    const base: DataTableHeader[] = [
      // The table uses table-layout: fixed (see the style block), so these pixel
      // widths are exact at every viewport instead of being renegotiated by the
      // browser, which used to wrap the 表数量 header and the action buttons.
      // Widths are measured content size plus a few px of slack.
      // minWidth repeats the width because the table falls back to auto layout
      // below 1366px, where only min-width stops the browser re-slicing columns.
      { title: 'ID', key: 'id', width: 68, minWidth: 68 },
      // Source names are capped at 10 CJK characters; longer ones wrap, so the
      // column never steals space from the connection string.
      { title: '采集源', key: 'name', width: 168, minWidth: 168 },
      { title: '类型', key: 'dbType', width: 136, minWidth: 136 },
      // The only elastic column: a connection string can carry long hostnames
      // and parameters, so it takes everything the other columns leave.
      { title: '连接串', key: 'url', minWidth: 96 },
      { title: '状态', key: 'enabled', width: 96, minWidth: 96 },
      { title: '表数量', key: 'tableCount', width: 100, minWidth: 100 },
      { title: '有效表数量', key: 'validTableCount', width: 132, minWidth: 132 },
      { title: '采集时间', key: 'startAt', width: 120, minWidth: 120 },
      { title: '采集日历', key: 'calendar', width: 112, minWidth: 112 },
    ];
    if (authStore.canWrite || authStore.hasOperateSources) {
      base.push({
        title: '操作',
        key: 'actions',
        align: 'center',
        sortable: false,
        width: 264,
        minWidth: 264,
        // Actions stay reachable if a very narrow viewport forces a scroll.
        fixed: 'end',
      });
    }
    return base;
  });

  const typeLabel = (dbType?: string) => {
    if (!dbType) return 'JDBC';
    if (dbType === 'mongodb') return 'MongoDB';
    if (dbType === 'elasticsearch') return 'Elasticsearch';
    return dbType.toUpperCase();
  };

  // 空值即自然日，不显示成空白以免看起来像没配置
  // Keep every label at three characters so the column is sized by its header,
  // not by one verbose value; the edit dialog spells out the 调休 nuance.
  const calendarLabel = (calendar?: string) => {
    if (calendar === 'WORK_DAY') return '工作日';
    if (calendar === 'TRADE_DAY') return '交易日';
    return '自然日';
  };

  const params = ref({});

  const getSources = () => {
    loading.value = true;
    const loader = showDisabled.value ? sourceService.list() : sourceService.listActiveSources();
    loader
      .then(resp => {
        sources.value = resp;
      })
      .catch(error => {
        console.log(error);
        notify('加载采集源列表失败: ' + error, 'error');
      })
      .finally(() => {
        loading.value = false;
      });
  };

  const doAction = (id: number, ctype) => {
    params.value = {
      mode: ctype,
      sid: id,
    };
    isShow.value = true;
    mode.value = ctype;
  };

  const cloneSource = item => {
    params.value = {
      mode: 'add',
      sid: -1,
      cloneData: {
        ...item,
        id: 0,
      },
    };
    isShow.value = true;
    mode.value = 'add';
  };

  const closeDialog = () => {
    isShow.value = false;
  };

  const requestClose = () => {
    if (addSourceRef.value?.isDirty?.()) {
      discardOpen.value = true;
      return;
    }
    closeDialog();
  };

  const forceClose = () => {
    discardOpen.value = false;
    closeDialog();
  };

  const handleSave = () => {
    getSources();
    closeDialog();
  };

  const openDeleteDialog = (id, name) => {
    itemIdToDelete.value = id;
    itemNameToDelete.value = name;
    deleteDialog.value = true;
  };

  const confirmDelete = () => {
    if (itemIdToDelete.value !== null) {
      deleteSource(itemIdToDelete.value);
    }
    deleteDialog.value = false;
  };

  const deleteSource = id => {
    sourceService
      .delete(id)
      .then(() => {
        notify('删除成功', 'success');
        sources.value = sources.value.filter(item => item.id !== id);
        if (sources.value.length === 0) {
          getSources();
        }
        itemIdToDelete.value = null;
      })
      .catch(error => {
        notify('删除失败: ' + error.message, 'error');
      });
  };

  const openCreateFromQuery = () => {
    const action = Array.isArray(route.query.action) ? route.query.action[0] : route.query.action;
    if (action !== 'create' || !authStore.canWrite) return;

    doAction(-1, 'add');

    const query = { ...route.query };
    delete query.action;
    router.replace({ path: route.path, query });
  };

  onMounted(() => {
    getSources();
    openCreateFromQuery();
  });

  watch(showDisabled, () => {
    getSources();
  });

  watch(
    () => route.query.action,
    () => {
      openCreateFromQuery();
    }
  );
</script>

<route lang="json">
{
  "meta": {
    "title": "采集源管理",
    "icon": "mdi-database-cog",
    "requiresAuth": true,
    "navGroup": "collectConfig",
    "navOrder": 10
  }
}
</route>

<style scoped>
  /* Fixed layout honors each column's pixel width verbatim; in auto layout the
     browser re-slices the widths whenever content or viewport changes. The
     breakpoint is where the fixed columns plus a readable connection string
     still fit; below it auto layout plus the columns' min-widths takes over and
     the table scrolls horizontally with the action column pinned. */
  @media (min-width: 1366px) {
    .source-table :deep(table) {
      table-layout: fixed;
    }
  }

  .table-url {
    display: inline-block;
    max-width: 100%;
  }
</style>
