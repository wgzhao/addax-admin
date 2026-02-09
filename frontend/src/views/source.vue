<template>
  <div class="row">
    <v-card title="数据源管理">
      <template v-slot:text>
        <v-row justify="center" align-center="center">
          <v-col cols="col-4">
            <v-text-field
              v-model="searchValue"
              density="compact"
              label="Search"
              prepend-inner-icon="mdi-magnify"
              single-line
              hide-details
            ></v-text-field>
          </v-col>
          <v-spacer />
          <v-col cols="auto">
            <v-btn variant="tonal" @click="doAction('-1', 'add')">新增</v-btn>
          </v-col>
          <v-spacer />
          <v-col cols="auto" class="d-flex align-center">
            <v-switch
              v-model="showDisabled"
              density="compact"
              color="primary"
              :label="showDisabled ? '显示全部源' : '仅显示启用源'"
            />
          </v-col>
        </v-row>
      </template>
      <v-card-text>
        <v-data-table
          :items="sources"
          :headers="headers"
          :search="searchValue"
          density="compact"
          items-per-page="20"
        >
          <template v-slot:item.enabled="{ item }">
            <v-chip
              size="small"
              :color="item.enabled == true ? 'success' : 'error'"
              :text="item.enabled == true ? '已启用' : '已禁用'"
              class="ml-2"
            ></v-chip>
          </template>
          <template v-slot:item.actions="{ item }">
            <v-btn
              variant="text"
              color="primary"
              class="me-2"
              @click="doAction(item.id, 'edit')"
              :title="'编辑'"
              icon="mdi-pencil"
            ></v-btn>
            <v-btn
              variant="text"
              color="info"
              class="me-2"
              @click="cloneSource(item)"
              :title="'克隆'"
              icon="mdi-content-copy"
            ></v-btn>
            <v-btn
              variant="text"
              color="error"
              class="me-2"
              @click="openDeleteDialog(item.id, item.name)"
              :title="'删除'"
              icon="mdi-delete"
            ></v-btn>
            <!-- <a href="#" class="btn btn-xs btn-info">使用场景</a>
                        <a href="#" class="btn btn-xs btn-info">探索源库</a> -->
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
  </div>
  <!-- form -->

  <v-dialog v-model="isShow" max-width="1080" scrollable>
    <AddDataSource v-bind="params" @save="handleSave" @close-dialog="closeDialog" />
  </v-dialog>

  <!-- 确认删除对话框 -->
  <v-dialog v-model="deleteDialog" max-width="600">
    <v-card>
      <v-card-title class="headline">确认删除</v-card-title>
      <v-card-text>您确定要删除{{ itemNameToDelete }}这个数据源吗？</v-card-text>
      <v-card-actions>
        <v-btn color="default" text @click="deleteDialog = false">取消</v-btn>
        <v-btn color="error" text @click="confirmDelete">确认</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue'
  import type { DataTableHeader } from 'vuetify'
  import sourceService from '@/service/source-service'
  import AddDataSource from '@/components/source/AddSource.vue'
  import { notify } from '@/stores/notifier'

  const sources = ref([])
  const isShow = ref(false)
  const deleteDialog = ref(false)
  const itemIdToDelete = ref(null)
  const itemNameToDelete = ref(null)
  const showDisabled = ref(false) // 默认不显示禁用源
  // 模式： show 显示，edit 编辑， add 新增
  const mode = ref('show')
  const searchValue = ref('')
  const headers: DataTableHeader[] = [
    { title: 'ID', key: 'id' },
    { title: '名称', key: 'name' },
    { title: '采集代码', key: 'code' },
    { title: '连接串', key: 'url' },
    { title: '是否启用', key: 'enabled' },
    {
      title: '采集时间',
      key: 'startAt'
    },
    { title: '操作', value: 'actions', align: 'center' }
  ]

  const params = ref({})
  const getSources = () => {
    const loader = showDisabled.value ? sourceService.list() : sourceService.listActiveSources()
    loader
      .then((resp) => {
        sources.value = resp
      })
      .catch((error) => {
        console.log(error)
        notify('加载数据源列表失败: ' + error, 'error')
      })
  }

  const doAction = (id, ctype) => {
    params.value = {
      mode: ctype,
      sid: id
    }
    isShow.value = true
    mode.value = ctype
  }

  const cloneSource = (item) => {
    params.value = {
      mode: 'add',
      sid: -1,
      cloneData: {
        ...item,
        id: 0,
        code: ''
      }
    }
    isShow.value = true
    mode.value = 'add'
  }

  const closeDialog = () => {
    isShow.value = false
  }

  // 处理保存事件
  const handleSave = () => {
    console.log('数据源保存成功，正在刷新列表...')
    // 重新获取数据源列表，确保显示最新数据
    getSources()
    // 关闭对话框
    closeDialog()
  }

  // 删除相关操作
  // 打开删除确认对话框
  const openDeleteDialog = (id, name) => {
    itemIdToDelete.value = id // 保存要删除的记录 ID
    itemNameToDelete.value = name
    deleteDialog.value = true // 打开对话框
  }

  // 确认删除
  const confirmDelete = () => {
    if (itemIdToDelete.value !== null) {
      deleteSource(itemIdToDelete.value)
    }
    deleteDialog.value = false
  }

  // 删除数据逻辑
  const deleteSource = (id) => {
    console.log(`删除记录 ID: ${id}`)
    // 这里实现删除逻辑，例如调用 API 接口或移除本地数据
    sourceService
      .delete(id)
      .then(() => {
        notify('删除成功', 'success')
        sources.value = sources.value.filter((item) => item.id !== id)
        // 如果当前列表被删到 0，可重新拉取一遍（为未来分页兼容）
        if (sources.value.length === 0) {
          getSources()
        }
        itemIdToDelete.value = null
      })
      .catch((error) => {
        notify('删除失败: ' + error.message, 'error')
      })
  }
  onMounted(() => {
    getSources()
  })

  // 切换显示禁用源时，重新加载列表
  watch(showDisabled, () => {
    getSources()
  })
</script>
<route lang="json">
{
  "meta": {
    "title": "采集源管理",
    "icon": "mdi-database",
    "requiresAuth": false
  }
}
</route>
<style></style>
