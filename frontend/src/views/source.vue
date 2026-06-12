<template>
  <div
    class="source-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': isShow || deleteDialog }"
  >
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">数据源管理</div>
            <div class="page-subtitle">统一维护采集源、连接配置与启停状态。</div>
          </div>
          <div class="ds-actions">
            <v-btn
              color="primary"
              variant="flat"
              prepend-icon="mdi-plus"
              @click="doAction(-1, 'add')"
              >新增数据源</v-btn
            >
          </div>
        </div>
      </v-card-text>
    </v-card>

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

    <v-card flat class="ds-card table-card ds-table-wrap">
      <v-card-text class="ds-card__content">
        <template v-if="sources.length === 0">
          <EmptyState
            title="暂无数据源"
            description="当前还没有配置任何数据源。点击下方按钮新增数据源后，即可继续维护连接与采集配置。"
            :primary="{ label: '新增数据源', icon: 'mdi-plus' }"
            @primary="() => doAction(-1, 'add')"
          />
        </template>
        <template v-else>
          <v-data-table
            :items="sources"
            :headers="headers"
            :search="searchValue"
            density="comfortable"
            items-per-page="20"
          >
            <template #item.url="{ item }">
              <span class="ds-cell-primary ds-mono-text ds-white-space-normal table-url">{{
                item.url || '-'
              }}</span>
            </template>

            <template #item.enabled="{ item }">
              <v-chip
                size="small"
                :color="item.enabled ? 'success' : 'error'"
                :variant="item.enabled ? 'tonal' : 'outlined'"
              >
                {{ item.enabled ? '已启用' : '已禁用' }}
              </v-chip>
            </template>

            <template #item.startAt="{ item }">
              <span class="ds-cell-support">{{ item.startAt || '-' }}</span>
            </template>

            <template #item.actions="{ item }">
              <div class="ds-action-inline">
                <v-btn
                  variant="text"
                  color="primary"
                  size="small"
                  class="px-2 ds-action-btn-main"
                  @click="doAction(item.id, 'edit')"
                >
                  <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
                </v-btn>
                <v-btn
                  variant="text"
                  color="info"
                  size="small"
                  class="px-2"
                  @click="cloneSource(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-content-copy</v-icon>克隆
                </v-btn>
                <v-btn
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
    max-width="1080"
    scrollable
  >
    <AddDataSource v-bind="params" @save="handleSave" @close-dialog="closeDialog" />
  </v-dialog>

  <v-dialog
    v-model="deleteDialog"
    class="ds-dialog-overlay"
    scrim="rgba(2, 6, 23, 0.62)"
    max-width="600"
  >
    <v-card>
      <v-card-title class="headline">确认删除</v-card-title>
      <v-card-text>您确定要删除 {{ itemNameToDelete }} 这个数据源吗？</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="deleteDialog = false">取消</v-btn>
        <v-btn color="error" variant="flat" @click="confirmDelete">确认删除</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import type { DataTableHeader } from 'vuetify';
  import sourceService from '@/service/source-service';
  import AddDataSource from '@/components/source/AddSource.vue';
  import EmptyState from '@/components/EmptyState.vue';
  import { notify } from '@/stores/notifier';

  const route = useRoute();
  const router = useRouter();

  const sources = ref([]);
  const isShow = ref(false);
  const deleteDialog = ref(false);
  const itemIdToDelete = ref<number | null>(null);
  const itemNameToDelete = ref<string | null>(null);
  const showDisabled = ref(false);
  const mode = ref('show');
  const searchValue = ref('');

  const headers: DataTableHeader[] = [
    { title: 'ID', key: 'id', width: '2%' },
    { title: '编号', key: 'code', width: '5%' },
    { title: '数据源', key: 'name', minWidth: '5%' },
    { title: '连接串', key: 'url', minWidth: '15%' },
    { title: '状态', key: 'enabled', width: '3%' },
    { title: '表数量', key: 'tableCount', width: '5%' },
    { title: '有效表数量', key: 'validTableCount', width: '7%' },
    { title: '采集时间', key: 'startAt', width: '6%' },
    { title: '操作', key: 'actions', align: 'center', sortable: false, width: '15%' },
  ];

  const params = ref({});

  const getSources = () => {
    const loader = showDisabled.value ? sourceService.list() : sourceService.listActiveSources();
    loader
      .then(resp => {
        sources.value = resp;
      })
      .catch(error => {
        console.log(error);
        notify('加载数据源列表失败: ' + error, 'error');
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
        code: '',
      },
    };
    isShow.value = true;
    mode.value = 'add';
  };

  const closeDialog = () => {
    isShow.value = false;
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
    if (action !== 'create') return;

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
    "title": "数据源管理",
    "icon": "mdi-database-cog",
    "requiresAuth": false,
    "navGroup": "source",
    "navOrder": 10
  }
}
</route>

<style scoped>
  .table-url {
    display: inline-block;
    max-width: 100%;
  }
</style>
