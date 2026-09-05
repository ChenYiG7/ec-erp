<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/order/list" title="订单管理" :columns="columns" :request-api="shopOrderApi.page">
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'order-index' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { useDictStore } from '@/stores/modules/dict'
import { shopOrderApi } from '@/api/apis/order/order'
import type { ShopOrderResponse } from '@/api/interface/order/order'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ShopOrderResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shopId', label: '店铺ID', width: 90 },
  { prop: 'platform', label: '平台', width: 110, enum: () => useDictStore().getDict('shop_platform').then(list => list.map(item => ({ label: item.dictLabel, value: item.dictValue }))) },
  { prop: 'orderStatus', label: '订单状态', width: 110 },
  { prop: 'platformOrderId', label: '平台单号', width: 200 },
  { prop: 'fulfillmentChannel', label: '履约渠道', width: 110 },
  { prop: 'currency', label: '币种', width: 70 },
  { prop: 'orderAmount', label: '订单金额', width: 120 },
  { prop: 'orderTime', label: '下单时间', width: 170 },
  { prop: 'paidTime', label: '付款时间', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()
</script>
