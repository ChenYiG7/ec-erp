<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/shop-products" title="店铺商品" :columns="columns" :request-api="shopProductApi.page">
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'shop-shop-product-index' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { shopProductApi } from '@/api/apis/shop/shop-product'
import type { ShopProductResponse } from '@/api/interface/shop/shop-product'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ShopProductResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shopId', label: '店铺ID', width: 100 },
  { prop: 'platformProductId', label: '平台商品ID', width: 160 },
  { prop: 'productId', label: '内部SPU', width: 120 },
  { prop: 'listingStatus', label: 'listing状态', width: 120 },
  { prop: 'lastSyncAt', label: '最近同步', width: 170 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()
</script>
