<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/inventory/flows" title="库存流水" :columns="columns" :request-api="pageWithSku">
      <!-- 内部SKU 列翻译(#7 专条):渲染读 options 模块缓存,预取在 pageWithSku -->
      <template #skuId="{ row }">{{ skuLabel(row.skuId) }}</template>
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'inventory-flow-index' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { inventoryFlowApi } from '@/api/apis/inventory/flow'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { InventoryFlowResponse } from '@/api/interface/inventory/flow'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<InventoryFlowResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'skuId', label: '内部SKU', width: 170 },
  { prop: 'warehouseId', label: '仓库', width: 130, enum: fetchWarehouseOptions },
  { prop: 'flowType', label: '流水类型', width: 130, tag: true, enum: [{ label: '采购入库', value: "IN_PURCHASE", tagType: 'success' }, { label: '发货出库', value: "OUT_SHIP", tagType: 'primary' }, { label: '退货入库', value: "IN_RETURN", tagType: 'warning' }, { label: '调整', value: "ADJUST", tagType: 'info' }, { label: '调拨转出', value: "TRANSFER_OUT", tagType: 'danger' }, { label: '调拨转入', value: "TRANSFER_IN", tagType: 'success' }, { label: '采购在途', value: "IN_TRANSIT", tagType: 'info' }, { label: '发货占用', value: "LOCK_SHIP", tagType: 'primary' }] },
  { prop: 'quantity', label: '变更数量', width: 100 },
  { prop: 'beforeQty', label: '变更前', width: 100 },
  { prop: 'afterQty', label: '变更后', width: 100 },
  { prop: 'bizType', label: '业务类型', width: 140 },
  { prop: 'bizId', label: '业务单据ID', width: 130 },
  { prop: 'remark', label: '备注' },
  { prop: 'createdAt', label: '创建时间', width: 170 },
]

// skuId 列翻译预取(#7 专条):页数据加载后按行去重批量取,查询无全量端点(SKU 随业务增长)
const pageWithSku = (params: Parameters<typeof inventoryFlowApi.page>[0]) =>
  inventoryFlowApi.page(params).then(res => {
    fetchSkuNames(res.list.map(r => r.skuId))
    return res
  })

const refreshTable = () => proTableRef.value?.getTableList()
</script>
