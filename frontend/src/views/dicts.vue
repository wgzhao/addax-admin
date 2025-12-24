<template>
  <v-container fluid class="pa-6">
    <v-row>
      <v-col cols="12" md="5">
        <v-card flat title="字典列表">
          <v-card-text>
            <v-data-table
              :items="dicts"
              :headers="dictHeaders"
              item-key="code"
              dense
              density="compact"
              class="elevation-1"
              @click:row="(e, row) => onSelectDict(row?.item ?? row)"
            >
              <template #top>
                <v-row>
                  <v-col>
                    <v-btn color="primary" @click="openDictDialog()">新增字典</v-btn>
                  </v-col>
                </v-row>
              </template>
              <template #item.actions="{ item }">
                <v-btn icon small @click.stop="openDictDialog(item)">
                  <v-icon>mdi-pencil</v-icon>
                </v-btn>
                <v-btn icon small @click.stop="deleteDict(item.code)">
                  <v-icon color="red">mdi-delete</v-icon>
                </v-btn>
              </template>
            </v-data-table>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="7">
        <v-card flat :title="selectedDict ? `字典明细 - ${selectedDict.name || selectedDict.code}` : '请选择字典'">
          <v-card-text>
            <div v-if="!selectedDict">请选择左侧字典查看明细</div>
            <div v-else>
              <v-row>
                <v-col>
                  <v-btn color="primary" @click="openItemDialog()">新增明细</v-btn>
                  <v-btn
                    v-if="selectedDict && selectedDict.code === 1021"
                    color="secondary"
                    class="ml-2"
                    @click="openBatchDialog()"
                  >
                    批量新增交易日
                  </v-btn>
                </v-col>
              </v-row>
              <v-data-table
                :items="items"
                :headers="itemHeaders"
                item-key="itemKey"
                dense
                density="compact"
                class="elevation-1"
              >
                <template #item.actions="{ item }">
                  <v-btn icon small @click.stop="openItemDialog(item)">
                    <v-icon>mdi-pencil</v-icon>
                  </v-btn>
                  <v-btn icon small @click.stop="deleteItem(item.itemKey)">
                    <v-icon color="red">mdi-delete</v-icon>
                  </v-btn>
                </template>
              </v-data-table>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Dict dialog -->
    <v-dialog v-model="dictDialog" max-width="600px">
      <v-card>
        <v-card-title>字典</v-card-title>
        <v-card-text>
          <v-form ref="dictForm">
            <v-text-field v-model="dictFormModel.code" label="编码" type="number" :disabled="!!dictFormModel.codeExists" />
            <v-text-field v-model="dictFormModel.name" label="名称" />
            <v-textarea v-model="dictFormModel.remark" label="说明" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="dictDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveDict">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Item dialog -->
    <v-dialog v-model="itemDialog" max-width="600px">
      <v-card>
        <v-card-title>字典明细</v-card-title>
        <v-card-text>
          <v-form ref="itemForm">
            <v-text-field v-model="itemFormModel.itemKey" label="键" />
            <v-text-field v-model="itemFormModel.itemValue" label="值" />
            <v-textarea v-model="itemFormModel.remark" label="说明" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="itemDialog = false">取消</v-btn>
          <v-btn color="primary" @click="saveItem">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Batch add trade dates dialog (for dict code 1021) -->
    <v-dialog v-model="batchDialog" max-width="600px">
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
              <v-btn text @click="batchDialog = false">取消</v-btn>
              <v-btn color="primary" @click="batchAddDates">生成并保存</v-btn>
            </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import dictService from '@/service/dict-service'
import type { SysDict, SysItem } from '@/types/database'

const dicts = ref<SysDict[]>([])
const items = ref<SysItem[]>([])
const selectedDict = ref<SysDict | null>(null)

const dictDialog = ref(false)
const itemDialog = ref(false)

const dictFormModel = ref<any>({ code: null, name: '', remark: '', codeExists: false })
const itemFormModel = ref<any>({ dictCode: null, itemKey: '', itemValue: '', remark: '' })

// batch add trade dates (for dict code 1021) -- simplified: collect year + includeWeekend
const batchDialog = ref(false)
const batchForm = ref<{ year: number | null; includeWeekend: boolean }>({ year: new Date().getFullYear(), includeWeekend: true })

