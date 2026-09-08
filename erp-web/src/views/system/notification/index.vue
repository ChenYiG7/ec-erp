<!--
  通知中心页(手写,gen:page 存在即跳过——api 文件 P2 已手工登记在先,生成器会撞文件;程式对齐生成页)
  TODO(#14) 站内通知完整列表面:铃铛下拉(最近 20 条)之外的全量分页 + 已读状态过滤;
  系统写入表对外只读,页面动作仅本人已读状态(单条/全部),后端按归属校验,无需 permKey
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/system/notifications" title="通知中心" :columns="columns" :request-api="NotificationApi.page">
      <!-- 工具栏左:全部已读(个人操作,无权限位;未读为 0 时禁用) -->
      <template #toolbarLeft>
        <el-button type="primary" :icon="CircleCheck" :disabled="!notificationStore.unreadCount" @click="onReadAll">全部已读</el-button>
      </template>
      <template #operation="scope">
        <el-button v-if="scope.row.readStatus === 0" type="primary" link @click="onRead(scope.row)">标记已读</el-button>
      </template>
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-notification-index' })
import { ref } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import { CircleCheck } from '@element-plus/icons-vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { NotificationApi } from '@/api/apis/system/notification'
import type { SysNotificationResponse } from '@/api/interface'
import { useNotificationStore } from '@/stores/modules/notification'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
// 红点未读数共享 store(操作后同步刷新,铃铛徽标即时归零)
const notificationStore = useNotificationStore()

// 列配置;readStatus 搜索下拉与列标签共用 enum(后端 Query 仅 readStatus/notifyType 两过滤,notifyType 单值不设搜索)
const columns: ColumnProps<SysNotificationResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'title', label: '标题', minWidth: 200 },
  { prop: 'content', label: '内容', minWidth: 280, showOverflowTooltip: true },
  { prop: 'notifyType', label: '类型', width: 130, tag: true, enum: [{ label: '拉单失败告警', value: "PULL_FAIL", tagType: 'danger' }] },
  { prop: 'readStatus', label: '状态', width: 90, tag: true, enum: [{ label: '未读', value: 0, tagType: 'warning' }, { label: '已读', value: 1, tagType: 'info' }], search: { el: 'select' } },
  { prop: 'readAt', label: '已读时间', width: 170 },
  { prop: 'createdAt', label: '时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 100 }
]

// 标记已读:本人已读状态,后端归属校验;回刷表格取服务端 readAt,同步铃铛徽标
const onRead = async (row: SysNotificationResponse) => {
  await NotificationApi.read(row.id)
  ElMessage.success('已标记为已读')
  refreshTable()
  notificationStore.refresh()
}

// 全部已读:同铃铛语义,仅作用于本人通知
const onReadAll = async () => {
  await NotificationApi.readAll()
  ElMessage.success('已全部标记为已读')
  refreshTable()
  notificationStore.refresh()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
