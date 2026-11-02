<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const tab = ref<'login' | 'register'>('login')
const form = ref({ username: '', password: '', nickname: '' })
const loading = ref(false)
const message = ref('')

async function login() {
  if (!form.value.username || !form.value.password) { message.value = '请输入用户名和密码'; return }
  loading.value = true; message.value = ''
  try {
    const data: any = await request.post('/api/user/login', {
      username: form.value.username, password: form.value.password,
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

async function register() {
  if (!form.value.username || !form.value.password) { message.value = '请输入用户名和密码'; return }
  loading.value = true; message.value = ''
  try {
    await request.post('/api/user/register', {
      username: form.value.username, password: form.value.password, nickname: form.value.nickname,
    })
    message.value = '注册成功，请登录'
    tab.value = 'login'
  } catch (e: any) {
    message.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="intro-logo">🎮</div>
      <span class="intro-kicker">TAPTAP GAME ANALYTICS</span>
      <h1>把游戏数据<br>变成你的<span>探索地图</span></h1>
      <p>浏览 10,219 款游戏，查看评分与热度，收藏喜欢的作品，并获得越来越懂你的推荐。</p>
      <div class="intro-features">
        <span>✓ 万级游戏库</span>
        <span>✓ 聚类分析</span>
        <span>✓ 智能推荐</span>
      </div>
    </section>

    <section class="card login-card">
      <div class="login-head">
        <span class="mini-mark">T</span>
        <div>
          <h2>{{ tab === 'login' ? '欢迎回来' : '创建新账号' }}</h2>
          <p>{{ tab === 'login' ? '登录后继续探索你的游戏世界' : '注册后即可收藏和评分' }}</p>
        </div>
      </div>

      <div class="tabs">
        <button :class="['tab', tab === 'login' ? 'active' : '']" @click="tab = 'login'">登录</button>
        <button :class="['tab', tab === 'register' ? 'active' : '']" @click="tab = 'register'">注册</button>
      </div>

      <div class="form">
        <input v-model="form.username" class="input" placeholder="用户名" />
        <input v-model="form.password" class="input" type="password" placeholder="密码（至少 6 位）" />
        <input v-if="tab === 'register'" v-model="form.nickname" class="input" placeholder="昵称（可选）" />
        <button v-if="tab === 'login'" class="btn block" :disabled="loading" @click="login">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
        <button v-else class="btn block" :disabled="loading" @click="register">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
        <p v-if="message" class="msg">{{ message }}</p>
        <p class="muted demo-tip">演示账号：<b>demo_user_01</b> / <b>123456</b></p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page { min-height: calc(100vh - 130px); display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(380px, .72fr); align-items: center; gap: 28px; padding: 24px 0; }
.login-intro { position: relative; overflow: hidden; min-height: 520px; padding: 52px; border-radius: 28px; color: #fff; background: linear-gradient(135deg, #1d4ed8 0%, #4f46e5 52%, #7c3aed 100%); box-shadow: 0 28px 70px rgba(37,99,235,.25); }
.login-intro::after { content: ""; position: absolute; width: 360px; height: 360px; right: -100px; bottom: -130px; border-radius: 50%; background: rgba(34,211,238,.22); box-shadow: -130px -180px 0 rgba(255,255,255,.08); }
.intro-logo { width: 64px; height: 64px; display: grid; place-items: center; border-radius: 20px; font-size: 32px; background: rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.25); }
.intro-kicker { display: inline-block; margin-top: 32px; color: #a5f3fc; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.login-intro h1 { position: relative; z-index: 1; margin-top: 14px; font-size: clamp(34px, 4.4vw, 56px); line-height: 1.15; letter-spacing: -.035em; }
.login-intro h1 span { color: #a5f3fc; }
.login-intro p { position: relative; z-index: 1; max-width: 560px; margin-top: 20px; color: rgba(255,255,255,.75); line-height: 1.85; font-size: 15px; }
.intro-features { position: relative; z-index: 1; display: flex; flex-wrap: wrap; gap: 10px; margin-top: 34px; }
.intro-features span { padding: 8px 12px; border-radius: 999px; background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.2); font-size: 12px; }
.login-card { width: 100%; padding: 34px; }
.login-head { display: flex; align-items: center; gap: 13px; }
.mini-mark { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 14px; color: #fff; background: linear-gradient(135deg, #2563eb, #7c3aed); font-size: 20px; font-weight: 900; box-shadow: 0 8px 18px rgba(37,99,235,.22); }
.login-head h2 { font-size: 22px; }
.login-head p { margin-top: 4px; color: #94a3b8; font-size: 12px; }
.tabs { display: flex; gap: 8px; margin: 28px 0 20px; padding: 5px; border-radius: 13px; background: #f1f5f9; }
.tab { flex: 1; padding: 9px 0; border: 0; border-radius: 10px; color: #64748b; background: transparent; font-size: 14px; font-weight: 650; }
.tab.active { color: #fff; background: linear-gradient(135deg, #2563eb, #7c3aed); box-shadow: 0 7px 16px rgba(37,99,235,.2); }
.form { display: flex; flex-direction: column; gap: 13px; }
.block { width: 100%; padding: 11px 0; }
.msg { color: #dc2626; font-size: 13px; text-align: center; }
.demo-tip { text-align: center; margin-top: 4px; }
@media (max-width: 900px) { .login-page { grid-template-columns: 1fr; } .login-intro { min-height: auto; padding: 34px; } }
@media (max-width: 520px) { .login-page { padding: 0; } .login-intro, .login-card { padding: 25px; border-radius: 20px; } }
</style>