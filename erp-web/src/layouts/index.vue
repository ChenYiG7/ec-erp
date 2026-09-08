<!-- 💥 这里是一次性加载 LayoutComponents -->
<template>
  <ElWatermark id="watermark" :font="font" :content="watermark ? ['EC-ERP', '内部系统'] : ''">
    <component :is="LayoutComponents[layout]" />
    <ThemeDrawer />
  </ElWatermark>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Layout',
})
import { ElWatermark } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, watch, type Component } from 'vue'
import type { LayoutType } from '@/stores/interface/store'
import { useGlobalStore } from '@/stores/modules/global'
import { useUserStore } from '@/stores/modules/user'
import { useNotificationStore } from '@/stores/modules/notification'
import ThemeDrawer from './components/ThemeDrawer/index.vue'
import LayoutVertical from './LayoutVertical/index.vue'
import LayoutClassic from './LayoutClassic/index.vue'
import LayoutTransverse from './LayoutTransverse/index.vue'
import LayoutColumns from './LayoutColumns/index.vue'

const LayoutComponents: Record<LayoutType, Component> = {
  vertical: LayoutVertical,
  classic: LayoutClassic,
  transverse: LayoutTransverse,
  columns: LayoutColumns,
}

const globalStore = useGlobalStore()

const isDark = computed(() => globalStore.isDark)
const layout = computed(() => globalStore.layout)
const watermark = computed(() => globalStore.watermark)

// 站内通知红点轮询:进入布局 = 已登录,启动;离开(登出/关页)清理
const notificationStore = useNotificationStore()
onMounted(() => {
  if (useUserStore().getUserToken()) {
    notificationStore.start()
  }
})
onBeforeUnmount(() => notificationStore.stop())

const font = reactive({ color: 'rgba(0, 0, 0, .15)' })
watch(isDark, () => (font.color = isDark.value ? 'rgba(255, 255, 255, .15)' : 'rgba(0, 0, 0, .15)'), {
  immediate: true,
})
</script>

<style scoped lang="scss">
.layout {
  min-width: 600px;
}
</style>
