<!--
  商品分析(#22 四期 BI 首个功能,手写页):SKU 选择器 + 窗口汇总卡 + SVG 日趋势(零图表依赖,同 #21 拍板)
  数据面 = 销量日表/库存日快照(#6,#20 同源零 DDL 纯读侧):
  柱=日销量(缺日=0),折线=日库存(跨仓合计,快照缺失=null 断点分段不连线)
-->

<template>
  <div class="table-box">
    <!-- 筛选卡:SKU 必选,窗口缺省近 30 天 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="内部SKU">
          <el-select v-model="filter.skuId" filterable placeholder="选择 SKU(销量或库存出现过的)" style="width: 280px">
            <el-option v-for="opt in skuOptions" :key="opt.skuId" :label="optionLabel(opt)" :value="opt.skuId" />
          </el-select>
        </el-form-item>
        <el-form-item label="统计窗口">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="起"
            end-placeholder="止"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="filter.skuId == null" @click="applyFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <template v-if="resp">
      <!-- 汇总卡(后端由趋势行内存计算) -->
      <el-card shadow="never" class="summary-card">
        <div class="summary-row">
          <div class="summary-item">
            <span class="summary-label">SKU</span>
            <span class="summary-value summary-sku">{{ skuTitle }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">窗口销量合计(件)</span>
            <span class="summary-value">{{ resp.summary.totalQtySold }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">动销天数</span>
            <span class="summary-value">{{ resp.summary.activeDays }} / {{ resp.trend.length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">期末库存(件,跨仓)</span>
            <span class="summary-value">
              {{ resp.summary.latestQtyOnHand ?? '-' }}
              <span v-if="resp.summary.latestStockDate" class="summary-sub">@{{ resp.summary.latestStockDate }}</span>
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">窗口</span>
            <span class="summary-value summary-window">{{ windowLabel }}</span>
          </div>
        </div>
      </el-card>
      <!-- 日趋势(纯 SVG,零图表依赖):柱=日销量,折线=日库存(断点分段) -->
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-header">
            <span>销量 / 库存日趋势</span>
            <span class="legend"> <i class="legend-bar" />日销量 <i class="legend-line" />日库存(快照缺失断点) </span>
          </div>
        </template>
        <div v-if="resp.trend.length === 0" class="chart-empty">窗口内无数据</div>
        <svg v-else class="trend-svg" viewBox="0 0 680 220" preserveAspectRatio="none">
          <line x1="40" y1="180" x2="670" y2="180" stroke="var(--el-border-color)" stroke-width="1" />
          <text x="4" y="24" class="axis-text">{{ axisMaxLabel }}</text>
          <text x="4" y="180" class="axis-text">0</text>
          <g v-for="(bar, i) in bars" :key="i">
            <rect :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" fill="var(--el-color-primary)" rx="2">
              <title>{{ bar.title }}</title>
            </rect>
          </g>
          <polyline
            v-for="(seg, i) in stockSegments"
            :key="'s' + i"
            :points="seg"
            fill="none"
            stroke="var(--el-color-success)"
            stroke-width="2"
            stroke-dasharray="4 2"
          />
          <g v-for="(label, i) in xLabels" :key="'l' + i">
            <text :x="label.x" y="198" text-anchor="middle" class="axis-text">{{ label.text }}</text>
          </g>
        </svg>
      </el-card>
    </template>
    <el-card v-else shadow="never">
      <div class="chart-empty">请先选择 SKU 再查询(选项 = 销量或库存数据面出现过的 SKU)</div>
    </el-card>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'report-goods-analysis-index' })
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElCard, ElDatePicker, ElForm, ElFormItem, ElOption, ElSelect } from 'element-plus'
import { reportApi } from '@/api/apis/report/report'
import type { SkuOptionRow, SkuTrendResponse } from '@/api/interface/report/report'

const filter = reactive<{ skuId?: number }>({})
const range = ref<[string, string] | null>(null)
const resp = ref<SkuTrendResponse>()

// SKU 选项 = 销量∪快照数据面出现过的 SKU(后端钳制 ≤500)
const skuOptions = ref<SkuOptionRow[]>([])
onMounted(() => {
  reportApi.goodsOptions().then(opts => (skuOptions.value = opts))
})

const optionLabel = (opt: SkuOptionRow) =>
  opt.productName ? `${opt.skuCode ?? opt.skuId} · ${opt.productName}` : (opt.skuCode ?? String(opt.skuId))
const skuTitle = computed(() => {
  const sku = resp.value?.sku
  return sku ? optionLabel(sku) : `SKU #${filter.skuId}`
})
const windowLabel = computed(() => {
  const trend = resp.value?.trend ?? []
  return trend.length === 0 ? '-' : `${trend[0].statDate} ~ ${trend[trend.length - 1].statDate}`
})

const fetchTrend = () => {
  if (filter.skuId == null) {
    return
  }
  reportApi
    .goodsTrend({ skuId: filter.skuId, dateFrom: range.value?.[0], dateTo: range.value?.[1] })
    .then(r => (resp.value = r))
}
const applyFilter = fetchTrend
const resetFilter = () => {
  filter.skuId = undefined
  range.value = null
  resp.value = undefined
}

// SVG 趋势几何:柱=日销量,虚线折线=日库存;y 轴以两序列最大值定标(同"件"量纲)
const CHART_W = 680
const CHART_H = 180
const CHART_LEFT = 40
const BAR_GAP = 6

const axisMax = computed(() =>
  Math.max(1, ...(resp.value?.trend ?? []).map(t => Math.max(t.qtySold, t.qtyOnHand ?? 0)))
)
const axisMaxLabel = computed(() => String(axisMax.value))
const barWidth = computed(() => {
  const n = resp.value?.trend.length ?? 0
  return n === 0 ? 0 : Math.max(4, Math.min(36, (CHART_W - CHART_LEFT) / n - BAR_GAP))
})
const bars = computed(() =>
  (resp.value?.trend ?? []).map((t, i) => {
    const n = resp.value!.trend.length
    const slot = (CHART_W - CHART_LEFT) / n
    const height = t.qtySold === 0 ? 1 : Math.max(2, (t.qtySold / axisMax.value) * (CHART_H - 30))
    return {
      x: CHART_LEFT + i * slot + (slot - barWidth.value) / 2,
      y: 180 - height,
      width: barWidth.value,
      height,
      title: `${t.statDate}  销量 ${t.qtySold} / 库存 ${t.qtyOnHand ?? '无快照'}`,
    }
  })
)
// 库存折线分段:qtyOnHand=null 的日子断开,连续非 null 段各一条 polyline
const stockSegments = computed(() => {
  const trend = resp.value?.trend ?? []
  const n = trend.length
  const slot = n === 0 ? 0 : (CHART_W - CHART_LEFT) / n
  const segments: string[] = []
  let current: string[] = []
  trend.forEach((t, i) => {
    if (t.qtyOnHand == null) {
      if (current.length > 1) {
        segments.push(current.join(' '))
      }
      current = []
      return
    }
    const x = CHART_LEFT + i * slot + slot / 2
    const y = 180 - Math.max(2, (t.qtyOnHand / axisMax.value) * (CHART_H - 30))
    current.push(`${x},${y}`)
  })
  if (current.length > 1) {
    segments.push(current.join(' '))
  }
  return segments
})
const xLabels = computed(() => {
  const trend = resp.value?.trend ?? []
  const n = trend.length
  const step = Math.max(1, Math.ceil(n / 10))
  const slot = n === 0 ? 0 : (CHART_W - CHART_LEFT) / n
  return trend
    .filter((_, i) => i % step === 0)
    .map((t, j) => ({ x: CHART_LEFT + j * step * slot + slot / 2, text: t.statDate.slice(5) }))
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
.summary-sku {
  font-size: 14px;
}
.summary-window {
  font-size: 14px;
}
.summary-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.legend {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.legend-bar {
  display: inline-block;
  width: 12px;
  height: 8px;
  background: var(--el-color-primary);
  border-radius: 2px;
}
.legend-line {
  display: inline-block;
  width: 16px;
  border-top: 2px dashed var(--el-color-success);
  margin-left: 8px;
}
.trend-svg {
  width: 100%;
  height: 240px;
}
.axis-text {
  font-size: 11px;
  fill: var(--el-text-color-secondary);
}
.chart-empty {
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
