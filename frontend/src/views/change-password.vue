<template>
  <v-container class="change-password-page">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="section-card" elevation="0">
          <v-card-title class="section-title">
            <v-icon class="mr-2" color="primary">mdi-lock-reset</v-icon>
            修改密码
          </v-card-title>
          <v-divider />
          <v-card-text class="section-body">
            <v-form ref="form" v-model="valid" class="form-stack">
              <v-text-field
                v-model="currentPassword"
                label="当前密码"
                type="password"
                :rules="[rules.required]"
                required
                variant="outlined"
                density="compact"
              ></v-text-field>
              <v-text-field
                v-model="newPassword"
                label="新密码"
                type="password"
                :rules="[rules.required, rules.minLength]"
                required
                variant="outlined"
                density="compact"
              ></v-text-field>
              <v-text-field
                v-model="confirmPassword"
                label="确认新密码"
                type="password"
                :rules="[rules.required, rules.matchPassword]"
                required
                variant="outlined"
                density="compact"
              ></v-text-field>
              <div class="action-bar">
                <v-btn variant="tonal" color="secondary" @click="router.back()">返回</v-btn>
                <v-btn :disabled="!valid" color="primary" @click="changePassword">提交</v-btn>
              </div>
            </v-form>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import { authService } from '@/service/auth-service'
  import { notify } from '@/stores/notifier'
  import router from '@/router'
  import { useAuthStore } from '@/stores/auth'

  const currentPassword = ref('')
  const newPassword = ref('')
  const confirmPassword = ref('')
  const valid = ref(false)

  const rules = {
    required: (value: string) => !!value || '此字段为必填项',
    minLength: (value: string) => value.length >= 6 || '密码长度至少为6个字符',
    matchPassword: (value: string) => value === newPassword.value || '两次密码输入不一致'
  }

  const authStore = useAuthStore()

  const changePassword = async () => {
    if (!valid.value) return
    if (newPassword.value !== confirmPassword.value) {
      notify('两次密码输入不一致', 'warning')
      return
    }

    try {
      const res: any = await authService.changePassword(currentPassword.value, newPassword.value)
      if (res && res.code === 0) {
        notify('密码修改成功，请重新登录', 'success')
        // clear local token and redirect to login
        authStore.logout()
        router.push('/login')
      } else {
        const msg = res && res.message ? res.message : '修改密码失败'
        notify(msg, 'error')
      }
    }
    catch (err: any) {
      notify('修改密码失败: ' + (err.message || err), 'error')
    }
  }
</script>
<style scoped>
.change-password-page {
  background: rgb(var(--v-theme-surface));
}

.section-card {
  background: rgb(var(--v-theme-surface-variant));
  border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: rgb(var(--v-theme-on-surface));
}

.section-body {
  background: transparent;
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
}
</style>
