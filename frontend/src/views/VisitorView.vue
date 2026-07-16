<template>
  <div class="page app-page">
    <header class="topbar glass-bar">
      <div class="brand">NexusMind <span>支持</span></div>
      <div class="account-actions">
        <span>{{ auth.user?.displayName }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </header>

    <main class="customer-workspace">
      <aside class="conversation-sidebar">
        <el-button class="new-chat" type="primary" @click="newConversation">新建咨询</el-button>
        <div class="sidebar-label">最近会话</div>
        <button
          v-for="item in sessions"
          :key="item.id"
          class="conversation-item"
          :class="{ active: session?.id === item.id }"
          type="button"
          @click="selectSession(item)"
        >
          <span>{{ item.title }}</span>
          <small>{{ statusLabel(item.status) }}</small>
        </button>
        <div v-if="!sessions.length" class="sidebar-empty">暂无历史会话</div>
      </aside>

      <section class="conversation-panel">
        <div class="conversation-header">
          <div>
            <h1>{{ session?.title || '新的咨询' }}</h1>
            <p>{{ session ? statusLabel(session.status) : '向我们描述你遇到的问题' }}</p>
          </div>
          <el-tag v-if="session" :type="statusType(session.status)" effect="plain">{{ statusLabel(session.status) }}</el-tag>
        </div>

        <div ref="messageList" v-loading="loadingMessages" class="messages conversation-messages">
          <div v-if="!messages.length && !typing" class="empty-conversation">
            <strong>我们能为你做什么？</strong>
            <span>发送消息后，系统会自动为你匹配合适的支持方式。</span>
          </div>
          <div v-for="message in messages" :key="message.id || message.localId" class="message" :class="message.senderType.toLowerCase()">
            <strong>{{ labelOf(message.senderType) }}</strong>
            <img v-if="message.imagePreviewUrl" :src="message.imagePreviewUrl" :alt="message.attachmentName || '咨询图片'" class="message-image" />
            <div>{{ message.content }}</div>
          </div>
          <div v-if="typing" class="message ai waiting">{{ longWait ? '正在继续整理，请稍候…' : '正在回复…' }}</div>
        </div>

        <div v-if="imagePreviewUrl" class="image-preview">
          <img :src="imagePreviewUrl" :alt="selectedImage?.name || '待发送图片'" />
          <span>{{ selectedImage?.name }}</span>
          <el-tooltip content="移除图片" placement="top"><el-button :icon="Close" circle text aria-label="移除图片" @click="clearSelectedImage" /></el-tooltip>
        </div>
        <div class="composer customer-composer">
          <input ref="imageInput" hidden type="file" accept="image/jpeg,image/png,image/gif" @change="selectImage" />
          <el-tooltip content="上传图片" placement="top"><el-button :icon="Picture" circle aria-label="上传图片" :disabled="sending || isClosed" @click="imageInput?.click()" /></el-tooltip>
          <el-input v-model="draft" :disabled="sending || isClosed" :placeholder="isClosed ? '会话已结束，请新建咨询' : '输入消息'" @keyup.enter="send" />
          <el-button type="primary" :loading="sending" :disabled="isClosed || (!draft.trim() && !selectedImage)" @click="send">发送</el-button>
          <el-button :loading="handoffSending" :disabled="isClosed || sending || handoffSending || session?.status === 'PENDING_HANDOFF' || session?.status === 'AGENT_SERVING'" @click="handoff">转人工</el-button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, Picture } from '@element-plus/icons-vue'
import { api, http } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface ChatSession { id: number; title: string; status: string }
interface ChatMessage { id?: number; localId?: string; senderType: string; content: string; messageType?: string; attachmentName?: string; imagePreviewUrl?: string }

const router = useRouter()
const auth = useAuthStore()
const sessions = ref<ChatSession[]>([])
const session = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const messageList = ref<HTMLElement | null>(null)
const draft = ref('')
const selectedImage = ref<File | null>(null)
const imagePreviewUrl = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const loadingMessages = ref(false)
const typing = ref(false)
const sending = ref(false)
const handoffSending = ref(false)
const longWait = ref(false)
const isClosed = computed(() => session.value?.status === 'CLOSED')
let socket: WebSocket | null = null
let socketVersion = 0
let reconnectTimer: number | undefined
let longWaitTimer: number | undefined
let localMessageId = 0
let streamingAiMessage: ChatMessage | null = null
let ignoredAiContent = ''
const objectUrls = new Set<string>()

onMounted(loadSessions)
onBeforeUnmount(() => {
  socketVersion++
  socket?.close()
  window.clearTimeout(reconnectTimer)
  window.clearTimeout(longWaitTimer)
  objectUrls.forEach(URL.revokeObjectURL)
})

