<!--
  本文件由 pnpm gen:page 生成后人工定制(fba-shipment,docs/plans/fba-shipment.md):
  动作按钮按状态机裁剪(DRAFT:编辑/装箱/取消/删除;BOXED:发出/取消;SHIPPED:收货登记;
  RECEIVING:收货登记/关闭);店铺/发货仓列走选项枚举翻译;详情(计划行/装箱树/diff)走
  FbaShipmentDetail,收货登记走 FbaReceiveDialog。写侧后端不限 admin(同发货单口径),
  权限由 perm_key(fulfill:fba:*)前端收口。
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/fulfill/fba-shipments"
      title="FBA发货单"
      :columns="columns"
      :request-api="fbaShipmentApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth) -->
      <template #toolbarLeft>
        <el-button v-auth="'fulfill:fba:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增FBA发货单</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;按钮按状态机裁剪) -->
      <template #operation="scope">
        <el-button type="primary" link @click="onDetail(scope.row)">详情</el-button>
        <template v-if="scope.row.status === 'DRAFT'">
          <el-button
            v-auth="'fulfill:fba:edit'"
            type="primary"
            link
            :icon="EditPen"
            @click="openForm('edit', scope.row)"
            >编辑</el-button
          >
          <el-button v-auth="'fulfill:fba:box'" type="warning" link @click="onBox(scope.row)">装箱完成</el-button>
          <el-button v-auth="'fulfill:fba:cancel'" type="info" link @click="onCancel(scope.row)">取消</el-button>
          <el-button v-auth="'fulfill:fba:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
        <template v-else-if="scope.row.status === 'BOXED'">
          <el-button v-auth="'fulfill:fba:ship'" type="warning" link @click="onShip(scope.row)">确认发出</el-button>
          <el-button v-auth="'fulfill:fba:cancel'" type="info" link @click="onCancel(scope.row)">取消</el-button>
        </template>
        <el-button
          v-if="scope.row.status === 'SHIPPED' || scope.row.status === 'RECEIVING'"
          v-auth="'fulfill:fba:receive'"
          type="success"
          link
          @click="onReceive(scope.row)"
          >收货登记</el-button
        >
        <el-button
          v-if="scope.row.status === 'RECEIVING'"
          v-auth="'fulfill:fba:close'"
          type="warning"
          link
          @click="onClose(scope.row)"
          >关闭</el-button
        >
      </template>
    </ProTable>
    <FbaShipmentForm ref="formRef" @saved="refreshTable" />
    <FbaReceiveDialog ref="receiveRef" @registered="refreshTable" />
    <FbaShipmentDetail ref="detailRef" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'fulfill-fba-shipment-index' })

import { CirclePlus, Delete, EditPen } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import { fbaShipmentApi } from '@/api/apis/fulfill/fbaShipment'
import { shopApi } from '@/api/apis/shop/shop'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { FbaShipmentResponse } from '@/api/interface/fulfill/fbaShipment'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import FbaReceiveDialog from './components/FbaReceiveDialog.vue'
import FbaShipmentDetail from './components/FbaShipmentDetail.vue'
import FbaShipmentForm from './components/FbaShipmentForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof FbaShipmentForm>>()
const receiveRef = ref<InstanceType<typeof FbaReceiveDialog>>()
const detailRef = ref<InstanceType<typeof FbaShipmentDetail>>()

// 店铺/发货仓选项(enum 同时驱动列翻译与搜索下拉;基础数据量小整页拉取)
const shopEnum = ref<{ label: string; value: number }[]>([])
const warehouseEnum = ref<{ label: string; value: number }[]>([])
shopApi
  .page({ pageNo: 1, pageSize: 500 })
  .then(page => (shopEnum.value = page.list.filter(s => s.status === 1).map(s => ({ label: s.shopName, value: s.id }))))
  .catch(() => {})
fetchWarehouseOptions()
  .then(opts => (warehouseEnum.value = opts))
  .catch(() => {})

