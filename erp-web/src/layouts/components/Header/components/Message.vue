<template>
  <div class="message">
    <el-popover placement="bottom" :width="360" trigger="click" @show="loadList">
      <template #reference>
        <el-badge :value="badgeValue" :hidden="!notificationStore.unreadCount" class="item">
          <line-md-bell class="cursor-pointer" />
        </el-badge>
      </template>
      <div class="message-header">
        <span class="message-header-title">站内通知</span>
        <el-button link type="primary" :disabled="!notificationStore.unreadCount" @click="readAll">全部已读</el-button>
      </div>
      <el-scrollbar max-height="360px">
        <div v-if="!list.length" class="message-empty">
          <img src="@/assets/images/noData.webp" alt="noData" />
          <div>暂无通知</div>
        </div>
        <div v-else class="message-list">
          <div v-for="item in list" :key="item.id" class="message-item" :class="{ 'is-unread': item.readStatus === 0 }">
            <div class="message-content" @click="markRead(item)">
              <span class="message-title">
                <el-badge v-if="item.readStatus === 0" is-dot class="message-dot" />
                {{ item.title }}
              </span>
              <span class="message-desc">{{ item.content }}</span>
              <span class="message-date">{{ item.createdAt }}</span>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Message'
})
import { ElBadge, ElButton, ElMessage, ElPopover, ElScrollbar } from 'element-plus'
import { computed, ref } from 'vue'
import LineMdBell from '~icons/line-md/bell?width=20px&height=20px'
import { NotificationApi } from '@/api/apis/system/notification'
import type { SysNotificationResponse } from '@/api/interface'
import { useNotificationStore } from '@/stores/modules/notification'

const PAGE_SIZE = 20
const notificationStore = useNotificationStore()
const list = ref<SysNotificationResponse[]>([])

const badgeValue = computed(() => (notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount))

const loadList = async () => {
  const data = await NotificationApi.page({ pageNo: 1, pageSize: PAGE_SIZE })
  list.value = data.list
}

const markRead = async (item: SysNotificationResponse) => {
  if (item.readStatus === 0) {
    await NotificationApi.read(item.id)
    item.readStatus = 1
    notificationStore.refresh()
  }
}

const readAll = async () => {
  await NotificationApi.readAll()
  list.value.forEach(item => (item.readStatus = 1))
  notificationStore.refresh()
  ElMessage.success('已全部标记为已读')
}
</script>

<style scoped lang="scss">
.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  .message-header-title {
    font-weight: 600;
  }
}
.message-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 260px;
  line-height: 45px;
}
.message-list {
  display: flex;
  flex-direction: column;
  .message-item {
    display: flex;
    align-items: center;
    padding: 12px 0;
    cursor: pointer;
    border-bottom: 1px solid var(--el-border-color-light);
    &:last-child {
      border: none;
    }
    &.is-unread .message-title {
      font-weight: 600;
    }
    .message-content {
      display: flex;
      flex-direction: column;
      gap: 4px;
      width: 100%;
      .message-title {
        display: flex;
        align-items: center;
      }
      .message-dot {
        margin-right: 6px;
      }
      .message-desc {
        display: -webkit-box;
        overflow: hidden;
        text-overflow: ellipsis;
        -webkit-line-clamp: 2;
        font-size: 12px;
        color: var(--el-text-color-regular);
        -webkit-box-orient: vertical;
      }
      .message-date {
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }
}
</style>
