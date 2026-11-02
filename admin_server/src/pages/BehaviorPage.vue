<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/request'

const data = ref<any>(null)
const error = ref('')
const pieRef = ref<HTMLDivElement | null>(null)
const lineRef = ref<HTMLDivElement | null>(null)
let pieChart: echarts.ECharts | null = null
let lineChart: echarts.ECharts | null = null

async function load() {
  try {
    data.value = await request.get('/api/admin/stats/behavior')
    await nextTick()
    renderCharts()
  } catch (e: any) { error.value = e.message }
}

function renderCharts() {
  if (pieRef.value) {
    pieChart = pieChart || echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['35%', '65%'],
        data: (data.value?.typeCounts || []).map((t: any) => ({ name: t.behaviorType, value: t.cnt })),
      }],
    })
  }
  if (lineRef.value) {
    lineChart = lineChart || echarts.init(lineRef.value)
    const daily = data.value?.daily || []
    lineChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: daily.map((d: any) => String(d.day).slice(0, 10)) },
      yAxis: { type: 'value' },
      series: [{ type: 'line', smooth: true, areaStyle: {}, data: daily.map((d: any) => d.cnt) }],
    })
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2>用户行为分析</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="data">
      <div class="grid" style="grid-template-columns: 1fr 1fr;">
        <div class="card"><h3>行为类型分布</h3><div ref="pieRef" class="chart"></div></div>
        <div class="card"><h3>近 7 天行为趋势</h3><div ref="lineRef" class="chart"></div></div>
      </div>
      <div class="grid mt" style="grid-template-columns: 1fr 1fr;">
        <div class="card">
          <h3>热门游戏 Top10</h3>
          <table class="table">
            <thead><tr><th>游戏</th><th>行为次数</th></tr></thead>
            <tbody><tr v-for="g in data.topGames" :key="g.gameId"><td>{{ g.title }}</td><td>{{ g.cnt }}</td></tr></tbody>
          </table>
        </div>
        <div class="card">
          <h3>活跃用户 Top10</h3>
          <table class="table">
            <thead><tr><th>用户</th><th>行为次数</th></tr></thead>
            <tbody><tr v-for="u in data.activeUsers" :key="u.id"><td>{{ u.nickname || u.username }}</td><td>{{ u.cnt }}</td></tr></tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mt { margin-top: 16px; }
</style>
