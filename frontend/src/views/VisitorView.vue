<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">NexusMind 访客咨询</div>
      <div>
        <el-button @click="goConsole">{{ canOpenConsole ? '客服控制台' : '账号登录' }}</el-button>
      </div>
    </header>
    <main class="visitor-main">
      <section class="panel chat-layout">
        <div ref="messageList" class="messages">
          <el-empty v-if="!messages.length && !typing" description="请输入问题开始咨询" />
          <div v-for="message in messages" :key="message.id || message.localId" class="message" :class="message.senderType.toLowerCase()">
            <strong>{{ labelOf(message.senderType) }}</strong>
            <img v-if="message.imagePreviewUrl" :src="message.imagePreviewUrl" :alt="message.attachmentName || '上传的图片'" class="message-image" />
            <div>{{ message.content }}</div>
          </div>
          <div v-if="typing" class="message ai">{{ longWait ? 'AI 仍在整理回复，请稍候' : 'AI 正在回复...' }}</div>
        </div>
        <div v-if="imagePreviewUrl" class="image-preview">
          <img :src="imagePreviewUrl" :alt="selectedImage?.name || '待发送图片'" />
          <span>{{ selectedImage?.name }}</span>
          <el-tooltip content="移除图片" placement="top">
            <el-button :icon="Close" circle text aria-label="移除图片" @click="clearSelectedImage" />
          </el-tooltip>
        </div>
        <div class="composer">
          <input ref="imageInput" hidden type="file" accept="image/jpeg,image/png,image/gif" @change="selectImage" />
          <el-button :icon="Picture" :disabled="sending" aria-label="上传图片" @click="imageInput?.click()">上传图片</el-button>
          <el-input v-model="draft" :disabled="sending" :placeholder="selectedImage ? '可以补充图片相关问题' : '请输入你的问题'" @keyup.enter="send" />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim() && !selectedImage" @click="send">发送</el-button>
          <el-button :loading="handoffSending" :disabled="sending || handoffSending" @click="handoff">转人工</el-button>
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

interface ChatSession {
  id: number
  title: string
}

interface ChatMessage {
  id?: number
  localId?: string
  senderType: string
  content: string
  messageType?: string
  attachmentName?: string
  imagePreviewUrl?: string
}

const router = useRouter()
const auth = useAuthStore()
const session = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const messageList = ref<HTMLElement | null>(null)
const draft = ref('')
const selectedImage = ref<File | null>(null)
const imagePreviewUrl = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const typing = ref(false)
const sending = ref(false)
const handoffSending = ref(false)
const longWait = ref(false)
const canOpenConsole = computed(() => auth.hasAnyRole(['ADMIN', 'AGENT']))
let socket: WebSocket | null = null
let longWaitTimer: number | undefined
let localMessageId = 0
let streamingAiMessage: ChatMessage | null = null
let socketReady: Promise<boolean> | null = null
let restRequestInFlight = false
let suppressAiContent = ''
let suppressAiTimer: number | undefined

onMounted(async () => {
  try {
    if (!auth.hasAnyRole(['ADMIN', 'AGENT'])) {
      await auth.startVisitor()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '访客登录失败')
  }
})

onBeforeUnmount(() => {
  window.clearTimeout(longWaitTimer)
  window.clearTimeout(suppressAiTimer)
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  socket?.close()
})

function connect() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${scheme}://${window.location.host}/ws/chat?token=${encodeURIComponent(auth.token || '')}`)
  socketReady = new Promise((resolve) => {
    if (!socket) return resolve(false)
    socket.onopen = () => {
      socket?.send(JSON.stringify({ type: 'JOIN_SESSION', sessionId: session.value?.id }))
      resolve(true)
    }
    socket.onerror = () => {
      finishWaiting()
      ElMessage.warning('实时连接异常，将使用普通请求发送消息')
      resolve(false)
    }
    socket.onclose = () => {
      finishWaiting()
      resolve(false)
    }
  })
  socket.onmessage = (event) => {
    const payload = JSON.parse(event.data)
    if (payload.type === 'AI_MESSAGE_DELTA') {
      appendAiDelta(payload.content || '')
    }
    if (payload.type === 'AI_MESSAGE_DONE') {
      finishWaiting()
      streamingAiMessage = null
    }
    if (payload.type === 'AI_MESSAGE') {
      finishWaiting()
      if (!restRequestInFlight && payload.content !== suppressAiContent) {
        addMessage({ senderType: 'AI', content: payload.content })
      }
    }
    if (payload.type === 'SYSTEM_NOTICE' || payload.type === 'TICKET_CREATED') {
      addMessage({ senderType: 'SYSTEM', content: payload.content })
    }
    if (payload.type === 'ERROR') {
      finishWaiting()
      ElMessage.error(payload.content || '实时消息处理失败')
    }
  }
}

