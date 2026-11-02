<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import request from '@/api/request'

interface GameItem {
  gameId: number
  title: string
  ratingScore?: number | null
  fansCount?: number | null
  iconUrl?: string | null
  developer?: string | null
}

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  mode?: string
  intent?: string
  items?: GameItem[]
  source?: string
  verified?: boolean
  warning?: string
  sql?: string
  loading?: boolean
}

const STORAGE_KEY = 'taptap_qa_messages'
const messages = ref<ChatMessage[]>([])
const question = ref('')
const loading = ref(false)
const error = ref('')
const chatRef = ref<HTMLElement | null>(null)
let seed = 0

const samples = [
  '2020 年以后发行的二次元游戏里，哪些厂商平均评分最高？',
  '评分 9 分以上的游戏有哪些？',
  '预约最高的 5 款游戏',
  '类似原神的游戏',
  '一共采集了多少款游戏？',
  'KMeans 聚类是什么？',
]

const sampleIcons = ['⌘', '✦', '↗', '◎', '#', '?']

function nextId() {
  seed += 1
  return seed
}

function formatFans(value?: number | null) {
  const n = Number(value || 0)
  return n >= 10000 ? `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万` : n.toLocaleString()
}

function modeLabel(message: ChatMessage) {
  if (message.mode === 'SQL') return '只读数据库查询'
  if (message.mode === 'SQL_LLM') return 'DeepSeek 数据总结'
  if (message.mode === 'LLM_GENERAL') return '通用知识补充'
  if (message.mode === 'LLM') return 'DeepSeek 增强'
  return '规则问答'
}

function modeClass(message: ChatMessage) {
  return {
    llm: message.mode === 'LLM' || message.mode === 'SQL_LLM',
    sql: message.mode === 'SQL' || message.mode === 'SQL_LLM',
    general: message.mode === 'LLM_GENERAL',
  }
}

function sourceLabel(message: ChatMessage) {
  if (message.source === 'GENERAL') return '未验证'
  if (message.source === 'DB') return '数据库验证'
  return '系统提示'
}

function persistMessages() {
  try {
    const safeMessages = messages.value
      .filter((message) => !message.loading && message.content.trim())
      .slice(-30)
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(safeMessages))
  } catch {
    // 浏览器禁用 sessionStorage 时不影响问答
  }
}

function restoreMessages() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (!Array.isArray(saved)) return
    messages.value = saved.filter((message: ChatMessage) => !message.loading && message.content?.trim())
    seed = messages.value.reduce((max, message) => Math.max(max, Number(message.id) || 0), 0)
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

async function scrollToBottom() {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

async function ask(forcedQuestion?: string) {
  const text = (forcedQuestion ?? question.value).trim()
  if (!text || loading.value) return

  messages.value.push({ id: nextId(), role: 'user', content: text })
  question.value = ''
  loading.value = true
  error.value = ''

  const assistantId = nextId()
  messages.value.push({ id: assistantId, role: 'assistant', content: '', loading: true })
  persistMessages()
  await scrollToBottom()

  try {
    const data: any = await request.post('/api/qa/ask', { question: text }, { timeout: 65000 })
    const index = messages.value.findIndex((item) => item.id === assistantId)
    const current = index >= 0 ? messages.value[index] : undefined
    if (current) {
      messages.value[index] = {
        id: current.id,
        role: 'assistant',
        content: data.answer || '暂时没有找到可用答案。',
        mode: data.mode || 'RULE',
        intent: data.intent || 'FALLBACK',
        items: data.items || [],
        source: data.source || 'DB',
        verified: data.verified !== false,
        warning: data.warning || '',
        sql: data.sql || '',
        loading: false,
      }
    }
  } catch (e: any) {
    error.value = e.message || '提问失败'
    const index = messages.value.findIndex((item) => item.id === assistantId)
    const current = index >= 0 ? messages.value[index] : undefined
    if (current) {
      messages.value[index] = {
        id: current.id,
        role: 'assistant',
        content: '这次请求没有成功，请稍后重试。规则问答和已有数据不会受到影响。',
        mode: 'RULE',
        source: 'SYSTEM',
        verified: false,
        warning: '本次请求没有返回可验证结果。',
        loading: false,
      }
    }
  } finally {
    loading.value = false
    persistMessages()
    await scrollToBottom()
  }
}

function useSample(sample: string) { ask(sample) }

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    ask()
  }
}

