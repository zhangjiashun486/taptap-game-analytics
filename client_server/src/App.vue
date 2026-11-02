<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const logged = ref(!!localStorage.getItem('token'))

// 路由变化时刷新登录状态（登录/退出后导航栏能及时更新）
watch(
  () => route.fullPath,
  () => { logged.value = !!localStorage.getItem('token') },
)

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
  <div class="layout">
    <header v-if="logged" class="navbar">
      <div class="navbar-inner">
        <div class="brand"><span>TapTap 游戏数据分析平台</span></div>
        <nav class="nav-links">
          <router-link to="/">首页</router-link>
          <router-link to="/recommend">推荐</router-link>
          <router-link to="/qa">问答</router-link>
          <router-link to="/profile">我的</router-link>
        </nav>
        <div class="nav-user">
          <span>{{ user?.nickname || user?.username }}</span>
          <a @click="logout">退出</a>
        </div>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>
