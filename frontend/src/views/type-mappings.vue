<template>
  <div class="type-mappings-page page-shell ds-management-page">
    <PageHeader title="类型映射" subtitle="维护源字段类型到 ODS/Hive 目标类型的映射。">
      <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="openDialog()">
        新增映射
      </v-btn>
    </PageHeader>

    <v-card flat class="ds-card table-card">
      <v-card-text class="ds-card__content section-body">
        <div class="section-header">
          <div>
            <div class="section-title">映射列表</div>
            <div class="section-subtitle">
              采集时按源字段类型匹配目标类型；未命中的源类型按默认类型处理。
            </div>
          </div>
          <v-text-field
            v-model="search"
            prepend-inner-icon="mdi-magnify"
            label="搜索源类型"
            variant="outlined"
            density="compact"
            hide-details
            clearable
            class="search-field"
          />
        </div>

        <v-skeleton-loader v-if="loading" type="table-row@6" class="ds-skeleton" />
        <template v-else-if="filtered.length === 0">
          <EmptyState
            title="暂无映射"
            description="当前没有可显示的映射。可点击「新增映射」添加源类型到目标类型的映射。"
            :primary="{ label: '新增映射', icon: 'mdi-plus' }"
            @primary="openDialog()"
          />
        </template>
        <v-data-table
          v-else
          :items="filtered"
          :headers="headers"
          item-key="sourceType"
          density="comfortable"
        >
          <template #item.sourceType="{ item }">
            <span class="ds-cell-primary ds-mono-text">{{ item.sourceType }}</span>
          </template>

          <template #item.targetType="{ item }">
            <span class="ds-cell-primary ds-mono-text">{{ item.targetType }}</span>
          </template>

          <template #item.remark="{ item }">
            <span class="ds-cell-support ds-white-space-normal">{{ item.remark || '-' }}</span>
          </template>

          <template #item.actions="{ item }">
            <div class="ds-action-inline">
              <v-btn
                size="small"
                variant="text"
                color="primary"
                class="px-2 ds-action-btn-main"
                @click.stop="openDialog(item)"
              >
                <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
              </v-btn>
              <v-btn
                size="small"
                variant="text"
                color="error"
                class="px-2"
                @click.stop="deleteMapping(item.sourceType)"
              >
                <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
              </v-btn>
            </div>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <v-dialog v-model="dialog" class="ds-dialog-overlay" max-width="560">
      <v-card>
        <div class="ds-dialog-head">
          <span class="ds-dialog-icon"><v-icon size="20">mdi-swap-horizontal</v-icon></span>
          <div class="ds-dialog-heading">
            <h2 class="ds-dialog-title">{{ editing ? '编辑映射' : '新增映射' }}</h2>
            <p class="ds-dialog-subtitle">源类型保存后作为映射键，编辑时不可修改。</p>
          </div>
          <v-btn
            icon="mdi-close"
            variant="text"
            size="small"
            aria-label="关闭"
            @click="dialog = false"
          />
        </div>
        <v-divider />
        <v-card-text class="ds-dialog-body">
          <v-form ref="form" @submit.prevent="save">
            <div class="dialog-stack">
              <div class="field">
                <label class="field-label" for="source-type">源类型</label>
                <v-text-field
                  id="source-type"
                  v-model="model.sourceType"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                  :disabled="editing"
                  :rules="[rules.required]"
                />
                <p class="field-help">源数据库的字段类型，例如 varchar、decimal、bigint。</p>
              </div>
              <div class="field">
                <label class="field-label" for="target-type">目标类型</label>
                <v-text-field
                  id="target-type"
                  v-model="model.targetType"
                  variant="outlined"
                  density="compact"
                  hide-details="auto"
                  :rules="[rules.required]"
                />
                <p class="field-help">映射到 ODS/Hive 的目标类型，例如 string、decimal、bigint。</p>
              </div>
              <div class="field">
                <label class="field-label" for="mapping-remark">说明</label>
                <v-textarea
                  id="mapping-remark"
                  v-model="model.remark"
                  rows="2"
                  auto-grow
                  variant="outlined"
                  density="compact"
                  hide-details
                />
              </div>
            </div>
          </v-form>
        </v-card-text>
        <v-card-actions class="ds-dialog-actions">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="save">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <ConfirmDialog
      v-model="confirmState.open"
      :title="confirmState.title"
      :message="confirmState.message"
      confirm-text="删除"
      @confirm="runConfirmed"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import PageHeader from '@/components/PageHeader.vue';
  import EmptyState from '@/components/EmptyState.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import typeMappingService from '@/service/type-mapping-service';
  import type { TypeMapping } from '@/service/type-mapping-service';
  import { notify } from '@/stores/notifier';

  const mappings = ref<TypeMapping[]>([]);
  const loading = ref(true);
  const search = ref('');

  const headers = [
    { title: '源类型', key: 'sourceType', minWidth: '180px' },
    { title: '目标类型', key: 'targetType', minWidth: '180px' },
    { title: '说明', key: 'remark', minWidth: '220px' },
    { title: '操作', key: 'actions', width: 180, sortable: false },
  ];

  const filtered = computed(() => {
    const q = search.value.trim().toLowerCase();
    if (!q) return mappings.value;
    return mappings.value.filter(
      m =>
        (m.sourceType || '').toLowerCase().includes(q) ||
        (m.targetType || '').toLowerCase().includes(q)
    );
  });

  const confirmState = ref<{
    open: boolean;
    title: string;
    message: string;
    action: (() => void) | null;
  }>({ open: false, title: '', message: '', action: null });

  const askConfirm = (title: string, message: string, action: () => void) => {
    confirmState.value = { open: true, title, message, action };
  };

  const runConfirmed = () => {
    const action = confirmState.value.action;
    confirmState.value.open = false;
    action?.();
  };

  const dialog = ref(false);
  const editing = ref(false);
  const form = ref<any>(null);
  const model = ref<TypeMapping>({ sourceType: '', targetType: '', remark: '' });

  const rules = {
    required: (value: any) => !!(value && String(value).trim()) || '必填项',
  };

  const load = () => {
    loading.value = true;
    typeMappingService
      .list()
      .then(res => {
        mappings.value = res || [];
      })
      .catch(() => {
        mappings.value = [];
      })
      .finally(() => {
        loading.value = false;
      });
  };

  const openDialog = (m?: TypeMapping) => {
    editing.value = !!m;
    if (m) {
      model.value = { sourceType: m.sourceType, targetType: m.targetType, remark: m.remark };
    } else {
      model.value = { sourceType: '', targetType: '', remark: '' };
    }
    dialog.value = true;
  };

  const save = async () => {
    const { valid } = await form.value.validate();
    if (!valid) return;
    const payload: TypeMapping = {
      sourceType: String(model.value.sourceType).trim(),
      targetType: String(model.value.targetType).trim(),
      remark: model.value.remark,
    };
    const op = editing.value
      ? typeMappingService.update(model.value.sourceType, payload)
      : typeMappingService.create(payload);
    op
      .then(() => {
        dialog.value = false;
        notify('映射已保存', 'success');
        load();
      })
      .catch(err => {
        notify('保存失败: ' + (err?.message || err), 'error');
      });
  };

  const deleteMapping = (sourceType: string) => {
    askConfirm('删除映射', `将删除源类型「${sourceType}」的类型映射，此操作不可撤销。`, () =>
      typeMappingService
        .remove(sourceType)
        .then(() => {
          notify('映射已删除', 'success');
          load();
        })
        .catch(err => notify('删除失败: ' + (err?.message || err), 'error'))
    );
  };

  onMounted(load);
</script>

<style scoped>
  .section-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .section-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .section-title {
    font-size: 1rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .section-subtitle {
    margin-top: 4px;
    color: var(--ds-text-2);
    line-height: 1.6;
  }

  .search-field {
    max-width: 260px;
  }

  .dialog-stack {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .field {
    min-width: 0;
  }

  .field-label {
    display: block;
    margin-bottom: 6px;
    color: rgb(var(--v-theme-on-surface));
    font-size: 12.5px;
    font-weight: 500;
  }

  .field-help {
    margin: 5px 0 0;
    color: var(--ds-text-3);
    font-size: 11.5px;
    line-height: 1.45;
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "类型映射",
    "icon": "mdi-swap-horizontal",
    "requiresAuth": true,
    "navGroup": "systemManage",
    "navOrder": 19,
    "roles": ["admin"]
  }
}
</route>
