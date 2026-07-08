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
          <div v-for="message in messages" :key="message.id || message.content" class="message" :class="message.senderType.toLowerCase()">
            <strong>{{ labelOf(message.senderType) }}</strong>
            <div>{{ message.content }}</div>
          </div>
          <div v-if="typing" class="message ai">AI 正在回复...</div>
        </div>
        <div class="composer">
          <el-input v-model="draft" placeholder="请输入你的问题" @keyup.enter="send" />
          <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          <el-button :disabled="sending || !session" @click="handoff">转人工</el-button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, http } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface ChatSession {
  id: number
  title: string
}

interface ChatMessage {
  id?: number
  senderType: string
  content: string
}

const router = useRouter()
const auth = useAuthStore()
const session = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const messageList = ref<HTMLElement | null>(null)
const draft = ref('')
const typing = ref(false)
const sending = ref(false)
const canOpenConsole = computed(() => auth.hasAnyRole(['ADMIN', 'AGENT']))
let socket: WebSocket | null = null

onMounted(async () => {
  try {
    if (!auth.token) {
      await auth.login('visitor', 'visitor123')
    }
    session.value = await api<ChatSession>(http.post('/chat/sessions', { title: '访客咨询' }))
    connect()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '访客会话创建失败')
  }
})

onBeforeUnmount(() => socket?.close())

function connect() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${scheme}://${window.location.host}/ws/chat`)
  socket.onopen = () => socket?.send(JSON.stringify({ type: 'JOIN_SESSION', sessionId: session.value?.id }))
  socket.onmessage = (event) => {
    const payload = JSON.parse(event.data)
    if (payload.type === 'AI_MESSAGE') {
      typing.value = false
      addMessage({ senderType: 'AI', content: payload.content })
    }
    if (payload.type === 'SYSTEM_NOTICE' || payload.type === 'TICKET_CREATED') {
      addMessage({ senderType: 'SYSTEM', content: payload.content })
    }
  }
  socket.onerror = () => {
    typing.value = false
    ElMessage.warning('实时连接异常，将使用普通请求发送消息')
  }
}

async function send() {
  if (!draft.value.trim() || !session.value) return
  const content = draft.value.trim()
  draft.value = ''
  sending.value = true
  typing.value = true
  addMessage({ senderType: 'VISITOR', content })
  try {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'CHAT_MESSAGE', sessionId: session.value.id, content }))
    } else {
      const response = await api<ChatMessage>(http.post(`/chat/sessions/${session.value.id}/messages`, { content }))
      typing.value = false
      addMessage(response)
    }
  } catch (error) {
    typing.value = false
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

async function handoff() {
  if (!session.value) return
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'HANDOFF_REQUEST', sessionId: session.value.id, content: '访客主动请求转人工' }))
    return
  }
  try {
    await api(http.post(`/chat/sessions/${session.value.id}/handoff`, { content: '访客主动请求转人工' }))
    addMessage({ senderType: 'SYSTEM', content: '已提交转人工请求，请等待客服处理' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '转人工失败')
  }
}

function labelOf(type: string) {
  return { VISITOR: '我', AI: 'AI 智能体', SYSTEM: '系统' }[type] || type
}

function goConsole() {
  router.push(canOpenConsole.value ? '/console/chat' : '/login')
}

function addMessage(message: ChatMessage) {
  messages.value.push(message)
  nextTick(() => {
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.visitor-main {
  width: min(1080px, 100%);
  margin: 0 auto;
  padding: 20px;
}
</style>
