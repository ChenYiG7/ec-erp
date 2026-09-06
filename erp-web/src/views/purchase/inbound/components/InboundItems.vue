<template>
  <!-- TODO(#10) 收货明细展开行:purchase_inbound_item 实收明细(人工录入,可≠平台/申明量);契约仅有 poItemId/skuId/inboundQty -->
  <div class="inbound-items">
    <el-table v-if="items.length" :data="items" size="small" border row-key="id">
      <el-table-column prop="poItemId" label="采购明细行ID" width="150" />
      <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
        <template #default="{ row: item }">{{ skuLabel(item.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="inboundQty" label="实收数量" width="110" />
    </el-table>
    <el-empty v-else-if="loaded" description="无收货明细" :image-size="60" />
    <span v-else class="inbound-items__loading">明细加载中…</span>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { purchaseInboundApi } from '@/api/apis/purchase/inbound'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import type { PurchaseInboundItemResponse, PurchaseInboundResponse } from '@/api/interface/purchase/inbound'

defineOptions({ name: 'InboundItems' })

const props = defineProps<{ row: PurchaseInboundResponse }>()

const items = ref<PurchaseInboundItemResponse[]>([])
const loaded = ref(false)

onMounted(async () => {
  // 守卫:row.id 缺失(异常挂载路径)不打后端,避免 /detail/undefined 500
  const id = props.row?.id
  if (id == null) {
    console.error('[InboundItems] row.id 缺失,跳过明细加载')
    loaded.value = true
    return
  }
  try {
    // 展开行懒加载:列表不带明细,取详情挂载 items
    const detail = await purchaseInboundApi.detail(id)
    items.value = detail.items ?? []
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(items.value.map(i => i.skuId))
  } finally {
    loaded.value = true
  }
})
</script>
<style scoped lang="scss">
.inbound-items {
  padding: 4px 12px;
  &__loading {
    display: inline-block;
    padding: 8px 0;
    color: var(--el-text-color-secondary);
  }
}
</style>
