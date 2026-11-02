<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const game = ref<any>(null)
const tags = ref<string[]>([])
const message = ref('')
const loading = ref(false)

function formatNumber(value: any) {
  const n = Number(value || 0)
  return n >= 10000 ? `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万` : n.toLocaleString()
}

function getYear(value: any) {
  return value ? String(value).slice(0, 4) : '未知'
}

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

async function load() {
  loading.value = true
  try {
    const data: any = await request.get(`/api/game/${route.params.id}`)
    game.value = data.game
    tags.value = data.tags || []
  } catch (e: any) {
    message.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function record(type: string, score?: number) {
  try {
    await request.post('/api/behavior', { gameId: game.value.gameId, behaviorType: type, score })
    message.value = type === 'FAVORITE' ? '已加入收藏' : type === 'PLAYED' ? '已标记为玩过' : `评分成功：${score} 分`
  } catch (e: any) {
    message.value = e.message || '操作失败'
  }
}

function rate() {
  const input = prompt('请输入评分（1~10）')
  if (!input) return
  const score = Number(input)
  if (Number.isNaN(score) || score < 1 || score > 10) {
    message.value = '评分必须是 1~10 的数字'
    return
  }
  record('RATE', score)
}

onMounted(load)
</script>

<template>
  <div class="detail-page">
    <button class="back-link" @click="router.back()">← 返回游戏库</button>

    <div v-if="loading" class="card loading-card muted">正在加载游戏资料...</div>

    <template v-else-if="game">
      <article class="detail-hero card">
        <div class="detail-cover cover-shell">
          <div class="cover-fallback">🎮</div>
          <img
            v-if="game.iconUrl"
            class="cover"
            :src="game.iconUrl"
            :alt="`${game.title} 游戏封面`"
            decoding="async"
            referrerpolicy="no-referrer"
            @error="onImgError"
          />
        </div>

        <div class="detail-info">
          <div class="detail-label">GAME PROFILE</div>
          <h1>{{ game.title }}</h1>
          <p class="detail-dev">{{ game.developer || '厂商信息待补充' }} · {{ game.platform || '多平台' }}</p>

          <div class="detail-stats">
            <div class="stat-item"><b>⭐ {{ game.ratingScore ?? '暂无' }}</b><span>综合评分</span></div>
            <div class="stat-item"><b>{{ formatNumber(game.fansCount) }}</b><span>关注人数</span></div>
            <div class="stat-item"><b>{{ formatNumber(game.reviewCount) }}</b><span>评论数量</span></div>
          </div>

          <div class="tags">
            <span v-for="tag in tags" :key="tag" class="tag">{{ tag }}</span>
          </div>

          <div class="actions">
            <button class="btn" @click="record('FAVORITE')">☆ 收藏游戏</button>
            <button class="btn ghost" @click="record('PLAYED')">✓ 标记玩过</button>
            <button class="btn ghost" @click="rate()">★ 给游戏评分</button>
          </div>
          <p v-if="message" class="msg">{{ message }}</p>
        </div>
      </article>

      <div class="detail-grid">
        <section class="card intro-card">
          <div class="section-title">
            <span>01</span>
            <h2>游戏简介</h2>
          </div>
          <p class="intro-text">{{ game.intro || '暂无简介' }}</p>
        </section>

        <aside class="card facts-card">
          <div class="section-title">
            <span>02</span>
            <h2>数据摘要</h2>
          </div>
          <dl class="facts-list">
            <div><dt>发行年份</dt><dd>{{ getYear(game.releasedTime) }}</dd></div>
            <div><dt>热度</dt><dd>{{ formatNumber(game.hitsTotal) }}</dd></div>
            <div><dt>愿望单</dt><dd>{{ formatNumber(game.wishCount) }}</dd></div>
            <div><dt>预约数</dt><dd>{{ formatNumber(game.reserveCount) }}</dd></div>
            <div><dt>付费方式</dt><dd>{{ game.price || '详见 TapTap' }}</dd></div>
          </dl>
        </aside>
      </div>
    </template>

    <div v-else class="card center error">{{ message || '没有找到这款游戏' }}</div>
  </div>
</template>

<style scoped>
.detail-page { display: flex; flex-direction: column; gap: 18px; }
.back-link { width: fit-content; border: 0; color: #4f46e5; background: transparent; font-size: 14px; font-weight: 700; padding: 4px 0; }
.loading-card { padding: 60px; text-align: center; }
.detail-hero { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: 30px; padding: 30px; background: linear-gradient(135deg, rgba(255,255,255,.98), rgba(239,246,255,.96)); }
.detail-cover { width: 230px; border-radius: 22px; box-shadow: 0 20px 38px rgba(30,64,175,.18); }
.detail-info { display: flex; flex-direction: column; justify-content: center; min-width: 0; }
.detail-label { color: #6366f1; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.detail-info h1 { margin-top: 8px; font-size: clamp(28px, 4vw, 42px); line-height: 1.16; letter-spacing: -.03em; }
.detail-dev { margin-top: 8px; color: #64748b; font-size: 14px; }
.detail-stats { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 22px; }
.stat-item { min-width: 112px; padding: 12px 15px; border-radius: 14px; background: rgba(255,255,255,.9); border: 1px solid #e7edf7; }
.stat-item b { display: block; color: #1e293b; font-size: 17px; }
.stat-item span { display: block; margin-top: 4px; color: #94a3b8; font-size: 11px; }
.tags { margin-top: 18px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 22px; }
.msg { margin-top: 12px; color: #059669; font-size: 13px; font-weight: 650; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(280px, .75fr); gap: 18px; }
.section-title { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.section-title span { width: 30px; height: 30px; display: grid; place-items: center; border-radius: 10px; color: #fff; background: linear-gradient(135deg, #2563eb, #7c3aed); font-size: 11px; font-weight: 800; }
.section-title h2 { font-size: 20px; }
.intro-text { color: #475569; line-height: 1.95; font-size: 14px; white-space: pre-wrap; }
.facts-list { display: flex; flex-direction: column; }
.facts-list > div { display: flex; justify-content: space-between; gap: 16px; padding: 11px 0; border-bottom: 1px solid #eef2f7; font-size: 13px; }
.facts-list > div:last-child { border-bottom: 0; }
.facts-list dt { color: #94a3b8; }
.facts-list dd { color: #334155; font-weight: 700; text-align: right; }
.error { color: #dc2626; }
@media (max-width: 820px) {
  .detail-hero { grid-template-columns: 1fr; }
  .detail-cover { width: min(230px, 70vw); }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>