import { ref } from 'vue'
import { goodsSkuApi } from './sku'

/**
 * SKU 名称翻译(跨页共享数据源,#7 专条 2026-09-06 收口):
 * 库存两页/订单·入库·发货·售后明细行/建单表单明细行/SKU 匹配页 skuId 列。
 * 照 warehouse/shop options 收口形态;契约无全量端点(SKU 随业务增长,/api/goods/skus 仅 SPU 内列表),
 * 改为按页去重 ids 批量取(GET /api/goods/skus/batch);模块级缓存跨页累积,重复翻页零请求
 */
const skuNames = ref<Record<string, string>>({})

// 串行队列:多组件同时挂载重复请求同一批 id 时先等前一批落缓存;单批失败不毒化后续(业务报错弹窗由拦截器统一处理)
let queue: Promise<void> = Promise.resolve()

const loadMissing = async (ids: Array<number | null | undefined>) => {
  const missing = [...new Set(ids.filter((id): id is number => id != null && skuNames.value[id] === undefined))]
  if (!missing.length) {
    return
  }
  const list = await goodsSkuApi.batch(missing)
  for (const o of list) {
    skuNames.value[o.id] = o.productName ? `${o.skuCode} · ${o.productName}` : o.skuCode
  }
  // 响应中查无的 ID(已删/脏数据)记空串哨兵:回落显示裸 ID,不再重复请求
  for (const id of missing) {
    if (skuNames.value[id] === undefined) {
      skuNames.value[id] = ''
    }
  }
}

/** 批量预取 SKU 翻译:传入页内/明细行的 skuId 集合(可含 null,自动剔除);渲染层用 skuLabel 读缓存 */
export const fetchSkuNames = (ids: Array<number | null | undefined>) => {
  queue = queue.then(() => loadMissing(ids).catch(() => {}))
  return queue
}

/** skuId → 可读 label("编码 · SPU名称",SkuSelector 同款);查无/未预取回落裸 ID,未绑定 null 显示 '-' */
export const skuLabel = (id?: number | null) => (id == null ? '-' : skuNames.value[id] || String(id))