function resetChat() {
  messages.value = []
  question.value = ''
  error.value = ''
  sessionStorage.removeItem(STORAGE_KEY)
}

onMounted(async () => {
  restoreMessages()
  await scrollToBottom()
})</script>

<template>
  <div class="qa-page">
    <header class="qa-head">
      <div>
        <div class="qa-kicker">AI GAME ASSISTANT</div>
        <h1>TapTap 游戏问答</h1>
      </div>
      <button v-if="messages.length" class="btn ghost new-chat" @click="resetChat">＋ 新对话</button>
    </header>

    <section v-if="!messages.length" class="welcome">
      <div class="ai-orb">🎮</div>
      <h2>你好，我是 TapTap 游戏分析助手</h2>
      <p>我可以查询数据库中的 10,219 款游戏；如果数据库无法回答，也会明确标注“未验证的通用知识补充”。</p>
      <div class="suggestions">
        <button v-for="(sample, index) in samples" :key="sample" class="suggestion" @click="useSample(sample)">
          <span class="suggestion-icon">{{ sampleIcons[index] }}</span>
          <span class="suggestion-text">{{ sample }}</span>
          <span class="suggestion-arrow">→</span>
        </button>
      </div>
    </section>

    <section v-else ref="chatRef" class="conversation">
      <article v-for="message in messages" :key="message.id" :class="['message', message.role]">
        <template v-if="message.role === 'user'">
          <div class="user-bubble">{{ message.content }}</div>
        </template>
        <template v-else>
          <div class="assistant-avatar">AI</div>
          <div class="assistant-content">
            <div class="assistant-name">
              <b>TapTap 数据助手</b>
              <span v-if="!message.loading" class="mode-pill" :class="modeClass(message)">
                {{ modeLabel(message) }}
              </span>
              <span v-if="!message.loading && message.source" class="source-pill" :class="{ unverified: message.verified === false }">
                {{ sourceLabel(message) }}
              </span>
            </div>
            <div v-if="message.loading" class="typing"><span></span><span></span><span></span></div>
            <template v-else>
              <p class="answer-content">{{ message.content }}</p>
              <div v-if="message.warning" class="answer-warning">
                <span>!</span>
                <span>{{ message.warning }}</span>
              </div>
              <details v-if="message.sql" class="sql-detail">
                <summary>查看本次只读 SQL</summary>
                <code>{{ message.sql }}</code>
              </details>
              <div v-if="message.intent" class="answer-foot"><span class="intent-chip">意图：{{ message.intent }}</span></div>
              <div v-if="message.items?.length" class="game-list">
                <button v-for="item in message.items" :key="item.gameId" class="game-result" @click="$router.push(`/game/${item.gameId}`)">
                  <span class="game-cover">
                    <span>🎮</span>
                    <img v-if="item.iconUrl" :src="item.iconUrl" :alt="item.title" loading="lazy" referrerpolicy="no-referrer" @error="(event) => ((event.target as HTMLImageElement).style.display = 'none')" />
                  </span>
                  <span class="game-copy">
                    <b>{{ item.title }}</b>
                    <small>⭐ {{ item.ratingScore ?? '暂无' }} · 关注 {{ formatFans(item.fansCount) }}</small>
                  </span>
                </button>
              </div>
            </template>
          </div>
        </template>
      </article>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="composer-wrap">
      <div class="composer">
        <textarea v-model="question" rows="1" placeholder="给 TapTap 数据助手下达指令，例如：找几款类似原神的高分游戏" @keydown="handleKeydown"></textarea>
        <button class="send-button" :disabled="loading || !question.trim()" @click="ask()" aria-label="发送"><span>{{ loading ? '…' : '↑' }}</span></button>
      </div>
      <p class="composer-tip">优先查询 MySQL 真实数据；数据库无法回答时，会明确标记为未验证的通用知识补充。</p>
    </div>
  </div>
</template>

