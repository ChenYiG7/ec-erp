<template>
  <!-- TODO(#12) 退货明细展开行:aftersale_return_item 实收明细(receive-return 人工录入,可≠平台申明,IN_RETURN 动账凭证) -->
  <div class="aftersale-return-items">
    <el-table v-if="items.length" :data="items" size="small" border row-key="id">
      <el-table-column prop="orderItemId" label="订单明细行ID" width="150" />
      <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
        <template #default="{ row: item }">{{ skuLabel(item.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="returnQty" label="实收退货数量" width="130" />
    </el-table>
    <el-empty v-else-if="loaded" description="无退货明细(收退件后展示实收)" :image-size="60" />
    <span v-else class="aftersale-return-items__loading">明细加载中…</span>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElEmpty, ElTable, ElTableColumn } from 'element-plus'
import { aftersaleOrderApi } from '@/api/apis/aftersale/order'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import type { AftersaleOrderResponse, AftersaleReturnItemResponse } from '@/api/interface/aftersale/order'

defineOptions({ name: 'AftersaleReturnItems' })

const props = defineProps<{ row: AftersaleOrderResponse }>()

const items = ref<AftersaleReturnItemResponse[]>([])
const loaded = ref(false)

onMounted(async () => {
  // 守卫:row.id 缺失(异常挂载路径)不打后端,避免 /detail/undefined 500
  const id = props.row?.id
  if (id == null) {
    console.error('[AftersaleReturnItems] row.id 缺失,跳过明细加载')
    loaded.value = true
    return
  }
  try {
    // 展开行懒加载:列表不带明细,取详情挂载 returnItems
    const detail = await aftersaleOrderApi.detail(id)
    items.value = detail.returnItems ?? []
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(items.value.map(i => i.skuId))
  } finally {
    loaded.value = true
  }
})
</script>
<style scoped lang="scss">
.aftersale-return-items {
  padding: 4px 12px;
  &__loading {
    display: inline-block;
    padding: 8px 0;
    color: var(--el-text-color-secondary);
  }
}
</style>
