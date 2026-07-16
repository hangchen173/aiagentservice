<template>
  <div class="page app-page">
    <header class="topbar glass-bar">
      <div class="brand">NexusMind <span>Backstage Desk</span></div>
      <div class="account-actions"><span>{{ auth.user?.displayName }}</span><el-tag effect="plain">{{ isAdmin ? '管理员' : '客服' }}</el-tag><el-button text @click="logout">退出</el-button></div>
    </header>
    <div class="workspace console-workspace">
      <aside class="sidebar console-sidebar">
        <el-menu :default-active="activeTab" @select="selectTab">
          <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.key">{{ item.label }}</el-menu-item>
        </el-menu>
      </aside>
      <main class="main console-main" :class="{ 'chat-main': activeTab === 'chat' }">
        <div class="page-heading">
          <div><h1>{{ currentTitle }}</h1><p>{{ currentSubtitle }}</p></div>
          <div class="heading-actions">
            <el-button :icon="Refresh" :loading="loading" circle aria-label="刷新" @click="refresh" />
            <el-button v-if="isAdmin" type="danger" plain :loading="cleanupLoading" @click="cleanupHistory">清理全部历史</el-button>
          </div>
        </div>

        <div class="metrics">
          <div class="metric"><span>全部会话</span><strong>{{ sessions.length }}</strong></div>
          <div class="metric"><span>待接工单</span><strong>{{ tickets.filter(t => t.status === 'OPEN').length }}</strong></div>
          <div class="metric"><span>处理中</span><strong>{{ tickets.filter(t => t.status === 'PROCESSING').length }}</strong></div>
          <div class="metric"><span>今日状态</span><strong class="online-text">在线</strong></div>
        </div>

        <section v-if="activeTab === 'chat'" class="support-desk">
          <div class="queue-pane">
            <div class="pane-title"><strong>会话队列</strong><span>{{ filteredSessions.length }}</span></div>
            <el-select v-model="queueFilter" aria-label="会话筛选">
              <el-option label="全部会话" value="ALL" /><el-option label="等待接入" value="PENDING_HANDOFF" /><el-option label="人工处理中" value="AGENT_SERVING" /><el-option label="已结束" value="CLOSED" />
            </el-select>
            <button v-for="item in filteredSessions" :key="item.id" class="queue-item" :class="{ active: selectedSession?.id === item.id }" type="button" @click="selectSession(item)">
              <span><strong>{{ item.title }}</strong><small>会话 #{{ item.id }}</small></span>
              <el-tag size="small" :type="sessionTag(item.status)" effect="plain">{{ sessionStatus(item.status) }}</el-tag>
            </button>
            <div v-if="!filteredSessions.length" class="pane-empty">当前没有会话</div>
          </div>

          <div class="agent-chat-pane" :class="{ 'has-session': selectedSession }">
            <div v-if="selectedSession" class="agent-chat-header">
              <div><h2>{{ selectedSession.title }}</h2><p>会话 #{{ selectedSession.id }} · {{ sessionStatus(selectedSession.status) }}</p></div>
              <div class="ticket-actions">
                <el-button v-if="selectedTicket?.status === 'OPEN'" type="primary" @click="acceptTicket(selectedTicket)">接单</el-button>
                <el-button v-if="selectedTicket?.status === 'PROCESSING'" @click="closeTicket(selectedTicket)">结束服务</el-button>
                <el-button v-if="selectedTicket?.status === 'CLOSED'" type="danger" plain @click="deleteTicket(selectedTicket)">删除工单</el-button>
              </div>
            </div>
            <div ref="messageList" v-loading="messagesLoading" class="messages agent-messages">
              <div v-if="!selectedSession" class="pane-empty large">从左侧选择一个会话</div>
              <div v-for="message in messages" :key="message.id || message.localId" class="message" :class="message.senderType.toLowerCase()">
                <strong>{{ senderLabel(message.senderType) }}</strong>
                <img v-if="message.imagePreviewUrl" :src="message.imagePreviewUrl" :alt="message.attachmentName || '咨询图片'" class="message-image" />
                <div>{{ message.content }}</div>
              </div>
            </div>
            <div class="reply-area">
              <div v-if="selectedTicket?.status === 'OPEN'" class="reply-gate">
                <span>该会话正在等待人工接入，接单后即可回复用户。</span>
                <el-button type="primary" @click="acceptTicket(selectedTicket)">接单并回复</el-button>
              </div>
              <div class="composer agent-composer">
                <el-input v-model="replyDraft" type="textarea" :rows="2" resize="none" :disabled="!canReply" :placeholder="replyPlaceholder" @keydown.ctrl.enter="sendReply" />
                <el-button type="primary" :loading="replySending" :disabled="!canReply || !replyDraft.trim()" @click="sendReply">回复</el-button>
              </div>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'tickets'" class="data-panel">
          <div class="table-toolbar"><el-select v-model="ticketFilter" aria-label="工单筛选"><el-option label="全部" value="ALL" /><el-option label="待接" value="OPEN" /><el-option label="处理中" value="PROCESSING" /><el-option label="已关闭" value="CLOSED" /></el-select></div>
          <el-table :data="filteredTickets" empty-text="暂无工单" @row-click="openTicketSession">
            <el-table-column prop="id" label="编号" width="90" /><el-table-column prop="title" label="工单" /><el-table-column prop="priority" label="优先级" width="110" /><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="ticketTag(row.status)" effect="plain">{{ ticketStatus(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="230"><template #default="{ row }"><el-button v-if="row.status === 'OPEN'" size="small" type="primary" @click.stop="acceptTicket(row)">接单</el-button><el-button v-if="row.status === 'PROCESSING'" size="small" @click.stop="closeTicket(row)">关闭</el-button><el-button v-if="row.status === 'CLOSED'" size="small" type="danger" plain @click.stop="deleteTicket(row)">删除</el-button></template></el-table-column>
          </el-table>
        </section>

        <section v-if="activeTab === 'agents' && isAdmin" class="data-panel">
          <div class="route-preview"><el-input v-model="routePreviewText" placeholder="输入访客消息以预览路由" @keyup.enter="previewRoute" /><el-button type="primary" :loading="routePreviewLoading" @click="previewRoute">预览</el-button></div>
          <div v-if="routePreview" class="preview-result"><div><span>智能体</span><strong>{{ routePreview.agentName }}</strong></div><div><span>场景</span><strong>{{ routePreview.scenario }}</strong></div><div><span>关键词</span><strong>{{ routePreview.matchedKeyword || '兜底' }}</strong></div><div><span>转人工</span><strong>{{ routePreview.handoffRecommended ? '建议' : '否' }}</strong></div></div>
          <el-table :data="agents" empty-text="暂无智能体"><el-table-column prop="name" label="名称" width="180" /><el-table-column prop="scenario" label="场景" width="220" /><el-table-column prop="prompt" label="系统指令" /><el-table-column label="启用" width="90"><template #default="{ row }"><el-switch v-model="row.enabled" @change="saveAgent(row)" /></template></el-table-column></el-table>
        </section>

        <section v-if="activeTab === 'models' && isAdmin" class="data-panel"><el-table :data="models" empty-text="暂无模型"><el-table-column prop="provider" label="供应商" width="150" /><el-table-column label="模型名"><template #default="{ row }"><el-input v-model="row.modelName" /></template></el-table-column><el-table-column label="温度" width="160"><template #default="{ row }"><el-input-number v-model="row.temperature" :min="0" :max="1" :step="0.1" /></template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button size="small" type="primary" @click="saveModel(row)">保存</el-button></template></el-table-column></el-table></section>
        <section v-if="activeTab === 'logs' && isAdmin" class="data-panel"><el-table :data="logs" empty-text="暂无日志"><el-table-column prop="modelName" label="模型" width="190" /><el-table-column prop="agentCode" label="智能体" width="130" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="latencyMs" label="耗时(ms)" width="120" /><el-table-column prop="errorMessage" label="错误" /></el-table></section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { api, http } from '../api/http'
import { useAuthStore } from '../stores/auth'

interface ChatSession { id:number; title:string; status:string; currentAiAgentCode:string }
interface ChatMessage { id?:number; localId?:string; senderType:string; content:string; messageType?:string; attachmentName?:string; imagePreviewUrl?:string }
interface Ticket { id:number; sessionId:number; assigneeId?:number; title:string; priority:string; status:string }
interface AiAgent { id:number; name:string; scenario:string; prompt:string; enabled:boolean }
interface AiModel { id:number; provider:string; modelName:string; temperature:number }
interface ModelCallLog { id:number; modelName:string; agentCode:string; status:string; latencyMs:number; errorMessage:string }
interface RoutePreview { agentName:string; scenario:string; matchedKeyword:string|null; handoffRecommended:boolean }
interface CleanupResult { sessions:number; messages:number; tickets:number }

const router=useRouter(), route=useRoute(), auth=useAuthStore()
const sessions=ref<ChatSession[]>([]), messages=ref<ChatMessage[]>([]), tickets=ref<Ticket[]>([]), agents=ref<AiAgent[]>([]), models=ref<AiModel[]>([]), logs=ref<ModelCallLog[]>([])
const selectedSession=ref<ChatSession|null>(null), replyDraft=ref(''), queueFilter=ref('ALL'), ticketFilter=ref('ALL'), routePreviewText=ref(''), routePreview=ref<RoutePreview|null>(null)
const loading=ref(false), messagesLoading=ref(false), replySending=ref(false), cleanupLoading=ref(false), routePreviewLoading=ref(false)
const messageList=ref<HTMLElement|null>(null), objectUrls=new Set<string>()
let socket:WebSocket|null=null, socketVersion=0, pollTimer:number|undefined, localId=0
const activeTab=computed(()=>String(route.params.tab||'chat')), isAdmin=computed(()=>auth.user?.role==='ADMIN')
const menuItems=computed(()=>[{key:'chat',label:'客服工作台'},{key:'tickets',label:'工单管理'},...(isAdmin.value?[{key:'agents',label:'智能体配置'},{key:'models',label:'模型配置'},{key:'logs',label:'调用日志'}]:[])])
const filteredSessions=computed(()=>queueFilter.value==='ALL'?sessions.value:sessions.value.filter(x=>x.status===queueFilter.value))
const filteredTickets=computed(()=>ticketFilter.value==='ALL'?tickets.value:tickets.value.filter(x=>x.status===ticketFilter.value))
const selectedTicket=computed(()=>tickets.value.find(x=>x.sessionId===selectedSession.value?.id))
const canReply=computed(()=>selectedTicket.value?.status==='PROCESSING'&&selectedSession.value?.status!=='CLOSED')
const replyPlaceholder=computed(()=>!selectedSession.value?'选择会话后回复':selectedTicket.value?.status==='OPEN'?'请先接单':canReply.value?'输入人工回复，Ctrl + Enter 发送':'当前会话不可回复')
const titles:Record<string,[string,string]>={chat:['客服工作台','接入用户会话并提供人工支持'],tickets:['工单管理','处理转人工请求及已完成记录'],agents:['智能体配置','管理业务路由与服务场景'],models:['模型配置','管理当前启用的文本模型'],logs:['调用日志','查看模型调用状态与耗时']}
const currentTitle=computed(()=>titles[activeTab.value]?.[0]||'工作台'), currentSubtitle=computed(()=>titles[activeTab.value]?.[1]||'')

onMounted(async()=>{ensureAllowedTab();await refresh();pollTimer=window.setInterval(()=>{refreshCore().catch(()=>undefined)},4000)})
onBeforeUnmount(()=>{socketVersion++;socket?.close();window.clearInterval(pollTimer);objectUrls.forEach(URL.revokeObjectURL)})
watch(activeTab,ensureAllowedTab)

async function refresh(){loading.value=true;try{await refreshCore();if(isAdmin.value){[agents.value,models.value,logs.value]=await Promise.all([api<AiAgent[]>(http.get('/agents')),api<AiModel[]>(http.get('/models')),api<ModelCallLog[]>(http.get('/logs'))])}}catch(e){ElMessage.error(messageOf(e,'加载失败'))}finally{loading.value=false}}
async function refreshCore(){const [sessionRows,ticketRows]=await Promise.all([api<ChatSession[]>(http.get('/chat/sessions')),api<Ticket[]>(http.get('/tickets'))]);sessions.value=sessionRows;tickets.value=ticketRows;if(selectedSession.value){selectedSession.value=sessionRows.find(x=>x.id===selectedSession.value?.id)||null;if(!selectedSession.value)messages.value=[]}}
async function selectSession(item:ChatSession){selectedSession.value=item;messagesLoading.value=true;try{messages.value=await hydrateImages(await api<ChatMessage[]>(http.get(`/chat/sessions/${item.id}/messages`)),item.id);connect();scrollBottom()}catch(e){ElMessage.error(messageOf(e,'消息加载失败'))}finally{messagesLoading.value=false}}
function connect(){if(!selectedSession.value)return;const version=++socketVersion;socket?.close();const scheme=location.protocol==='https:'?'wss':'ws';const active=new WebSocket(`${scheme}://${location.host}/ws/chat?token=${encodeURIComponent(auth.token)}`);socket=active;active.onopen=()=>active.send(JSON.stringify({type:'JOIN_SESSION',sessionId:selectedSession.value?.id}));active.onmessage=async event=>{const payload=JSON.parse(event.data);if(payload.type==='ERROR'){ElMessage.warning(payload.content||'会话连接已断开');active.close();return}if(['CHAT_MESSAGE','SYSTEM_NOTICE','TICKET_CREATED','AGENT_ACCEPTED','SESSION_CLOSED'].includes(payload.type)){await reloadSelected();await refreshCore()}};active.onclose=()=>{if(version!==socketVersion||!selectedSession.value)return;refreshCore().then(()=>{if(version===socketVersion&&selectedSession.value)window.setTimeout(connect,1500)}).catch(()=>undefined)}}
async function reloadSelected(){if(!selectedSession.value)return;messages.value=await hydrateImages(await api<ChatMessage[]>(http.get(`/chat/sessions/${selectedSession.value.id}/messages`)),selectedSession.value.id);scrollBottom()}
async function acceptTicket(ticket:Ticket){try{await api(http.post(`/tickets/${ticket.id}/accept`));ElMessage.success('已接单');await refreshCore();const target=sessions.value.find(x=>x.id===ticket.sessionId);if(target)await selectSession(target)}catch(e){ElMessage.error(messageOf(e,'接单失败'))}}
async function sendReply(){if(!canReply.value||!replyDraft.value.trim()||!selectedSession.value)return;replySending.value=true;const content=replyDraft.value.trim();try{const row=await api<ChatMessage>(http.post(`/chat/sessions/${selectedSession.value.id}/agent-messages`,{content}));replyDraft.value='';row.localId=`local-${++localId}`;messages.value.push(row);scrollBottom()}catch(e){ElMessage.error(messageOf(e,'回复失败'))}finally{replySending.value=false}}
async function closeTicket(ticket:Ticket){try{await ElMessageBox.confirm('关闭后用户将不能继续在该会话中发送消息。','结束人工服务',{type:'warning',confirmButtonText:'确认关闭',cancelButtonText:'取消'});await api(http.post(`/tickets/${ticket.id}/close`));ElMessage.success('工单已关闭');await refreshCore();await reloadSelected()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(messageOf(e,'关闭失败'))}}
async function deleteTicket(ticket:Ticket){try{await ElMessageBox.confirm('删除后工单将从列表中移除，会话消息仍保留。','删除已关闭工单',{type:'warning',confirmButtonText:'删除',cancelButtonText:'取消'});await api(http.delete(`/tickets/${ticket.id}`));ElMessage.success('工单已删除');await refreshCore()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(messageOf(e,'删除失败'))}}
async function openTicketSession(ticket:Ticket){const target=sessions.value.find(x=>x.id===ticket.sessionId);if(target){await router.push('/console/chat');await selectSession(target)}}
async function cleanupHistory(){try{await ElMessageBox.confirm('这会永久清除全部会话、消息、工单、模型日志和聊天图片，账号及配置会保留。','清理全部历史数据',{type:'error',confirmButtonText:'确认清理',cancelButtonText:'取消'});cleanupLoading.value=true;const result=await api<CleanupResult>(http.delete('/admin/history'));selectedSession.value=null;messages.value=[];await refresh();ElMessage.success(`已清理 ${result.sessions} 个会话、${result.messages} 条消息和 ${result.tickets} 个工单`)}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(messageOf(e,'清理失败'))}finally{cleanupLoading.value=false}}
async function previewRoute(){if(!routePreviewText.value.trim())return;routePreviewLoading.value=true;try{routePreview.value=await api(http.post('/agents/route-preview',{content:routePreviewText.value.trim()}))}catch(e){ElMessage.error(messageOf(e,'预览失败'))}finally{routePreviewLoading.value=false}}
async function saveAgent(row:AiAgent){try{await api(http.put(`/agents/${row.id}`,row));ElMessage.success('智能体已更新')}catch(e){row.enabled=!row.enabled;ElMessage.error(messageOf(e,'保存失败'))}}
async function saveModel(row:AiModel){try{await api(http.put(`/models/${row.id}`,row));ElMessage.success('模型配置已更新')}catch(e){ElMessage.error(messageOf(e,'保存失败'))}}
async function hydrateImages(rows:ChatMessage[],sessionId:number){return Promise.all(rows.map(async row=>{if(row.messageType!=='IMAGE'||!row.id)return row;try{const response=await http.get(`/chat/sessions/${sessionId}/messages/${row.id}/image`,{responseType:'blob'});const url=URL.createObjectURL(response.data);objectUrls.add(url);return{...row,imagePreviewUrl:url}}catch{return row}}))}
function selectTab(tab:string){router.push(`/console/${tab}`)}function ensureAllowedTab(){if(!menuItems.value.some(x=>x.key===activeTab.value))router.replace('/console/chat')}
function logout(){auth.logout();router.replace('/login')}function scrollBottom(){nextTick(()=>{if(messageList.value)messageList.value.scrollTop=messageList.value.scrollHeight})}
function senderLabel(t:string){return{VISITOR:'用户',AI:'智能助手',SYSTEM:'服务通知',AGENT:'人工客服'}[t]||t}function sessionStatus(s:string){return{AI_SERVING:'智能服务',PENDING_HANDOFF:'等待人工',AGENT_SERVING:'人工服务',CLOSED:'已结束'}[s]||s}function ticketStatus(s:string){return{OPEN:'待接',PROCESSING:'处理中',CLOSED:'已关闭'}[s]||s}function sessionTag(s:string){return s==='PENDING_HANDOFF'?'warning':s==='AGENT_SERVING'?'success':s==='CLOSED'?'info':'primary'}function ticketTag(s:string){return s==='OPEN'?'warning':s==='PROCESSING'?'success':'info'}function messageOf(e:unknown,f:string){return e instanceof Error?e.message:f}
</script>

<style scoped>
.console-workspace{grid-template-columns:220px minmax(0,1fr)}.console-sidebar{padding:18px 10px;background:linear-gradient(180deg,#1c1519,#120e11)}.console-sidebar .el-menu{border-right:0;background:transparent}.console-main{padding:28px;background:radial-gradient(circle at 86% 0,rgba(158,37,64,.1),transparent 30%)}.console-main.chat-main{display:flex;height:calc(100dvh - 64px);min-height:0;flex-direction:column;overflow:hidden}.page-heading{display:flex;flex:0 0 auto;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:22px}.page-heading h1{margin:0;font-family:Georgia,"Songti SC",serif;font-size:29px;font-weight:500}.page-heading p{margin:7px 0 0;color:var(--muted)}.heading-actions,.account-actions,.ticket-actions{display:flex;align-items:center;gap:10px}.online-text{font-size:18px!important;color:var(--cyan)}.chat-main .metrics{flex:0 0 auto}.support-desk{display:grid;min-width:0;min-height:0;grid-template-columns:320px minmax(0,1fr);border:1px solid var(--line-strong);border-radius:6px;background:#1a1418;box-shadow:var(--shadow);overflow:hidden}.chat-main .support-desk{height:auto;max-height:none;flex:1 1 auto}.queue-pane{min-height:0;padding:16px;border-right:1px solid var(--line);background:#181216;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable}.pane-title{display:flex;justify-content:space-between;margin-bottom:14px;color:var(--gold)}.queue-pane .el-select{width:100%;margin-bottom:12px}.queue-item{display:flex;width:100%;align-items:center;justify-content:space-between;gap:8px;padding:12px 10px;border:1px solid transparent;border-radius:5px;background:transparent;color:#d8cecb;text-align:left;cursor:pointer}.queue-item:hover,.queue-item.active{border-color:var(--line);background:#2a1d23}.queue-item.active{border-left-color:var(--crimson-bright)}.queue-item span,.queue-item strong,.queue-item small{display:block;min-width:0}.queue-item strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.queue-item small{margin-top:4px;color:var(--muted)}.agent-chat-pane{display:grid;min-width:0;min-height:0;grid-template-rows:auto minmax(0,1fr) auto;overflow:hidden}.agent-chat-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px 22px;border-bottom:1px solid var(--line);background:#21191e}.agent-chat-header h2{margin:0;font-family:Georgia,"Songti SC",serif;font-size:19px;font-weight:500}.agent-chat-header p{margin:5px 0 0;color:var(--muted);font-size:12px}.agent-messages{min-height:0;padding:20px;background:radial-gradient(circle at 50% 0,rgba(158,37,64,.07),transparent 42%);overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable}.reply-area{position:relative;z-index:2;flex:0 0 auto;border-top:1px solid var(--line);background:#181216;box-shadow:0 -10px 24px rgba(0,0,0,.18)}.reply-gate{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 18px 0;color:var(--muted);font-size:13px}.agent-composer{align-items:flex-end;padding:12px 18px 18px;border-top:0}.pane-empty{padding:28px;text-align:center;color:var(--muted)}.pane-empty.large{display:grid;min-height:100%;place-items:center}.data-panel{padding:18px;border:1px solid var(--line);border-radius:6px;background:#1a1418;box-shadow:var(--shadow)}.table-toolbar{display:flex;justify-content:flex-end;margin-bottom:14px}.table-toolbar .el-select{width:180px}.route-preview{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;margin-bottom:14px}.preview-result{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:16px}.preview-result div{padding:12px;border:1px solid var(--line);border-radius:5px;background:#21191e}.preview-result span,.preview-result strong{display:block}.preview-result span{color:var(--muted);font-size:12px}.preview-result strong{margin-top:5px}.message-image{display:block;width:min(320px,100%);max-height:280px;margin:8px 0;border:1px solid var(--line);border-radius:5px;object-fit:contain}@media(max-height:800px) and (min-width:651px){.chat-main .metrics{display:none}.page-heading{margin-bottom:14px}}@media(max-width:900px){.console-workspace{grid-template-columns:1fr}.console-sidebar{padding:6px 12px;border-bottom:1px solid var(--line)}.console-sidebar .el-menu{display:flex;overflow-x:auto}.console-main{padding:16px}.console-main.chat-main{height:calc(100dvh - 118px)}.support-desk{grid-template-columns:260px minmax(420px,1fr);overflow-x:auto}}@media(max-width:650px){.chat-main .metrics{display:none}.page-heading{align-items:stretch;flex-direction:column;margin-bottom:12px}.page-heading p{display:none}.heading-actions{justify-content:flex-end}.support-desk{grid-template-columns:1fr;grid-template-rows:minmax(130px,28%) minmax(0,1fr);overflow:hidden}.queue-pane{padding:10px;border-right:0;border-bottom:1px solid var(--line)}.queue-pane .el-select{margin-bottom:6px}.queue-item{padding:8px}.agent-chat-pane{height:auto;min-height:0}.agent-chat-header{padding:12px}.agent-messages{padding:12px}.reply-gate{align-items:stretch;flex-direction:column;padding:10px 12px 0}.agent-composer{padding:10px 12px 12px}.preview-result{grid-template-columns:repeat(2,minmax(0,1fr))}}
.agent-chat-pane:not(.has-session){grid-template-rows:minmax(0,1fr) auto}
@media(max-width:900px){.console-workspace{height:calc(100dvh - 64px);min-height:0;grid-template-rows:auto minmax(0,1fr);overflow:hidden}.console-main.chat-main{height:auto}}
</style>
