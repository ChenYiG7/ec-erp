<!--
  本文件由 pnpm gen:page 生成后人工定制(头程运费分摊 #33,docs/plans/first-mile-freight.md):
  动作按钮按状态机裁剪(DRAFT:编辑/装箱/取消/删除;BOXED:发货/取消;SHIPPED:分摊;ALLOCATED:关闭);
  双仓列走仓库选项枚举翻译;详情(装箱树+分摊行)走 FirstLegShipmentDetail,录运飞走 FirstLegShipDialog。
  越权最终拦截在后端(写操作 admin 双闸)。
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/finance/first-leg-shipments"
      title="头程发货单"
      :columns="columns"
      :request-api="firstLegShipmentApi.page"
    >
      <!-- 工具栏左:新增(仅草稿态可录,按钮权限收口在页面侧 v-auth) -->
      <template #toolbarLeft>
        <el-button v-auth="'finance:first-leg:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增头程单</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮按状态机裁剪) -->
      <template #operation="scope">
        <el-button type="primary" link @click="onDetail(scope.row)">详情</el-button>
        <template v-if="scope.row.status === 'DRAFT'">
          <el-button
            v-auth="'finance:first-leg:edit'"
            type="primary"
            link
            :icon="EditPen"
            @click="openForm('edit', scope.row)"
            >编辑</el-button
          >
          <el-button v-auth="'finance:first-leg:box'" type="warning" link @click="onBox(scope.row)">装箱完成</el-button>
          <el-button v-auth="'finance:first-leg:cancel'" type="info" link @click="onCancel(scope.row)">取消</el-button>
          <el-button
            v-auth="'finance:first-leg:remove'"
            type="danger"
            link
            :icon="Delete"
            @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'BOXED'">
          <el-button v-auth="'finance:first-leg:ship'" type="warning" link @click="onShip(scope.row)"
            >确认发货</el-button
          >
          <el-button v-auth="'finance:first-leg:cancel'" type="info" link @click="onCancel(scope.row)">取消</el-button>
        </template>
        <el-button
          v-if="scope.row.status === 'SHIPPED'"
          v-auth="'finance:first-leg:allocate'"
          type="success"
          link
          @click="onAllocate(scope.row)"
          >运费分摊</el-button
        >
        <el-button
          v-if="scope.row.status === 'ALLOCATED'"
          v-auth="'finance:first-leg:close'"
          type="warning"
          link
          @click="onClose(scope.row)"
          >关闭</el-button
        >
      </template>
    </ProTable>
    <FirstLegShipmentForm ref="formRef" @saved="refreshTable" />
    <FirstLegShipDialog ref="shipRef" @shipped="refreshTable" />
    <FirstLegShipmentDetail ref="detailRef" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-first-leg-index' })

import { CirclePlus, Delete, EditPen } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import { firstLegShipmentApi } from '@/api/apis/finance/firstLeg'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { FirstLegShipmentResponse } from '@/api/interface/finance/firstLeg'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import FirstLegShipDialog from './components/FirstLegShipDialog.vue'
import FirstLegShipmentDetail from './components/FirstLegShipmentDetail.vue'
import FirstLegShipmentForm from './components/FirstLegShipmentForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof FirstLegShipmentForm>>()
const shipRef = ref<InstanceType<typeof FirstLegShipDialog>>()
const detailRef = ref<InstanceType<typeof FirstLegShipmentDetail>>()

// 双仓共用仓库选项(enum 同时驱动列翻译与搜索下拉;基础数据量小整页拉取)
const warehouseEnum = ref<{ label: string; value: number }[]>([])
fetchWarehouseOptions()
  .then(opts => (warehouseEnum.value = opts))
  .catch(() => {})

// 列配置(gen:page 产出 + 搜索项人工补;enum 选项同时供单元格格式化与搜索下拉)
const columns: ColumnProps<FirstLegShipmentResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shipmentNo', label: '头程单号', width: 170, search: { el: 'input', order: 1 } },
  {
    prop: 'fromWarehouseId',
    label: '国内仓',
    width: 130,
    enum: warehouseEnum,
    search: { el: 'select', order: 2 },
  },
  {
    prop: 'toWarehouseId',
    label: '目的仓',
    width: 130,
    enum: warehouseEnum,
    search: { el: 'select', order: 3 },
  },
  {
    prop: 'status',
    label: '状态',
    width: 100,
    tag: true,
    search: { el: 'select', order: 4 },
    enum: [
      { label: '草稿', value: 'DRAFT', tagType: 'info' },
      { label: '已装箱', value: 'BOXED', tagType: 'warning' },
      { label: '已发货', value: 'SHIPPED', tagType: 'primary' },
      { label: '已分摊', value: 'ALLOCATED', tagType: 'success' },
      { label: '已关闭', value: 'CLOSED', tagType: 'success' },
      { label: '已取消', value: 'CANCELED', tagType: 'danger' },
    ],
  },
  { prop: 'carrier', label: '物流商', width: 110 },
  { prop: 'waybillNo', label: '运单号', width: 140 },
  { prop: 'freightAmount', label: '运费原币', width: 110 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'freightCny', label: '运费CNY', width: 110 },
  { prop: 'chargeWeight', label: '计费重kg', width: 100 },
  { prop: 'volumeWeight', label: '体积重kg', width: 100 },
  { prop: 'shippedAt', label: '发货时间', width: 170 },
  {
    prop: 'allocateStrategy',
    label: '分摊策略',
    width: 100,
    tag: true,
    enum: [
      { label: '按数量', value: 'QTY', tagType: 'info' },
      { label: '按重量', value: 'WEIGHT', tagType: 'warning' },
      { label: '按金额', value: 'AMOUNT', tagType: 'primary' },
    ],
  },
  { prop: 'allocRemark', label: '分摊说明', width: 200, showOverflowTooltip: true },
  { prop: 'allocatedAt', label: '分摊时间', width: 170 },
  { prop: 'remark', label: '备注', width: 180, showOverflowTooltip: true },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 260 },
]

