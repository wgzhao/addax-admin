<template>
  <v-container fluid class="pa-6 accounts-page">
    <v-card flat title="账号管理" class="section-card">
      <template #append>
        <v-btn color="primary" size="small" @click="openCreateDialog">新增账号</v-btn>
      </template>
      <v-card-text>
        <v-row class="mb-2" align="center">
          <v-col cols="12" md="4">
            <v-text-field
              v-model="searchValue"
              density="compact"
              label="搜索账号"
              prepend-inner-icon="mdi-magnify"
              single-line
              hide-details
            />
          </v-col>
        </v-row>

        <v-data-table
          :items="users"
          :headers="headers"
          :search="searchValue"
          item-key="username"
          density="compact"
          class="elevation-1"
        >
          <template #item.enabled="{ item }">
            <v-chip size="small" :color="item.enabled ? 'success' : 'error'">
              {{ item.enabled ? '已启用' : '已禁用' }}
            </v-chip>
          </template>

          <template #item.authorities="{ item }">
            <span>{{ formatAuthorities(item.authorities) }}</span>
          </template>

          <template #item.actions="{ item }">
            <div class="action-inline">
              <v-btn icon size="small" @click="openEditDialog(item)" title="编辑">
                <v-icon>mdi-pencil</v-icon>
              </v-btn>
              <v-btn
                v-if="item.enabled"
                icon
                size="small"
                @click="toggleEnabled(item, false)"
                title="禁用"
              >
                <v-icon color="warning">mdi-pause-circle</v-icon>
              </v-btn>
              <v-btn v-else icon size="small" @click="toggleEnabled(item, true)" title="启用">
                <v-icon color="success">mdi-play-circle</v-icon>
              </v-btn>
              <v-btn icon size="small" @click="removeUser(item)" title="删除">
                <v-icon color="red">mdi-delete</v-icon>
              </v-btn>
            </div>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>

    <v-dialog v-model="dialogVisible" max-width="560">
      <v-card>
        <v-card-title>{{ dialogMode === 'create' ? '新增账号' : '编辑账号' }}</v-card-title>
        <v-card-text>
          <v-form>
            <v-text-field
              v-model="formModel.username"
              label="用户名"
              :disabled="dialogMode === 'edit'"
              :rules="[rules.required]"
            />
            <v-text-field
              v-model="formModel.password"
              :label="dialogMode === 'create' ? '密码' : '新密码（留空表示不修改）'"
              :type="showPassword ? 'text' : 'password'"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              @click:append-inner="showPassword = !showPassword"
              :rules="dialogMode === 'create' ? [rules.required, rules.password] : [rules.passwordOptional]"
              autocomplete="off"
            />
            <v-select
              v-model="formModel.authority"
              :items="authorityOptions"
              label="角色"
              :rules="[rules.required]"
            />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="dialogVisible = false">取消</v-btn>
          <v-btn color="primary" @click="saveUser">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import userService, { type AccountUser } from '@/service/user-service'
  import { notify } from '@/stores/notifier'

  type DialogMode = 'create' | 'edit'

  const users = ref<AccountUser[]>([])
  const searchValue = ref('')
  const dialogVisible = ref(false)
  const dialogMode = ref<DialogMode>('create')
  const showPassword = ref(false)

  const headers = [
    { title: '用户名', key: 'username' },
    { title: '状态', key: 'enabled' },
    { title: '角色', key: 'authorities' },
    { title: '操作', key: 'actions', sortable: false }
  ]

  const authorityOptions = ['admin', 'user']

  const formModel = ref<{ username: string; password: string; authority: string }>({
    username: '',
    password: '',
    authority: 'user'
  })

  const rules = {
    required: (v: string) => !!v || '必填项',
    password: (v: string) => (v && v.length >= 6) || '密码至少 6 位',
    passwordOptional: (v: string) => !v || v.length >= 6 || '密码至少 6 位'
  }

  const loadUsers = async () => {
    try {
      users.value = await userService.list()
    } catch (error: any) {
      notify('加载账号列表失败: ' + (error.message || error), 'error')
      users.value = []
    }
  }

  const normalizeAuthority = (authority?: string) => {
    const value = (authority || '').trim().toLowerCase()
    if (value.startsWith('role_')) {
      return value.slice(5) === 'admin' ? 'admin' : 'user'
    }
    return value === 'admin' ? 'admin' : 'user'
  }

  const formatAuthorities = (authorities: string[]) => {
    if (!authorities || authorities.length === 0) {
      return '-'
    }
    return authorities.map((item) => normalizeAuthority(item)).join(', ')
  }

  const openCreateDialog = () => {
    dialogMode.value = 'create'
    formModel.value = {
      username: '',
      password: '',
      authority: 'user'
    }
    showPassword.value = false
    dialogVisible.value = true
  }

  const openEditDialog = (item: AccountUser) => {
    dialogMode.value = 'edit'
    formModel.value = {
      username: item.username,
      password: '',
      authority: normalizeAuthority(item.authorities?.[0])
    }
    showPassword.value = false
    dialogVisible.value = true
  }

  const saveUser = async () => {
    const username = formModel.value.username?.trim()
    const password = formModel.value.password || ''
    const authority = formModel.value.authority

    if (!username) {
      notify('用户名不能为空', 'warning')
      return
    }

    if (dialogMode.value === 'create' && password.length < 6) {
      notify('密码至少 6 位', 'warning')
      return
    }

    if (dialogMode.value === 'edit' && password && password.length < 6) {
      notify('密码至少 6 位', 'warning')
      return
    }

    try {
      if (dialogMode.value === 'create') {
        await userService.create({
          username,
          password,
          enabled: true,
          authority
        })
        notify('账号创建成功', 'success')
      } else {
        const payload: { password?: string; authority?: string } = { authority }
        if (password) {
          payload.password = password
        }
        await userService.update(username, payload)
        notify('账号更新成功', 'success')
      }

      dialogVisible.value = false
      await loadUsers()
    } catch (error: any) {
      notify((dialogMode.value === 'create' ? '创建账号失败: ' : '更新账号失败: ') + (error.message || error), 'error')
    }
  }

  const toggleEnabled = async (item: AccountUser, enabled: boolean) => {
    const actionText = enabled ? '启用' : '禁用'
    if (!confirm(`确认${actionText}账号 ${item.username} 吗？`)) {
      return
    }

    try {
      if (enabled) {
        await userService.enable(item.username)
      } else {
        await userService.disable(item.username)
      }
      notify(`${actionText}成功`, 'success')
      await loadUsers()
    } catch (error: any) {
      notify(`${actionText}失败: ` + (error.message || error), 'error')
    }
  }

  const removeUser = async (item: AccountUser) => {
    if (!confirm(`确认删除账号 ${item.username} 吗？`)) {
      return
    }

    try {
      await userService.delete(item.username)
      notify('删除成功', 'success')
      await loadUsers()
    } catch (error: any) {
      notify('删除失败: ' + (error.message || error), 'error')
    }
  }

  onMounted(() => {
    loadUsers()
  })
</script>

<style scoped>
  .action-inline {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
</style>
