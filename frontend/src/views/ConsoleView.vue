<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">NexusMind 多智能体客服中枢</div>
      <div>
        <span>{{ auth.user?.displayName }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </header>
    <div class="workspace">
      <aside class="sidebar">
        <el-menu :default-active="activeTab" @select="selectTab">
          <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">{{ item.label }}</el-menu-item>
        </el-menu>
      </aside>
      <main class="main">
        <div v-loading="loading" class="metrics">
          <div class="metric">会话<strong>{{ sessions.length }}</strong></div>
          <div class="metric">工单<strong>{{ tickets.length }}</strong></div>
          <div v-if="isAdmin" class="metric">智能体<strong>{{ agents.length }}</strong></div>
          <div v-if="isAdmin" class="metric">模型<strong>{{ models.length }}</strong></div>
        </div>
        <div v-if="isAdmin" class="maintenance-bar">
          <el-button :loading="cleanupLoading" @click="cleanupDemoData">清理演示数据</el-button>
        </div>

        <section v-if="activeTab === 'chat'" v-loading="loading" class="panel">
          <div class="panel-header">
            <div>
              <h2>客服工作台</h2>
              <p>查看访客会话和智能体响应轨迹</p>
            </div>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
          </div>
          <el-table :data="sessions" height="420" empty-text="暂无会话" highlight-current-row @row-click="loadMessages">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="status" label="状态" width="140" />
            <el-table-column prop="currentAiAgentCode" label="智能体" width="140" />
          </el-table>
          <el-divider />
          <div class="messages compact">
            <el-empty v-if="!messages.length" description="点击会话查看消息" />
            <div v-for="message in messages" :key="message.id" class="message" :class="message.senderType.toLowerCase()">
              <strong>{{ labelOfSender(message.senderType) }}</strong>
              <div>{{ message.content }}</div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'tickets'" v-loading="loading" class="panel">
          <div class="panel-header">
            <div>
              <h2>工单管理</h2>
              <p>跟进转人工、投诉和售后升级请求</p>
            </div>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
          </div>
          <el-table :data="tickets" height="480" empty-text="暂无工单">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="priority" label="优先级" width="110" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status === 'CLOSED'" @click="closeTicket(row)">关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="activeTab === 'agents' && isAdmin" v-loading="loading" class="panel">
          <div class="panel-header">
            <div>
              <h2>智能体配置</h2>
              <p>启停不同业务场景下的客服智能体</p>
            </div>
          </div>
          <div class="route-preview">
            <el-input v-model="routePreviewText" placeholder="输入一段访客消息，预览会命中的智能体" @keyup.enter="previewRoute" />
            <el-button type="primary" :loading="routePreviewLoading" @click="previewRoute">预览</el-button>
          </div>
          <div v-if="routePreview" class="preview-result">
            <div>
              <span>命中智能体</span>
              <strong>{{ routePreview.agentName }}</strong>
            </div>
            <div>
              <span>业务场景</span>
              <strong>{{ routePreview.scenario }}</strong>
            </div>
            <div>
              <span>关键词</span>
              <strong>{{ routePreview.matchedKeyword || '兜底' }}</strong>
            </div>
            <div>
              <span>转人工</span>
              <strong>{{ routePreview.handoffRecommended ? '建议' : '否' }}</strong>
            </div>
          </div>
          <el-table :data="agents" height="480" empty-text="暂无智能体">
            <el-table-column prop="name" label="名称" width="180" />
            <el-table-column prop="scenario" label="场景" width="220" />
            <el-table-column prop="prompt" label="Prompt" />
            <el-table-column prop="enabled" label="启用" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="saveAgent(row)" />
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="activeTab === 'models' && isAdmin" v-loading="loading" class="panel">
          <div class="panel-header">
            <div>
              <h2>模型配置</h2>
              <p>管理当前启用模型的调用参数</p>
            </div>
          </div>
          <el-table :data="models" empty-text="暂无模型">
            <el-table-column prop="provider" label="供应商" />
            <el-table-column label="模型名">
              <template #default="{ row }">
                <el-input v-model="row.modelName" />
              </template>
            </el-table-column>
            <el-table-column label="温度" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.temperature" :min="0" :max="1" :step="0.1" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="saveModel(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="activeTab === 'logs' && isAdmin" v-loading="loading" class="panel">
          <div class="panel-header">
            <div>
              <h2>调用日志</h2>
              <p>排查模型调用耗时、状态和异常信息</p>
            </div>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
          </div>
          <el-table :data="logs" height="480" empty-text="暂无日志">
            <el-table-column prop="modelName" label="模型" width="150" />
            <el-table-column prop="agentCode" label="智能体" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="latencyMs" label="耗时(ms)" width="110" />
            <el-table-column prop="errorMessage" label="错误" />
          </el-table>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, http } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface ChatSession {
  id: number
  title: string
  status: string
  currentAiAgentCode: string
}

interface ChatMessage {
  id: number
  senderType: string
  content: string
}

interface Ticket {
  id: number
  title: string
  priority: string
  status: string
}

interface AiAgent {
  id: number
  name: string
  scenario: string
  prompt: string
  enabled: boolean
}

interface AiModel {
  id: number
  provider: string
  modelName: string
  temperature: number
}

interface ModelCallLog {
  id: number
  modelName: string
  agentCode: string
  status: string
  latencyMs: number
  errorMessage: string
}

interface RoutePreview {
  agentId: number
  agentCode: string
  agentName: string
  scenario: string
  matchedKeyword: string | null
  fallback: boolean
  handoffRecommended: boolean
}

interface DemoDataCleanupResponse {
  sessions: number
  messages: number
  tickets: number
  handoffRecords: number
  modelCallLogs: number
}

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const sessions = ref<ChatSession[]>([])
const messages = ref<ChatMessage[]>([])
const tickets = ref<Ticket[]>([])
const agents = ref<AiAgent[]>([])
const models = ref<AiModel[]>([])
const logs = ref<ModelCallLog[]>([])
const routePreviewText = ref('')
const routePreview = ref<RoutePreview | null>(null)
const routePreviewLoading = ref(false)
const cleanupLoading = ref(false)
const loading = ref(false)
const activeTab = computed(() => String(route.params.tab || 'chat'))
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const menuItems = computed(() => [
  { key: 'chat', label: '客服工作台' },
  { key: 'tickets', label: '工单管理' },
  ...(isAdmin.value
    ? [
        { key: 'agents', label: '智能体配置' },
        { key: 'models', label: '模型配置' },
        { key: 'logs', label: '调用日志' }
      ]
    : [])
])

onMounted(async () => {
  if (!auth.token) {
    router.push('/login')
    return
  }
  ensureAllowedTab()
  await refresh()
})

watch(activeTab, ensureAllowedTab)

async function refresh() {
  loading.value = true
  try {
    const [sessionRows, ticketRows] = await Promise.all([
      api<ChatSession[]>(http.get('/chat/sessions')),
      api<Ticket[]>(http.get('/tickets'))
    ])
    sessions.value = sessionRows
    tickets.value = ticketRows
    if (isAdmin.value) {
      const [agentRows, modelRows, logRows] = await Promise.all([
        api<AiAgent[]>(http.get('/agents')),
        api<AiModel[]>(http.get('/models')),
        api<ModelCallLog[]>(http.get('/logs'))
      ])
      agents.value = agentRows
      models.value = modelRows
      logs.value = logRows
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMessages(row: ChatSession) {
  try {
    messages.value = await api<ChatMessage[]>(http.get(`/chat/sessions/${row.id}/messages`))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '消息加载失败')
  }
}

async function closeTicket(row: Ticket) {
  try {
    await api(http.put(`/tickets/${row.id}/status`, { status: 'CLOSED', assigneeId: auth.user?.userId }))
    ElMessage.success('工单已关闭')
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关闭失败')
  }
}

async function saveAgent(row: AiAgent) {
  try {
    await api(http.put(`/agents/${row.id}`, row))
    ElMessage.success('智能体已更新')
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function saveModel(row: AiModel) {
  try {
    await api(http.put(`/models/${row.id}`, row))
    ElMessage.success('模型配置已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
}

async function previewRoute() {
  const content = routePreviewText.value.trim()
  if (!content) {
    ElMessage.warning('请输入要预览的访客消息')
    return
  }
  routePreviewLoading.value = true
  try {
    routePreview.value = await api<RoutePreview>(http.post('/agents/route-preview', { content }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预览失败')
  } finally {
    routePreviewLoading.value = false
  }
}

async function cleanupDemoData() {
  cleanupLoading.value = true
  try {
    const result = await api<DemoDataCleanupResponse>(http.delete('/admin/demo-data'))
    ElMessage.success(`已清理 ${result.sessions} 个会话、${result.messages} 条消息、${result.tickets} 个工单`)
    messages.value = []
    await refresh()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清理失败')
  } finally {
    cleanupLoading.value = false
  }
}

function selectTab(tab: string) {
  router.push(`/console/${tab}`)
}

function logout() {
  auth.logout()
  router.push('/login')
}

function ensureAllowedTab() {
  if (!menuItems.value.some((item) => item.key === activeTab.value)) {
    router.replace('/console/chat')
  }
}

function labelOfSender(type: string) {
  return { VISITOR: '访客', AI: 'AI 智能体', SYSTEM: '系统', AGENT: '人工客服' }[type] || type
}
</script>

<style scoped>
.compact {
  max-height: 300px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  color: #16324f;
}

.panel-header p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.route-preview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.preview-result {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.preview-result div {
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fafc;
}

.preview-result span {
  display: block;
  color: #667085;
  font-size: 12px;
  margin-bottom: 4px;
}

.preview-result strong {
  color: #16324f;
  font-size: 14px;
}

@media (max-width: 800px) {
  .topbar {
    height: auto;
    min-height: 60px;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 16px;
  }

  .sidebar {
    padding: 8px 12px;
  }

  .sidebar :deep(.el-menu) {
    display: flex;
    overflow-x: auto;
    border-right: 0;
  }

  .sidebar :deep(.el-menu-item) {
    flex: 0 0 auto;
  }

  .main {
    padding: 12px;
  }

  .panel {
    overflow-x: auto;
  }

  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .route-preview,
  .preview-result {
    grid-template-columns: 1fr;
  }
}
</style>
