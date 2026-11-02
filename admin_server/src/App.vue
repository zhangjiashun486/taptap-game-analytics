<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const logged = ref(!!localStorage.getItem('token'))

watch(() => route.fullPath, () => { logged.value = !!localStorage.getItem('token') })

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || 'null') } catch { return null }
})

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<template>
  <div v-if="logged" class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">📊 TapTap 管理端</div>
      <nav class="menu">
        <router-link to="/">总览</router-link>
        <router-link to="/crawl">采集监控</router-link>
        <router-link to="/cluster">聚类分析</router-link>
        <router-link to="/behavior">用户行为</router-link>
        <router-link to="/qa">问答统计</router-link>
      </nav>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <span class="muted">管理员：{{ user?.username || 'admin' }}</span>
        <a @click="logout">退出登录</a>
      </header>
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
  <router-view v-else />
</template>
