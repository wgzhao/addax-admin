<template>
  <div
    class="dict-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dictDialog || itemDialog || batchDialog }"
  >
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">字典维护</div>
            <div class="page-subtitle">统一维护系统字典及其明细项。</div>
          </div>
          <div class="ds-actions">
            <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="openDictDialog()"
              >新增字典</v-btn
            >
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-row class="section-grid stretch-row" dense>
      <v-col cols="12" md="5" class="col-stack">
        <v-card flat class="ds-card table-card stretch-card ds-table-wrap">
          <v-card-text class="ds-card__content section-body">
            <div class="section-header">
              <div>
                <div class="section-title">字典列表</div>
                <div class="section-subtitle">左侧展示字典定义，点击任意一项即可切换右侧明细。</div>
              </div>
            </div>

            <template v-if="dicts.length === 0">
              <EmptyState
                title="暂无字典"
                description="当前还没有配置任何字典。点击下方按钮新增字典后，即可继续维护对应明细。"
                :primary="{ label: '新增字典', icon: 'mdi-plus' }"
                @primary="openDictDialog()"
              />
            </template>
            <v-data-table
              v-else
              :items="dicts"
              :headers="dictHeaders"
              item-key="code"
              density="comfortable"
              @click:row="(e, row) => onSelectDict(row?.item ?? row)"
            >
              <template #item.name="{ item }">
                <div class="ds-cell-stack">
                  <span class="ds-cell-meta">编码 {{ item.code }}</span>
                  <span class="ds-cell-primary">{{ item.name || '-' }}</span>
                </div>
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
                    @click.stop="openDictDialog(item)"
                  >
                    <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
                  </v-btn>
                  <v-btn
                    size="small"
                    variant="text"
                    color="error"
                    class="px-2"
                    @click.stop="deleteDict(item.code)"
                  >
                    <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
                  </v-btn>
                </div>
              </template>
            </v-data-table>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="7" class="col-stack">
        <v-card flat class="ds-card table-card stretch-card ds-table-wrap">
          <v-card-text class="ds-card__content section-body">
            <div class="section-header section-header--with-actions">
              <div>
                <div class="section-title">
                  {{
                    selectedDict
                      ? `字典明细 - ${selectedDict.name || selectedDict.code}`
                      : '请选择字典'
                  }}
                </div>
                <div class="section-subtitle">
                  {{
                    selectedDict
                      ? '右侧展示当前字典下的明细项，可直接新增、编辑或删除。'
                      : '请先在左侧选择一个字典，再查看或编辑对应明细。'
                  }}
                </div>
              </div>
              <div class="ds-actions">
                <v-btn
                  color="primary"
                  variant="flat"
                  size="small"
                  prepend-icon="mdi-plus"
                  @click="openItemDialog()"
                  :disabled="!selectedDict"
                >
                  新增明细
                </v-btn>
                <v-btn
                  v-if="selectedDict && selectedDict.code === 1021"
                  color="secondary"
                  variant="tonal"
                  size="small"
                  prepend-icon="mdi-calendar-plus"
                  @click="openBatchDialog()"
                >
                  批量新增交易日
                </v-btn>
              </div>
            </div>

            <div v-if="!selectedDict" class="dict-empty-state">请选择左侧字典查看明细</div>
            <div v-else>
              <v-data-table
                :items="items"
                :headers="itemHeaders"
                item-key="itemKey"
                density="comfortable"
              >
                <template #item.itemKey="{ item }">
                  <span class="ds-cell-primary ds-mono-text">{{ item.itemKey }}</span>
                </template>

                <template #item.itemValue="{ item }">
                  <span class="ds-cell-support ds-white-space-normal">{{
                    item.itemValue || '-'
                  }}</span>
                </template>

                <template #item.remark="{ item }">
                  <span class="ds-cell-support ds-white-space-normal">{{
                    item.remark || '-'
                  }}</span>
                </template>

                <template #item.actions="{ item }">
                  <div class="ds-action-inline">
                    <v-btn
                      size="small"
                      variant="text"
                      color="primary"
                      class="px-2 ds-action-btn-main"
                      @click.stop="openItemDialog(item)"
                    >
                      <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
                    </v-btn>
                    <v-btn
                      size="small"
                      variant="text"
                      color="error"
                      class="px-2"
                      @click.stop="deleteItem(item.itemKey)"
                    >
                      <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
                    </v-btn>
                  </div>
                </template>
              </v-data-table>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog
      v-model="dictDialog"
      class="ds-dialog-overlay"
      scrim="rgba(2, 6, 23, 0.62)"
      max-width="600px"
    >
      <v-card>
        <v-card-title>字典</v-card-title>
        <v-card-text>
          <v-form ref="dictForm">
            <v-text-field
              v-model="dictFormModel.code"
              label="编码"
              type="number"
              :disabled="!!dictFormModel.codeExists"
            />
            <v-text-field v-model="dictFormModel.name" label="名称" />
            <v-textarea v-model="dictFormModel.remark" label="说明" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dictDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="saveDict">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="itemDialog"
      class="ds-dialog-overlay"
      scrim="rgba(2, 6, 23, 0.62)"
      max-width="600px"
    >
      <v-card>
        <v-card-title>字典明细</v-card-title>
        <v-card-text>
          <v-form ref="itemForm">
            <v-text-field v-model="itemFormModel.itemKey" label="键" />
            <v-textarea
              v-if="isJsonValueDictCode(itemFormModel.dictCode)"
              v-model="itemFormModel.itemValue"
              label="值"
              rows="8"
              auto-grow
            />
            <v-text-field v-else v-model="itemFormModel.itemValue" label="值" />
            <v-textarea v-model="itemFormModel.remark" label="说明" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="itemDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="saveItem">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="batchDialog"
      class="ds-dialog-overlay"
      scrim="rgba(2, 6, 23, 0.62)"
      max-width="600px"
    >
      <v-card>
        <v-card-title>批量新增交易日</v-card-title>
        <v-card-text>
          <v-form>
            <v-row>
              <v-col cols="12" md="6">
                <v-text-field v-model="batchForm.year" type="number" label="年份 (YYYY)" />
              </v-col>
              <v-col cols="12" md="6">
                <v-checkbox v-model="batchForm.includeWeekend" label="包含周末" />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="batchDialog = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="batchAddDates">生成并保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import EmptyState from '@/components/EmptyState.vue';
  import dictService from '@/service/dict-service';
  import type { SysDict, SysItem } from '@/types/database';

  const dicts = ref<SysDict[]>([]);
  const items = ref<SysItem[]>([]);
  const selectedDict = ref<SysDict | null>(null);

  const dictDialog = ref(false);
  const itemDialog = ref(false);

  const dictFormModel = ref<any>({ code: null, name: '', remark: '', codeExists: false });
  const itemFormModel = ref<any>({ dictCode: null, itemKey: '', itemValue: '', remark: '' });

  const batchDialog = ref(false);
  const batchForm = ref<{ year: number | null; includeWeekend: boolean }>({
    year: new Date().getFullYear(),
    includeWeekend: true,
  });

  const openBatchDialog = () => {
    batchForm.value = { year: new Date().getFullYear(), includeWeekend: true };
    batchDialog.value = true;
  };

  const batchAddDates = async () => {
    if (!selectedDict.value || selectedDict.value.code !== 1021)
      return alert('仅支持交易日字典 (1021)');
    const year = batchForm.value.year;
    if (!year || year < 1900 || year > 9999) return alert('请输入有效年份');
    const include = batchForm.value.includeWeekend;
    try {
      await dictService.generateTradeCalendar(year, include);
      batchDialog.value = false;
      if (selectedDict.value) loadItems(selectedDict.value.code);
      alert('已请求后端生成交易日，完成后请刷新明细查看结果');
    } catch (err) {
      console.error(err);
      alert('后端生成失败，请查看控制台');
    }
  };

  const dictHeaders = [
    { title: '字典', key: 'name', minWidth: '180px' },
    { title: '说明', key: 'remark', minWidth: '180px' },
    { title: '操作', key: 'actions', width: 220, sortable: false },
  ];

  const itemHeaders = [
    { title: '键', key: 'itemKey', minWidth: '180px' },
    { title: '值', key: 'itemValue', minWidth: '220px' },
    { title: '说明', key: 'remark', minWidth: '180px' },
    { title: '操作', key: 'actions', width: 220, sortable: false },
  ];

  const loadDicts = () => {
    dictService
      .listDicts()
      .then(res => {
        dicts.value = res || [];
      })
      .catch(() => {
        dicts.value = [];
      });
  };

  const loadItems = (dictCode: number) => {
    dictService
      .listSysItems(dictCode)
      .then(res => {
        items.value = res || [];
      })
      .catch(() => {
        items.value = [];
      });
  };

  const onSelectDict = (row?: any) => {
    const actual = row && row.item ? row.item : row;
    if (!actual) return;
    selectedDict.value = actual as SysDict;
    if (actual.code != null) loadItems(actual.code);
  };

  const openDictDialog = (d?: SysDict) => {
    if (d) {
      dictFormModel.value = { code: d.code, name: d.name, remark: d.remark, codeExists: true };
    } else {
      dictFormModel.value = { code: null, name: '', remark: '', codeExists: false };
    }
    dictDialog.value = true;
  };

  const saveDict = () => {
    const payload: SysDict = {
      code: Number(dictFormModel.value.code),
      name: dictFormModel.value.name,
      remark: dictFormModel.value.remark,
    };
    dictService
      .createOrUpdateDict(payload)
      .then(() => {
        dictDialog.value = false;
        loadDicts();
      })
      .catch(e => console.error(e));
  };

  const deleteDict = (code: number) => {
    if (!confirm('确认删除字典及其明细？')) return;
    dictService.deleteDict(code).then(() => {
      if (selectedDict.value && selectedDict.value.code === code) {
        selectedDict.value = null;
        items.value = [];
      }
      loadDicts();
    });
  };

  const openItemDialog = (it?: SysItem) => {
    if (!selectedDict.value) return;
    if (it) {
      itemFormModel.value = {
        dictCode: selectedDict.value.code,
        itemKey: it.itemKey,
        itemValue: it.itemValue,
        remark: it.remark,
      };
    } else {
      itemFormModel.value = {
        dictCode: selectedDict.value.code,
        itemKey: '',
        itemValue: '',
        remark: '',
      };
    }
    itemDialog.value = true;
  };

  const isJsonValueDictCode = (dictCode?: number | null) => {
    return [5000, 5001].includes(Number(dictCode));
  };

  const saveItem = () => {
    const payload: SysItem = {
      dictCode: itemFormModel.value.dictCode,
      itemKey: itemFormModel.value.itemKey,
      itemValue: itemFormModel.value.itemValue,
      remark: itemFormModel.value.remark,
    };
    const op = items.value.find(i => i.itemKey === payload.itemKey)
      ? dictService.updateDictItem
      : dictService.createDictItem;
    op(payload)
      .then(() => {
        itemDialog.value = false;
        if (selectedDict.value) loadItems(selectedDict.value.code);
      })
      .catch(e => console.error(e));
  };

  const deleteItem = (itemKey: string) => {
    if (!selectedDict.value) return;
    if (!confirm('确认删除该明细？')) return;
    dictService.deleteDictItem(selectedDict.value.code, itemKey).then(() => {
      loadItems(selectedDict.value!.code);
    });
  };

  onMounted(() => {
    loadDicts();
  });
</script>

<style scoped>
  .section-grid {
    row-gap: 12px;
  }

  .stretch-row {
    align-items: stretch;
  }

  .col-stack {
    display: flex;
    flex-direction: column;
  }

  .stretch-card {
    flex: 1 1 auto;
  }

  .section-body {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .section-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .section-header--with-actions {
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

  .dict-empty-state {
    padding: 28px 16px;
    border-radius: 14px;
    border: 1px dashed var(--ds-border-default);
    color: var(--ds-text-2);
    text-align: center;
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "字典维护",
    "icon": "mdi-book-open-page-variant",
    "navGroup": "systemManage",
    "navOrder": 20
  }
}
</route>
