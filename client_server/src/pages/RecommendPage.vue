<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const list = ref<any[]>([])
const mode = ref('')
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data: any = await request.get('/api/recommend', { params: { limit: 12 } })
    list.value = data.list || []
    mode.value = data.mode
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function formatFans(value: number) {
  const n = Number(value || 0)
  return n >= 10000 ? `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万` : n.toLocaleString()
}

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

onMounted(load)
</script>

<template>
  <div class="recommend-page">
    <section class="recommend-head card">
      <div>
        <div class="library-kicker">SMART RECOMMEND</div>
        <h1>为你推荐</h1>
        <p>{{ mode === 'COLD_START' ? '先逛逛首页，收藏或评分后推荐会更懂你。' : '根据你的收藏、玩过和评分记录生成。' }}</p>
      </div>
      <span class="mode-chip">{{ mode === 'COLD_START' ? '冷启动推荐' : '个性化推荐' }}</span>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="loading" class="grid">
      <div v-for="i in 8" :key="i" class="card skeleton-card">
        <div class="skeleton-cover"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    </div>

    <div v-else-if="list.length" class="grid">
      <article v-for="item in list" :key="item.gameId" class="card game-card" @click="router.push(`/game/${item.gameId}`)">
        <div class="cover-shell">
          <div class="cover-fallback">🎮</div>
          <img
            v-if="item.iconUrl"
            class="cover"
            :src="item.iconUrl"
            :alt="`${item.title} 游戏封面`"
            loading="lazy"
            decoding="async"
            referrerpolicy="no-referrer"
            @error="onImgError"
          />
          <span v-if="item.ratingScore != null" class="rating-badge">⭐ {{ item.ratingScore }}</span>
        </div>
        <div class="game-card-body">
          <h3 class="game-card-title">{{ item.title }}</h3>
          <p class="game-card-dev">{{ item.developer || '厂商信息待补充' }}</p>
          <div class="game-meta">
            <span class="score">⭐ {{ item.ratingScore ?? '暂无' }}</span>
            <span>关注 {{ formatFans(item.fansCount) }}</span>
          </div>
          <p v-if="item.reason" class="reason">💡 {{ item.reason }}</p>
        </div>
      </article>
    </div>

    <div v-else class="card center muted">暂时没有推荐结果</div>
  </div>
</template>

<style scoped>
.recommend-page { display: flex; flex-direction: column; gap: 22px; }
.recommend-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 28px 30px; color: #fff; border: 0; background: linear-gradient(125deg, #0f766e, #2563eb 58%, #7c3aed); box-shadow: 0 20px 45px rgba(37,99,235,.22); }
.recommend-head h1 { margin-top: 7px; font-size: clamp(26px, 4vw, 38px); }
.recommend-head p { margin-top: 8px; color: rgba(255,255,255,.76); font-size: 14px; }
.mode-chip { white-space: nowrap; padding: 9px 13px; border-radius: 999px; background: rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.25); font-size: 12px; font-weight: 700; }
.error { color: #dc2626; }
.reason { margin-top: 10px; padding: 8px 10px; border-radius: 10px; color: #4f46e5; background: #eef2ff; font-size: 12px; line-height: 1.5; }
@media (max-width: 640px) { .recommend-head { align-items: flex-start; flex-direction: column; } }
</style>