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

  const currentPassword = ref('')
  const newPassword = ref('')
  const confirmPassword = ref('')
  const valid = ref(false)

  const rules = {
    required: (value: string) => !!value || '此字段为必填项',
    minLength: (value: string) => value.length >= 6 || '密码长度至少为6个字符',
    matchPassword: (value: string) => value === newPassword.value || '两次密码输入不一致'
  }

  const changePassword = () => {
    if (!valid.value) return
    console.log('密码已修改:', { currentPassword, newPassword })
    // Add API call logic here
  }
</script>
