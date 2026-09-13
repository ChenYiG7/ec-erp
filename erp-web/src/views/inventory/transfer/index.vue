<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  仓内作业定制(2026-09-11,TODO#30):按三态状态机裁剪动作按钮;明细查看走 TransferOrderDetail
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/inventory/transfer-orders"
      title="调拨单"
      :columns="columns"
      :request-api="transferOrderApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'inventory:transfer:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增调拨单</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮可见性按状态机裁剪,越权拦截在后端) -->
      <template #operation="scope">
        <el-button type="primary" link @click="onDetail(scope.row)">详情</el-button>
        <template v-if="scope.row.status === 'DRAFT'">
          <el-button
            v-auth="'inventory:transfer:edit'"
            type="primary"
            link
            :icon="EditPen"
            @click="openForm('edit', scope.row)"
            >编辑</el-button
          >
          <el-button v-auth="'inventory:transfer:confirm'" type="warning" link @click="onConfirm(scope.row)"
            >确认</el-button
          >
          <el-button v-auth="'inventory:transfer:cancel'" type="danger" link @click="onCancel(scope.row)"
            >取消</el-button
          >
          <el-button
            v-auth="'inventory:transfer:remove'"
            type="danger"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'IN_TRANSIT'">
          <el-button v-auth="'inventory:transfer:confirm'" type="success" link @click="onReceive(scope.row)"
            >到货确认</el-button
          >
        </template>
      </template>
    </ProTable>
    <TransferOrderForm ref="formRef" @saved="refreshTable" />
    <TransferOrderDetail ref="detailRef" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'inventory-transfer-index' })

import { CirclePlus, Delete, EditPen } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import { transferOrderApi } from '@/api/apis/inventory/transfer'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { TransferOrderResponse } from '@/api/interface/inventory/transfer'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import TransferOrderDetail from './components/TransferOrderDetail.vue'
import TransferOrderForm from './components/TransferOrderForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof TransferOrderForm>>()
const detailRef = ref<InstanceType<typeof TransferOrderDetail>>()

// 列配置(gen:page 产出 + 搜索项人工补;enum 选项同时供单元格格式化与搜索下拉)
const columns: ColumnProps<TransferOrderResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'transferNo', label: '调拨单号', width: 180, search: { el: 'input', order: 1 } },
  {
    prop: 'fromWarehouseId',
    label: '调出仓',
    width: 120,
    enum: fetchWarehouseOptions,
    search: { el: 'select', order: 2 },
  },
  {
    prop: 'toWarehouseId',
    label: '调入仓',
    width: 120,
    enum: fetchWarehouseOptions,
    search: { el: 'select', order: 3 },
  },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    tag: true,
    search: { el: 'select', order: 4 },
    enum: [
      { label: '草稿', value: 'DRAFT', tagType: 'info' },
      { label: '在途', value: 'IN_TRANSIT', tagType: 'warning' },
      { label: '已确认', value: 'CONFIRMED', tagType: 'success' },
      { label: '已取消', value: 'CANCELED', tagType: 'danger' },
    ],
  },
  { prop: 'remark', label: '备注' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 280 },
]

const openForm = (mode: 'add' | 'edit', row?: TransferOrderResponse) => {
  formRef.value?.open(mode, row)
}

const onDetail = (row: TransferOrderResponse) => detailRef.value?.open(row)

const handleDelete = async (row: TransferOrderResponse) => {
  await ElMessageBox.confirm('确认删除该调拨单吗?', '提示', { type: 'warning' })
  await transferOrderApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 确认调拨:DIRECT 直达(两腿动账)/ IN_TRANSIT 在途模式(调出仓出库+调入仓占在途,到货确认后入库)
const onConfirm = async (row: TransferOrderResponse) => {
  const inTransit = row.transitMode === 'IN_TRANSIT'
  const tip = inTransit
    ? `确认发出调拨单 ${row.transferNo} 吗?调出仓立即出库,调入仓记在途,到货需再点「到货确认」,不可逆。`
    : `确认调拨单 ${row.transferNo} 吗?确认即达:将立即按明细逐行两腿动账,不可逆。`
  await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
  await transferOrderApi.confirm(row.id)
  ElMessage.success(inTransit ? '已发出,调入仓记在途' : '调拨已确认,库存已两腿动账')
  refreshTable()
}

// 到货确认(#30 在途模式):IN_TRANSIT→CONFIRMED,逐行在途转在库(按计划数全额核销,短少走盘点调整)
const onReceive = async (row: TransferOrderResponse) => {
  await ElMessageBox.confirm(`确认调拨单 ${row.transferNo} 已到货吗?调入仓在途将转为在库。`, '提示', {
    type: 'warning',
  })
  await transferOrderApi.receive(row.id)
  ElMessage.success('到货已确认,在途已转入库')
  refreshTable()
}

// 取消:仅草稿可取消(已确认库存已动账,冲销走反向调拨)
const onCancel = async (row: TransferOrderResponse) => {
  await ElMessageBox.confirm(`确认取消调拨单 ${row.transferNo} 吗?`, '提示', { type: 'warning' })
  await transferOrderApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
