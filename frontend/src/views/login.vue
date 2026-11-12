<template>
  <div class="login-container">
    <section class="login-brand" aria-label="产品介绍">
      <div class="brand-lockup">
        <img :src="APP_LOGO" class="brand-mark" alt="" width="40" height="40" />
        <div class="brand-copy">
          <div class="brand-name">{{ APP_NAME }}</div>
          <div class="brand-sub">{{ APP_SUBTITLE }}</div>
        </div>
      </div>

      <div class="brand-message">
        <h1>让数据采集像水电一样可靠</h1>
        <p>
          统一纳管采集源、目标端与采集表，实时掌握运行健康、数据质量与 SLA，
          让数据团队把时间花在数据上，而不是排障上。
        </p>
      </div>

      <ul class="brand-points">
        <li>
          <v-icon size="18">mdi-database-sync-outline</v-icon>
          <span>统一纳管 · 支持关系型数据库、MongoDB、Elasticsearch</span>
        </li>
        <li>
          <v-icon size="18">mdi-chart-timeline-variant</v-icon>
          <span>可观测 · 失败、缺失与慢表一屏定位</span>
        </li>
        <li>
          <v-icon size="18">mdi-shield-check-outline</v-icon>
          <span>企业级 · 审计、权限与多节点高可用</span>
        </li>
      </ul>

      <div class="brand-meta">
        <span>v{{ APP_VERSION }}</span>
        <span>{{ APP_ENV_LABEL }}环境</span>
        <a :href="APP_DOCS_URL" target="_blank" rel="noopener noreferrer">使用文档</a>
      </div>
    </section>

    <section class="login-panel" aria-label="登录">
      <div class="panel-head">
        <h2>登录</h2>
        <p>使用系统账号登录以继续</p>
      </div>

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
        />
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
        />

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

      <div class="panel-foot">
        <span>遇到问题？</span>
        <a :href="APP_SUPPORT_URL" target="_blank" rel="noopener noreferrer">联系支持</a>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import router from '@/router';
  import { useAuthStore } from '@/stores/auth';
  import { authService } from '@/service/auth-service';
  import { notify } from '@/stores/notifier';
  import { APP_DOCS_URL, APP_LOGO, APP_NAME, APP_SUBTITLE, APP_SUPPORT_URL } from '@/config/branding';
  import { APP_ENV_LABEL, APP_VERSION } from '@/config/version';

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
        // Resolve the role before navigating; the first route may depend on it (for example a
        // scoped user is redirected away from the cross-source dashboard).
        await authStore.loadCurrentUser();
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
    display: grid;
    grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
    background: rgb(var(--v-theme-background));
  }

  .login-brand {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 28px;
    padding: 64px clamp(40px, 7vw, 112px);
    background:
      radial-gradient(circle at 18% 18%, rgba(var(--v-theme-primary), 0.16), transparent 42%),
      radial-gradient(circle at 82% 82%, rgba(var(--v-theme-info), 0.1), transparent 46%),
      rgb(var(--v-theme-surface));
    border-inline-end: 1px solid var(--ds-border-subtle);
  }

  .brand-lockup {
    display: inline-flex;
    align-items: center;
    gap: 12px;
  }

  .brand-mark {
    width: 40px;
    height: 40px;
    border-radius: 11px;
  }

  .brand-name {
    font-size: 18px;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
    letter-spacing: 0.1px;
  }

  .brand-sub {
    margin-top: 3px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .brand-message h1 {
    margin: 0;
    max-width: 560px;
    font-size: clamp(26px, 3vw, 38px);
    line-height: 1.22;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: rgb(var(--v-theme-on-surface));
  }

  .brand-message p {
    max-width: 520px;
    margin: 14px 0 0;
    font-size: 14px;
    line-height: 1.8;
    color: var(--ds-text-2);
  }

  .brand-points {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin: 0;
    padding: 0;
    list-style: none;
    color: var(--ds-text-2);
    font-size: 13px;
  }

  .brand-points li {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .brand-points .v-icon {
    color: rgb(var(--v-theme-primary));
  }

  .brand-meta {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .brand-meta a {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }

  .login-panel {
    align-self: center;
    width: min(420px, calc(100% - 48px));
    margin: 0 auto;
    padding: 40px 36px 32px;
    border-radius: 16px;
    border: 1px solid var(--ds-border-default);
    background: rgb(var(--v-theme-surface));
    box-shadow: 0 18px 48px rgba(2, 6, 23, 0.08);
  }

  .panel-head h2 {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-head p {
    margin: 8px 0 24px;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .login-form {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .login-btn {
    margin-top: 18px;
    height: 46px;
    font-weight: 600;
    letter-spacing: 0.4px;
  }

  .panel-foot {
    display: flex;
    justify-content: center;
    gap: 4px;
    margin-top: 22px;
    font-size: 12px;
    color: var(--ds-text-3);
  }

  .panel-foot a {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
  }

  @media (max-width: 960px) {
    .login-container {
      grid-template-columns: 1fr;
    }

    .login-brand {
      display: none;
    }

    .login-panel {
      margin-top: 12vh;
      margin-bottom: 12vh;
    }
  }

  @media (max-width: 480px) {
    .login-panel {
      width: calc(100% - 32px);
      padding: 28px 20px 24px;
    }
  }
</style>
