<template>
  <div
    class="accounts-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible }"
  >
    <v-card flat class="ds-card page-header-card">
      <v-card-text class="ds-card__content">
        <div class="page-header flex-wrap">
          <div>
            <div class="page-title">账号管理</div>
            <div class="page-subtitle">统一维护系统账号、角色与启停状态。</div>
          </div>
          <div class="ds-actions">
            <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="openCreateDialog"
              >新增账号</v-btn
            >
          </div>
        </div>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card toolbar-card">
      <v-card-text class="ds-card__content">
        <v-row density="comfortable" align="center">
          <v-col cols="12" md="4" lg="3">
            <v-text-field
              v-model="searchValue"
              density="compact"
              variant="outlined"
              label="搜索账号"
              prepend-inner-icon="mdi-magnify"
              single-line
              hide-details
            />
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card flat class="ds-card table-card ds-table-wrap">
      <v-card-text class="ds-card__content">
        <template v-if="users.length === 0">
          <EmptyState
            title="暂无账号"
            description="当前还没有配置任何系统账号。点击下方按钮新增账号后，即可继续分配角色与启停状态。"
            :primary="{ label: '新增账号', icon: 'mdi-plus' }"
            @primary="openCreateDialog"
          />
        </template>
        <template v-else>
          <v-data-table
            :items="users"
            :headers="headers"
            :search="searchValue"
            item-key="username"
            density="comfortable"
          >
            <template #item.username="{ item }">
              <div class="ds-cell-stack">
                <span class="ds-cell-meta">系统账号</span>
                <span class="ds-cell-primary">{{ item.username }}</span>
              </div>
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

            <template #item.authorities="{ item }">
              <span class="ds-cell-support">{{ formatAuthorities(item.authorities) }}</span>
            </template>

            <template #item.actions="{ item }">
              <div class="ds-action-inline">
                <v-btn
                  size="small"
                  variant="text"
                  color="primary"
                  class="px-2 ds-action-btn-main"
                  @click="openEditDialog(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-pencil</v-icon>编辑
                </v-btn>
                <v-btn
                  v-if="item.enabled"
                  size="small"
                  variant="text"
                  color="warning"
                  class="px-2"
                  @click="toggleEnabled(item, false)"
                >
                  <v-icon size="16" class="mr-1">mdi-pause-circle</v-icon>禁用
                </v-btn>
                <v-btn
                  v-else
                  size="small"
                  variant="text"
                  color="success"
                  class="px-2"
                  @click="toggleEnabled(item, true)"
                >
                  <v-icon size="16" class="mr-1">mdi-play-circle</v-icon>启用
                </v-btn>
                <v-btn
                  size="small"
                  variant="text"
                  color="error"
                  class="px-2"
                  @click="removeUser(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-delete</v-icon>删除
                </v-btn>
              </div>
            </template>
          </v-data-table>
        </template>
      </v-card-text>
    </v-card>

    <v-dialog
      v-model="dialogVisible"
      class="ds-dialog-overlay"
      scrim="rgba(2, 6, 23, 0.62)"
      max-width="560"
    >
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
              :rules="
                dialogMode === 'create'
                  ? [rules.required, rules.password]
                  : [rules.passwordOptional]
              "
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
          <v-btn variant="text" @click="dialogVisible = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="saveUser">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import { onMounted, ref } from 'vue';
  import userService, { type AccountUser } from '@/service/user-service';
  import EmptyState from '@/components/EmptyState.vue';
  import { notify } from '@/stores/notifier';

  type DialogMode = 'create' | 'edit';

  const users = ref<AccountUser[]>([]);
  const searchValue = ref('');
  const dialogVisible = ref(false);
  const dialogMode = ref<DialogMode>('create');
  const showPassword = ref(false);

  const headers = [
    { title: '用户名', key: 'username', minWidth: '180px' },
    { title: '状态', key: 'enabled', width: 110 },
    { title: '角色', key: 'authorities', minWidth: '180px' },
    { title: '操作', key: 'actions', sortable: false, width: 320 },
  ];

  const authorityOptions = ['admin', 'user'];

  const formModel = ref<{ username: string; password: string; authority: string }>({
    username: '',
    password: '',
    authority: 'user',
  });

  const rules = {
    required: (v: string) => !!v || '必填项',
    password: (v: string) => (v && v.length >= 6) || '密码至少 6 位',
    passwordOptional: (v: string) => !v || v.length >= 6 || '密码至少 6 位',
  };

  const loadUsers = async () => {
    try {
      users.value = await userService.list();
    } catch (error: any) {
      notify('加载账号列表失败: ' + (error.message || error), 'error');
      users.value = [];
    }
  };

  const normalizeAuthority = (authority?: string) => {
    const value = (authority || '').trim().toLowerCase();
    if (value.startsWith('role_')) {
      return value.slice(5) === 'admin' ? 'admin' : 'user';
    }
    return value === 'admin' ? 'admin' : 'user';
  };

  const formatAuthorities = (authorities: string[]) => {
    if (!authorities || authorities.length === 0) {
      return '-';
    }
    return authorities.map(item => normalizeAuthority(item)).join(', ');
  };

  const openCreateDialog = () => {
    dialogMode.value = 'create';
    formModel.value = {
      username: '',
      password: '',
      authority: 'user',
    };
    showPassword.value = false;
    dialogVisible.value = true;
  };

  const openEditDialog = (item: AccountUser) => {
    dialogMode.value = 'edit';
    formModel.value = {
      username: item.username,
      password: '',
      authority: normalizeAuthority(item.authorities?.[0]),
    };
    showPassword.value = false;
    dialogVisible.value = true;
  };

  const saveUser = async () => {
    const username = formModel.value.username?.trim();
    const password = formModel.value.password || '';
    const authority = formModel.value.authority;

    if (!username) {
      notify('用户名不能为空', 'warning');
      return;
    }

    if (dialogMode.value === 'create' && password.length < 6) {
      notify('密码至少 6 位', 'warning');
      return;
    }

    if (dialogMode.value === 'edit' && password && password.length < 6) {
      notify('密码至少 6 位', 'warning');
      return;
    }

    try {
      if (dialogMode.value === 'create') {
        await userService.create({
          username,
          password,
          enabled: true,
          authority,
        });
        notify('账号创建成功', 'success');
      } else {
        const payload: { password?: string; authority?: string } = { authority };
        if (password) {
          payload.password = password;
        }
        await userService.update(username, payload);
        notify('账号更新成功', 'success');
      }

      dialogVisible.value = false;
      await loadUsers();
    } catch (error: any) {
      notify(
        (dialogMode.value === 'create' ? '创建账号失败: ' : '更新账号失败: ') +
          (error.message || error),
        'error'
      );
    }
  };

  const toggleEnabled = async (item: AccountUser, enabled: boolean) => {
    const actionText = enabled ? '启用' : '禁用';
    if (!confirm(`确认${actionText}账号 ${item.username} 吗？`)) {
      return;
    }

    try {
      if (enabled) {
        await userService.enable(item.username);
      } else {
        await userService.disable(item.username);
      }
      notify(`${actionText}成功`, 'success');
      await loadUsers();
    } catch (error: any) {
      notify(`${actionText}失败: ` + (error.message || error), 'error');
    }
  };

  const removeUser = async (item: AccountUser) => {
    if (!confirm(`确认删除账号 ${item.username} 吗？`)) {
      return;
    }

    try {
      await userService.delete(item.username);
      notify('删除成功', 'success');
      await loadUsers();
    } catch (error: any) {
      notify('删除失败: ' + (error.message || error), 'error');
    }
  };

  onMounted(() => {
    loadUsers();
  });
</script>

<route lang="json">
{
  "meta": {
    "title": "账号管理",
    "icon": "mdi-account-group-outline",
    "requiresAuth": true,
    "navHidden": true
  }
}
</route>
