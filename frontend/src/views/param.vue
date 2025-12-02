<template>
  <!-- 主表配置 -- 参数管理 -->
  <v-row>
    <v-col cols="col-6" align="left" justify="top">
      <v-card>
        <v-card-text>
          <v-data-table :headers="dictHeaders" :items="dicts" density="compact">
            <template v-slot:item.actions="{ item }">
              <v-icon
                icon="mdi-chevron-right"
                size="small"
                class="me-2"
                @click="getItems(item.code)"
              ></v-icon>
            </template>
          </v-data-table>
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="col-6">
      <v-card>
        <v-card-text>
          <v-row>
            <v-col cols="auto">
              <span class="text-h6">{{ currCode }} 参数详情</span>
            </v-col>
            <v-spacer></v-spacer>
            <v-col>
              <v-btn color="info" class="btn btn-primary" @click="addItem">新增</v-btn>
            </v-col>
          </v-row>
          <v-data-table
            v-if="sysItems"
            :headers="itemHeaders"
            :items="sysItems"
            items-per-page="20"
            density="default"
            class="elevation-1"
          >
            <template v-slot:top>
              <!-- edit/new form -->
              <v-dialog v-model="dialog" max-width="500px">
                <v-card>
                  <v-card-title>
                    <span class="text-h5">{{ formTitle }}</span>
                  </v-card-title>
                  <v-card-text>
                    <v-text-field
                      v-model="editedItem.dictCode"
                      label="参数值"
                      disabled
                    ></v-text-field>
                    <v-text-field v-model="editedItem.itemKey" label="参数项"></v-text-field>
                    <v-textarea v-model="editedItem.itemValue" label="参数名称"></v-textarea>
                    <v-text-field v-model="editedItem.remark" label="备注"></v-text-field>
                  </v-card-text>
                  <v-card-actions>
                    <v-btn color="info" @click="close">取消</v-btn>
                    <v-btn color="primary" @click="saveItem">保存</v-btn>
                  </v-card-actions>
                </v-card>
              </v-dialog>
              <!-- delete alert dialog -->
              <v-dialog v-model="dialogDelete" max-width="500px">
                <v-card>
                  <v-card-title class="text-h5">
                    Are you sure you want to delete this item?
                  </v-card-title>
                  <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color="warning" @click="closeDelete">Cancel</v-btn>
                    <v-btn color="primary" @click="deleteItemConfirm">OK</v-btn>
                  </v-card-actions>
                </v-card>
              </v-dialog>
            </template>

            <!-- action -->
            <template v-slot:item.actions="{ item }">
              <v-icon size="small" class="me-2" @click="editItem(item)">mdi-pencil</v-icon>
              <v-icon size="small" @click="deleteItem(item)">mdi-delete</v-icon>
            </template>
          </v-data-table>
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed, watch, nextTick } from 'vue'
  import dictService from '@/service/dict-service'
  import { notify } from '@/stores/notifier'
  import { SysDict, SysItem } from '@/types/database'

  const dicts = ref<SysDict[]>([])
  const sysItems = ref<SysItem[]>([])

  const dictHeaders = [
    { title: '字典编号', value: 'code', width: '10%' },
    { title: '字典名称', value: 'name', width: '25%' },
    { title: '字典分类', value: 'classification', width: '10%' },
    { title: '说明', value: 'remark', width: '50%' },
    { title: '#', value: 'actions', width: '5%' }
  ]
  const itemHeaders = [
    { title: '字典编号', value: 'dictCode' },
    { title: '参数项', value: 'itemKey' },
    { title: '参数值', value: 'itemValue' },
    { title: '备注', value: 'remark', width: '30%' },
    { title: 'Action', value: 'actions', width: '10%' }
  ]
  const dialog = ref(false)
  const dialogDelete = ref(false)
  const currCode = ref<number>(0)
  const editedIndex = ref(-1)
  const editedItem = ref<Partial<SysItem>>({})
  const defaultItem = ref<Partial<SysItem>>({})
  const formTitle = computed(() => {
    return editedIndex.value === -1 ? 'New Item' : 'Edit Item'
  })

  const close = () => {
    dialog.value = false
    nextTick(() => {
      editedItem.value = Object.assign({}, defaultItem.value)
      editedIndex.value = -1
    })
  }

  const closeDelete = () => {
    dialogDelete.value = false
    nextTick(() => {
      editedItem.value = Object.assign({}, defaultItem.value)
      editedIndex.value = -1
    })
  }
  watch(dialog, async (newVal) => {
    if (newVal) {
      dialog.value = true
    } else {
      close()
    }
  })

  watch(dialogDelete, async (newVal) => {
    if (newVal) {
      dialogDelete.value = true
    } else {
      closeDelete()
    }
  })

  const getItems = (code) => {
    currCode.value = code
    dictService.listSysItems(code).then((res) => {
      sysItems.value = res
    })
  }

  const addItem = () => {
    // add default entry_code
    // entryCode = editItem.value.dictCode;
    // editedItem.value = Object.assign({}, defaultItem.value)
    // editedItem.value.entryCode = entryCode;

    editedItem.value = Object.assign({}, defaultItem.value)
    editedItem.value.dictCode = currCode.value

    dialog.value = !dialog.value
  }
  const editItem = (item) => {
    editedIndex.value = sysItems.value.indexOf(item)
    editedItem.value = Object.assign({}, item)
    dialog.value = true
  }

  const deleteItem = (item) => {
    editedIndex.value = sysItems.value.indexOf(item)
    editedItem.value = Object.assign({}, item)
    dialogDelete.value = true
  }

  const deleteItemConfirm = () => {
    const ec = editedItem.value.dictCode
    const ev = editedItem.value.itemKey
    dictService
      .deleteDictItem(ec, ev)
      .then(() => {
        if (editedIndex.value > -1) {
          sysItems.value.splice(editedIndex.value, 1)
        }
        notify('删除成功', 'success')
        closeDelete()
      })
      .catch((err) => {
        notify('删除失败: ' + err, 'error')
      })
  }
  const saveItem = () => {
    // 确保必要的字段存在
    if (!editedItem.value.dictCode || !editedItem.value.itemKey) {
      notify('请填写必要的字段', 'warning')
      return
    }

    // const itemToSave = editedItem.value as SysItem;

    // if (editedIndex.value > -1) {
    //   Object.assign(sysItems.value[editedIndex.value], itemToSave);
    // } else {
    //   sysItems.value.push(itemToSave);
    // }

    // // 创建Map对象用于API调用
    // const itemMap = new Map<string, string>();
    // itemMap.set('dictCode', String(itemToSave.dictCode));
    // itemMap.set('itemKey', itemToSave.itemKey);
    // if (itemToSave.itemValue) itemMap.set('itemValue', itemToSave.itemValue);
    // if (itemToSave.remark) itemMap.set('remark', itemToSave.remark);

    // save
    if (editedIndex.value > -1) {
      // update
      dictService
        .updateDictItem(editedItem.value as SysItem)
        .then(() => {
          notify('保存成功', 'success')
          sysItems[editedIndex.value] = editedItem.value as SysItem
        })
        .catch((err) => notify('保存失败: ' + err, 'error'))
    } else {
      // create
      dictService
        .createDictItem(editedItem.value as SysItem)
        .then((res) => {
          sysItems.value.push(res)
          notify('保存成功', 'success')
        })
        .catch((err) => notify('保存失败: ' + err, 'error'))
    }
    close()
  }
  onMounted(() => {
    dictService.listDicts().then((res) => {
      dicts.value = res
    })
  })
</script>

<route lang="json">
{
  "meta": {
    "title": "系统配置",
    "icon": "mdi-settings",
    "requiresAuth": false
  }
}
</route>

<style lang="css" scoped>
  /* 覆盖 .v-data-table 的斑马纹背景 */
  .v-data-table .v-data-table__divider:nth-child(odd) {
    background-color: #fafafa !important;
    /* 奇数行的背景颜色 */
  }

  .v-data-table .v-data-table__divider:nth-child(even) {
    background-color: #ffffff !important;
    /* 偶数行的背景颜色 */
  }
</style>
