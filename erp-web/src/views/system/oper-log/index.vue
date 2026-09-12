<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/oper-logs"
      title="操作日志"
      :columns="columns"
      :request-api="loadOperLogs"
    >
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-oper-log-index' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { sysOperLogApi } from '@/api/apis/system/oper-log'
import type { SysOperLogQuery, SysOperLogResponse } from '@/api/interface/system/oper-log'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;搜索四件为人工手调段:username 模糊/module·resultStatus 下拉/时间窗范围)
const columns: ColumnProps<SysOperLogResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'username', label: '操作人', width: 110, search: { el: 'input', order: 1 } },
  {
    prop: 'module',
    label: '模块',
    width: 100,
    tag: true,
    enum: [
      { label: '订单', value: 'order', tagType: 'primary' },
      { label: '库存', value: 'inventory', tagType: 'warning' },
      { label: '发货', value: 'fulfill', tagType: 'success' },
      { label: '采购', value: 'purchase', tagType: 'info' },
      { label: '财务', value: 'finance', tagType: 'danger' },
    ],
    search: { el: 'select', order: 2 },
  },
  {
    prop: 'resultStatus',
    label: '结果',
    width: 90,
    tag: true,
    enum: [
      { label: '成功', value: 'OK', tagType: 'success' },
      { label: '失败', value: 'FAIL', tagType: 'danger' },
    ],
    search: { el: 'select', order: 3 },
  },
  { prop: 'action', label: '动作', width: 150 },
  { prop: 'bizType', label: '业务类型', width: 100 },
  { prop: 'bizId', label: '业务ID', width: 90 },
  { prop: 'ip', label: 'IP', width: 130 },
  { prop: 'traceId', label: '链路ID', width: 140 },
  { prop: 'costMs', label: '耗时(ms)', width: 100 },
  { prop: 'paramsJson', label: '参数', width: 200, showOverflowTooltip: true },
  { prop: 'errorMsg', label: '失败原因', showOverflowTooltip: true },
  { prop: 'createdAt', label: '操作时间', width: 170 },
  // 搜索-only 虚拟列:操作时间范围(值格式 ISO,后端 SysOperLogQuery @DateTimeFormat ISO 绑定 LocalDateTime)
  {
    prop: 'timeRange',
    label: '操作时间',
    isShow: false,
    search: {
      el: 'date-picker',
      order: 4,
      attrs: { type: 'datetimerange', valueFormat: 'YYYY-MM-DDTHH:mm:ss' },
    },
  },
]

/** ProTable 数据源:拆时间范围虚拟字段 → beginTime/endTime(后端时间窗闭区间) */
const loadOperLogs = async (
  params: SysOperLogQuery & { timeRange?: [string, string] | null; pageNo?: number; pageSize?: number }
) => {
  const { timeRange, ...rest } = params
  return sysOperLogApi.page({
    ...rest,
    beginTime: timeRange?.[0],
    endTime: timeRange?.[1],
  })
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
