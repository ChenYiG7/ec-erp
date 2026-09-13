<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
  人工改动(#29 订单域补课):补审核状态/订单来源列与过滤、审核动作、手工录单入口与内销单编辑
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/order/list"
      title="订单管理"
      :columns="columns"
      :request-api="shopOrderApi.page"
    >
      <!-- 工具栏左:手工录单(#29 内销订单录入入口;按钮权限收口在页面侧 v-auth,toolbarLeft prop 的 auth 无效禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'order:manual'" type="primary" :icon="CirclePlus" @click="openManualForm()"
          >手工录单</el-button
        >
      </template>

      <!-- 订单明细展开行(懒加载详情 items,见 OrderItems;#16 人工槽;v-if 判 id 拦 el-table hidden-columns 空对象行的预渲染) -->
      <template #expand="scope">
        <OrderItems v-if="scope.row?.id != null" :row="scope.row" />
      </template>

      <!-- 操作列(#29):待审核/无需审核/已驳回可裁定(已通过为审核终态);内销单待发货可改 -->
      <template #operation="scope">
        <el-button
          v-if="scope.row.reviewStatus !== 2"
          v-auth="'order:review'"
          type="primary"
          link
          :icon="Select"
          @click="openReview(scope.row)"
          >审核</el-button
        >
        <el-button
          v-if="scope.row.orderSource === 'MANUAL' && scope.row.orderStatus === 'WAIT_SHIP'"
          v-auth="'order:manual'"
          type="primary"
          link
          :icon="EditPen"
          @click="openManualForm(scope.row)"
          >编辑</el-button
        >
      </template>
    </ProTable>
    <ReviewDialog ref="reviewDialogRef" @saved="refreshTable" />
    <ManualOrderForm ref="manualFormRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'order-index' })

import { CirclePlus, EditPen, Select } from '@element-plus/icons-vue'
import { ElButton } from 'element-plus'
import { ref } from 'vue'
import { shopOrderApi } from '@/api/apis/order/order'
import { fetchShopOptions } from '@/api/apis/shop/options'
import type { ShopOrderResponse } from '@/api/interface/order/order'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { useDictStore } from '@/stores/modules/dict'
import ManualOrderForm from './components/ManualOrderForm.vue'
import OrderItems from './components/OrderItems.vue'
import ReviewDialog from './components/ReviewDialog.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const reviewDialogRef = ref<InstanceType<typeof ReviewDialog>>()
const manualFormRef = ref<InstanceType<typeof ManualOrderForm>>()

/** 审核状态词表(#29;与后端 OrderReviewConsts 对齐,同时供列渲染与搜索下拉) */
const reviewStatusEnum = [
  { label: '无需审核', value: 0 },
  { label: '待审核', value: 1, tagType: 'warning' },
  { label: '已通过', value: 2, tagType: 'success' },
  { label: '已驳回', value: 3, tagType: 'danger' },
]

/** 订单来源词表(#29) */
const orderSourceEnum = [
  { label: '平台拉单', value: 'PLATFORM' },
  { label: '内销录单', value: 'MANUAL', tagType: 'warning' },
]

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉;expand 展开行 = 订单明细,#16 人工槽)
const columns: ColumnProps<ShopOrderResponse>[] = [
  { type: 'expand', width: 44 },
  { type: 'index', label: '#', width: 55 },
  {
    prop: 'shopId',
    label: '店铺',
    width: 130,
    enum: fetchShopOptions,
    search: { el: 'select', order: 1 },
  },
  {
    prop: 'platform',
    label: '平台',
    width: 110,
    enum: () =>
      useDictStore()
        .getDict('shop_platform')
        .then(list => list.map(item => ({ label: item.dictLabel, value: item.dictValue }))),
    search: { el: 'select', order: 2 },
  },
  {
    prop: 'orderStatus',
    label: '订单状态',
    width: 110,
    enum: [
      { label: '待付款', value: 'WAIT_PAY' },
      { label: '待发货', value: 'WAIT_SHIP' },
      { label: '已发货', value: 'SHIPPED' },
      { label: '已完成', value: 'COMPLETED' },
      { label: '已取消', value: 'CANCELLED' },
      { label: '已关闭', value: 'CLOSED' },
    ],
    search: { el: 'select', order: 3 },
  },
  {
    prop: 'orderSource',
    label: '来源',
    width: 100,
    enum: orderSourceEnum,
    search: { el: 'select', order: 4 },
  },
  {
    prop: 'reviewStatus',
    label: '审核状态',
    width: 110,
    enum: reviewStatusEnum,
    search: { el: 'select', order: 5 },
  },
  { prop: 'platformOrderId', label: '平台单号', width: 200 },
  { prop: 'fulfillmentChannel', label: '履约渠道', width: 110 },
  { prop: 'currency', label: '币种', width: 70 },
  { prop: 'orderAmount', label: '订单金额', width: 120 },
  { prop: 'orderTime', label: '下单时间', width: 170 },
  { prop: 'paidTime', label: '付款时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 130 },
]

/** 打开审核弹窗(#29;审核人/时间后端回填) */
const openReview = (row: ShopOrderResponse) => {
  reviewDialogRef.value?.open(row)
}

/** 打开录单表单(#29;不传 row = 新增内销单,传 row = 编辑内销单) */
const openManualForm = (row?: ShopOrderResponse) => {
  manualFormRef.value?.open(row)
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
