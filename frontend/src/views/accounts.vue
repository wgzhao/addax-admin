<template>
  <div
    class="accounts-page page-shell ds-management-page"
    :class="{ 'ds-management-page--modal-open': dialogVisible }"
  >
    <PageHeader title="账号管理" subtitle="统一维护系统账号、角色与启停状态。">
      <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="openCreateDialog">
        新增账号
      </v-btn>
    </PageHeader>

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

    <v-card flat class="ds-card table-card ds-table-wrap ds-table-scroll">
      <v-card-text class="ds-card__content">
        <v-skeleton-loader v-if="loading" type="table-row@6" class="ds-skeleton" />
        <template v-else-if="users.length === 0">
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
                <span class="ds-cell-meta">{{ item.username }}</span>
                <span class="ds-cell-primary">{{ item.displayName || item.username }}</span>
              </div>
            </template>

            <template #item.email="{ item }">
              <span class="ds-cell-support">{{ item.email || '-' }}</span>
            </template>

            <template #item.lastLoginAt="{ item }">
              <span class="ds-cell-support">{{ formatTime(item.lastLoginAt) }}</span>
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
                  v-if="canGrant(item)"
                  size="small"
                  variant="text"
                  color="info"
                  class="px-2"
                  @click="openGrantDialog(item)"
                >
                  <v-icon size="16" class="mr-1">mdi-shield-key-outline</v-icon>授权
                </v-btn>
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
      max-width="480"
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
              v-model="formModel.displayName"
              label="姓名 / 显示名"
              placeholder="留空时使用用户名"
            />
            <v-text-field
              v-model="formModel.email"
              label="邮箱（可选）"
              :rules="[rules.emailOptional]"
            />
            <v-textarea
              v-model="formModel.remark"
              label="备注（可选）"
              rows="2"
              auto-grow
              variant="outlined"
              density="compact"
            />
            <v-text-field
              v-model="formModel.password"
              :label="dialogMode === 'create' ? '密码' : '新密码（留空表示不修改）'"
              :type="showPassword ? 'text' : 'password'"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              :rules="
                dialogMode === 'create'
                  ? [rules.required, rules.password]
                  : [rules.passwordOptional]
              "
              autocomplete="off"
              @click:append-inner="showPassword = !showPassword"
            />
            <v-text-field
              v-if="showConfirmPassword"
              ref="confirmPasswordRef"
              v-model="formModel.confirmPassword"
              label="确认密码"
              :type="showPassword ? 'text' : 'password'"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              :rules="
                dialogMode === 'create'
                  ? [rules.confirmRequired, rules.passwordMatch]
                  : [rules.passwordMatchOptional]
              "
              autocomplete="off"
              @click:append-inner="showPassword = !showPassword"
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

    <v-dialog v-model="grantDialogVisible" max-width="640">
      <v-card>
        <v-card-title>采集源授权 · {{ grantUser }}</v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" density="compact" class="mb-3">
            所有登录账号都可以查看采集源和采集表；这里只控制“可操作”权限，即修改配置、执行采集、查看作业与日志。
          </v-alert>
          <v-skeleton-loader v-if="grantLoading" type="list-item-two-line@3" />
          <v-table v-else density="compact">
            <thead>
              <tr>
                <th>采集源</th>
                <th style="width: 180px">授权</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in grantRows" :key="row.sourceId">
                <td>{{ row.sourceName }}</td>
                <td>
                  <v-select
                    v-model="row.accessLevel"
                    :items="grantLevelOptions"
                    item-title="label"
                    item-value="value"
                    density="compact"
                    variant="outlined"
                    hide-details
                  />
                </td>
              </tr>
              <tr v-if="grantRows.length === 0">
                <td colspan="2">暂无采集源</td>
              </tr>
            </tbody>
          </v-table>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="grantDialogVisible = false">取消</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="grantSaving"
            :disabled="grantLoading"
            @click="saveGrants"
          >
            保存授权
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <ConfirmDialog
      v-model="confirmState.open"
      :title="confirmState.title"
      :message="confirmState.message"
      :tone="confirmState.tone"
      :confirm-text="confirmState.tone === 'danger' ? '确认' : '确定'"
      @confirm="runConfirmed"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, useTemplateRef, watch } from 'vue';
  import userService, { type AccountUser, type SourceGrant } from '@/service/user-service';
  import sourceService from '@/service/source-service';
  import EmptyState from '@/components/EmptyState.vue';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import PageHeader from '@/components/PageHeader.vue';
  import { notify } from '@/stores/notifier';

  type DialogMode = 'create' | 'edit';

  const users = ref<AccountUser[]>([]);
  const loading = ref(true);
  const searchValue = ref('');
  const confirmState = ref<{
    open: boolean;
    title: string;
    message: string;
    tone: 'danger' | 'primary';
    action: (() => unknown) | null;
  }>({ open: false, title: '', message: '', tone: 'danger', action: null });

  const runConfirmed = () => {
    const action = confirmState.value.action;
    confirmState.value.open = false;
    action?.();
  };
  const dialogVisible = ref(false);
  const dialogMode = ref<DialogMode>('create');
  const grantDialogVisible = ref(false);
  const grantUser = ref('');
  const grantLoading = ref(false);
  const grantSaving = ref(false);
  const grantLevelOptions = [
    { label: '无', value: '' },
    { label: '可操作', value: 'OPERATE' },
  ];
  const grantRows = ref<
    Array<{ sourceId: number; sourceName: string; accessLevel: '' | SourceGrant['accessLevel'] }>
  >([]);
  const showPassword = ref(false);
  const confirmPasswordRef = useTemplateRef<{ validate: () => void }>('confirmPasswordRef');

  const headers = [
    { title: '账号 / 姓名', key: 'username', minWidth: '200px' },
    { title: '邮箱', key: 'email', minWidth: '200px' },
    { title: '最后登录', key: 'lastLoginAt', width: 170 },
    { title: '状态', key: 'enabled', width: 110 },
    { title: '角色', key: 'authorities', minWidth: '150px' },
    { title: '操作', key: 'actions', sortable: false, width: 370 },
  ];

  const authorityOptions = ['admin', 'user', 'guest'];

  const formModel = ref<{
    username: string;
    password: string;
    confirmPassword: string;
    authority: string;
    displayName: string;
    email: string;
    remark: string;
  }>({
    username: '',
    password: '',
    confirmPassword: '',
    authority: 'user',
    displayName: '',
    email: '',
    remark: '',
  });

  // Create always needs the confirmation; edit only once a new password is being typed
  const showConfirmPassword = computed(
    () => dialogMode.value === 'create' || formModel.value.password.length > 0
  );

  // Drop a stale confirmation so a cleared password cannot block saving
  watch(
    () => formModel.value.password,
    value => {
      if (!value) {
        formModel.value.confirmPassword = '';
        return;
      }
      // Re-validate the confirmation immediately, otherwise a later edit of the password
      // keeps showing a stale verdict
      confirmPasswordRef.value?.validate();
    }
  );

  const rules = {
    required: (v: string) => !!v || '必填项',
    password: (v: string) => (v && v.length >= 6) || '密码至少 6 位',
    passwordOptional: (v: string) => !v || v.length >= 6 || '密码至少 6 位',
    confirmRequired: (v: string) => !!v || '请再次输入密码',
    passwordMatch: (v: string) => v === formModel.value.password || '两次输入的密码不一致',
    passwordMatchOptional: (v: string) =>
      !v || v === formModel.value.password || '两次输入的密码不一致',
    emailOptional: (v: string) => !v || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v) || '邮箱格式不正确',
  };

  const formatTime = (value?: string | null) => {
    if (!value) return '-';
    return value.replace('T', ' ').slice(0, 19);
  };

  const loadUsers = async () => {
    loading.value = true;
    try {
      users.value = await userService.list();
    } catch (error: any) {
      notify('加载账号列表失败: ' + (error.message || error), 'error');
      users.value = [];
    } finally {
      loading.value = false;
    }
  };

  const normalizeAuthority = (authority?: string) => {
    let value = (authority || '').trim().toLowerCase();
    if (value.startsWith('role_')) {
      value = value.slice(5);
    }
    // Keep guest distinct; mapping it to user would silently change the account on edit.
    return value === 'admin' || value === 'guest' ? value : 'user';
  };

  const formatAuthorities = (authorities: string[]) => {
    if (!authorities || authorities.length === 0) {
      return '-';
    }
    return authorities.map(item => normalizeAuthority(item)).join(', ');
  };

  const roleOf = (item: AccountUser) => {
    const roles = (item.authorities || []).map(normalizeAuthority);
    if (roles.includes('admin')) return 'admin';
    if (roles.includes('user')) return 'user';
    return 'guest';
  };

  // Guest is read-only by definition and admin already manages every source; only user accounts
  // can receive a management grant.
  const canGrant = (item: AccountUser) => roleOf(item) === 'user';

  const openCreateDialog = () => {
    dialogMode.value = 'create';
    formModel.value = {
      username: '',
      password: '',
      confirmPassword: '',
      authority: 'user',
      displayName: '',
      email: '',
      remark: '',
    };
    showPassword.value = false;
    dialogVisible.value = true;
  };

  const openEditDialog = (item: AccountUser) => {
    dialogMode.value = 'edit';
    formModel.value = {
      username: item.username,
      password: '',
      confirmPassword: '',
      authority: normalizeAuthority(item.authorities?.[0]),
      displayName: item.displayName || '',
      email: item.email || '',
      remark: item.remark || '',
    };
    showPassword.value = false;
    dialogVisible.value = true;
  };

  const saveUser = async () => {
    const username = formModel.value.username?.trim();
    const password = formModel.value.password || '';
    const confirmPassword = formModel.value.confirmPassword || '';
    const authority = formModel.value.authority;
    const displayName = formModel.value.displayName?.trim() || '';
    const email = formModel.value.email?.trim() || '';
    const remark = formModel.value.remark?.trim() || '';

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

    if (password && !confirmPassword) {
      notify('请再次输入密码', 'warning');
      return;
    }

    if (password !== confirmPassword) {
      notify('两次输入的密码不一致', 'warning');
      return;
    }

    try {
      if (dialogMode.value === 'create') {
        await userService.create({
          username,
          password,
          enabled: true,
          authority,
          displayName,
          email,
          remark,
        });
        notify('账号创建成功', 'success');
      } else {
        const payload: {
          password?: string;
          authority?: string;
          displayName: string;
          email: string;
          remark: string;
        } = { authority, displayName, email, remark };
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
    confirmState.value = {
      open: true,
      title: `${actionText}账号`,
      message: enabled
        ? `启用账号「${item.username}」后，该账号可以重新登录系统。`
        : `禁用账号「${item.username}」后，该账号将无法登录系统。`,
      tone: enabled ? 'primary' : 'danger',
      action: async () => {
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
      },
    };
  };

  const removeUser = async (item: AccountUser) => {
    confirmState.value = {
      open: true,
      title: '删除账号',
      message: `将删除账号「${item.username}」，此操作不可撤销。`,
      tone: 'danger',
      action: async () => {
        try {
          await userService.delete(item.username);
          notify('删除成功', 'success');
          await loadUsers();
        } catch (error: any) {
          notify('删除失败: ' + (error.message || error), 'error');
        }
      },
    };
  };

  const openGrantDialog = async (item: AccountUser) => {
    if (!canGrant(item)) return;
    grantUser.value = item.username;
    grantRows.value = [];
    grantDialogVisible.value = true;
    grantLoading.value = true;
    try {
      const [sources, grants] = await Promise.all([
        sourceService.list(),
        userService.grants(item.username),
      ]);
      const bySource = new Map(grants.map(grant => [grant.sourceId, grant.accessLevel]));
      grantRows.value = sources.map(source => ({
        sourceId: source.id,
        sourceName: source.name,
        accessLevel: bySource.get(source.id) === 'OPERATE' ? 'OPERATE' : '',
      }));
    } catch (error: any) {
      notify('加载采集源授权失败: ' + (error.message || error), 'error');
      grantDialogVisible.value = false;
    } finally {
      grantLoading.value = false;
    }
  };

  const saveGrants = async () => {
    grantSaving.value = true;
    try {
      await userService.replaceGrants(
        grantUser.value,
        grantRows.value
          .filter(row => row.accessLevel)
          .map(row => ({
            sourceId: row.sourceId,
            accessLevel: row.accessLevel as SourceGrant['accessLevel'],
          }))
      );
      notify('采集源授权已保存', 'success');
      grantDialogVisible.value = false;
    } catch (error: any) {
      notify('保存采集源授权失败: ' + (error.message || error), 'error');
    } finally {
      grantSaving.value = false;
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
    "navGroup": "systemManage",
    "navOrder": 35,
    "roles": ["admin"]
  }
}
</route>
