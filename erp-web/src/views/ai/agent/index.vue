<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<!--
  AI智能体页(chat 模板,#6;SSE 流式会话非 CRUD 范式)
  每轮结束以服务端历史为准回读(USER/AI/TOOL 落库、title 回填都在后端);role 不落会话,同会话可跨角色续聊
-->
<template>
  <div class="main-box chat-box">
    <!-- 会话栏 -->
    <aside class="chat-aside">
      <el-button type="primary" :icon="Plus" class="new-session-btn" @click="onNewSession">新建会话</el-button>
      <el-radio-group v-model="role" class="role-switch" size="small" :disabled="sending">
        <el-radio-button v-for="r in ROLES" :key="r.value" :value="r.value">{{ r.label }}</el-radio-button>
      </el-radio-group>

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
      <MessageList :messages="messages" :empty-text="emptyHint" />
      <ChatInput :disabled="sending" :placeholder="placeholder" @send="onSend" />
    </main>
  </div>
</template>

<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'ai-agent-index' })
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElRadioButton, ElRadioGroup, ElScrollbar } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { agentApi } from '@/api/apis/ai/agent'
import type { AgentRole } from '@/api/interface/ai/agent'
import type { AiChatSessionResponse, ChatUIMessage } from '@/api/interface/ai/agent'

/** 角色文案词表(spec 拍板收口,禁散落模板字面量) */
const ROLES: { value: AgentRole; label: string }[] = [
  { value: 'SUPPORT', label: '客服助手' },
  { value: 'OPS', label: '运营助手' }
]
const ROLE_HINTS: Record<AgentRole, { empty: string; placeholder: string }> = {
  SUPPORT: { empty: '向客服助手提问,支持查询订单 / 库存 / 商品 / 售后数据', placeholder: '问问订单、库存、商品、售后…(Enter 发送 / Shift+Enter 换行)' },
  OPS: { empty: '向运营助手提问,专注库存与商品盘面', placeholder: '查库存口径(在库/占用/在途/可用)、商品盘面…(Enter 发送 / Shift+Enter 换行)' }
}

const role = ref<AgentRole>('SUPPORT')
const emptyHint = computed(() => ROLE_HINTS[role.value].empty)
const placeholder = computed(() => ROLE_HINTS[role.value].placeholder)

const sessions = ref<AiChatSessionResponse[]>([])
const activeSessionId = ref<number>()
const messages = ref<ChatUIMessage[]>([])
const sending = ref(false)
// 占位行负数 id 自减,不与库内正数 id 混淆
let localSeq = 0

const loadSessions = async () => {
  const { list } = await agentApi.pageSessions(role.value, { pageNo: 1, pageSize: 100 })
  sessions.value = list
}

const loadMessages = async (sessionId: number) => {
  messages.value = await agentApi.listMessages(role.value, sessionId)
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
    const id = await agentApi.createSession(role.value)
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
      const id = await agentApi.createSession(role.value)
      activeSessionId.value = id
      sessions.value = []
    }
    const sessionId = activeSessionId.value!
    messages.value.push({ id: --localSeq, sessionId, role: 'USER', content: text, createdAt: '' })
    const placeholder: ChatUIMessage = { id: --localSeq, sessionId, role: 'AI', content: '', createdAt: '', streaming: true }
    messages.value.push(placeholder)
    await agentApi.chatStream(role.value, sessionId, text, chunk => {
      placeholder.content += chunk
    })
  } catch (error) {
    // SSE 通道错误在 postSse 单点收口抛出,这里补提示(拦截器管不到 fetch)
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
    .role-switch {
      margin-bottom: 12px;
      :deep(.el-radio-button) {
        width: 50%;
        .el-radio-button__inner {
          width: 100%;
        }
      }
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
