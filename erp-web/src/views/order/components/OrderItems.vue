<template>
  <!-- TODO(#16) 订单明细展开行:详情带明细(GET /api/orders/{id} withItems,拉单落库事实源,#4);
       明细行自带 productName/platformSku(平台侧快照),内部SKU 列翻译随 #7 专条收口(goods/options 批量端点) -->
  <div class="order-items">
    <el-table v-if="items.length" :data="items" size="small" border row-key="id">
      <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="platformSku" label="平台SKU" width="160" show-overflow-tooltip />
      <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
        <template #default="{ row: item }">{{ skuLabel(item.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column prop="unitPrice" label="单价" width="110" />
      <el-table-column prop="itemAmount" label="行金额" width="110" />
      <el-table-column prop="currency" label="币种" width="70" />
    </el-table>
    <el-empty v-else-if="loaded" description="无明细" :image-size="60" />
    <span v-else class="order-items__loading">明细加载中…</span>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { shopOrderApi } from '@/api/apis/order/order'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import type { ShopOrderItemResponse, ShopOrderResponse } from '@/api/interface/order/order'

defineOptions({ name: 'OrderItems' })

const props = defineProps<{ row: ShopOrderResponse }>()

const items = ref<ShopOrderItemResponse[]>([])
const loaded = ref(false)

onMounted(async () => {
  // 守卫:row.id 缺失(异常挂载路径)不打后端,避免 /detail/undefined 500
  const id = props.row?.id
  if (id == null) {
    console.error('[OrderItems] row.id 缺失,跳过明细加载')
    loaded.value = true
    return
  }
  try {
    // 展开行懒加载:列表不带明细,取详情挂载 items
    const detail = await shopOrderApi.detail(id)
    items.value = detail.items ?? []
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(items.value.map(i => i.skuId))
  } finally {
    loaded.value = true
  }
})
</script>
<style scoped lang="scss">
.order-items {
  padding: 4px 12px;
  &__loading {
    display: inline-block;
    padding: 8px 0;
    color: var(--el-text-color-secondary);
  }
}
</style>
