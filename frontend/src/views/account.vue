<template>
  <div class="page-shell account-page">
    <PageHeader title="我的账号" subtitle="查看账号资料与采集权限，维护登录密码与界面外观。" />

    <div v-if="loading" class="account-loading" aria-live="polite">
      <v-progress-circular indeterminate color="primary" />
      <span>正在加载账号信息…</span>
    </div>

    <v-alert v-else-if="loadError" type="error" variant="tonal" class="account-alert">
      {{ loadError }}
      <template #append>
        <v-btn size="small" variant="text" @click="loadProfile">重试</v-btn>
      </template>
    </v-alert>

    <v-row v-else-if="me" dense>
      <v-col cols="12" md="7" class="account-col">
        <v-card class="section-card ds-card" elevation="0" data-testid="account-profile-card">
          <v-card-text class="section-body">
            <div class="account-identity">
              <v-avatar size="52" color="primary" variant="tonal" class="account-avatar">
                {{ initials }}
              </v-avatar>
              <div class="account-identity-text">
                <div class="account-name" data-testid="account-display-name">
                  {{ me.displayName || me.username }}
                </div>
                <div class="account-username">@{{ me.username }}</div>
              </div>
              <v-chip size="small" variant="tonal" :color="roleColor" data-testid="account-role">
                {{ roleLabel }}
              </v-chip>
              <v-btn
                v-if="!editingProfile"
                size="small"
                variant="text"
                color="primary"
                prepend-icon="mdi-pencil"
                data-testid="account-edit-profile"
                @click="startProfileEdit"
              >
                编辑
              </v-btn>
            </div>

            <v-form
              v-if="editingProfile"
              v-model="profileFormValid"
              class="account-profile-form"
              @submit.prevent="saveProfile"
            >
              <p class="account-hint">
                用户名 {{ me.username }} 不可修改；角色与启停由管理员维护。
              </p>
              <v-text-field
                v-model="profileForm.displayName"
                label="姓名"
                variant="outlined"
                density="compact"
                :rules="[displayNameRule]"
                data-testid="account-edit-display-name"
              />
              <v-text-field
                v-model="profileForm.email"
                label="邮箱"
                variant="outlined"
                density="compact"
                :rules="[emailRule]"
                data-testid="account-edit-email"
              />
              <v-textarea
                v-model="profileForm.remark"
                label="备注"
                variant="outlined"
                density="compact"
                rows="2"
                :rules="[remarkRule]"
                data-testid="account-edit-remark"
              />
              <div class="account-form-actions">
                <v-btn variant="text" :disabled="savingProfile" @click="cancelProfileEdit">
                  取消
                </v-btn>
                <v-btn
                  type="submit"
                  color="primary"
                  :loading="savingProfile"
                  :disabled="!profileFormValid || savingProfile"
                  data-testid="account-save-profile"
                >
                  保存
                </v-btn>
              </div>
            </v-form>

            <template v-else>
              <v-list density="compact" class="account-fields">
                <v-list-item v-for="field in profileFields" :key="field.label">
                  <v-list-item-title class="account-field-label">{{
                    field.label
                  }}</v-list-item-title>
                  <v-list-item-subtitle
                    class="account-field-value"
                    :class="{ 'account-field-value--muted': field.muted }"
                  >
                    {{ field.value }}
                  </v-list-item-subtitle>
                </v-list-item>
              </v-list>

              <p class="account-hint">
                姓名、邮箱与备注由本人维护；用户名、角色与启停状态由管理员管理。
              </p>
            </template>
          </v-card-text>
        </v-card>

        <v-card class="section-card ds-card" elevation="0" data-testid="account-grants-card">
          <v-card-text class="section-body">
            <h2 class="ds-section-title">采集授权</h2>

            <p v-if="isAdmin" class="account-hint account-hint--top">
              管理员账号可操作全部采集源，无需单独授权。
            </p>
            <p v-else-if="isGuest" class="account-hint account-hint--top">
              访客账号只读：可查看采集源与采集数据，不能新增、编辑或执行采集表。
            </p>
            <template v-else>
              <p v-if="!grants.length" class="account-hint account-hint--top">
                尚未授权任何采集源：可查看全部数据，但不能新增、编辑或执行采集表。
              </p>
              <v-table v-else density="compact" class="account-grants-table">
                <thead>
                  <tr>
                    <th>采集源</th>
                    <th class="account-grant-level">权限</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="grant in grants" :key="grant.sourceId">
                    <td>{{ grant.sourceName }}</td>
                    <td class="account-grant-level">
                      <v-chip size="x-small" variant="tonal" color="primary">可操作</v-chip>
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </template>
          </v-card-text>
        </v-card>
      </v-col>

      <v-col cols="12" md="5" class="account-col">
        <v-card class="section-card ds-card" elevation="0" data-testid="account-security-card">
          <v-card-text class="section-body">
            <div class="account-card-head">
              <h2 class="ds-section-title">安全</h2>
              <v-btn
                v-if="!passwordFormOpen"
                size="small"
                variant="tonal"
                color="primary"
                prepend-icon="mdi-lock-reset"
                @click="openPasswordForm"
              >
                修改密码
              </v-btn>
            </div>

            <v-list density="compact" class="account-fields">
              <v-list-item>
                <v-list-item-title class="account-field-label">密码更新时间</v-list-item-title>
                <v-list-item-subtitle class="account-field-value">
                  {{ formatDateTime(me.passwordChangedAt) }}
                </v-list-item-subtitle>
              </v-list-item>
            </v-list>

            <v-expand-transition>
              <v-form
                v-if="passwordFormOpen"
                v-model="passwordFormValid"
                class="account-password-form"
                @submit.prevent="submitPassword"
              >
                <v-text-field
                  v-model="currentPassword"
                  label="当前密码"
                  type="password"
                  autocomplete="current-password"
                  variant="outlined"
                  density="compact"
                  :rules="[requiredRule]"
                  data-testid="account-current-password"
                />
                <v-text-field
                  v-model="newPassword"
                  label="新密码"
                  type="password"
                  autocomplete="new-password"
                  variant="outlined"
                  density="compact"
                  :rules="[requiredRule, minLengthRule]"
                  data-testid="account-new-password"
                />
                <v-text-field
                  v-model="confirmPassword"
                  label="确认新密码"
                  type="password"
                  autocomplete="new-password"
                  variant="outlined"
                  density="compact"
                  :rules="[requiredRule, matchRule]"
                  data-testid="account-confirm-password"
                />
                <div class="account-form-actions">
                  <v-btn variant="text" :disabled="submittingPassword" @click="closePasswordForm">
                    取消
                  </v-btn>
                  <v-btn
                    type="submit"
                    color="primary"
                    :loading="submittingPassword"
                    :disabled="!passwordFormValid || submittingPassword"
                    data-testid="account-submit-password"
                  >
                    提交
                  </v-btn>
                </div>
                <p class="account-hint">修改成功后当前会话会退出，需要用新密码重新登录。</p>
              </v-form>
            </v-expand-transition>
          </v-card-text>
        </v-card>

        <v-card class="section-card ds-card" elevation="0" data-testid="account-appearance-card">
          <v-card-text class="section-body">
            <h2 class="ds-section-title">外观偏好</h2>
            <v-radio-group
              :model-value="displayedPreference"
              density="compact"
              hide-details="auto"
              class="account-theme-group"
              @update:model-value="applyPreference"
            >
              <v-radio label="跟随系统" value="system" />
              <v-radio label="浅色" value="light" />
              <v-radio label="深色" value="dark" />
            </v-radio-group>
            <p class="account-hint account-hint--top" data-testid="account-theme-hint">
              <template v-if="preference === 'default'"> 当前为默认浅色（未显式选择）。 </template>
              <template v-else-if="preference === 'system'">
                当前跟随系统设置，系统切换外观时本页会同步变化。
              </template>
              <template v-else>
                当前使用{{ themeStore.theme === 'dark' ? '深色' : '浅色' }}外观。
              </template>
              该偏好仅保存在当前浏览器，不跨设备同步。
            </p>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import PageHeader from '@/components/PageHeader.vue';
  import userService from '@/service/user-service';
  import type { AccountUser, SourceGrant } from '@/service/user-service';
  import { authService } from '@/service/auth-service';
  import { useAuthStore } from '@/stores/auth';
  import { useAppTheme } from '@/composables/useAppTheme';
  import { notify } from '@/stores/notifier';
  import { highestRole } from '@/utils/roles';

  const router = useRouter();
  const authStore = useAuthStore();
  const { themeStore } = useAppTheme();

  const loading = ref(true);
  const loadError = ref('');
  const me = ref<AccountUser | null>(null);

  const grants = computed<SourceGrant[]>(() => me.value?.grants || []);
  const role = computed(() => highestRole(me.value?.authorities));
  const isAdmin = computed(() => role.value === 'admin');
  const isGuest = computed(() => role.value === 'guest');

  const ROLE_LABEL: Record<string, string> = {
    admin: '管理员',
    user: '操作员',
    guest: '访客',
  };
  const ROLE_COLOR: Record<string, string> = {
    admin: 'primary',
    user: 'info',
    guest: 'secondary',
  };
  const roleLabel = computed(() => ROLE_LABEL[role.value] || role.value);
  const roleColor = computed(() => ROLE_COLOR[role.value] || 'secondary');

  const initials = computed(() => {
    const source = (me.value?.displayName || me.value?.username || '?').trim();
    return source.slice(0, 1).toUpperCase();
  });

  const formatDateTime = (value?: string | null) =>
    value ? value.replace('T', ' ').slice(0, 19) : '-';

  const profileFields = computed(() => {
    const user = me.value;
    if (!user) return [];
    return [
      { label: '用户名', value: user.username, muted: false },
      { label: '姓名', value: user.displayName || '未设置', muted: !user.displayName },
      { label: '邮箱', value: user.email || '未配置', muted: !user.email },
      { label: '备注', value: user.remark || '无', muted: !user.remark },
      { label: '加入时间', value: formatDateTime(user.createdAt), muted: false },
      { label: '最后登录', value: formatDateTime(user.lastLoginAt), muted: false },
    ];
  });

  const loadProfile = async () => {
    loading.value = true;
    loadError.value = '';
    try {
      me.value = await userService.me();
    } catch (error: any) {
      loadError.value = error?.message || '加载账号信息失败';
    } finally {
      loading.value = false;
    }
  };

  // Profile edit: only descriptive fields; the server rejects anything else on this endpoint.
  const editingProfile = ref(false);
  const savingProfile = ref(false);
  const profileFormValid = ref(false);
  const profileForm = ref({ displayName: '', email: '', remark: '' });

  const displayNameRule = (value: string) =>
    (value || '').length <= 100 || '姓名不能超过 100 个字符';
  const emailRule = (value: string) => (value || '').length <= 200 || '邮箱不能超过 200 个字符';
  const remarkRule = (value: string) => (value || '').length <= 500 || '备注不能超过 500 个字符';

  const startProfileEdit = () => {
    profileForm.value = {
      displayName: me.value?.displayName || '',
      email: me.value?.email || '',
      remark: me.value?.remark || '',
    };
    editingProfile.value = true;
  };

  const cancelProfileEdit = () => {
    editingProfile.value = false;
  };

  const saveProfile = async () => {
    if (!profileFormValid.value || savingProfile.value) return;
    savingProfile.value = true;
    try {
      const updated = (await userService.updateMe({
        displayName: profileForm.value.displayName.trim(),
        email: profileForm.value.email.trim(),
        remark: profileForm.value.remark.trim(),
      })) as AccountUser;
      me.value = updated;
      // The topbar reads the display name from the auth store, so keep it in sync.
      authStore.setDisplayName(updated.displayName || '');
      notify('账号资料已保存', 'success');
      editingProfile.value = false;
    } catch (error: any) {
      notify(error?.message || '保存账号资料失败', 'error');
    } finally {
      savingProfile.value = false;
    }
  };

  // Appearance
  const preference = computed(() => themeStore.preference);
  const displayedPreference = computed(() =>
    themeStore.preference === 'default' ? 'light' : themeStore.preference
  );
  const applyPreference = (value: unknown) => {
    if (value !== 'system' && value !== 'light' && value !== 'dark') return;
    themeStore.setPreference(value);
  };

  // Password form
  const passwordFormOpen = ref(false);
  const passwordFormValid = ref(false);
  const submittingPassword = ref(false);
  const currentPassword = ref('');
  const newPassword = ref('');
  const confirmPassword = ref('');

  const requiredRule = (value: string) => !!value || '此字段为必填项';
  const minLengthRule = (value: string) => (value || '').length >= 6 || '密码长度至少为6个字符';
  const matchRule = (value: string) => value === newPassword.value || '两次密码输入不一致';

  const openPasswordForm = () => {
    currentPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
    passwordFormOpen.value = true;
  };

  const closePasswordForm = () => {
    passwordFormOpen.value = false;
  };

  const submitPassword = async () => {
    if (!passwordFormValid.value || submittingPassword.value) return;
    if (newPassword.value !== confirmPassword.value) {
      notify('两次密码输入不一致', 'warning');
      return;
    }
    submittingPassword.value = true;
    try {
      // Why: /auth/change-password reports credential failures as HTTP 200 with a non-zero
      // business code, so the promise resolving does not mean the password changed.
      const res: any = await authService.changePassword(currentPassword.value, newPassword.value);
      if (res?.code !== 0) {
        const message = res?.code === 403 ? '当前密码不正确' : res?.message || '修改密码失败';
        notify(message, 'error');
        return;
      }
      notify('密码修改成功，请使用新密码重新登录', 'success');
      authStore.logout();
      router.push('/login');
    } catch (error: any) {
      notify(error?.message || '修改密码失败', 'error');
    } finally {
      submittingPassword.value = false;
    }
  };

  onMounted(loadProfile);
