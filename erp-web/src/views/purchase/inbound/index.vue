<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/purchase/inbounds" title="入库单" :columns="columns" :request-api="purchaseInboundApi.page">
      <!-- 工具栏左:新建入库单(#10 人工扩展槽;按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'purchase:inbound:add'" type="primary" :icon="CirclePlus" @click="openCreateForm">新建入库单</el-button>
      </template>
      <!-- 收货明细展开行(懒加载详情 items,见 InboundItems) -->
      <template #expand="scope">
        <InboundItems :row="scope.row" />
      </template>
      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;PENDING 才可操作,越权拦截在后端) -->
      <template #operation="scope">
        <el-button v-if="scope.row.status === 'PENDING'" v-auth="'purchase:inbound:confirm'" type="primary" link @click="onConfirm(scope.row)">确认入库</el-button>
        <el-button v-if="scope.row.status === 'PENDING'" v-auth="'purchase:inbound:cancel'" type="danger" link @click="onCancel(scope.row)">取消</el-button>
      </template>
    </ProTable>
    <InboundCreateForm ref="createFormRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'purchase-inbound-index' })
import { ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { CirclePlus } from '@element-plus/icons-vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { purchaseInboundApi } from '@/api/apis/purchase/inbound'
import type { PurchaseInboundResponse } from '@/api/interface/purchase/inbound'
import InboundItems from './components/InboundItems.vue'
import InboundCreateForm from './components/InboundCreateForm.vue'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
// 建入库单表单(#10 人工扩展槽)
const createFormRef = ref<InstanceType<typeof InboundCreateForm>>()
const openCreateForm = () => createFormRef.value?.open()

// 列配置(gen:page 按 spec role=column/all 产出;expand 展开行 = 收货明细,#10 人工槽)
const columns: ColumnProps<PurchaseInboundResponse>[] = [
  { type: 'expand', width: 44 },
  { type: 'index', label: '#', width: 55 },
  { prop: 'inboundNo', label: '入库单号', width: 180 },
  { prop: 'poId', label: '采购单ID', width: 110 },
  { prop: 'warehouseId', label: '仓库', width: 130, enum: fetchWarehouseOptions },
  { prop: 'status', label: '状态', width: 100, tag: true, enum: [{ label: '待入库', value: "PENDING", tagType: 'warning' }, { label: '已入库', value: "RECEIVED", tagType: 'success' }, { label: '已取消', value: "CANCELLED", tagType: 'info' }] },
  { prop: 'createdBy', label: '创建人', width: 90 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'remark', label: '备注' },
  { prop: 'operation', label: '操作', fixed: 'right', width: 160 }
]

// 确认入库:PENDING→RECEIVED,同事务逐行 IN_PURCHASE 动账 + arrived_qty 原子累加(防超收),任一步失败整体回滚
const onConfirm = async (row: PurchaseInboundResponse) => {
  await ElMessageBox.confirm(`确认入库单 ${row.inboundNo} 收货入库吗?确认后将按明细写入库存,不可回退。`, '提示', { type: 'warning' })
  await purchaseInboundApi.confirm(row.id)
  ElMessage.success('入库成功')
  refreshTable()
}

// 取消:仅 PENDING 可取消(RECEIVED 已动账禁取消,引导走售后/退货流程)
const onCancel = async (row: PurchaseInboundResponse) => {
  await ElMessageBox.confirm(`确认取消入库单 ${row.inboundNo} 吗?`, '提示', { type: 'warning' })
  await purchaseInboundApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
