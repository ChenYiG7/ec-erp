<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
  人工接线:左侧 TreeFilter 分类树过滤(gen:page 不产树形布局,docs/09 §4 例外;--force 重生成后需重新接线)
-->

<template>
  <div class="table-box">
    <TreeFilter title="商品分类" label="name" :request-api="categoryTreeApi" @change="changeCategory" />
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
import type { ProductResponse } from '@/api/interface/goods/product'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 分类过滤:initParam 响应式变化触发 ProTable 自动重查(useTable watch)
const initParam = reactive<{ categoryId?: number }>({})
const changeCategory = (categoryId: number) => {
  initParam.categoryId = categoryId
}

// 分类树数据源(GET /api/goods/categories/tree)
const categoryTreeApi = () => categoryApi.tree()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ProductResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'spuCode', label: 'SPU编码', width: 180 },
  { prop: 'name', label: '商品名称', width: 220 },
  { prop: 'brandId', label: '品牌ID', width: 100 },
  { prop: 'categoryId', label: '分类ID', width: 100 },
  { prop: 'status', label: '状态', width: 90, enum: [{ label: '启用', value: 1, tagType: 'success' }, { label: '停用', value: 0, tagType: 'danger' }] },
  { prop: 'createdAt', label: '创建时间', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()
</script>
