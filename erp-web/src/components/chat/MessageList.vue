<!--
  AI 会话消息列表(#6 跨页公共组件:chat 对话页 / agent 智能体页共用)
  用法:<MessageList :messages="messages" empty-text="空态提示"/>;USER 右/AI 左气泡,
  TOOL 行词表预留(后端暂不落)防御渲染;流式光标 + 内容变化自动滚底
-->
<template>
  <div ref="listRef" class="message-list">
    <template v-if="messages.length">
      <div v-for="msg in messages" :key="msg.id" class="message-row" :class="{ 'is-user': msg.role === 'USER' }">
        <!-- 工具调用步进:弱化单行(审计可见,不抢对话主流) -->
        <div v-if="msg.role === 'TOOL'" class="message-tool">
          <el-icon><Tools /></el-icon>
          <span>{{ msg.toolName ?? '工具调用' }}</span>
        </div>
        <div v-else class="message-bubble" :class="{ 'is-ai': msg.role !== 'USER' }">
          <!-- 纯文本 pre-wrap 直显:markdown 渲染留 TODO(#6) 余量(引库需拍板,先规避 XSS 面) -->
          <span class="message-content">{{ msg.content }}</span>
          <span v-if="msg.streaming" class="message-cursor">▍</span>
        </div>
      </div>
    </template>
    <el-empty v-else :description="emptyText" :image-size="90" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElEmpty, ElIcon } from 'element-plus'
import { Tools } from '@element-plus/icons-vue'
import type { ChatUIMessage } from '@/api/interface/ai/chat'

const props = withDefaults(
  defineProps<{
    messages: ChatUIMessage[]
    /** 空态提示文案(默认 chat 对话页口径,agent 页按角色传) */
    emptyText?: string
  }>(),
  { emptyText: '向 AI 提问,支持查询订单 / 库存 / 商品 / 售后数据' }
)

const listRef = ref<HTMLDivElement>()

const scrollToBottom = () => {
  nextTick(() => {
    const el = listRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 内容增长(流式 chunk 追加)与行数变化都触发滚底;列表小,deep 可承受
watch(() => props.messages, scrollToBottom, { deep: true })
onMounted(scrollToBottom)
</script>

<style scoped lang="scss">
.message-list {
  flex: 1;

  // 列向 flex 收缩前提:允许压缩,滚动才落在自身
  min-height: 0;
  padding: 16px 20px;
  overflow-y: auto;
  .message-row {
    display: flex;

    // USER 靠右;AI/TOOL 靠左
    justify-content: flex-start;
    margin-bottom: 14px;
    &.is-user {
      justify-content: flex-end;
    }
  }
  .message-bubble {
    max-width: 76%;
    padding: 10px 14px;
    font-size: 14px;
    line-height: 1.7;
    overflow-wrap: break-word;
    background-color: var(--el-fill-color-light);
    border-radius: 8px;
    &.is-ai {
      background-color: var(--el-color-primary-light-9);
    }
    .message-content {
      white-space: pre-wrap;
    }
    .message-cursor {
      display: inline-block;
      margin-left: 2px;
      color: var(--el-color-primary);
      animation: cursor-blink 1s step-start infinite;
    }
  }
  .message-tool {
    display: flex;
    gap: 4px;
    align-items: center;
    padding: 2px 10px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    background-color: var(--el-fill-color-lighter);
    border-radius: 10px;
  }
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}
</style>
