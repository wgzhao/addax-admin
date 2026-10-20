<template>
  <div class="login-container">
    <div class="login-panel">
      <div class="logo-container">
        <img src="/logo.png" alt="Addax Admin" class="logo" />
      </div>

      <h1 class="login-title">统一采集管理系统</h1>
      <p class="login-subtitle">使用系统账号登录以继续</p>

      <v-form v-model="form" class="login-form" @submit.prevent="login">
        <v-text-field
          v-model="auth.username"
          :readonly="loading"
          :rules="[required]"
          class="modern-input"
          clearable
          autocomplete="username"
          autocorrect="off"
          autocapitalize="none"
          spellcheck="false"
          label="用户名"
          prepend-inner-icon="mdi-account-circle"
          variant="outlined"
          density="comfortable"
          flat
          color="primary"
        ></v-text-field>

        <v-text-field
          v-model="auth.password"
          type="password"
          :readonly="loading"
          :rules="[required]"
          class="modern-input"
          clearable
          autocomplete="current-password"
          autocorrect="off"
          autocapitalize="none"
          spellcheck="false"
          label="密码"
          prepend-inner-icon="mdi-lock"
          variant="outlined"
          density="comfortable"
          flat
          color="primary"
        ></v-text-field>

        <v-btn
          :disabled="!form"
          :loading="loading"
          block
          color="primary"
          size="large"
          type="submit"
          variant="flat"
          class="login-btn"
        >
          登录
        </v-btn>
      </v-form>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import router from '@/router';
  import { useAuthStore } from '@/stores/auth';

  import { authService } from '@/service/auth-service';
  import { notify } from '@/stores/notifier';

  const form = ref(false);
  const auth = ref({
    username: '',
    password: '',
  });

  const loading = ref<boolean>(false);
  const authStore = useAuthStore();

  function required(v: string): boolean | string {
    return !!v || '此项为必填项';
  }

  async function login() {
    loading.value = true;
    try {
      const res: any = await authService.login(auth.value);
      // 后端返回 ApiResponse<T>，约定 code==0 为成功
      if (res && res.code === 0 && res.data) {
        authStore.setToken(res.data);
        authStore.setUserName(auth.value.username);
        await router.push({ path: '/' });
      } else {
        const msg = res && res.message ? res.message : '登录失败';
        notify(msg, 'error');
      }
    } catch (err: any) {
      // 拦截器已把后端业务消息（如"账号或密码不正确"）透传为 err.message
      notify(err?.message || '登录失败', 'error');
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .login-container {
    min-height: 100vh;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
    background: rgb(var(--v-theme-background));
  }

  .login-panel {
    width: 100%;
    max-width: 420px;
    padding: 40px 36px 32px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
    background: rgb(var(--v-theme-surface));
  }

  .logo-container {
    text-align: center;
    margin-bottom: 24px;
  }

  .logo {
    width: 104px;
  }

  .login-title {
    margin: 0;
    text-align: center;
    font-size: 22px;
    font-weight: 600;
    letter-spacing: 0.2px;
    color: rgb(var(--v-theme-on-surface));
  }

  .login-subtitle {
    margin: 8px 0 28px;
    text-align: center;
    font-size: 14px;
    color: rgb(var(--v-theme-on-surface-variant));
  }

  .login-form {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .login-btn {
    margin-top: 20px;
    height: 46px;
    font-weight: 600;
    letter-spacing: 0.4px;
  }

  @media (max-width: 480px) {
    .login-panel {
      padding: 28px 20px 24px;
    }
  }
</style>
