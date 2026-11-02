<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/api/request'

const data = ref<any>(null)
const error = ref('')

async function load() {
  try { data.value = await request.get('/api/admin/stats/overview') }
  catch (e: any) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <h2>总览</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="data" class="grid kpi-grid">
      <div class="card kpi"><div class="num">{{ data.games.toLocaleString() }}</div><div class="label">游戏总数</div></div>
      <div class="card kpi"><div class="num">{{ data.tags.toLocaleString() }}</div><div class="label">标签数</div></div>
      <div class="card kpi"><div class="num">{{ data.gameTags.toLocaleString() }}</div><div class="label">游戏-标签关系</div></div>
      <div class="card kpi"><div class="num">{{ data.users.toLocaleString() }}</div><div class="label">注册用户</div></div>
      <div class="card kpi"><div class="num">{{ data.behaviors.toLocaleString() }}</div><div class="label">用户行为记录</div></div>
      <div class="card kpi"><div class="num">{{ data.qaLogs.toLocaleString() }}</div><div class="label">问答调用</div></div>
      <div class="card kpi"><div class="num">{{ data.crawlLogs.toLocaleString() }}</div><div class="label">采集日志</div></div>
      <div class="card kpi"><div class="num">run #{{ data.latestClusterRun }}</div><div class="label">最新聚类批次</div></div>
    </div>
    <div v-else-if="!error" class="muted">加载中...</div>
  </div>
</template>
