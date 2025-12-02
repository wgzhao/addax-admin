<template>
  <div class="login-container">
    <div class="background-layer">
      <div class="gradient-overlay"></div>
      <div class="tech-particles"></div>
    </div>

    <v-sheet class="login-sheet pa-16" rounded="xl" elevation="24">
      <v-card class="login-card px-10 py-12" max-width="480">
        <div class="logo-container mb-8">
          <img src="/logo.png" alt="Logo" class="logo" />
        </div>

        <h2 class="login-title mb-10">Addax Admin Access</h2>

        <v-form v-model="form" @submit.prevent="login">
          <v-text-field
            v-model="auth.username"
            :readonly="loading"
            :rules="[required]"
            class="modern-input mb-8"
            clearable
            label="Username/Email"
            prepend-inner-icon="mdi-account-circle"
            variant="outlined"
            density="comfortable"
            flat
            color="cyan"
            bg-color="rgba(255, 255, 255, 0.1)"
            base-color="white"
          ></v-text-field>

          <v-text-field
            type="password"
            v-model="auth.password"
            :readonly="loading"
            :rules="[required]"
            class="modern-input mb-10"
            clearable
            label="Password"
            prepend-inner-icon="mdi-lock"
            variant="outlined"
            density="comfortable"
            flat
            color="cyan"
            bg-color="rgba(255, 255, 255, 0.1)"
            base-color="white"
          ></v-text-field>

          <v-btn
            :disabled="!form"
            :loading="loading"
            block
            color="primary"
            size="large"
            type="submit"
            variant="elevated"
            class="login-btn"
          >
            <span>Login</span>
            <v-icon right class="ml-2">mdi-login</v-icon>
          </v-btn>
        </v-form>

        <!-- <div class="additional-links mt-6">
          <v-btn type="text" color="grey" >
            Forgot Credentials?
          </v-btn>
        </div> -->
      </v-card>
    </v-sheet>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue'
  import router from '@/router'
  import { useAuthStore } from '@/stores/auth'

  import { authService } from '@/service/auth-service'
  import { notify } from '@/stores/notifier'

  const form = ref(false)
  const auth = ref({
    username: 'admin',
    password: '389f89beb8d7'
  })

  const loading = ref<boolean>(false)
  const sessionExpired = ref<boolean>(false)
  const authStore = useAuthStore()

  function onSubmit() {
    if (!form.value) return
    loading.value = true
    setTimeout(() => (loading.value = false), 2000)
  }
  function required(v: string): boolean | string {
    return !!v || 'Field is required'
  }

  function login() {
    // reset the error message
    // clearMessages();
    authService.login(auth.value).then((res) => {
      if (res.data == null) {
        notify('登录失败', 'error')
      } else {
        // let data = {
        //   token: res.data.accessToken,
        //   username: auth.value.username,
        //   role: ""
        // };
        authStore.setToken(res.data)
        authStore.setUserName(auth.value.username)
        // localStorage.setItem("userinfo", JSON.stringify(data));
        router.push({ path: '/' })
      }
      router.push({ path: '/' })
    })

    // extract the user role from the token
    // const userRole = extractUserRoleFromToken(accessToken);

    // call the stores login method this will update the stores state
    // authStore.login(userRole);

    // redirect to the home page
    // await router.push("/");
  }
</script>

<style scoped>
  .login-container {
    height: 100vh;
    width: 100vw;
    display: flex;
    align-items: center;
    justify-content: center;
    position: fixed;
    top: 0;
    left: 0;
    overflow: hidden;
    background: #0a0a23;
  }

  .background-layer {
    position: absolute;
    width: 100%;
    height: 100%;
    background: linear-gradient(135deg, #1e1e5f 0%, #2a4066 50%, #0a0a23 100%);
    animation: gradientFlow 20s ease infinite;
  }

  .gradient-overlay {
    position: absolute;
    width: 100%;
    height: 100%;
    background: radial-gradient(
      circle at center,
      rgba(255, 255, 255, 0.05) 0%,
      rgba(0, 0, 0, 0.9) 70%
    );
  }

  .tech-particles {
    position: absolute;
    width: 100%;
    height: 100%;
    background: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600"><circle cx="100" cy="100" r="2" fill="rgba(255,255,255,0.2)"/><circle cx="700" cy="500" r="3" fill="rgba(255,255,255,0.15)"/><circle cx="400" cy="300" r="2" fill="rgba(255,255,255,0.25)"/></svg>')
      repeat;
    animation: particleFloat 30s linear infinite;
  }

  .login-sheet {
    background: rgba(30, 30, 95, 0.2);
    /* 调整为更深的半透明底色 */
    backdrop-filter: blur(15px);
    border: 1px solid rgba(255, 255, 255, 0.15);
    min-width: 600px;
    position: relative;
    z-index: 1;
  }

  .login-card {
    background: linear-gradient(145deg, #2a2a72 0%, #1e1e5f 100%);
    /* 深色渐变 */
    border-radius: 20px !important;
    box-shadow: 0 15px 40px rgba(0, 0, 0, 0.4);
  }

  .logo-container {
    text-align: center;
  }

  .logo {
    width: 120px;
    transition: transform 0.4s ease;
  }

  .logo:hover {
    transform: scale(1.1) rotate(5deg);
  }

  .login-title {
    text-align: center;
    color: #ffffff;
    /* 白色标题 */
    font-weight: 700;
    font-size: 28px;
    letter-spacing: 1.5px;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .modern-input {
    transition: all 0.3s ease;
  }

  .modern-input:hover {
    transform: translateX(4px);
  }

  .login-btn {
    border-radius: 30px;
    height: 50px;
    text-transform: none;
    font-weight: 600;
    letter-spacing: 1.5px;
    background: linear-gradient(90deg, #00bcd4 0%, #4dd0e1 100%);
    /* 青色渐变 */
    color: #ffffff;
    transition: all 0.4s ease;
  }

  .login-btn:hover {
    transform: scale(1.02);
    box-shadow: 0 8px 20px rgba(0, 188, 212, 0.4);
  }

  .additional-links {
    text-align: center;
  }

  .additional-links .v-btn {
    color: #b0bec5;
    /* 浅灰色链接 */
  }

  @keyframes gradientFlow {
    0% {
      background-position: 0% 0%;
    }

    50% {
      background-position: 100% 100%;
    }

    100% {
      background-position: 0% 0%;
    }
  }

  @keyframes particleFloat {
    0% {
      transform: translateY(0);
    }

    100% {
      transform: translateY(-1000px);
    }
  }
</style>
