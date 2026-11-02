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
    data.value = await request.get('/api/admin/stats/cluster')
    await nextTick()
    renderChart()
  } catch (e: any) { error.value = e.message }
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  const clusters = (data.value?.clusters || []).filter((c: any) => c.clusterId >= 0)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['35%', '65%'],
      data: clusters.map((c: any) => ({ name: c.clusterName, value: c.size })),
      label: { formatter: '{b}: {c}' },
    }],
  })
}

onMounted(load)
</script>

<template>
  <div>
    <h2>聚类分析</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="data">
      <div class="grid kpi-grid">
        <div class="card kpi"><div class="num">#{{ data.run?.runId }}</div><div class="label">最新批次</div></div>
        <div class="card kpi"><div class="num">{{ data.run?.algorithm }}</div><div class="label">算法</div></div>
        <div class="card kpi"><div class="num">K={{ data.run?.kValue }}</div><div class="label">簇数量</div></div>
        <div class="card kpi"><div class="num">{{ data.run?.silhouette }}</div><div class="label">轮廓系数</div></div>
      </div>
      <div class="grid mt" style="grid-template-columns: 1fr 1fr;">
        <div class="card"><h3>各簇规模</h3><div ref="chartRef" class="chart"></div></div>
        <div class="card">
          <h3>簇画像</h3>
          <table class="table">
            <thead><tr><th>簇</th><th>规模</th><th>关键词</th></tr></thead>
            <tbody>
              <tr v-for="c in data.clusters" :key="c.clusterId">
                <td>{{ c.clusterName }}</td>
                <td>{{ c.size }}</td>
                <td class="muted">{{ c.keywords }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mt { margin-top: 16px; }
</style>
