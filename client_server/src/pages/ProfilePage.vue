<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const user = ref<any>(null)
const behaviors = ref<any[]>([])
const loading = ref(false)
const error = ref('')

const typeText: Record<string, string> = {
  VIEW: '浏览', FAVORITE: '收藏', LIKE: '点赞', PLAYED: '玩过', RATE: '评分',
}

async function load() {
  loading.value = true; error.value = ''
  try {
    user.value = await request.get('/api/user/me')
    behaviors.value = (await request.get('/api/behavior/mine')) as any
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}

onMounted(load)
</script>

<template>
  <div>
    <h2>个人中心</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="user" class="card user-card">
      <div class="avatar">{{ (user.nickname || user.username || '?').slice(0, 1) }}</div>
      <div>
        <div class="name">{{ user.nickname || user.username }}</div>
        <div class="muted">账号：{{ user.username }} · 角色：{{ user.role }}</div>
      </div>
      <button class="btn gray logout" @click="logout">退出登录</button>
    </div>

    <div class="card behaviors">
      <h3>我的行为记录（{{ behaviors.length }} 条）</h3>
      <p v-if="loading" class="muted">加载中...</p>
      <table v-else class="table">
        <thead><tr><th>游戏</th><th>行为</th><th>评分</th><th>时间</th></tr></thead>
        <tbody>
          <tr v-for="b in behaviors" :key="b.id">
            <td>{{ b.gameTitle }}</td>
            <td>{{ typeText[b.behaviorType] || b.behaviorType }}</td>
            <td>{{ b.score ?? '-' }}</td>
            <td>{{ b.createdAt }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
h2 { margin-bottom: 16px; }
.error { color: #dc2626; margin-bottom: 12px; }
.user-card { display: flex; align-items: center; gap: 16px; }
.avatar { width: 56px; height: 56px; border-radius: 50%; background: #2563eb; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.name { font-size: 18px; font-weight: 600; }
.logout { margin-left: auto; }
.behaviors { margin-top: 16px; }
.behaviors h3 { margin-bottom: 12px; }
.table { width: 100%; border-collapse: collapse; font-size: 14px; }
.table th, .table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f2f5; }
.table th { color: #6b7280; font-weight: 500; }
</style>
