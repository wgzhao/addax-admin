<template>
  <v-container>
    <v-card>
      <v-card-title>修改密码</v-card-title>
      <v-card-text>
        <v-form ref="form" v-model="valid">
          <v-text-field
            v-model="currentPassword"
            label="当前密码"
            type="password"
            :rules="[rules.required]"
            required
          ></v-text-field>
          <v-text-field
            v-model="newPassword"
            label="新密码"
            type="password"
            :rules="[rules.required, rules.minLength]"
            required
          ></v-text-field>
          <v-text-field
            v-model="confirmPassword"
            label="确认新密码"
            type="password"
            :rules="[rules.required, rules.matchPassword]"
            required
          ></v-text-field>
          <v-btn :disabled="!valid" color="primary" @click="changePassword">提交</v-btn>
        </v-form>
      </v-card-text>
    </v-card>
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
