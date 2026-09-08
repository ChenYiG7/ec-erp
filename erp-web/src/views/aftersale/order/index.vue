<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/aftersale/orders"
      title="售后单"
      :columns="columns"
      :request-api="aftersaleOrderApi.page"
    >
      <!-- 退货明细展开行(懒加载详情 returnItems,见 AftersaleReturnItems) -->
      <template #expand="scope">
        <AftersaleReturnItems :row="scope.row" />
      </template>
      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮按状态机裁剪,越权拦截在后端) -->
      <template #operation="scope">
        <el-button
          v-if="scope.row.status === 'PENDING'"
          v-auth="'aftersale:order:agree'"
          type="primary"
          link
          @click="onAgree(scope.row)"
          >同意</el-button
        >
        <el-button
          v-if="scope.row.status === 'PENDING'"
          v-auth="'aftersale:order:reject'"
          type="danger"
          link
          @click="onReject(scope.row)"
          >拒绝</el-button
        >
        <el-button
          v-if="scope.row.status === 'RETURNING'"
          v-auth="'aftersale:order:receive-return'"
          type="primary"
          link
          @click="onReceiveReturn(scope.row)"
          >收退件</el-button
        >
        <el-button
          v-if="['APPROVED', 'RETURN_RECEIVED'].includes(scope.row.status)"
          v-auth="'aftersale:order:refund'"
          type="warning"
          link
          @click="onRefund(scope.row)"
          >退款</el-button
        >
        <el-button
          v-if="scope.row.status === 'REFUNDED'"
          v-auth="'aftersale:order:complete'"
          type="success"
          link
          @click="onComplete(scope.row)"
          >完成</el-button
        >
      </template>
    </ProTable>
    <ReceiveReturnForm ref="receiveReturnFormRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'aftersale-order-index' })
import { ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { aftersaleOrderApi } from '@/api/apis/aftersale/order'
import { fetchShopOptions } from '@/api/apis/shop/options'
import type { AftersaleOrderResponse } from '@/api/interface/aftersale/order'
import AftersaleReturnItems from './components/AftersaleReturnItems.vue'
import ReceiveReturnForm from './components/ReceiveReturnForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const receiveReturnFormRef = ref<InstanceType<typeof ReceiveReturnForm>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉;expand 展开行 = 退货明细,#12 人工槽)
const columns: ColumnProps<AftersaleOrderResponse>[] = [
  { type: 'expand', width: 44 },
  { type: 'index', label: '#', width: 55 },
  { prop: 'aftersaleNo', label: '售后单号', width: 180 },
  { prop: 'orderId', label: '订单ID', width: 110 },
  { prop: 'shopId', label: '店铺', width: 130, enum: fetchShopOptions },
  {
    prop: 'type',
    label: '类型',
    width: 110,
    tag: true,
    enum: [
      { label: '仅退款', value: 'REFUND_ONLY', tagType: 'info' },
      { label: '退货退款', value: 'RETURN_REFUND', tagType: 'warning' },
      { label: '换货', value: 'EXCHANGE', tagType: 'primary' },
      { label: '补发', value: 'RESEND', tagType: 'success' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    width: 130,
    tag: true,
    enum: [
      { label: '待处理', value: 'PENDING', tagType: 'warning' },
      { label: '已同意', value: 'APPROVED', tagType: 'primary' },
      { label: '待收退件', value: 'RETURNING', tagType: 'warning' },
      { label: '已收退件', value: 'RETURN_RECEIVED', tagType: 'primary' },
      { label: '已退款', value: 'REFUNDED', tagType: 'success' },
      { label: '已完成', value: 'COMPLETED', tagType: 'success' },
      { label: '已拒绝', value: 'REJECTED', tagType: 'danger' },
      { label: '已取消', value: 'CANCELLED', tagType: 'info' },
    ],
  },
  { prop: 'refundAmount', label: '退款金额', width: 120 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'reason', label: '售后原因' },
  { prop: 'result', label: '处理结果' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 150 },
]

// 同意:PENDING→APPROVED|RETURNING(后端按 type 分流:仅退款/补发→已同意,退货退款/换货→待收退件)
const onAgree = async (row: AftersaleOrderResponse) => {
  await ElMessageBox.confirm(`确认同意售后单 ${row.aftersaleNo} 吗?退货类将进入待收退件。`, '提示', { type: 'warning' })
  await aftersaleOrderApi.agree(row.id, {})
  ElMessage.success('已同意')
  refreshTable()
}

// 拒绝:PENDING→REJECTED,result 必填留痕(后端原子回填,条件更新即守卫)
const onReject = async (row: AftersaleOrderResponse) => {
  const { value } = await ElMessageBox.prompt(`请输入售后单 ${row.aftersaleNo} 的拒绝原因(必填):`, '拒绝售后', {
    type: 'warning',
    inputValidator: (v: string) => (v && v.trim() ? true : '拒绝原因必填'),
  })
  await aftersaleOrderApi.reject(row.id, { result: value.trim() })
  ElMessage.success('已拒绝')
  refreshTable()
}

// 收退件:RETURNING→RETURN_RECEIVED 复合动作(占位+逐行 IN_RETURN 动账+实收明细落库),表单见 ReceiveReturnForm
const onReceiveReturn = (row: AftersaleOrderResponse) => receiveReturnFormRef.value?.open(row)

// 退款:APPROVED|RETURN_RECEIVED→REFUNDED,前置白名单即类型血缘(退货类强制已收退件,后端校验)
const onRefund = async (row: AftersaleOrderResponse) => {
  await ElMessageBox.confirm(`确认售后单 ${row.aftersaleNo} 退款吗?`, '提示', { type: 'warning' })
  await aftersaleOrderApi.refund(row.id, {})
  ElMessage.success('已退款')
  refreshTable()
}

// 完成:REFUNDED→COMPLETED 终态收尾
const onComplete = async (row: AftersaleOrderResponse) => {
  await ElMessageBox.confirm(`确认售后单 ${row.aftersaleNo} 处理完成吗?`, '提示', { type: 'warning' })
  await aftersaleOrderApi.complete(row.id, {})
  ElMessage.success('已完成')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