async function loadSessions() {
  try {
    sessions.value = await api<ChatSession[]>(http.get('/chat/sessions'))
    if (session.value) {
      session.value = sessions.value.find(item => item.id === session.value?.id) || null
    } else if (sessions.value.length) {
      await selectSession(sessions.value[0])
    }
  } catch (error) { ElMessage.error(messageOf(error, '会话加载失败')) }
}

async function selectSession(item: ChatSession) {
  session.value = item
  loadingMessages.value = true
  try {
    const rows = await api<ChatMessage[]>(http.get(`/chat/sessions/${item.id}/messages`))
    messages.value = await hydrateImages(rows, item.id)
    connect()
    scrollToBottom()
  } catch (error) { ElMessage.error(messageOf(error, '消息加载失败')) }
  finally { loadingMessages.value = false }
}

function newConversation() {
  session.value = null
  messages.value = []
  socketVersion++
  socket?.close()
  socket = null
  finishWaiting()
}

function connect() {
  if (!session.value) return
  const version = ++socketVersion
  socket?.close()
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  const active = new WebSocket(`${scheme}://${location.host}/ws/chat?token=${encodeURIComponent(auth.token)}`)
  socket = active
  active.onopen = () => active.send(JSON.stringify({ type: 'JOIN_SESSION', sessionId: session.value?.id }))
  active.onmessage = (event) => handleSocket(JSON.parse(event.data))
  active.onclose = () => {
    if (version === socketVersion && session.value) reconnectTimer = window.setTimeout(connect, 1500)
  }
}

function handleSocket(payload: { type: string; content?: string }) {
  const content = payload.content || ''
  if (payload.type === 'AI_MESSAGE_DELTA') appendAiDelta(content)
  if (payload.type === 'AI_MESSAGE_DONE') { finishWaiting(); streamingAiMessage = null }
  if (payload.type === 'AI_MESSAGE' && content !== ignoredAiContent) { finishWaiting(); addMessage({ senderType: 'AI', content }) }
  if (payload.type === 'AGENT_MESSAGE') addMessage({ senderType: 'AGENT', content })
  if (['SYSTEM_NOTICE', 'TICKET_CREATED', 'AGENT_ACCEPTED', 'SESSION_CLOSED'].includes(payload.type)) {
    addMessage({ senderType: 'SYSTEM', content })
    loadSessions().catch(() => undefined)
  }
  if (payload.type === 'ERROR') { finishWaiting(); ElMessage.error(content || '实时消息处理失败') }
}

async function send() {
  if (sending.value || isClosed.value || (!draft.value.trim() && !selectedImage.value)) return
  const file = selectedImage.value
  const content = draft.value.trim() || '请描述这张图片'
  const preview = imagePreviewUrl.value
  draft.value = ''; selectedImage.value = null; imagePreviewUrl.value = ''
  if (imageInput.value) imageInput.value.value = ''
  addMessage({ senderType: 'VISITOR', content, messageType: file ? 'IMAGE' : 'TEXT', attachmentName: file?.name, imagePreviewUrl: preview })
  startWaiting()
  try {
    const activeSession = await ensureSession()
    if (file) {
      const form = new FormData(); form.append('content', content); form.append('image', file)
      const response = await api<ChatMessage>(http.post(`/chat/sessions/${activeSession.id}/messages/image`, form))
      ignoredAiContent = response.content
      addMessage(response); finishWaiting()
    } else if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'CHAT_MESSAGE', sessionId: activeSession.id, content }))
    } else {
      const response = await api<ChatMessage>(http.post(`/chat/sessions/${activeSession.id}/messages`, { content }))
      ignoredAiContent = response.content
      addMessage(response); finishWaiting()
    }
  } catch (error) { finishWaiting(); ElMessage.error(messageOf(error, '发送失败')) }
}

async function handoff() {
  if (handoffSending.value || isClosed.value) return
  handoffSending.value = true
  try {
    const activeSession = await ensureSession()
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'HANDOFF_REQUEST', sessionId: activeSession.id, content: '用户主动请求转人工' }))
    else await api(http.post(`/chat/sessions/${activeSession.id}/handoff`, { content: '用户主动请求转人工' }))
  } catch (error) { ElMessage.error(messageOf(error, '转人工失败')) }
  finally { handoffSending.value = false }
}

async function ensureSession() {
  if (session.value) return session.value
  const created = await api<ChatSession>(http.post('/chat/sessions', { title: '客户咨询' }))
  session.value = created; sessions.value.unshift(created); connect()
  return created
}

function selectImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 8 * 1024 * 1024) return ElMessage.error('图片不能超过 8 MB')
  clearSelectedImage(); selectedImage.value = file
  imagePreviewUrl.value = URL.createObjectURL(file); objectUrls.add(imagePreviewUrl.value)
}
function clearSelectedImage() { if (imagePreviewUrl.value) { URL.revokeObjectURL(imagePreviewUrl.value); objectUrls.delete(imagePreviewUrl.value) }; imagePreviewUrl.value = ''; selectedImage.value = null; if (imageInput.value) imageInput.value.value = '' }
function addMessage(message: ChatMessage) { if (!message.id && !message.localId) message.localId = `local-${++localMessageId}`; messages.value.push(message); scrollToBottom() }
function appendAiDelta(content: string) { if (!streamingAiMessage) { streamingAiMessage = { localId: `local-${++localMessageId}`, senderType: 'AI', content: '' }; messages.value.push(streamingAiMessage) }; typing.value = false; streamingAiMessage.content += content; scrollToBottom() }
function startWaiting() { sending.value = true; typing.value = true; longWait.value = false; longWaitTimer = window.setTimeout(() => longWait.value = true, 8000) }
function finishWaiting() { sending.value = false; typing.value = false; longWait.value = false; window.clearTimeout(longWaitTimer) }
function scrollToBottom() { nextTick(() => { if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight }) }
async function hydrateImages(rows: ChatMessage[], sessionId: number) { return Promise.all(rows.map(async row => { if (row.messageType !== 'IMAGE' || !row.id) return row; try { const response = await http.get(`/chat/sessions/${sessionId}/messages/${row.id}/image`, { responseType: 'blob' }); const url = URL.createObjectURL(response.data); objectUrls.add(url); return { ...row, imagePreviewUrl: url } } catch { return row } })) }
function labelOf(type: string) { return { VISITOR: '我', AI: '智能助手', SYSTEM: '服务通知', AGENT: '人工客服' }[type] || type }
function statusLabel(status: string) { return { AI_SERVING: '智能服务中', PENDING_HANDOFF: '等待人工接入', AGENT_SERVING: '人工服务中', CLOSED: '已结束' }[status] || status }
function statusType(status: string) { return status === 'CLOSED' ? 'info' : status === 'PENDING_HANDOFF' ? 'warning' : status === 'AGENT_SERVING' ? 'success' : 'primary' }
function messageOf(error: unknown, fallback: string) { return error instanceof Error ? error.message : fallback }
function logout() { auth.logout(); router.replace('/login') }
</script>

<style scoped>
.customer-workspace { display: grid; grid-template-columns: 260px minmax(0, 1fr); width: min(1240px, 100%); min-height: calc(100vh - 64px); margin: 0 auto; background: #fff; border-inline: 1px solid #e5e5e7; }
.conversation-sidebar { padding: 18px 14px; border-right: 1px solid #e5e5e7; background: #fafafa; }
.new-chat { width: 100%; }
.sidebar-label { margin: 24px 8px 8px; color: #86868b; font-size: 12px; font-weight: 600; text-transform: uppercase; }
.conversation-item { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; padding: 11px 10px; border: 0; border-radius: 7px; background: transparent; text-align: left; cursor: pointer; }
.conversation-item:hover, .conversation-item.active { background: #e9e9ed; }
.conversation-item span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-item small, .sidebar-empty { color: #86868b; }
.sidebar-empty { padding: 18px 8px; font-size: 13px; }
.conversation-panel { display: grid; grid-template-rows: auto minmax(0, 1fr) auto auto; min-width: 0; height: calc(100vh - 64px); }
.conversation-header { display: flex; align-items: center; justify-content: space-between; padding: 22px 28px; border-bottom: 1px solid #e5e5e7; }
.conversation-header h1 { margin: 0; font-size: 20px; }.conversation-header p { margin: 5px 0 0; color: #86868b; font-size: 13px; }
.conversation-messages { padding: 28px; }
.empty-conversation { display: grid; place-content: center; min-height: 100%; text-align: center; color: #86868b; }.empty-conversation strong { color: #1d1d1f; font-size: 22px; }.empty-conversation span { margin-top: 8px; }
.customer-composer { padding: 16px 20px 20px; }.image-preview { margin: 0 20px; }
.message-image { display: block; width: min(320px, 100%); max-height: 320px; margin: 8px 0; border-radius: 7px; object-fit: contain; }
.image-preview { display: grid; grid-template-columns: 48px minmax(0,1fr) 32px; align-items: center; gap: 10px; padding: 10px; border: 1px solid #e5e5e7; border-radius: 7px; }.image-preview img { width: 48px; height: 48px; border-radius: 6px; object-fit: cover; }
@media (max-width: 760px) { .customer-workspace { grid-template-columns: 1fr; }.conversation-sidebar { display: flex; gap: 8px; overflow-x: auto; border-right: 0; border-bottom: 1px solid #e5e5e7; }.new-chat { width: auto; flex: 0 0 auto; }.sidebar-label,.sidebar-empty { display: none; }.conversation-item { width: 170px; flex: 0 0 auto; }.conversation-panel { height: calc(100vh - 130px); }.conversation-header,.conversation-messages { padding: 16px; }.customer-composer { padding: 12px; } }
</style>
