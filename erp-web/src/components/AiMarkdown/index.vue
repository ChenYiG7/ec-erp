<template>
  <!-- AI 消息 markdown 渲染(#6 p3-chat-ux §2.1,计划书已归档 docs/plans/archive/):流式期纯文本直显(完成后才解析,避免逐 token 重解析卡顿),
       完成态 marked 解析 + DOMPurify 净化后 v-html——AI 输出按不可信输入,XSS 净化是硬红线(§五),
       禁裸 v-html;chat 对话页 / agent 智能体页经 MessageList 共用 -->
  <span v-if="streaming" class="ai-markdown__plain">{{ content }}</span>
  <!-- eslint-disable-next-line vue/no-v-html —— 内容已过 DOMPurify 净化 -->
  <span v-else class="ai-markdown" v-html="rendered"></span>
</template>

<script setup lang="ts">
defineOptions({ name: 'AiMarkdown' })

import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed } from 'vue'

const props = withDefaults(defineProps<{ content?: string; streaming?: boolean }>(), {
  content: '',
  streaming: false,
})

// 链接一律新窗口 + noopener(DOMPurify 钩子统一收口,禁散落 mutation);模块级注册一次
DOMPurify.addHook('afterSanitizeAttributes', node => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

// breaks:true 单换行即 <br>(LLM 回复常以单换行分段,聊天语义);gfm 表格/删除线;
// marked.parse 以 async:false 窄化为同步 string 返回
const rendered = computed(() => {
  const html = marked.parse(props.content, { async: false, gfm: true, breaks: true })
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
})
</script>

<style scoped lang="scss">
// 纯文本态(流式):保持气泡内 pre-wrap 语义(原 .message-content 行为)
.ai-markdown__plain {
  white-space: pre-wrap;
}

// markdown 态:配色全走 EP CSS 变量,暗色主题自适应(docs/09;不硬编码色值)
.ai-markdown {
  overflow-wrap: break-word;

  :deep(p) {
    margin: 0 0 8px;

    &:last-child {
      margin-bottom: 0;
    }
  }
  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 12px 0 6px;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.5;

    &:first-child {
      margin-top: 0;
    }
  }
  :deep(ul),
  :deep(ol) {
    margin: 4px 0 8px;
    padding-left: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }
  :deep(li) {
    margin: 2px 0;
  }
  :deep(a) {
    color: var(--el-color-primary);
  }
  :deep(strong) {
    font-weight: 600;
  }
  :deep(blockquote) {
    margin: 6px 0;
    padding: 2px 10px;
    color: var(--el-text-color-secondary);
    border-left: 3px solid var(--el-border-color);
  }
  :deep(code) {
    padding: 1px 5px;
    font-family: var(--el-font-family-mono, monospace);
    font-size: 12px;
    background-color: var(--el-fill-color);
    border-radius: 4px;
  }
  :deep(pre) {
    margin: 6px 0;
    padding: 10px 12px;
    overflow-x: auto;
    background-color: var(--el-fill-color-darker);
    border-radius: 6px;

    &:last-child {
      margin-bottom: 0;
    }

    code {
      padding: 0;
      background-color: transparent;
    }
  }
  :deep(table) {
    display: block;
    margin: 6px 0;
    overflow-x: auto;
    border-collapse: collapse;

    th,
    td {
      padding: 4px 10px;
      border: 1px solid var(--el-border-color-lighter);
    }
    th {
      background-color: var(--el-fill-color-light);
      font-weight: 600;
    }
  }
  :deep(hr) {
    margin: 10px 0;
    border: none;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
</style>
