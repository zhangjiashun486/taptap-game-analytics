<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const username = ref('admin')
const password = ref('123456')
const loading = ref(false)
const message = ref('')

async function login() {
  loading.value = true; message.value = ''
  try {
    const data: any = await request.post('/api/admin/login', {
      username: username.value, password: password.value,
    })
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data))
    router.push('/')
  } catch (e: any) {
    message.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="card login-card">
      <h2 class="login-title">📊 TapTap 管理端</h2>
      <p class="muted login-sub">管理员登录（账号在 application.properties 中配置）</p>
      <div class="form">
        <input v-model="username" class="input" placeholder="管理员用户名" />
        <input v-model="password" class="input" type="password" placeholder="密码" @keyup.enter="login" />
        <button class="btn block" :disabled="loading" @click="login">{{ loading ? '登录中...' : '登 录' }}</button>
        <p v-if="message" class="error">{{ message }}</p>
        <p class="muted tip">默认账号：admin / 123456</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrap { display: flex; justify-content: center; padding-top: 12vh; }
.login-card { width: 420px; padding: 32px; }
.login-title { text-align: center; color: #2563eb; }
.login-sub { text-align: center; margin-top: 8px; }
.form { display: flex; flex-direction: column; gap: 12px; margin-top: 24px; }
.block { width: 100%; padding: 10px 0; }
.tip { text-align: center; }
</style>
