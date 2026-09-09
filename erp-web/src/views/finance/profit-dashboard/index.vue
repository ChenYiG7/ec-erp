<!--
  利润看板(#21 利润面产品化,手写页):汇总卡 + SVG 日趋势(零图表依赖)+ SKU 利润排行表
  口径与实时销售利润页同源(同一契约 /summary /trend /sku-rank)——缺成本/缺汇率行不计入对应金额,
  毛利率=利润/销售额 前端自算(缺口口径见汇总卡计数)
-->

<template>
  <div class="table-box">
    <!-- 筛选卡:initParam 变化自动重拉三路数据 -->
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
    <!-- 汇总卡 -->
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
        <div class="summary-item">
          <span class="summary-label">利润率</span>
          <span class="summary-value">{{ marginRate }}</span>
        </div>
        <div class="summary-item summary-gaps">
          <el-tag v-if="(summary?.missingRateCount ?? 0) > 0" type="warning" size="small"
            >缺汇率 {{ summary!.missingRateCount }}</el-tag
          >
          <el-tag v-if="(summary?.costMissingCount ?? 0) > 0" type="info" size="small"
            >未出库 {{ summary!.costMissingCount }}</el-tag
          >
          <el-tag v-if="(summary?.commissionMissingCount ?? 0) > 0" type="info" size="small"
            >待结算 {{ summary!.commissionMissingCount }}</el-tag
          >
        </div>
      </div>
    </el-card>
    <!-- 日趋势(纯 SVG,零图表依赖):柱=利润,线=销售额 -->
    <el-card shadow="never" class="chart-card">
      <template #header><span>利润日趋势</span></template>
      <div v-if="trend.length === 0" class="chart-empty">暂无数据(条件内无已支付订单行)</div>
      <svg v-else class="trend-svg" viewBox="0 0 680 220" preserveAspectRatio="none">
        <line x1="40" y1="180" x2="670" y2="180" stroke="var(--el-border-color)" stroke-width="1" />
        <text x="4" y="24" class="axis-text">{{ maxSalesLabel }}</text>
        <text x="4" y="180" class="axis-text">0</text>
        <g v-for="(bar, i) in bars" :key="i">
          <rect
            :x="bar.x"
            :y="bar.y"
            :width="bar.width"
            :height="bar.height"
            :fill="bar.negative ? 'var(--el-color-danger)' : 'var(--el-color-success)'"
            rx="2"
          >
            <title>{{ bar.title }}</title>
          </rect>
        </g>
        <polyline :points="salesLine" fill="none" stroke="var(--el-color-primary)" stroke-width="2" />
        <g v-for="(label, i) in xLabels" :key="'l' + i">
          <text :x="label.x" y="198" text-anchor="middle" class="axis-text">{{ label.text }}</text>
        </g>
      </svg>
    </el-card>
    <!-- SKU 利润排行 -->
    <el-card shadow="never">
      <template #header><span>SKU 利润排行(Top 10,按利润降序;未绑定 SKU 的订单行不参与)</span></template>
      <el-table :data="rank" stripe>
        <el-table-column type="index" label="#" width="55" />
        <el-table-column label="内部SKU" width="150">
          <template #default="{ row }">{{ skuLabel(row.skuId) }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" label="销量(件)" width="90" />
        <el-table-column prop="salesCny" label="销售额(CNY)" width="120" />
        <el-table-column prop="costCny" label="成本(CNY)" width="110" />
        <el-table-column prop="commissionCny" label="佣金(CNY)" width="110" />
        <el-table-column label="利润(CNY)" width="120">
          <template #default="{ row }">
            <span :class="Number(row.profitCny) < 0 ? 'profit-negative' : ''">{{ row.profitCny }}</span>
          </template>
        </el-table-column>
        <el-table-column label="利润率" width="90">
          <template #default="{ row }">{{ marginOf(row as ProfitSkuRankRow) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-profit-dashboard-index' })
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { profitApi } from '@/api/apis/finance/profit'
import { fetchShopOptions } from '@/api/apis/shop/options'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { useDictStore } from '@/stores/modules/dict'
import type {
  OrderProfitQuery,
  OrderProfitSummary,
  ProfitDailyTrendRow,
  ProfitSkuRankRow,
} from '@/api/interface/finance/profit'

// 筛选态(与利润明细页同款,少 SKU 维度——看板看全局)
const filter = reactive<{ shopId?: number; platform?: string }>({})
const range = ref<[string, string] | null>(null)
const initParam = reactive<OrderProfitQuery>({})

const summary = ref<OrderProfitSummary>()
const trend = ref<ProfitDailyTrendRow[]>([])
const rank = ref<ProfitSkuRankRow[]>([])

const profitClass = computed(() => (Number(summary.value?.profitCny ?? 0) < 0 ? 'profit-negative' : ''))
const marginRate = computed(() => {
  const sales = Number(summary.value?.salesCny ?? 0)
  const profit = Number(summary.value?.profitCny ?? 0)
  return sales > 0 ? ((profit / sales) * 100).toFixed(1) + '%' : '-'
})
const marginOf = (row: { salesCny: string | number; profitCny: string | number }) => {
  const sales = Number(row.salesCny)
  return sales > 0 ? ((Number(row.profitCny) / sales) * 100).toFixed(1) + '%' : '-'
}

const fetchAll = () => {
  const params = { ...initParam }
  profitApi.summary(params).then(s => (summary.value = s))
  profitApi.trend(params).then(t => (trend.value = t))
  profitApi
    .skuRank({ ...params, topN: 10 })
    .then(r => (rank.value = r))
    .then(() => fetchSkuNames(rank.value.map(row => row.skuId)))
}
onMounted(fetchAll)
watch(initParam, fetchAll, { deep: true })

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
  initParam.dateFrom = range.value?.[0]
  initParam.dateTo = range.value?.[1]
}
const resetFilter = () => {
  filter.shopId = undefined
  filter.platform = undefined
  range.value = null
  applyFilter()
}