// 列配置(gen:page 产出 + 店铺/仓枚举翻译与搜索项人工补)
const columns: ColumnProps<FbaShipmentResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shipmentNo', label: 'FBA单号', width: 160, search: { el: 'input', order: 1 } },
  { prop: 'shopId', label: '店铺', width: 150, enum: shopEnum, search: { el: 'select', order: 2 } },
  { prop: 'marketplace', label: '站点', width: 80, search: { el: 'input', order: 3 } },
  {
    prop: 'warehouseId',
    label: '发货仓',
    width: 130,
    enum: warehouseEnum,
    search: { el: 'select', order: 4 },
  },
  { prop: 'platformShipmentId', label: '平台ShipmentId', width: 180, showOverflowTooltip: true },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    tag: true,
    search: { el: 'select', order: 5 },
    enum: [
      { label: '草稿', value: 'DRAFT', tagType: 'info' },
      { label: '已装箱', value: 'BOXED', tagType: 'warning' },
      { label: '已发出', value: 'SHIPPED', tagType: 'primary' },
      { label: '收货登记中', value: 'RECEIVING', tagType: 'warning' },
      { label: '已关闭', value: 'CLOSED', tagType: 'success' },
      { label: '已取消', value: 'CANCELED', tagType: 'danger' },
    ],
  },
  { prop: 'shippedAt', label: '发出时间', width: 170 },
  { prop: 'receivedAt', label: '收货登记', width: 170 },
  { prop: 'remark', label: '备注', width: 180, showOverflowTooltip: true },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 300 },
]

const openForm = (mode: 'add' | 'edit', row?: FbaShipmentResponse) => {
  formRef.value?.open(mode, row)
}

const onDetail = (row: FbaShipmentResponse) => detailRef.value?.open(row)

const handleDelete = async (row: FbaShipmentResponse) => {
  await ElMessageBox.confirm(
    `确认删除 FBA 单 ${row.shipmentNo} 吗?仅草稿/已取消可删,计划行与装箱明细将一并删除。`,
    '提示',
    { type: 'warning' }
  )
  await fbaShipmentApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 装箱完成:DRAFT→BOXED(至少 1 箱有件且逐SKU Σ箱内件=计划量,后端预检;之后计划与箱内容冻结)
const onBox = async (row: FbaShipmentResponse) => {
  await ElMessageBox.confirm(`确认 ${row.shipmentNo} 装箱完成吗?装箱后计划与箱内容冻结不可改。`, '提示', {
    type: 'warning',
  })
  await fbaShipmentApi.box(row.id)
  ElMessage.success('已装箱')
  refreshTable()
}

// 确认发出:BOXED→SHIPPED 复合事务(勾稽 + 逐SKU OUT_SHIP 出库动账,可用不足整单回滚)
const onShip = async (row: FbaShipmentResponse) => {
  await ElMessageBox.confirm(
    `确认发出 ${row.shipmentNo} 吗?将在国内仓按计划量逐 SKU 出库动账(OUT_SHIP,成本随移动加权账结转),库存不足整单回滚。`,
    '确认发出',
    { type: 'warning', confirmButtonText: '确认发出' }
  )
  await fbaShipmentApi.ship(row.id)
  ElMessage.success('已发出,库存已出库动账')
  refreshTable()
}

// 收货登记:SHIPPED→RECEIVING 首登 / RECEIVING 重复登记覆盖(按SKU录平台收货量,发出SKU必须全部在列)
const onReceive = (row: FbaShipmentResponse) => receiveRef.value?.open(row)

// 关闭:RECEIVING→CLOSED(diff 对账事实冻结)
const onClose = async (row: FbaShipmentResponse) => {
  await ElMessageBox.confirm(`确认关闭 ${row.shipmentNo} 吗?关闭后对账差异行冻结,不可再登记收货。`, '提示', {
    type: 'warning',
  })
  await fbaShipmentApi.close(row.id)
  ElMessage.success('已关闭')
  refreshTable()
}

// 取消:仅草稿/已装箱(已发出起库存已动账禁取消,后端守卫)
const onCancel = async (row: FbaShipmentResponse) => {
  await ElMessageBox.confirm(`确认取消 FBA 单 ${row.shipmentNo} 吗?已发出后不可取消。`, '提示', {
    type: 'warning',
  })
  await fbaShipmentApi.cancel(row.id)
  ElMessage.success('已取消')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
