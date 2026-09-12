<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
  仓内作业定制(2026-09-11,TODO#30):按六态状态机裁剪动作按钮;录实盘/详情走 StocktakeCountDialog
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/inventory/stocktakes"
      title="盘点单"
      :columns="columns"
      :request-api="stocktakeOrderApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'inventory:stocktake:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增盘点单</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮可见性按状态机裁剪,越权拦截在后端) -->
      <template #operation="scope">
        <el-button type="primary" link @click="onDetail(scope.row)">详情</el-button>
        <template v-if="scope.row.status === 'DRAFT'">
          <el-button
            v-auth="'inventory:stocktake:edit'"
            type="primary"
            link
            :icon="EditPen"
            @click="openForm('edit', scope.row)"
            >编辑</el-button
          >
          <el-button v-auth="'inventory:stocktake:start'" type="warning" link @click="onStart(scope.row)"
            >开始盘点</el-button
          >
          <el-button v-auth="'inventory:stocktake:cancel'" type="danger" link @click="onCancel(scope.row)"
            >取消</el-button
          >
          <el-button
            v-auth="'inventory:stocktake:remove'"
            type="danger"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'COUNTING'">
          <el-button v-auth="'inventory:stocktake:count'" type="warning" link @click="onCount(scope.row)"
            >录实盘</el-button
          >
          <el-button v-auth="'inventory:stocktake:submit'" type="warning" link @click="onSubmit(scope.row)"
            >提交盘点</el-button
          >
          <el-button v-auth="'inventory:stocktake:cancel'" type="danger" link @click="onCancel(scope.row)"
            >取消</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'PENDING_ADJUST'">
          <el-button v-auth="'inventory:stocktake:adjust'" type="warning" link @click="onAdjust(scope.row)"
            >生成调整</el-button
          >
          <el-button v-auth="'inventory:stocktake:cancel'" type="danger" link @click="onCancel(scope.row)"
            >取消</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'ADJUSTED'">
          <el-button v-auth="'inventory:stocktake:close'" type="warning" link @click="onClose(scope.row)"
            >关闭</el-button
          >
        </template>
      </template>
    </ProTable>
    <StocktakeOrderForm ref="formRef" @saved="refreshTable" />
    <StocktakeCountDialog ref="countRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'inventory-stocktake-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { stocktakeOrderApi } from '@/api/apis/inventory/stocktake'
import type { StocktakeOrderResponse } from '@/api/interface/inventory/stocktake'
import StocktakeOrderForm from './components/StocktakeOrderForm.vue'
import StocktakeCountDialog from './components/StocktakeCountDialog.vue'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof StocktakeOrderForm>>()
const countRef = ref<InstanceType<typeof StocktakeCountDialog>>()

// 列配置(gen:page 产出 + 搜索项人工补;enum 选项同时供单元格格式化与搜索下拉)
const columns: ColumnProps<StocktakeOrderResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'stocktakeNo', label: '盘点单号', width: 180, search: { el: 'input', order: 1 } },
  { prop: 'warehouseId', label: '盘点仓', width: 130, enum: fetchWarehouseOptions, search: { el: 'select', order: 2 } },
  {
    prop: 'scopeType',
    label: '盘点范围',
    width: 110,
    enum: [
      { label: '全仓', value: 'ALL', tagType: 'info' },
      { label: 'SKU集', value: 'SKU_SET', tagType: 'warning' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    tag: true,
    search: { el: 'select', order: 3 },
    enum: [
      { label: '草稿', value: 'DRAFT', tagType: 'info' },
      { label: '盘点中', value: 'COUNTING', tagType: 'primary' },
      { label: '待调整', value: 'PENDING_ADJUST', tagType: 'warning' },
      { label: '已调整', value: 'ADJUSTED', tagType: 'success' },
      { label: '已关闭', value: 'CLOSED', tagType: 'info' },
      { label: '已取消', value: 'CANCELED', tagType: 'danger' },
    ],
  },
  { prop: 'remark', label: '备注' },
  { prop: 'confirmedAt', label: '调整确认时间', width: 170 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 300 },
]

const openForm = (mode: 'add' | 'edit', row?: StocktakeOrderResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm('确认删除该盘点单吗?', '提示', { type: 'warning' })
  await stocktakeOrderApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 详情/录实盘共用弹窗(COUNTING 可编辑实盘,其余状态只读看快照/实盘/差异)
const onDetail = (row: StocktakeOrderResponse) => countRef.value?.open(row, 'view')
const onCount = (row: StocktakeOrderResponse) => countRef.value?.open(row, 'count')

// 开始盘点:DRAFT→COUNTING(条件更新在后端,失败弹拦截器报错)
const onStart = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm(`确认开始盘点 ${row.stocktakeNo} 吗?开始后进入录实盘阶段。`, '提示', { type: 'warning' })
  await stocktakeOrderApi.start(row.id)
  ElMessage.success('已开始盘点')
  refreshTable()
}

// 提交盘点:COUNTING→PENDING_ADJUST(实盘录齐方可通过,缺行后端整单拒绝)
const onSubmit = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm(`确认提交盘点 ${row.stocktakeNo} 吗?所有明细行须已录入实盘。`, '提示', { type: 'warning' })
  await stocktakeOrderApi.submit(row.id)
  ElMessage.success('已提交,待生成调整')
  refreshTable()
}

// 生成调整:PENDING_ADJUST→ADJUSTED,按确认时点账面 re-diff 逐行动账,可用不足整单回滚
const onAdjust = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm(
    `确认为 ${row.stocktakeNo} 生成差异调整吗?将按确认时点账面逐行 ADJUST 动账,不可逆。`,
    '提示',
    { type: 'warning' }
  )
  await stocktakeOrderApi.adjust(row.id)
  ElMessage.success('差异调整已生成')
  refreshTable()
}

// 关闭:ADJUSTED→CLOSED 终态(只有已调整可达,保证差异必处理或明确放弃)
const onClose = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm(`确认关闭盘点单 ${row.stocktakeNo} 吗?关闭后为终态。`, '提示', { type: 'warning' })
  await stocktakeOrderApi.close(row.id)
  ElMessage.success('已关闭')
  refreshTable()
}

// 取消:未动账三态(草稿/盘点中/待调整)可取消,已调整/已关闭后端拒绝
const onCancel = async (row: StocktakeOrderResponse) => {
  await ElMessageBox.confirm(`确认取消盘点单 ${row.stocktakeNo} 吗?`, '提示', { type: 'warning' })
  await stocktakeOrderApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