const openForm = (mode: 'add' | 'edit', row?: FirstLegShipmentResponse) => {
  formRef.value?.open(mode, row)
}

const onDetail = (row: FirstLegShipmentResponse) => detailRef.value?.open(row)

const handleDelete = async (row: FirstLegShipmentResponse) => {
  await ElMessageBox.confirm(`确认删除头程单 ${row.shipmentNo} 吗?仅草稿/已取消可删,装箱明细将一并删除。`, '提示', {
    type: 'warning',
  })
  await firstLegShipmentApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 装箱完成:DRAFT→BOXED(至少 1 箱有 SKU,后端守卫;箱内容此后冻结,改单走取消重建)
const onBox = async (row: FirstLegShipmentResponse) => {
  await ElMessageBox.confirm(`确认 ${row.shipmentNo} 装箱完成吗?装箱后箱内容冻结不可改。`, '提示', {
    type: 'warning',
  })
  await firstLegShipmentApi.box(row.id)
  ElMessage.success('已装箱')
  refreshTable()
}

// 确认发货:BOXED→SHIPPED,录运费/汇率(无报价或币种信息不全在弹窗/后端拦截)
const onShip = (row: FirstLegShipmentResponse) => shipRef.value?.open(row)

// 运费分摊:SHIPPED→ALLOCATED;三策略按主单配置,全0基数自动降级,部分缺基数后端报错列明 SKU
const onAllocate = async (row: FirstLegShipmentResponse) => {
  try {
    await ElMessageBox.confirm(
      `确认按「${strategyLabel(row.allocateStrategy)}」策略分摊 ${row.shipmentNo} 的运费 ${row.freightCny} CNY 吗?分摊后不可重算。`,
      '运费分摊',
      { type: 'warning', confirmButtonText: '执行分摊' }
    )
  } catch {
    return
  }
  await firstLegShipmentApi.allocate(row.id)
  ElMessage.success('分摊完成,可在详情查看各 SKU 分摊行')
  refreshTable()
}

const onClose = async (row: FirstLegShipmentResponse) => {
  await ElMessageBox.confirm(`确认关闭头程单 ${row.shipmentNo} 吗?`, '提示', { type: 'warning' })
  await firstLegShipmentApi.close(row.id)
  ElMessage.success('已关闭')
  refreshTable()
}

// 取消:仅草稿/已装箱(已发货起运费已录,后端守卫)
const onCancel = async (row: FirstLegShipmentResponse) => {
  await ElMessageBox.confirm(`确认取消头程单 ${row.shipmentNo} 吗?已发货起不可取消。`, '提示', {
    type: 'warning',
  })
  await firstLegShipmentApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const strategyLabel = (s?: string) => (s === 'QTY' ? '按数量' : s === 'AMOUNT' ? '按金额' : '按重量')

const refreshTable = () => proTableRef.value?.getTableList()
</script>
