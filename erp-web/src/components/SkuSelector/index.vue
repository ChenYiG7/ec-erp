<template>
  <!-- 跨页面公共组件:SKU 搜索选择器(#16 槽位,收口裸 ID 手填)。
       demo:<SkuSelector v-model="skuId" @change="onSkuChange" />
       契约无全局 SKU 搜索端点,内部 = 商品 keyword 搜索(top5)→ 各 SPU 并行拉 SKU 拍平;
       后端若补 SKU 搜索端点,仅换本组件实现,调用方不动 -->
  <el-select
    :model-value="modelValue"
    remote
    filterable
    clearable
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    class="sku-selector"
    @update:model-value="onSelect"
    @clear="onClear"
  >
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value" />
  </el-select>
</template>
<script setup lang="ts">
// 跨页面组件命名与文件路径一致(src/components/SkuSelector/index.vue)
defineOptions({ name: 'SkuSelector' })
import { ref } from 'vue'
import { ElOption, ElSelect } from 'element-plus'
import { productApi } from '@/api/apis/goods/product'
import { goodsSkuApi } from '@/api/apis/goods/sku'
import type { ProductResponse } from '@/api/interface/goods/product'

export interface SkuSelectorOption {
  label: string
  value: number
  skuCode: string
  productName: string
}

const props = defineProps<{
  modelValue?: number
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  change: [option: SkuSelectorOption | undefined]
}>()

// 商品搜索前 N 名(每名一次 SPU 内 SKU 请求,人工低频可控;总数上限防巨型选项集)
const PRODUCT_LIMIT = 5
const OPTION_LIMIT = 50

const loading = ref(false)
const options = ref<SkuSelectorOption[]>([])

let searchSeq = 0
let debounceTimer: ReturnType<typeof setTimeout> | undefined

// 远程搜索:商品 keyword 分页(top N)→ 并行取各 SPU 的 SKU 拍平;序号防竞态(慢响应不覆盖新结果)
const search = async (keyword: string) => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  const kw = keyword.trim()
  if (!kw) {
    options.value = []
    return
  }
  debounceTimer = setTimeout(async () => {
    const seq = ++searchSeq
    loading.value = true
    try {
      const { list } = await productApi.page({ keyword: kw, pageNo: 1, pageSize: PRODUCT_LIMIT })
      const skuLists = await Promise.all(list.map((p: ProductResponse) => goodsSkuApi.listByProduct(p.id)))
      if (seq !== searchSeq) {
        return
      }
      options.value = list
        .flatMap((p: ProductResponse, i: number) =>
          (skuLists[i] ?? []).map(sku => ({
            label: `${sku.skuCode} · ${p.name}`,
            value: sku.id,
            skuCode: sku.skuCode,
            productName: p.name,
          }))
        )
        .slice(0, OPTION_LIMIT)
    } finally {
      if (seq === searchSeq) {
        loading.value = false
      }
    }
  }, 300)
}

const onSelect = (value: number | undefined) => {
  emit('update:modelValue', value)
  emit(
    'change',
    options.value.find(o => o.value === value)
  )
}

const onClear = () => {
  emit('update:modelValue', undefined)
  emit('change', undefined)
}

// 回显选中项:外部回填 skuId(编辑场景)时选项列表为空,补一个占位选项保证 label 可见
const ensureOption = (value?: number) => {
  if (value && !options.value.some(o => o.value === value)) {
    options.value = [{ label: `SKU ID ${value}`, value, skuCode: String(value), productName: '' }, ...options.value]
  }
}
defineExpose({ ensureOption, search })
</script>
<style scoped lang="scss">
.sku-selector {
  width: 100%;
}
</style>
