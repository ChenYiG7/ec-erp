<!--
  实时销售利润页(#19③ 三口径第一层,手写页:汇总卡+筛选卡+ProTable 组合,gen:page 不适用)
  口径:利润 = 售价(CNY) − 出库成本(CNY) − 平台佣金(CNY);缺成本=未出库,缺佣金=待结算,
  缺汇率=本位币列为空(禁猜)——缺口以 tag 显性呈现,不静默归零
-->

<template>
  <div class="table-box">
    <!-- 汇总卡:同筛选条件全量聚合;缺口单独计数 -->
    <el-card shadow="never" class="summary-card">
      <div class="summary-row">
        <div class="summary-item">
          <span class="summary-label">订单行数</span>
          <span class="summary-value">{{ summary?.orderItemCount ?? '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">销售额(CNY)</span>
          <span class="summary-value">{{ summary?.salesCny ?? '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">成本(CNY)</span>
          <span class="summary-value">{{ summary?.costCny ?? '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">佣金(CNY)</span>
          <span class="summary-value">{{ summary?.commissionCny ?? '-' }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">利润(CNY)</span>
          <span class="summary-value" :class="profitClass">{{ summary?.profitCny ?? '-' }}</span>
        </div>
        <div class="summary-item summary-gaps">
          <el-tag v-if="(summary?.missingRateCount ?? 0) > 0" type="warning" size="small">缺汇率 {{ summary!.missingRateCount }}</el-tag>
          <el-tag v-if="(summary?.costMissingCount ?? 0) > 0" type="info" size="small">未出库 {{ summary!.costMissingCount }}</el-tag>
          <el-tag v-if="(summary?.commissionMissingCount ?? 0) > 0" type="info" size="small">待结算 {{ summary!.commissionMissingCount }}</el-tag>
        </div>
      </div>
    </el-card>
    <!-- 筛选卡:initParam 响应式变化触发 ProTable/汇总自动重查 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="店铺">
          <el-select v-model="filter.shopId" clearable filterable placeholder="全部店铺" style="width: 180px">
            <el-option v-for="opt in shopOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="filter.platform" clearable placeholder="全部平台" style="width: 150px">
            <el-option v-for="item in platformOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="SKU">
          <el-input v-model="skuInput" placeholder="内部SKU ID" style="width: 140px" @keyup.enter="applyFilter" />
        </el-form-item>
        <el-form-item label="下单时间">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            range-separator="至"
            start-placeholder="起"
            end-placeholder="止"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <ProTable ref="proTableRef" page-id="/finance/profit" title="实时销售利润" :columns="columns" :request-api="pageWithSku" :init-param="initParam">
      <template #skuId="{ row }">{{ skuLabel(row.skuId) }}</template>
      <template #rate="{ row }">{{ row.rate ?? '缺汇率' }}</template>
      <template #costCny="{ row }">
        <el-tag v-if="row.costMissing" type="info" size="small">未出库</el-tag>
        <span v-else>{{ row.costCny }}</span>
      </template>
      <template #commissionCny="{ row }">
        <el-tag v-if="row.commissionMissing" type="warning" size="small">待结算</el-tag>
        <span v-else>{{ row.commissionCny }}</span>
      </template>
      <template #profitCny="{ row }">
        <span v-if="row.profitCny == null" class="profit-missing">-</span>
        <span v-else :class="Number(row.profitCny) < 0 ? 'profit-negative' : ''">{{ row.profitCny }}</span>
      </template>
    </ProTable>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-profit-index' })
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElCard, ElButton, ElDatePicker, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { profitApi } from '@/api/apis/finance/profit'
import { fetchShopOptions } from '@/api/apis/shop/options'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { useDictStore } from '@/stores/modules/dict'
import type { OrderProfitQuery, OrderProfitRow, OrderProfitSummary } from '@/api/interface/finance/profit'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 筛选态:apply 时同步进 initParam(响应式自动重查表格);汇总随 watch 同步刷新
const filter = reactive<{ shopId?: number; platform?: string; skuId?: number }>({})
const skuInput = ref('')
const range = ref<[string, string] | null>(null)
const initParam = reactive<OrderProfitQuery>({})

// 汇总(同 initParam 条件)
const summary = ref<OrderProfitSummary>()
const profitClass = computed(() => (Number(summary.value?.profitCny ?? 0) < 0 ? 'profit-negative' : ''))

const fetchSummary = () => profitApi.summary({ ...initParam }).then(s => (summary.value = s))
onMounted(fetchSummary)
watch(initParam, fetchSummary, { deep: true })

// 店铺/平台选项
const shopOptions = ref<Array<{ label: string; value: number }>>([])
fetchShopOptions().then(opts => (shopOptions.value = opts))
const platformOptions = ref<Array<{ label: string; value: string }>>([])
useDictStore()
  .getDict('shop_platform')
  .then(list => (platformOptions.value = list.map(item => ({ label: item.dictLabel, value: item.dictValue }))))

const applyFilter = () => {
  initParam.shopId = filter.shopId
  initParam.platform = filter.platform
  initParam.skuId = skuInput.value ? Number(skuInput.value) : undefined
  initParam.dateFrom = range.value?.[0]
  initParam.dateTo = range.value?.[1]
}
const resetFilter = () => {
  filter.shopId = undefined
  filter.platform = undefined
  skuInput.value = ''
  range.value = null
  applyFilter()
}

// 列配置(金额原币/汇率/三本位币金额;缺口 tag 显性呈现)
const columns: ColumnProps<OrderProfitRow>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'platformOrderId', label: '平台单号', width: 190 },
  { prop: 'platform', label: '平台', width: 100, tag: true, enum: () => useDictStore().getDict('shop_platform').then(list => list.map(item => ({ label: item.dictLabel, value: item.dictValue }))) },
  { prop: 'orderTime', label: '下单时间', width: 165 },
  { prop: 'skuId', label: '内部SKU', width: 150 },
  { prop: 'quantity', label: '数量', width: 70 },
  { prop: 'itemAmount', label: '售价(原币)', width: 110 },
  { prop: 'currency', label: '币种', width: 70 },
  { prop: 'rate', label: '汇率', width: 110 },
  { prop: 'salesCny', label: '销售额(CNY)', width: 115 },
  { prop: 'costCny', label: '成本(CNY)', width: 110 },
  { prop: 'commissionCny', label: '佣金(CNY)', width: 115 },
  { prop: 'profitCny', label: '利润(CNY)', width: 115 },
]

// SKU 列翻译预取(同 flow 页 #7 专条):行内 skuId 可空(未绑定行)
const pageWithSku = (params: Parameters<typeof profitApi.page>[0]) =>
  profitApi.page(params).then(res => {
    fetchSkuNames(res.list.map(r => r.skuId).filter((id): id is number => id != null))
    return res
  })

const refreshTable = () => proTableRef.value?.getTableList()

</script>
<style scoped lang="scss">
.summary-card {
  margin-bottom: 12px;
  :deep(.el-card__body) {
    padding: 14px 20px;
  }
}
.summary-row {
  display: flex;
  align-items: center;
  gap: 36px;
  flex-wrap: wrap;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.summary-value {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.summary-gaps {
  flex-direction: row;
  gap: 8px;
}
.filter-card {
  margin-bottom: 12px;
  :deep(.el-card__body) {
    padding: 14px 20px 0;
  }
}
.profit-negative {
  color: var(--el-color-danger);
}
.profit-missing {
  color: var(--el-text-color-secondary);
}
</style>