async function send() {
  if (sending.value || (!draft.value.trim() && !selectedImage.value)) return
  const file = selectedImage.value
  const content = draft.value.trim() || '请描述这张图片'
  const previewUrl = imagePreviewUrl.value
  draft.value = ''
  selectedImage.value = null
  imagePreviewUrl.value = ''
  if (imageInput.value) imageInput.value.value = ''
  startWaiting()
  addMessage({ senderType: 'VISITOR', content, messageType: file ? 'IMAGE' : 'TEXT', imagePreviewUrl: previewUrl, attachmentName: file?.name })
  try {
    const activeSession = await ensureSession()
    if (file) {
      const form = new FormData()
      form.append('content', content)
      form.append('image', file)
      restRequestInFlight = true
      const response = await api<ChatMessage>(http.post(`/chat/sessions/${activeSession.id}/messages/image`, form))
      suppressRealtimeAi(response.content)
      finishWaiting()
      addMessage(response)
    } else if (await waitForSocket()) {
      socket?.send(JSON.stringify({ type: 'CHAT_MESSAGE', sessionId: activeSession.id, content }))
    } else {
      restRequestInFlight = true
      const response = await api<ChatMessage>(http.post(`/chat/sessions/${activeSession.id}/messages`, { content }))
      suppressRealtimeAi(response.content)
      finishWaiting()
      addMessage(response)
    }
  } catch (error) {
    finishWaiting()
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    restRequestInFlight = false
  }
}

async function waitForSocket() {
  if (socket?.readyState === WebSocket.OPEN) return true
  if (!socketReady) return false
  return Promise.race([
    socketReady,
    new Promise<boolean>((resolve) => window.setTimeout(() => resolve(false), 2500))
  ])
}

function suppressRealtimeAi(content: string) {
  suppressAiContent = content
  window.clearTimeout(suppressAiTimer)
  suppressAiTimer = window.setTimeout(() => {
    suppressAiContent = ''
  }, 5000)
}

function selectImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.error('图片不能超过 8 MB')
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  clearSelectedImage()
  selectedImage.value = file
  imagePreviewUrl.value = URL.createObjectURL(file)
}

function clearSelectedImage() {
  if (imagePreviewUrl.value) URL.revokeObjectURL(imagePreviewUrl.value)
  imagePreviewUrl.value = ''
  selectedImage.value = null
  if (imageInput.value) imageInput.value.value = ''
}

async function handoff() {
  if (handoffSending.value || sending.value) return
  handoffSending.value = true
  try {
    const activeSession = await ensureSession()
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'HANDOFF_REQUEST', sessionId: activeSession.id, content: '访客主动请求转人工' }))
    } else {
      await api(http.post(`/chat/sessions/${activeSession.id}/handoff`, { content: '访客主动请求转人工' }))
      addMessage({ senderType: 'SYSTEM', content: '已提交转人工请求，请等待客服处理' })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '转人工失败')
  } finally {
    handoffSending.value = false
  }
}

function labelOf(type: string) {
  return { VISITOR: '我', AI: 'AI 智能体', SYSTEM: '系统' }[type] || type
}

function goConsole() {
  router.push(canOpenConsole.value ? '/console/chat' : '/login')
}

async function ensureSession() {
  if (session.value) {
    return session.value
  }
  session.value = await api<ChatSession>(http.post('/chat/sessions', { title: '访客咨询' }))
  connect()
  return session.value
}

function addMessage(message: ChatMessage) {
  if (!message.id && !message.localId) {
    message.localId = `local-${++localMessageId}`
  }
  messages.value.push(message)
  scrollToBottom()
}

function appendAiDelta(content: string) {
  if (!streamingAiMessage) {
    streamingAiMessage = { localId: `local-${++localMessageId}`, senderType: 'AI', content: '' }
    messages.value.push(streamingAiMessage)
  }
  typing.value = false
  longWait.value = false
  window.clearTimeout(longWaitTimer)
  streamingAiMessage.content += content
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  })
}

function startWaiting() {
  sending.value = true
  typing.value = true
  longWait.value = false
  window.clearTimeout(longWaitTimer)
  longWaitTimer = window.setTimeout(() => {
    longWait.value = true
  }, 8000)
}

function finishWaiting() {
  sending.value = false
  typing.value = false
  longWait.value = false
  window.clearTimeout(longWaitTimer)
}
</script>

<style scoped>
.visitor-main {
  width: min(1080px, 100%);
  margin: 0 auto;
  padding: 20px;
}

.image-preview {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 10px;
  padding: 10px 4px;
  border-top: 1px solid #e6ebf2;
  color: #4b5563;
  font-size: 14px;
}

.image-preview img {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  object-fit: cover;
}

.image-preview span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-image {
  display: block;
  width: min(320px, 100%);
  max-height: 320px;
  margin: 6px 0;
  border-radius: 6px;
  object-fit: contain;
}

</style>
