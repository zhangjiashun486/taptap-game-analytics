<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import request from '@/api/request'

type TaskKey = 'game-id' | 'detail' | 'archive'
type TaskStatus = 'idle' | 'running' | 'done' | 'failed'

interface CrawlTab {
  key: TaskKey
  icon: string
  title: string
  subtitle: string
  description: string
  command: string
}

interface CrawlTaskState {
  status: TaskStatus
  progress: number
  logs: string[]
  stage: string
  taskId?: number
  lastLogId: number
  busy?: boolean
}

const data = ref<any>(null)
const error = ref('')
const activeKey = ref<TaskKey>('game-id')
const showRisk = ref(false)
const featureNotice = ref('')
const timers: Record<string, number> = {}

const tabs: CrawlTab[] = [
  {
    key: 'game-id',
    icon: '①',
    title: '游戏 ID 采集',
    subtitle: '榜单种子 + 标签闭合',
    description: '从 6 个榜单建立种子，再沿标签滚雪球发现新游戏，保存最新 ID 集合。',
    command: 'demo_task_runner.py --mode game-id',
  },
  {
    key: 'detail',
    icon: '②',
    title: '详情补爬',
    subtitle: '按 ID 补全字段',
    description: '按游戏 ID 逐个请求详情，写入 JSONL，支持已有进度文件的断点续爬。',
    command: 'demo_task_runner.py --mode detail',
  },
  {
    key: 'archive',
    icon: '③',
    title: '入库与归档',
    subtitle: 'MySQL + HDFS',
    description: '将最新 JSONL 覆盖导入 MySQL，再把数据文件归档到 HDFS 的 /taptap 目录。',
    command: 'demo_task_runner.py --mode archive',
  },
]

const tasks = ref<Record<TaskKey, CrawlTaskState>>({
  'game-id': { status: 'idle', progress: 0, logs: ['[待执行] 点击“立即执行”创建任务'], stage: '等待执行', lastLogId: 0 },
  detail: { status: 'idle', progress: 0, logs: ['[待执行] 点击“立即执行”创建任务'], stage: '等待执行', lastLogId: 0 },
  archive: { status: 'idle', progress: 0, logs: ['[待执行] 点击“立即执行”创建任务'], stage: '等待执行', lastLogId: 0 },
})

const activeTab = computed<CrawlTab>(() => tabs.find((item) => item.key === activeKey.value) || tabs[0]!)
const activeTask = computed<CrawlTaskState>(() => tasks.value[activeKey.value])
const statusText = computed(() => {
  if (activeTask.value.status === 'running') return '执行中'
  if (activeTask.value.status === 'done') return '已完成'
  if (activeTask.value.status === 'failed') return '执行失败'
  return '等待执行'
})

async function load() {
  try {
    data.value = await request.get('/api/admin/stats/crawl')
  } catch (e: any) {
    error.value = e.message || '历史采集数据加载失败'
  }
}

function selectTab(key: TaskKey) {
  activeKey.value = key
}

function openRiskDialog() {
  if (activeTask.value.status === 'running') return
  showRisk.value = true
}

function showNotImplemented(name: string) {
  featureNotice.value = `${name}功能暂未实现。当前版本已保留按钮入口，后续可接入真实任务暂停与断点控制。`
}

function closeFeatureNotice() {
  featureNotice.value = ''
}

function normalizeStatus(status: string): TaskStatus {
  if (status === 'RUNNING') return 'running'
  if (status === 'DONE') return 'done'
  if (status === 'FAILED') return 'failed'
  return 'idle'
}