<style scoped>.qa-page { position: relative; min-height: calc(100vh - 155px); display: flex; flex-direction: column; }
.qa-page::before { content: ""; position: fixed; z-index: -1; top: 120px; left: 50%; width: 760px; height: 420px; transform: translateX(-50%); border-radius: 50%; background: radial-gradient(circle, rgba(59,130,246,.13), rgba(124,58,237,.08) 38%, transparent 70%); filter: blur(24px); pointer-events: none; }
.qa-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 22px; }
.qa-kicker { color: #4f46e5; font-size: 11px; font-weight: 850; letter-spacing: .14em; }
.qa-head h1 { margin-top: 6px; font-size: clamp(24px, 3vw, 34px); letter-spacing: -.03em; }
.new-chat { padding: 9px 14px; }
.welcome { flex: 1; min-height: 470px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 32px 0 50px; }
.ai-orb { width: 68px; height: 68px; display: grid; place-items: center; border-radius: 22px; font-size: 32px; color: #fff; background: linear-gradient(145deg, #2563eb, #6d28d9); box-shadow: 0 18px 40px rgba(79,70,229,.3); }
.welcome h2 { margin-top: 24px; font-size: clamp(24px, 3.5vw, 36px); letter-spacing: -.035em; }
.welcome p { max-width: 620px; margin-top: 12px; color: #64748b; line-height: 1.8; font-size: 14px; }
.suggestions { width: min(790px, 100%); display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 34px; text-align: left; }
.suggestion { display: flex; align-items: center; gap: 12px; min-height: 62px; padding: 13px 15px; border: 1px solid #e5eaf3; border-radius: 16px; color: #334155; background: rgba(255,255,255,.88); box-shadow: 0 8px 25px rgba(30,64,175,.05); transition: .2s ease; }
.suggestion:hover { transform: translateY(-2px); border-color: #bfdbfe; box-shadow: 0 14px 30px rgba(37,99,235,.12); }
.suggestion-icon { flex: 0 0 auto; width: 34px; height: 34px; display: grid; place-items: center; border-radius: 11px; color: #fff; background: linear-gradient(135deg, #2563eb, #06b6d4); font-weight: 800; }
.suggestion:nth-child(2n) .suggestion-icon { background: linear-gradient(135deg, #7c3aed, #ec4899); }
.suggestion:nth-child(3n) .suggestion-icon { background: linear-gradient(135deg, #0f766e, #22c55e); }
.suggestion-text { flex: 1; font-size: 13px; font-weight: 650; }
.suggestion-arrow { color: #94a3b8; transition: .2s ease; }
.suggestion:hover .suggestion-arrow { color: #2563eb; transform: translateX(3px); }
.conversation { flex: 1; min-height: 430px; max-height: calc(100vh - 350px); overflow-y: auto; padding: 8px 3px 30px; scroll-behavior: smooth; }
.message { display: flex; gap: 12px; margin-bottom: 28px; animation: message-in .25s ease both; }
.message.user { justify-content: flex-end; }
.user-bubble { max-width: min(680px, 82%); padding: 13px 17px; border-radius: 18px 18px 5px 18px; color: #fff; background: linear-gradient(135deg, #2563eb, #4f46e5); box-shadow: 0 10px 24px rgba(37,99,235,.2); line-height: 1.7; font-size: 14px; }
.assistant-avatar { flex: 0 0 auto; width: 36px; height: 36px; display: grid; place-items: center; border-radius: 12px; color: #fff; background: linear-gradient(145deg, #0f766e, #2563eb); font-size: 12px; font-weight: 900; box-shadow: 0 8px 18px rgba(37,99,235,.2); }
.assistant-content { min-width: 0; flex: 1; padding-top: 2px; }
.assistant-name { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: #334155; font-size: 13px; }
.mode-pill { padding: 3px 8px; border-radius: 999px; color: #0369a1; background: #e0f2fe; font-size: 10px; font-weight: 800; }
.mode-pill.llm { color: #fff; background: linear-gradient(135deg, #2563eb, #7c3aed); }
.mode-pill.sql { color: #065f46; background: #d1fae5; }
.mode-pill.general { color: #92400e; background: #fef3c7; }
.source-pill { padding: 3px 8px; border-radius: 999px; color: #166534; background: #dcfce7; font-size: 10px; font-weight: 800; }
.source-pill.unverified { color: #9a3412; background: #ffedd5; }
.answer-warning { display: flex; gap: 8px; margin-top: 12px; padding: 10px 12px; border: 1px solid #fed7aa; border-radius: 12px; color: #9a3412; background: #fff7ed; font-size: 12px; line-height: 1.65; }
.answer-warning span:first-child { flex: 0 0 auto; width: 18px; height: 18px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: #f97316; font-size: 11px; font-weight: 900; }
.sql-detail { margin-top: 12px; overflow: hidden; border: 1px solid #dbeafe; border-radius: 12px; background: #f8fbff; }
.sql-detail summary { padding: 9px 12px; color: #1d4ed8; cursor: pointer; user-select: none; font-size: 12px; font-weight: 700; }
.sql-detail code { display: block; overflow-x: auto; padding: 11px 12px; border-top: 1px solid #dbeafe; color: #334155; background: #fff; white-space: pre-wrap; word-break: break-all; font-size: 11px; line-height: 1.7; }
.answer-content { margin-top: 10px; color: #1e293b; line-height: 1.9; font-size: 15px; white-space: pre-wrap; }
.answer-foot { margin-top: 8px; }
.intent-chip { color: #94a3b8; font-size: 11px; }
.typing { display: flex; gap: 5px; padding: 15px 0; }
.typing span { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; animation: typing 1s infinite ease-in-out; }
.typing span:nth-child(2) { animation-delay: .15s; }
.typing span:nth-child(3) { animation-delay: .3s; }
@keyframes typing { 0%, 60%, 100% { transform: translateY(0); opacity: .45; } 30% { transform: translateY(-6px); opacity: 1; } }
@keyframes message-in { from { opacity: 0; transform: translateY(7px); } to { opacity: 1; transform: translateY(0); } }.game-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 16px; }
.game-result { display: flex; align-items: center; gap: 11px; padding: 10px; border: 1px solid #e7edf7; border-radius: 14px; text-align: left; background: #fff; transition: .18s ease; }
.game-result:hover { border-color: #bfdbfe; transform: translateY(-2px); box-shadow: 0 10px 24px rgba(37,99,235,.1); }
.game-cover { position: relative; flex: 0 0 auto; width: 44px; height: 44px; display: grid; place-items: center; overflow: hidden; border-radius: 12px; background: linear-gradient(135deg, #dbeafe, #ede9fe); }
.game-cover span { position: absolute; }
.game-cover img { position: relative; z-index: 1; width: 100%; height: 100%; object-fit: cover; }
.game-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.game-copy b { overflow: hidden; color: #1e293b; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.game-copy small { color: #94a3b8; font-size: 11px; }
.composer-wrap { position: sticky; bottom: 10px; z-index: 8; padding-top: 8px; }
.composer { display: flex; align-items: flex-end; gap: 10px; padding: 10px 10px 10px 16px; border: 1px solid #dce5f2; border-radius: 22px; background: rgba(255,255,255,.96); box-shadow: 0 18px 48px rgba(30,64,175,.15); backdrop-filter: blur(18px); }
.composer:focus-within { border-color: #93c5fd; box-shadow: 0 20px 50px rgba(37,99,235,.2), 0 0 0 4px rgba(37,99,235,.07); }
.composer textarea { flex: 1; min-height: 28px; max-height: 140px; padding: 6px 0; resize: none; border: 0; outline: 0; color: #1e293b; background: transparent; font-size: 14px; line-height: 1.6; }
.send-button { flex: 0 0 auto; width: 42px; height: 42px; display: grid; place-items: center; border: 0; border-radius: 15px; color: #fff; background: linear-gradient(145deg, #1d4ed8, #7c3aed); box-shadow: 0 10px 22px rgba(79,70,229,.3); font-size: 21px; font-weight: 800; transition: .18s ease; }
.send-button:hover:not(:disabled) { transform: translateY(-2px) scale(1.03); }
.send-button:disabled { opacity: .35; cursor: not-allowed; }
.composer-tip { margin-top: 8px; color: #94a3b8; text-align: center; font-size: 11px; }
.error { margin: 10px 0; color: #dc2626; text-align: center; font-size: 13px; }
@media (max-width: 700px) {
  .suggestions, .game-list { grid-template-columns: 1fr; }
  .welcome { min-height: 360px; }
  .user-bubble { max-width: 92%; }
  .conversation { max-height: none; }
}
</style>