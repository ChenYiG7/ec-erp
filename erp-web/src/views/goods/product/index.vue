<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
  人工接线:左侧 TreeFilter 分类树过滤(gen:page 不产树形布局,docs/09 §4 例外;--force 重生成后需重新接线)
-->

<template>
  <!-- 人工接线:全局 .table-box 为纵向 flex,树形过滤页需横向左右布局,product-box 覆盖为 row(重生成后需保留) -->
  <div class="table-box product-box">
    <TreeFilter title="商品分类" label="name" :data="categoryTree" @change="changeCategory" />
    <div class="table-main">
      <ProTable ref="proTableRef" page-id="/goods/product" title="商品管理" :columns="columns" :request-api="productApi.page" :init-param="initParam">
      </ProTable>
    </div>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'goods-product-index' })
import { reactive, ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import TreeFilter from '@/components/TreeFilter/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { productApi } from '@/api/apis/goods/product'
import { categoryApi } from '@/api/apis/goods/category'
import { brandApi } from '@/api/apis/goods/brand'
import type { ProductResponse } from '@/api/interface/goods/product'
import type { CategoryNode } from '@/api/interface/goods/category'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 分类过滤:initParam 响应式变化触发 ProTable 自动重查(useTable watch)
const initParam = reactive<{ categoryId?: number }>({})
const changeCategory = (categoryId: number) => {
  initParam.categoryId = categoryId
}

// 分类树:页面单点拉取(同一 Promise 复用),TreeFilter(:data)与分类列 enum 共用。
// 禁两处各自 requestApi:请求封装按 method+url+params 对并发去重,后发者 abort 先发者
// (base.ts AxiosCanceler),同 URL 两请求并存必有一个 CanceledError -> 树显示"暂无数据"
const categoryTree = ref<CategoryNode[]>([])
let categoryTreePromise: Promise<CategoryNode[]> | null = null
const getCategoryTree = () => (categoryTreePromise ??= categoryApi.tree())
getCategoryTree().then(tree => (categoryTree.value = tree))

// 品牌/分类列 id -> 名称映射(enum 异步拉取,ProTable 单元格自动按 label 渲染)
// 品牌走分页接口全量拉(pageSize 上限内);分类复用上方单点拉取的树,拍平成 {label,value} 列表
const brandEnum = async () => {
  const { list } = await brandApi.page({ pageNo: 1, pageSize: 500 })
  return list.map(item => ({ label: item.name, value: item.id }))
}
const flattenCategory = (nodes: CategoryNode[], out: { label: string; value: number }[] = []) => {
  nodes.forEach(node => {
    out.push({ label: node.name, value: node.id })
    if (node.children?.length) {
      flattenCategory(node.children, out)
    }
  })
  return out
}
const categoryEnum = async () => flattenCategory(await getCategoryTree())

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
// 品牌/分类列显示名称而非裸 id(2026-09-07 人工接线:enum 映射,label 同步去 "ID" 后缀)
const columns: ColumnProps<ProductResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'spuCode', label: 'SPU编码', width: 180 },
  { prop: 'name', label: '商品名称', width: 220 },
  { prop: 'brandId', label: '品牌', width: 140, enum: brandEnum },
  { prop: 'categoryId', label: '分类', width: 140, enum: categoryEnum },
  { prop: 'status', label: '状态', width: 90, enum: [{ label: '启用', value: 1, tagType: 'success' }, { label: '停用', value: 0, tagType: 'danger' }] },
  { prop: 'createdAt', label: '创建时间', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()
</script>

<style scoped lang="scss">
.product-box {
  flex-direction: row;
}
</style>