const openBatchDialog = () => {
  batchForm.value = { year: new Date().getFullYear(), includeWeekend: true }
  batchDialog.value = true
}

const batchAddDates = async () => {
  if (!selectedDict.value || selectedDict.value.code !== 1021) return alert('仅支持交易日字典 (1021)')
  const year = batchForm.value.year
  if (!year || year < 1900 || year > 9999) return alert('请输入有效年份')
  const include = batchForm.value.includeWeekend
  try {
    await dictService.generateTradeCalendar(year, include)
    batchDialog.value = false
    if (selectedDict.value) loadItems(selectedDict.value.code)
    alert('已请求后端生成交易日，完成后请刷新明细查看结果')
  } catch (err) {
    console.error(err)
    alert('后端生成失败，请查看控制台')
  }
}

const dictHeaders = [
  { title: '编码', key: 'code' },
  { title: '名称', key: 'name' },
  { title: '说明', key: 'remark' },
  { title: '操作', key: 'actions' }
]

const itemHeaders = [
  { title: '键', key: 'itemKey' },
  { title: '值', key: 'itemValue' },
  { title: '说明', key: 'remark' },
  { title: '操作', key: 'actions' }
]

const loadDicts = () => {
  dictService
    .listDicts()
    .then((res) => {
      dicts.value = res || []
    })
    .catch(() => {
      dicts.value = []
    })
}

const loadItems = (dictCode: number) => {
  dictService
    .listSysItems(dictCode)
    .then((res) => {
      items.value = res || []
    })
    .catch(() => {
      items.value = []
    })
}

const onSelectDict = (row?: any) => {
  // Vuetify may pass a wrapper (with .item) or the raw item; normalize both cases
  const actual = row && row.item ? row.item : row
  if (!actual) return
  selectedDict.value = actual as SysDict
  if (actual.code != null) loadItems(actual.code)
}

const openDictDialog = (d?: SysDict) => {
  if (d) {
    dictFormModel.value = { code: d.code, name: d.name, remark: d.remark, codeExists: true }
  } else {
    dictFormModel.value = { code: null, name: '', remark: '', codeExists: false }
  }
  dictDialog.value = true
}

const saveDict = () => {
  const payload: SysDict = {
    code: Number(dictFormModel.value.code),
    name: dictFormModel.value.name,
    remark: dictFormModel.value.remark
  }
  dictService
    .createOrUpdateDict(payload)
    .then(() => {
      dictDialog.value = false
      loadDicts()
    })
    .catch((e) => console.error(e))
}

const deleteDict = (code: number) => {
  if (!confirm('确认删除字典及其明细？')) return
  dictService.deleteDict(code).then(() => {
    if (selectedDict.value && selectedDict.value.code === code) {
      selectedDict.value = null
      items.value = []
    }
    loadDicts()
  })
}

const openItemDialog = (it?: SysItem) => {
  if (!selectedDict.value) return
  if (it) {
    itemFormModel.value = { dictCode: selectedDict.value.code, itemKey: it.itemKey, itemValue: it.itemValue, remark: it.remark }
  } else {
    itemFormModel.value = { dictCode: selectedDict.value.code, itemKey: '', itemValue: '', remark: '' }
  }
  itemDialog.value = true
}

const saveItem = () => {
  const payload: SysItem = {
    dictCode: itemFormModel.value.dictCode,
    itemKey: itemFormModel.value.itemKey,
    itemValue: itemFormModel.value.itemValue,
    remark: itemFormModel.value.remark
  }
  const op = items.value.find((i) => i.itemKey === payload.itemKey) ? dictService.updateDictItem : dictService.createDictItem
  op(payload)
    .then(() => {
      itemDialog.value = false
      if (selectedDict.value) loadItems(selectedDict.value.code)
    })
    .catch((e) => console.error(e))
}

const deleteItem = (itemKey: string) => {
  if (!selectedDict.value) return
  if (!confirm('确认删除该明细？')) return
  dictService.deleteDictItem(selectedDict.value.code, itemKey).then(() => {
    loadItems(selectedDict.value!.code)
  })
}

onMounted(() => {
  loadDicts()
})
</script>

<route lang="json">
{
  "meta": { "title": "字典维护", "icon": "mdi-book-open-page-variant" }
}
</route>