function formatTime(value?: string) {
  if (!value) return new Date().toLocaleTimeString('zh-CN', { hour12: false })
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

function applyTask(key: TaskKey, task: any) {
  const state = tasks.value[key]!
  state.taskId = task.id
  state.status = normalizeStatus(task.status)
  state.progress = Number(task.progress || 0)
  state.stage = task.stage || '执行中'
}

async function refreshLogs(key: TaskKey) {
  const state = tasks.value[key]!
  if (!state.taskId) return
  const logs: any[] = await request.get(`/api/admin/crawl/tasks/${state.taskId}/logs`, {
    params: { afterId: state.lastLogId },
  })
  for (const item of logs) {
    state.logs.push(`[${formatTime(item.createdAt)}] ${item.message}`)
    state.lastLogId = Math.max(state.lastLogId, Number(item.id) || 0)
  }
}

async function startTask() {
  showRisk.value = false
  const key = activeKey.value
  const state = tasks.value[key]!
  try {
    const task: any = await request.post('/api/admin/crawl/tasks', { taskType: key })
    state.logs = []
    state.lastLogId = 0
    applyTask(key, task)
    await refreshLogs(key)
    startPolling(key)
  } catch (e: any) {
    state.status = 'failed'
    state.stage = '启动失败'
    state.logs.push(`[${formatTime()}] ${e.message || '任务启动失败'}`)
  }
}

function startPolling(key: TaskKey) {
  stopPolling(key)
  pollTask(key)
  timers[key] = window.setInterval(() => pollTask(key), 900)
}

function stopPolling(key: TaskKey) {
  if (timers[key]) {
    window.clearInterval(timers[key])
    delete timers[key]
  }
}

async function pollTask(key: TaskKey) {
  const state = tasks.value[key]!
  if (!state.taskId || state.busy) return
  state.busy = true
  try {
    const task: any = await request.get(`/api/admin/crawl/tasks/${state.taskId}`)
    applyTask(key, task)
    await refreshLogs(key)
    if (task.status === 'DONE' || task.status === 'FAILED') {
      stopPolling(key)
      await load()
    }
  } catch (e: any) {
    state.logs.push(`[${formatTime()}] 状态轮询失败：${e.message || '网络错误'}`)
  } finally {
    state.busy = false
  }
}

async function restoreLatestTasks() {
  for (const tab of tabs) {
    try {
      const task: any = await request.get('/api/admin/crawl/tasks/latest', {
        params: { taskType: tab.key },
      })
      if (!task) continue
      const state = tasks.value[tab.key]!
      state.logs = []
      state.lastLogId = 0
      applyTask(tab.key, task)
      await refreshLogs(tab.key)
      if (task.status === 'RUNNING') startPolling(tab.key)
    } catch {
      // 尚无历史任务，保持待执行状态
    }
  }
}

function statusClass(status: string) {
  return status === 'DONE' ? 'ok' : status === 'FAILED' ? 'failed' : 'running'
}

onMounted(async () => {
  await load()
  await restoreLatestTasks()
})
onBeforeUnmount(() => Object.keys(timers).forEach((key) => stopPolling(key as TaskKey)))
</script>

<template>
  <div class="crawl-page">
    <header class="page-hero">
      <div>
        <div class="hero-kicker">DATA PIPELINE CENTER</div>
        <h1>数据同步中心</h1>
        <p>管理 TapTap 数据采集、详情补爬、MySQL 入库与 HDFS 归档任务。</p>
      </div>
      <span class="demo-badge">安全隔离演示 · 正式数据只读</span>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="tabs">
      <button v-for="tab in tabs" :key="tab.key" :class="['tab-card', { active: activeKey === tab.key }]" @click="selectTab(tab.key)">
        <span class="tab-icon">{{ tab.icon }}</span>
        <span class="tab-copy">
          <b>{{ tab.title }}</b>
          <small>{{ tab.subtitle }}</small>
        </span>
        <span v-if="tasks[tab.key].status === 'running'" class="tab-dot running"></span>
        <span v-else-if="tasks[tab.key].status === 'done'" class="tab-dot done">✓</span>
        <span v-else-if="tasks[tab.key].status === 'failed'" class="tab-dot failed">!</span>
      </button>
    </div>

    <section class="task-layout">
      <div class="task-panel card">
        <div class="task-head">
          <div>
            <div class="section-label">CURRENT TASK</div>
            <h2>{{ activeTab.title }}</h2>
            <p>{{ activeTab.description }}</p>
          </div>
          <span :class="['status-pill', activeTask.status]">{{ statusText }}</span>
        </div>

        <div class="task-meta">
          <div><span>执行脚本</span><b>{{ activeTab.command }}</b></div>
          <div><span>当前阶段</span><b>{{ activeTask.stage }}</b></div>
          <div><span>数据策略</span><b>演示隔离 · 正式数据只读</b></div>
        </div>

        <div class="progress-head">
          <span>任务进度</span>
          <b>{{ activeTask.progress }}%</b>
        </div>
        <div class="progress-track"><div class="progress-fill" :style="{ width: `${activeTask.progress}%` }"></div></div>

        <div class="action-row">
          <button class="action primary" @click="openRiskDialog">立即执行</button>
          <button class="action" @click="showNotImplemented('暂停')">暂停</button>
          <button class="action" @click="showNotImplemented('断点续传')">断点续传</button>
          <span class="hint">本地 + HDFS 二段式保存</span>
        </div>

        <div class="console">
          <div class="console-head"><span class="dot red"></span><span class="dot yellow"></span><span class="dot green"></span><b>运行日志</b></div>
          <div class="console-body">
            <div v-for="(line, index) in activeTask.logs" :key="`${index}-${line}`" class="log-line">
              <span class="prompt">›</span>{{ line }}
            </div>
          </div>
        </div>
      </div>

      <aside class="info-column">
        <div class="card flow-card">
          <h3>任务流程</h3>
          <div class="flow-step"><span>1</span><b>读取本地断点</b></div>
          <div class="flow-step"><span>2</span><b>采集并解析数据</b></div>
          <div class="flow-step"><span>3</span><b>覆盖写最新文件</b></div>
          <div class="flow-step"><span>4</span><b>MySQL / HDFS 归档</b></div>
        </div>
        <div class="card storage-card">
          <h3>存储位置</h3>
          <p><span>本地</span>data/raw/</p>
          <p><span>HDFS</span>/taptap</p>
          <p><span>WebHDFS</span>192.168.100.101:50070</p>
        </div>
      </aside>
    </section>

    <section v-if="data" class="history-section">
      <div class="kpi-grid">
        <div v-for="s in data.statusCounts" :key="s.status" class="card kpi-card">
          <span :class="['kpi-dot', statusClass(s.status)]"></span>
          <div><b>{{ s.cnt }}</b><small>{{ s.status }}</small></div>
        </div>
      </div>
      <div class="card history-card">
        <div class="history-head"><h3>最近采集日志</h3><span class="muted">来自 MySQL t_crawl_log</span></div>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>ID</th><th>任务类型</th><th>状态</th><th>总数</th><th>成功</th><th>失败</th><th>说明</th><th>开始时间</th></tr></thead>
            <tbody>
              <tr v-for="log in data.recent" :key="log.id">
                <td>{{ log.id }}</td><td>{{ log.taskType }}</td>
                <td><span :class="['status-mini', statusClass(log.status)]">{{ log.status }}</span></td>
                <td>{{ log.total }}</td><td>{{ log.success }}</td><td>{{ log.failed }}</td><td>{{ log.message }}</td><td>{{ log.startedAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <div v-if="showRisk" class="modal-mask" @click.self="showRisk = false">
      <div class="modal-card">
        <div class="modal-icon">!</div>
        <h3>确认执行采集任务？</h3>
        <p>任务开始后，请不要刷新页面或切换路由。真实执行接入后，中断可能导致任务状态无法及时更新。</p>
        <div class="risk-list">
          <span>预计耗时较长</span><span>会请求外部接口</span><span>完成后覆盖最新文件</span>
        </div>
        <div class="modal-actions"><button class="action" @click="showRisk = false">取消</button><button class="action primary" @click="startTask">确认执行演示</button></div>
      </div>
    </div>

    <div v-if="featureNotice" class="modal-mask" @click.self="closeFeatureNotice">
      <div class="modal-card compact">
        <div class="modal-icon soft">i</div>
        <h3>功能暂未实现</h3>
        <p>{{ featureNotice }}</p>
        <div class="modal-actions"><button class="action primary" @click="closeFeatureNotice">知道了</button></div>
      </div>
    </div>
  </div>
</template>

<style scoped>.crawl-page { display: flex; flex-direction: column; gap: 18px; }
.page-hero { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 28px 30px; border-radius: 20px; color: #fff; background: radial-gradient(circle at 85% 15%, rgba(34,211,238,.2), transparent 23%), linear-gradient(125deg, #0f172a, #1e3a8a 57%, #4f46e5); box-shadow: 0 22px 48px rgba(30,64,175,.2); }
.hero-kicker { color: #67e8f9; font-size: 11px; font-weight: 850; letter-spacing: .13em; }
.page-hero h1 { margin-top: 7px; font-size: clamp(26px, 3vw, 38px); letter-spacing: -.03em; }
.page-hero p { margin-top: 9px; color: rgba(255,255,255,.72); font-size: 13px; line-height: 1.7; }
.demo-badge { flex: 0 0 auto; padding: 8px 12px; border-radius: 999px; background: rgba(255,255,255,.13); border: 1px solid rgba(255,255,255,.2); font-size: 11px; font-weight: 700; }
.tabs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.tab-card { position: relative; display: flex; align-items: center; gap: 12px; padding: 15px; border: 1px solid #e3e9f4; border-radius: 16px; text-align: left; color: #334155; background: #fff; box-shadow: 0 8px 24px rgba(30,64,175,.05); transition: .2s ease; }
.tab-card:hover { transform: translateY(-2px); border-color: #bfdbfe; box-shadow: 0 13px 30px rgba(37,99,235,.1); }
.tab-card.active { color: #fff; border-color: transparent; background: linear-gradient(135deg, #2563eb, #4f46e5 55%, #7c3aed); box-shadow: 0 15px 34px rgba(79,70,229,.25); }
.tab-icon { flex: 0 0 auto; width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; color: #2563eb; background: #eff6ff; font-weight: 900; }
.tab-card.active .tab-icon { color: #fff; background: rgba(255,255,255,.16); }
.tab-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.tab-copy b { font-size: 14px; }
.tab-copy small { color: #94a3b8; font-size: 11px; }
.tab-card.active .tab-copy small { color: rgba(255,255,255,.68); }
.tab-dot { margin-left: auto; width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; color: #fff; font-size: 11px; }
.tab-dot.running { background: #06b6d4; animation: pulse 1.2s infinite; }
.tab-dot.done { background: #22c55e; }
.tab-dot.failed { background: #ef4444; }
@keyframes pulse { 50% { box-shadow: 0 0 0 7px rgba(6,182,212,.14); } }
.task-layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(250px, .65fr); gap: 16px; }
.task-panel { padding: 22px; }
.task-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.section-label { color: #4f46e5; font-size: 10px; font-weight: 850; letter-spacing: .12em; }
.task-head h2 { margin-top: 5px; }
.task-head p { max-width: 720px; margin-top: 7px; color: #64748b; line-height: 1.7; font-size: 13px; }
.status-pill { flex: 0 0 auto; padding: 6px 11px; border-radius: 999px; font-size: 11px; font-weight: 800; }
.status-pill.idle { color: #475569; background: #eef2f7; }
.status-pill.running { color: #0369a1; background: #e0f2fe; }
.status-pill.done { color: #047857; background: #d1fae5; }
.status-pill.failed { color: #b91c1c; background: #fee2e2; }
.task-meta { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 20px; }
.task-meta > div { padding: 11px 13px; border-radius: 12px; background: #f8fafc; border: 1px solid #edf1f7; }
.task-meta span { display: block; color: #94a3b8; font-size: 10px; }
.task-meta b { display: block; margin-top: 5px; color: #334155; font-size: 12px; }
.progress-head { display: flex; justify-content: space-between; margin-top: 22px; color: #64748b; font-size: 12px; }
.progress-head b { color: #2563eb; }
.progress-track { height: 10px; margin-top: 9px; overflow: hidden; border-radius: 999px; background: #e9eef7; }
.progress-fill { height: 100%; border-radius: inherit; background: linear-gradient(90deg, #06b6d4, #2563eb 58%, #7c3aed); box-shadow: 0 0 16px rgba(37,99,235,.4); transition: width .45s ease; }
.action-row { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 22px; }
.action { padding: 9px 15px; border: 1px solid #dbe4f0; border-radius: 10px; color: #475569; background: #fff; font-size: 13px; font-weight: 650; transition: .18s ease; }
.action:hover { transform: translateY(-1px); border-color: #bfdbfe; color: #2563eb; }
.action.primary { color: #fff; border: 0; background: linear-gradient(135deg, #2563eb, #7c3aed); box-shadow: 0 8px 20px rgba(79,70,229,.24); }
.action.primary:hover { color: #fff; }
.hint { margin-left: auto; color: #94a3b8; font-size: 11px; }.console { margin-top: 20px; overflow: hidden; border-radius: 15px; background: #0b1220; box-shadow: inset 0 0 0 1px rgba(148,163,184,.12); }
.console-head { display: flex; align-items: center; gap: 7px; padding: 10px 13px; color: #94a3b8; background: #111c30; font-size: 11px; }
.console-head b { margin-left: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; }
.dot.red { background: #f87171; }
.dot.yellow { background: #fbbf24; }
.dot.green { background: #4ade80; }
.console-body { min-height: 190px; max-height: 250px; overflow-y: auto; padding: 14px; font-family: Consolas, "Courier New", monospace; }
.log-line { margin-bottom: 9px; color: #cbd5e1; font-size: 12px; line-height: 1.55; }
.prompt { margin-right: 8px; color: #22d3ee; }
.info-column { display: flex; flex-direction: column; gap: 16px; }
.flow-card, .storage-card { padding: 18px; }
.flow-step { display: flex; align-items: center; gap: 10px; padding: 9px 0; color: #475569; font-size: 12px; border-bottom: 1px solid #f1f5f9; }
.flow-step:last-child { border-bottom: 0; }
.flow-step span { width: 24px; height: 24px; display: grid; place-items: center; border-radius: 8px; color: #fff; background: linear-gradient(135deg, #2563eb, #7c3aed); font-size: 10px; font-weight: 800; }
.storage-card p { display: flex; justify-content: space-between; gap: 10px; padding: 9px 0; color: #475569; border-bottom: 1px solid #f1f5f9; font-size: 12px; }
.storage-card p:last-child { border-bottom: 0; }
.storage-card span { color: #94a3b8; }
.history-section { display: flex; flex-direction: column; gap: 14px; }
.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.kpi-card { display: flex; align-items: center; gap: 12px; padding: 16px; }
.kpi-dot { width: 12px; height: 12px; border-radius: 50%; }
.kpi-dot.ok { background: #22c55e; box-shadow: 0 0 0 5px rgba(34,197,94,.12); }
.kpi-dot.failed { background: #ef4444; box-shadow: 0 0 0 5px rgba(239,68,68,.12); }
.kpi-dot.running { background: #06b6d4; box-shadow: 0 0 0 5px rgba(6,182,212,.12); }
.kpi-card b { display: block; font-size: 22px; color: #1e293b; }
.kpi-card small { color: #94a3b8; font-size: 11px; }
.history-card { padding: 0; overflow: hidden; }
.history-head { display: flex; align-items: center; justify-content: space-between; padding: 17px 19px; border-bottom: 1px solid #eef2f7; }
.history-head h3 { margin: 0; }
.table-wrap { overflow-x: auto; }
.status-mini { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 10px; font-weight: 800; }
.status-mini.ok { color: #047857; background: #d1fae5; }
.status-mini.failed { color: #b91c1c; background: #fee2e2; }
.status-mini.running { color: #0369a1; background: #e0f2fe; }
.modal-mask { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 20px; background: rgba(15,23,42,.48); backdrop-filter: blur(7px); }
.modal-card { width: min(480px, 100%); padding: 28px; border-radius: 22px; text-align: center; background: #fff; box-shadow: 0 30px 80px rgba(15,23,42,.28); animation: modal-in .2s ease; }
.modal-card.compact { width: min(420px, 100%); }
@keyframes modal-in { from { opacity: 0; transform: translateY(10px) scale(.98); } }
.modal-icon { width: 54px; height: 54px; display: grid; place-items: center; margin: 0 auto 16px; border-radius: 18px; color: #fff; background: linear-gradient(135deg, #f59e0b, #ef4444); font-size: 24px; font-weight: 900; }
.modal-icon.soft { background: linear-gradient(135deg, #2563eb, #7c3aed); }
.modal-card h3 { font-size: 20px; }
.modal-card p { margin-top: 10px; color: #64748b; line-height: 1.8; font-size: 13px; }
.risk-list { display: flex; justify-content: center; flex-wrap: wrap; gap: 7px; margin-top: 16px; }
.risk-list span { padding: 5px 9px; border-radius: 999px; color: #b45309; background: #fef3c7; font-size: 11px; }
.modal-actions { display: flex; justify-content: center; gap: 10px; margin-top: 24px; }
.error { color: #dc2626; }
@media (max-width: 1050px) {
  .tabs { grid-template-columns: 1fr; }
  .task-layout { grid-template-columns: 1fr; }
  .task-meta { grid-template-columns: 1fr; }
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
