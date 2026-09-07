<!-- AI 输入框(#6 跨页公共组件:chat 对话页 / agent 智能体页共用):Enter 发送 / Shift+Enter 换行,IME 组合态不误发 -->
<template>
  <div class="chat-input">
    <el-input
      v-model="text"
      type="textarea"
      :autosize="{ minRows: 2, maxRows: 5 }"
      :maxlength="2000"
      show-word-limit
      :disabled="disabled"
      :placeholder="placeholder"
      @keydown.enter="onEnter"
    />
    <el-button type="primary" :disabled="disabled || !canSend" :loading="disabled" @click="doSend">发送</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    /** 占位提示文案(默认 chat 对话页口径,agent 页按角色传) */
    placeholder?: string
  }>(),
  { placeholder: '问问订单、库存、商品、售后…(Enter 发送 / Shift+Enter 换行)' }
)
const emit = defineEmits<{ (e: 'send', text: string): void }>()

const text = ref('')
const canSend = computed(() => Boolean(text.value.trim()))

const doSend = () => {
  const value = text.value.trim()
  if (!value || props.disabled) {
    return
  }
  emit('send', value)
  text.value = ''
}

// 组合输入(中文候选态)回车不发送、不阻止默认的候选确认
const onEnter = (event: Event | KeyboardEvent) => {
  if ('isComposing' in event && event.isComposing) {
    return
  }
  event.preventDefault()
  doSend()
}
</script>

<style scoped lang="scss">
.chat-input {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  .el-button {
    height: 40px;
  }
}
</style>
