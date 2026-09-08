<!--
  AI 对话页(#6 手写页,gen:page 不适用:SSE 流式会话非 CRUD 范式)
  左:会话列表(本人,最近更新倒序) / 右:消息流 + 输入;首条消息自动建会话
  每轮结束以服务端历史为准回读(USER/AI 双行落库、title 摘要回填都在后端)
-->
<template>
  <div class="main-box chat-box">
    <!-- 会话栏 -->
    <aside class="chat-aside">
      <el-button type="primary" :icon="Plus" class="new-session-btn" @click="onNewSession">新建会话</el-button>
      <el-scrollbar class="session-scroll">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="onSelectSession(session)"
        >
          <span class="session-title">{{ session.title }}</span>
          <span class="session-time">{{ session.updatedAt }}</span>
        </div>
        <el-empty v-if="!sessions.length" description="暂无会话" :image-size="60" />
      </el-scrollbar>
    </aside>
    <!-- 对话区 -->
    <main class="chat-main">
      <MessageList :messages="messages" />
      <ChatInput :disabled="sending" @send="onSend" />
    </main>
  </div>
</template>

<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'ai-chat-index' })
import { onMounted, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElScrollbar } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { aiChatApi } from '@/api/apis/ai/chat'
import type { AiChatSessionResponse, ChatUIMessage } from '@/api/interface/ai/chat'

const sessions = ref<AiChatSessionResponse[]>([])
const activeSessionId = ref<number>()
const messages = ref<ChatUIMessage[]>([])
const sending = ref(false)
// 占位行负数 id 自减,不与库内正数 id 混淆
let localSeq = 0

const loadSessions = async () => {
  const { list } = await aiChatApi.pageSessions({ pageNo: 1, pageSize: 100 })
  sessions.value = list
}

const loadMessages = async (sessionId: number) => {
  messages.value = await aiChatApi.listMessages(sessionId)
}

const onSelectSession = async (session: AiChatSessionResponse) => {
  if (sending.value || session.id === activeSessionId.value) {
    return
  }
  activeSessionId.value = session.id
  messages.value = []
  await loadMessages(session.id)
}

const onNewSession = async () => {
  if (sending.value) {
    return
  }
  try {
    const id = await aiChatApi.createSession()
    await loadSessions()
    activeSessionId.value = id
    messages.value = []
  } catch {
    // 错误提示已由拦截器统一弹出
  }
}

// 首条消息自动建会话;流式 chunk 追加到占位 AI 行;结束/失败都以服务端历史回读兜底
const onSend = async (text: string) => {
  if (sending.value) {
    return
  }
  sending.value = true
  try {
    if (!activeSessionId.value) {
      const id = await aiChatApi.createSession()
      activeSessionId.value = id
      sessions.value = []
    }
    const sessionId = activeSessionId.value!
    messages.value.push({ id: --localSeq, sessionId, role: 'USER', content: text, createdAt: '' })
    const placeholder: ChatUIMessage = {
      id: --localSeq,
      sessionId,
      role: 'AI',
      content: '',
      createdAt: '',
      streaming: true,
    }
    messages.value.push(placeholder)
    await aiChatApi.chatStream(sessionId, text, chunk => {
      placeholder.content += chunk
    })
  } catch (error) {
    // SSE 通道错误在 chatStream 单点收口抛出,这里补提示(拦截器管不到 fetch)
    ElMessage.error(error instanceof Error ? error.message : '回复失败,请稍后重试')
  } finally {
    sending.value = false
    messages.value.forEach(msg => (msg.streaming = false))
    if (activeSessionId.value) {
      await loadMessages(activeSessionId.value).catch(() => {})
    }
    await loadSessions().catch(() => {})
  }
}

onMounted(async () => {
  try {
    await loadSessions()
    // 默认激活最近会话
    if (sessions.value.length) {
      await onSelectSession(sessions.value[0])
    }
  } catch {
    // 错误提示已由拦截器统一弹出
  }
})
</script>

<style scoped lang="scss">
.chat-box {
  // 白底卡片观感对齐 table-box;暗色走 el 变量自适应
  background-color: var(--el-bg-color);
  border-radius: 6px;
  .chat-aside {
    display: flex;
    flex-direction: column;
    width: 240px;
    padding: 12px;
    border-right: 1px solid var(--el-border-color-lighter);
    .new-session-btn {
      margin-bottom: 12px;
    }
    .session-scroll {
      flex: 1;

      // 列向 flex 收缩前提:允许压缩,el-scrollbar 内滚才生效
      min-height: 0;
      .session-item {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 8px 10px;
        margin-bottom: 4px;
        cursor: pointer;
        border-radius: 6px;
        .session-title {
          overflow: hidden;
          text-overflow: ellipsis;
          font-size: 13px;
          white-space: nowrap;
        }
        .session-time {
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }
        &:hover {
          background-color: var(--el-fill-color-light);
        }
        &.active {
          background-color: var(--el-color-primary-light-9);
          .session-title {
            font-weight: 600;
            color: var(--el-color-primary);
          }
        }
      }
    }
  }
  .chat-main {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    padding: 12px 16px 16px;
  }
}
</style>