</script>

<style scoped lang="scss">
  .account-page {
    background: rgb(var(--v-theme-surface));
  }

  .account-col {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .account-loading {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 32px 4px;
    color: rgba(var(--v-theme-on-surface), 0.7);
  }

  .account-identity {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .account-avatar {
    font-weight: 600;
  }

  .account-identity-text {
    min-width: 0;
    flex: 1;
  }

  .account-name {
    font-size: 18px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .account-username {
    font-size: 13px;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .account-fields {
    margin-top: 4px;
    background: transparent;
  }

  .account-field-label {
    font-size: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .account-field-value {
    font-size: 14px;
    opacity: 1;
    color: rgb(var(--v-theme-on-surface));
    white-space: normal;
  }

  .account-field-value--muted {
    color: rgba(var(--v-theme-on-surface), 0.5);
  }

  .account-hint {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(var(--v-theme-on-surface), 0.6);
  }

  .account-hint--top {
    margin-top: 8px;
  }

  .account-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .account-grants-table {
    margin-top: 8px;
    background: transparent;
  }

  .account-grant-level {
    width: 88px;
    text-align: right;
    white-space: nowrap;
  }

  .account-profile-form {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 18px;
  }

  .account-profile-form .account-hint {
    margin-bottom: 4px;
  }

  .account-password-form {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 8px;
  }

  .account-form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .account-theme-group {
    margin-top: 6px;
  }
</style>

<route lang="json">
{
  "meta": {
    "title": "我的账号",
    "icon": "mdi-account-circle-outline",
    "requiresAuth": true,
    "navHidden": true
  }
}
</route>
