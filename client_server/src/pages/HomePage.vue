<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const limit = 12
const keyword = ref('')
const sort = ref('fans')
const loading = ref(false)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit)))
const displayTotal = computed(() => total.value ? total.value.toLocaleString() : '10,219')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data: any = await request.get('/api/game/page', {
      params: { page: page.value, limit, keyword: keyword.value, sort: sort.value },
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e: any) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function changeSort() { page.value = 1; load() }
function prev() { if (page.value > 1) { page.value--; load(); window.scrollTo({ top: 0, behavior: 'smooth' }) } }
function next() { if (page.value < totalPages.value) { page.value++; load(); window.scrollTo({ top: 0, behavior: 'smooth' }) } }
function openGame(id: number) { router.push(`/game/${id}`) }
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
  <div class="home-page">
    <section class="hero card">
      <div class="hero-copy">
        <span class="hero-kicker">TAPTAP GAME INSIGHTS</span>
        <h1>发现下一款<br><em>值得投入</em>的好游戏</h1>
        <p class="hero-desc">
          基于 TapTap 游戏库的评分、关注度、评论与标签数据，帮助你快速筛选热门大作、口碑佳作和潜力新品。
        </p>
        <div class="hero-metrics">
          <div class="hero-metric"><b>{{ displayTotal }}</b><span>游戏数据</span></div>
          <div class="hero-metric"><b>607</b><span>游戏标签</span></div>
          <div class="hero-metric"><b>5</b><span>聚类分群</span></div>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="hero-console">🎮</div>
        <span class="hero-chip one">高分推荐</span>
        <span class="hero-chip two">热度分析</span>
        <span class="hero-chip three">个性化推荐</span>
      </div>
    </section>

    <section class="library-head">
      <div>
        <div class="library-kicker">GAME LIBRARY</div>
        <h2>探索游戏库</h2>
      </div>
      <span class="library-count">当前共 {{ total.toLocaleString() }} 款游戏</span>
    </section>

    <div class="card toolbar">
      <input v-model="keyword" class="input search" placeholder="搜索游戏名称，如：原神" @keyup.enter="search" />
      <select v-model="sort" class="input select" @change="changeSort">
        <option value="fans">按关注数</option>
        <option value="rating">按评分</option>
        <option value="review">按评论数</option>
        <option value="release">按发行时间</option>
      </select>
      <button class="btn" @click="search">搜索游戏</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading" class="grid">
      <div v-for="i in 8" :key="i" class="card skeleton-card">
        <div class="skeleton-cover"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line short"></div>
      </div>
    </div>

    <div v-else-if="list.length" class="grid">
      <article v-for="game in list" :key="game.gameId" class="card game-card" @click="openGame(game.gameId)">
        <div class="cover-shell">
          <div class="cover-fallback">🎮</div>
          <img
            v-if="game.iconUrl"
            class="cover"
            :src="game.iconUrl"
            :alt="`${game.title} 游戏封面`"
            loading="lazy"
            decoding="async"
            referrerpolicy="no-referrer"
            @error="onImgError"
          />
          <span v-if="game.ratingScore != null" class="rating-badge">⭐ {{ game.ratingScore }}</span>
        </div>
        <div class="game-card-body">
          <h3 class="game-card-title">{{ game.title }}</h3>
          <p class="game-card-dev">{{ game.developer || '厂商信息待补充' }}</p>
          <div class="game-meta">
            <span class="score">⭐ {{ game.ratingScore ?? '暂无评分' }}</span>
            <span>关注 {{ formatFans(game.fansCount) }}</span>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="card center muted">没有找到符合条件的游戏</div>

    <div v-if="total > 0" class="card pager">
      <button class="btn gray" :disabled="page <= 1" @click="prev">上一页</button>
      <span class="muted">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <button class="btn" :disabled="page >= totalPages" @click="next">下一页</button>
    </div>
  </div>
</template>