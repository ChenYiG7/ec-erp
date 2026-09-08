<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/fulfill/delivery-orders"
      title="发货单"
      :columns="columns"
      :request-api="deliveryOrderApi.page"
    >
      <!-- 工具栏左:新建发货单(#11 人工扩展槽;按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'fulfill:delivery:add'" type="primary" :icon="CirclePlus" @click="openCreateForm"
          >新建发货单</el-button
        >
      </template>
      <!-- 发货明细展开行(懒加载详情 items,见 DeliveryItems) -->
      <template #expand="scope">
        <DeliveryItems :row="scope.row" />
      </template>
      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮按状态机裁剪,越权拦截在后端) -->
      <template #operation="scope">
        <el-button
          v-if="scope.row.status === 'PENDING'"
          v-auth="'fulfill:delivery:edit'"
          type="primary"
          link
          @click="openEditForm(scope.row)"
          >编辑</el-button
        >
        <el-button
          v-if="scope.row.status === 'PENDING'"
          v-auth="'fulfill:delivery:ship'"
          type="primary"
          link
          @click="onShip(scope.row)"
          >确认发货</el-button
        >
        <el-button
          v-if="scope.row.status === 'SHIPPED'"
          v-auth="'fulfill:delivery:deliver'"
          type="warning"
          link
          @click="onDeliver(scope.row)"
          >标记签收</el-button
        >
        <el-button
          v-if="scope.row.status === 'PENDING'"
          v-auth="'fulfill:delivery:cancel'"
          type="danger"
          link
          @click="onCancel(scope.row)"
          >取消</el-button
        >
      </template>
    </ProTable>
    <DeliveryCreateForm ref="createFormRef" @saved="refreshTable" />
    <DeliveryEditForm ref="editFormRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'fulfill-delivery-index' })
import { ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { CirclePlus } from '@element-plus/icons-vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { deliveryOrderApi } from '@/api/apis/fulfill/delivery'
import { fetchShopOptions } from '@/api/apis/shop/options'
import type { DeliveryOrderResponse } from '@/api/interface/fulfill/delivery'
import DeliveryItems from './components/DeliveryItems.vue'
import DeliveryCreateForm from './components/DeliveryCreateForm.vue'
import DeliveryEditForm from './components/DeliveryEditForm.vue'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
// 建发货单表单(#11 人工扩展槽)
const createFormRef = ref<InstanceType<typeof DeliveryCreateForm>>()
const openCreateForm = () => createFormRef.value?.open()
// 编辑发货单表单(#11 后补物流信息槽位,仅 PENDING)
const editFormRef = ref<InstanceType<typeof DeliveryEditForm>>()
const openEditForm = (row: DeliveryOrderResponse) => editFormRef.value?.open(row)

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉;expand 展开行 = 发货明细,#11 人工槽)
const columns: ColumnProps<DeliveryOrderResponse>[] = [
  { type: 'expand', width: 44 },
  { type: 'index', label: '#', width: 55 },
  { prop: 'deliveryNo', label: '发货单号', width: 180 },
  { prop: 'orderId', label: '订单ID', width: 110 },
  { prop: 'shopId', label: '店铺', width: 130, enum: fetchShopOptions },
  { prop: 'warehouseId', label: '仓库', width: 130, enum: fetchWarehouseOptions },
  {
    prop: 'type',
    label: '类型',
    width: 110,
    tag: true,
    enum: [
      { label: '自发货', value: 'SELF_FULFILL', tagType: 'primary' },
      { label: 'FBA', value: 'FBA', tagType: 'success' },
      { label: '海外仓', value: 'OVERSEAS', tagType: 'warning' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    width: 100,
    tag: true,
    enum: [
      { label: '待发货', value: 'PENDING', tagType: 'warning' },
      { label: '已发货', value: 'SHIPPED', tagType: 'primary' },
      { label: '已签收', value: 'DELIVERED', tagType: 'success' },
      { label: '已取消', value: 'CANCELLED', tagType: 'info' },
    ],
  },
  { prop: 'logisticsCompany', label: '物流公司', width: 120 },
  { prop: 'trackingNo', label: '运单号', width: 160 },
  { prop: 'shipByTime', label: '承诺发货时限', width: 170 },
  { prop: 'shippedAt', label: '发货时间', width: 170 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 170 },
]

// 确认发货:PENDING→SHIPPED,同事务逐行 OUT_SHIP 动账 + 回写 shipped_at + 发足判定推进订单(部分发货不推进),任一步失败整体回滚
const onShip = async (row: DeliveryOrderResponse) => {
  await ElMessageBox.confirm(
    `确认发货单 ${row.deliveryNo} 出库发货吗?确认后将按明细扣减出库仓库存,不可回退。`,
    '提示',
    { type: 'warning' }
  )
  await deliveryOrderApi.ship(row.id)
  ElMessage.success('发货成功')
  refreshTable()
}

// 标记签收:SHIPPED→DELIVERED(物流轨迹/签收回传平台随 #3 adapter)
const onDeliver = async (row: DeliveryOrderResponse) => {
  await ElMessageBox.confirm(`确认发货单 ${row.deliveryNo} 已签收吗?`, '提示', { type: 'warning' })
  await deliveryOrderApi.deliver(row.id)
  ElMessage.success('已签收')
  refreshTable()
}

// 取消:仅 PENDING 可取消(SHIPPED 已动账禁取消,异常走售后/退货流程)
const onCancel = async (row: DeliveryOrderResponse) => {
  await ElMessageBox.confirm(`确认取消发货单 ${row.deliveryNo} 吗?`, '提示', { type: 'warning' })
  await deliveryOrderApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