// SVG 趋势几何:柱=利润(正绿负红),折线=销售额;y 轴以销售额最大值定标(利润与销售额同币种同量级)
const CHART_W = 680
const CHART_H = 180
const CHART_LEFT = 40
const BAR_GAP = 6

const maxSales = computed(() => Math.max(...trend.value.map(t => Number(t.salesCny)), 1))
const maxSalesLabel = computed(() => String(maxSales.value))
const barWidth = computed(() =>
  trend.value.length === 0 ? 0 : Math.max(4, Math.min(36, (CHART_W - CHART_LEFT) / trend.value.length - BAR_GAP))
)
const bars = computed(() =>
  trend.value.map((t, i) => {
    const profit = Number(t.profitCny)
    const slot = (CHART_W - CHART_LEFT) / trend.value.length
    const x = CHART_LEFT + i * slot + (slot - barWidth.value) / 2
    const ratio = Math.min(Math.abs(profit) / maxSales.value, 1)
    const height = profit === 0 ? 1 : Math.max(2, ratio * (CHART_H - 30))
    const y = profit < 0 ? 180 : 180 - height
    return {
      x,
      y,
      width: barWidth.value,
      height,
      negative: profit < 0,
      title: `${t.statDate}  利润 ${t.profitCny} / 销售额 ${t.salesCny}(${t.orderItemCount} 行)`,
    }
  })
)
const salesLine = computed(() =>
  trend.value
    .map((t, i) => {
      const slot = (CHART_W - CHART_LEFT) / trend.value.length
      const x = CHART_LEFT + i * slot + slot / 2
      const y = 180 - Math.max(2, (Number(t.salesCny) / maxSales.value) * (CHART_H - 30))
      return `${x},${y}`
    })
    .join(' ')
)
const xLabels = computed(() => {
  const step = Math.max(1, Math.ceil(trend.value.length / 10))
  return trend.value
    .filter((_, i) => i % step === 0)
    .map((t, j) => {
      const i = j * step
      const slot = (CHART_W - CHART_LEFT) / trend.value.length
      return { x: CHART_LEFT + i * slot + slot / 2, text: t.statDate.slice(5) }
    })
})
</script>
<style scoped lang="scss">
.filter-card,
.summary-card,
.chart-card {
  margin-bottom: 12px;
  :deep(.el-card__body) {
    padding: 14px 20px;
  }
}
.filter-card :deep(.el-card__body) {
  padding: 14px 20px 0;
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
.trend-svg {
  width: 100%;
  height: 240px;
}
.axis-text {
  font-size: 11px;
  fill: var(--el-text-color-secondary);
}
.profit-negative {
  color: var(--el-color-danger);
}
.chart-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
