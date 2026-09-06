<template>
  <!-- TODO(#11) 发货明细展开行:delivery_order_item 发货进度事实源(仅 sku_id 已绑定订单行参与);契约仅有 orderItemId/skuId/shipQty -->
  <div class="delivery-items">
    <el-table v-if="items.length" :data="items" size="small" border row-key="id">
      <el-table-column prop="orderItemId" label="订单明细行ID" width="150" />
      <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
        <template #default="{ row: item }">{{ skuLabel(item.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="shipQty" label="发货数量" width="110" />
    </el-table>
    <el-empty v-else-if="loaded" description="无发货明细" :image-size="60" />
    <span v-else class="delivery-items__loading">明细加载中…</span>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { deliveryOrderApi } from '@/api/apis/fulfill/delivery'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import type { DeliveryOrderItemResponse, DeliveryOrderResponse } from '@/api/interface/fulfill/delivery'

defineOptions({ name: 'DeliveryItems' })

const props = defineProps<{ row: DeliveryOrderResponse }>()

const items = ref<DeliveryOrderItemResponse[]>([])
const loaded = ref(false)

onMounted(async () => {
  // 守卫:row.id 缺失(异常挂载路径)不打后端,避免 /detail/undefined 500
  const id = props.row?.id
  if (id == null) {
    console.error('[DeliveryItems] row.id 缺失,跳过明细加载')
    loaded.value = true
    return
  }
  try {
    // 展开行懒加载:列表不带明细,取详情挂载 items
    const detail = await deliveryOrderApi.detail(id)
    items.value = detail.items ?? []
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(items.value.map(i => i.skuId))
  } finally {
    loaded.value = true
  }
})
</script>
<style scoped lang="scss">
.delivery-items {
  padding: 4px 12px;
  &__loading {
    display: inline-block;
    padding: 8px 0;
    color: var(--el-text-color-secondary);
  }
}
</style>
