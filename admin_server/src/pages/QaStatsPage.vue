<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '@/api/request'

const data = ref<any>(null)
const error = ref('')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

async function load() {
  try {
    data.value = await request.get('/api/admin/stats/qa')
    await nextTick()
    renderChart()
  } catch (e: any) { error.value = e.message }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['35%', '65%'],
      data: (data.value?.intentCounts || []).map((i: any) => ({ name: i.intent, value: i.cnt })),
    }],
  })
}

onMounted(load)
</script>

<template>
  <div>
    <h2>问答统计</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="data" class="grid" style="grid-template-columns: 1fr 1fr;">
      <div class="card"><h3>意图分布</h3><div ref="chartRef" class="chart"></div></div>
      <div class="card">
        <h3>最近问答</h3>
        <table class="table">
          <thead><tr><th>问题</th><th>意图</th><th>命中</th></tr></thead>
          <tbody><tr v-for="q in data.recent" :key="q.createdAt + q.question"><td>{{ q.question }}</td><td>{{ q.intent }}</td><td>{{ q.hitCount }}</td></tr></tbody>
        </table>
      </div>
    </div>
  </div>
</template>
