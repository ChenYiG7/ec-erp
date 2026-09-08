<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <!-- 待匹配处理台:默认只看待匹配行(init-param 固定 matchStatus=0;过滤切换随搜索表单接入生成器增强后放开) -->
    <ProTable
      ref="proTableRef"
      page-id="/goods/shop-product-skus"
      title="SKU匹配"
      :columns="columns"
      :request-api="pageWithSku"
      :init-param="{ matchStatus: 0 }"
    >
      <!-- 内部SKU 列翻译(#7 专条):渲染读 options 模块缓存,预取在 pageWithSku -->
      <template #skuId="{ row }">{{ skuLabel(row.skuId) }}</template>
      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽;越权拦截在后端) -->
      <template #operation="scope">
        <el-button
          v-if="scope.row.matchStatus === 0"
          v-auth="'shop:product-sku:bind'"
          type="primary"
          link
          @click="onBind(scope.row)"
          >人工绑定</el-button
        >
      </template>
    </ProTable>
    <!-- 人工绑定弹窗(#16 收口裸 ID 手填:prompt 录 ID → SKU 搜索选择器) -->
    <BindDialog ref="bindDialogRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'shop-shop-product-sku-index' })
import { ref } from 'vue'
import { ElButton } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { shopProductSkuApi } from '@/api/apis/shop/shop-product-sku'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import type { ShopProductSkuResponse } from '@/api/interface/shop/shop-product-sku'
import BindDialog from './components/BindDialog.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
// 人工绑定弹窗
const bindDialogRef = ref<InstanceType<typeof BindDialog>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ShopProductSkuResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'sellerSku', label: '卖家SKU', width: 180 },
  { prop: 'shopProductId', label: '平台商品ID', width: 130 },
  { prop: 'skuId', label: '内部SKU', width: 170 },
  {
    prop: 'matchStatus',
    label: '匹配状态',
    width: 110,
    tag: true,
    enum: [
      { label: '待匹配', value: 0, tagType: 'warning' },
      { label: '自动匹配', value: 1, tagType: 'success' },
      { label: '人工绑定', value: 2, tagType: 'primary' },
    ],
  },
  { prop: 'quantity', label: '可售数量', width: 100 },
  { prop: 'price', label: '平台售价', width: 110 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 120 },
]

// skuId 列翻译预取(#7 专条):页数据加载后按行去重批量取,查询无全量端点(SKU 随业务增长)
const pageWithSku = (params: Parameters<typeof shopProductSkuApi.page>[0]) =>
  shopProductSkuApi.page(params).then(res => {
    fetchSkuNames(res.list.map(r => r.skuId))
    return res
  })

// 人工绑定:打开选择器弹窗(SKU 搜索选择器选定内部 SKU);回填 sku_id + match_status=2,重复绑定幂等,存在性校验在后端
const onBind = (row: ShopProductSkuResponse) => {
  bindDialogRef.value?.open(row)
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
