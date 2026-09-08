<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/purchase/orders"
      title="采购单"
      :columns="columns"
      :request-api="purchaseOrderApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'purchase:order:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增采购单</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮可见性按状态机裁剪,越权拦截在后端 @PreAuthorize) -->
      <template #operation="scope">
        <template v-if="scope.row.status === 'DRAFT'">
          <el-button
            v-auth="'purchase:order:edit'"
            type="primary"
            link
            :icon="EditPen"
            @click="openForm('edit', scope.row)"
            >编辑</el-button
          >
          <el-button v-auth="'purchase:order:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
            >删除</el-button
          >
          <el-button v-auth="'purchase:order:audit'" type="warning" link @click="onAudit(scope.row)">审核</el-button>
        </template>
        <el-button
          v-if="['AUDITED', 'PARTIAL_RECEIVED', 'RECEIVED'].includes(scope.row.status)"
          v-auth="'purchase:order:close'"
          type="warning"
          link
          @click="onClose(scope.row)"
          >关闭</el-button
        >
      </template>
    </ProTable>
    <PurchaseOrderForm ref="formRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'purchase-order-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { purchaseOrderApi } from '@/api/apis/purchase/order'
import type { PurchaseOrderResponse } from '@/api/interface/purchase/order'
import PurchaseOrderForm from './components/PurchaseOrderForm.vue'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof PurchaseOrderForm>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<PurchaseOrderResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'poNo', label: '采购单号', width: 180 },
  { prop: 'supplierId', label: '供应商ID', width: 100 },
  { prop: 'warehouseId', label: '仓库', width: 130, enum: fetchWarehouseOptions },
  {
    prop: 'status',
    label: '状态',
    width: 130,
    tag: true,
    enum: [
      { label: '草稿', value: 'DRAFT', tagType: 'info' },
      { label: '已审核', value: 'AUDITED', tagType: 'primary' },
      { label: '部分入库', value: 'PARTIAL_RECEIVED', tagType: 'warning' },
      { label: '已入库', value: 'RECEIVED', tagType: 'success' },
      { label: '已关闭', value: 'CLOSED', tagType: 'danger' },
    ],
  },
  { prop: 'totalAmount', label: '总金额', width: 130 },
  { prop: 'createdBy', label: '创建人', width: 90 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 220 },
]

const openForm = (mode: 'add' | 'edit', row?: PurchaseOrderResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: PurchaseOrderResponse) => {
  await ElMessageBox.confirm('确认删除该采购单吗?', '提示', { type: 'warning' })
  await purchaseOrderApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 审核:DRAFT→AUDITED(条件更新在后端,失败弹拦截器报错;admin 按钮权限 + 后端 @PreAuthorize hasRole('admin') 双闸)
const onAudit = async (row: PurchaseOrderResponse) => {
  await ElMessageBox.confirm(`确认审核采购单 ${row.poNo} 吗?审核后不可编辑。`, '提示', { type: 'warning' })
  await purchaseOrderApi.audit(row.id)
  ElMessage.success('审核通过')
  refreshTable()
}

// 关闭:AUDITED/PARTIAL_RECEIVED/RECEIVED→CLOSED,剩余未收量作废(草稿单请走删除)
const onClose = async (row: PurchaseOrderResponse) => {
  await ElMessageBox.confirm(`确认关闭采购单 ${row.poNo} 吗?剩余未收量将作废。`, '提示', { type: 'warning' })
  await purchaseOrderApi.close(row.id)
  ElMessage.success('已关闭')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
