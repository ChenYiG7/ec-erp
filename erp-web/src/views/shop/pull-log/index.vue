<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/pull-logs"
      title="拉单日志"
      :columns="columns"
      :request-api="pullLogApi.page"
    >
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'shop-pull-log-index' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { pullLogApi } from '@/api/apis/shop/pull-log'
import { fetchShopOptions } from '@/api/apis/shop/options'
import type { PullLogResponse } from '@/api/interface/shop/pull-log'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<PullLogResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shopId', label: '店铺', width: 130, enum: fetchShopOptions },
  // 人工接线(2026-09-08 #11):SHIPMENT = 发货回传平台记录(非拉取型,窗口退化为本次时刻、条数恒 1),
  // 与 ORDER/PRODUCT/REFUND 同表观测 —— 回传失败排障与拉单共用本页(docs/07 §3 排障第一入口)
  {
    prop: 'dataType',
    label: '数据类型',
    width: 120,
    enum: [
      { label: '订单拉取', value: 'ORDER', tagType: 'primary' },
      { label: '商品同步', value: 'PRODUCT', tagType: 'success' },
      { label: '售后同步', value: 'REFUND', tagType: 'warning' },
      { label: '发货回传', value: 'SHIPMENT', tagType: 'danger' },
    ],
  },
  {
    prop: 'success',
    label: '结果',
    width: 90,
    tag: true,
    enum: [
      { label: '成功', value: 1, tagType: 'success' },
      { label: '失败', value: 0, tagType: 'danger' },
    ],
  },
  { prop: 'windowStart', label: '窗口起点', width: 170 },
  { prop: 'windowEnd', label: '窗口终点', width: 170 },
  { prop: 'pulledCount', label: '拉取条数', width: 100 },
  { prop: 'durationMs', label: '耗时(ms)', width: 100 },
  {
    prop: 'pullWay',
    label: '触发方式',
    width: 100,
    enum: [
      { label: '定时', value: 'JOB', tagType: 'info' },
      { label: '手动', value: 'MANUAL', tagType: 'primary' },
      { label: '事件', value: 'EVENT', tagType: 'warning' },
    ],
  },
  { prop: 'errorMsg', label: '失败原因' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()
</script>
