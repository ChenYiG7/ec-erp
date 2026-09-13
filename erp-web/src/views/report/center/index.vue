<!--
  报表中心页(#20 报表域 V1,手写页):销售面(日报/周报/SKU明细三 tab)+ 库存快照面 + Excel 导出;
  经营简报 tab(#23 智能报表 V1,四期 BI):周期预览,定时推送在后端 Job 走 #14 出口(站内+邮箱+Webhook)
  数据面 = order_sales_daily / inventory_snapshot_daily(系统已就位的两个日快照表,零 DDL 纯读侧);
  快照不可回溯(docs/03 §7.2),日期缺省=最新快照日;导出 xlsx 走 blob 下载
  可视化(纯 CSS/SVG 零图表依赖,同 #21/#22 拍板):汇总卡 + 趋势/条形/构成图,数据均为前端内存聚合
-->

<template>
  <div class="table-box">
    <el-card shadow="never" class="filter-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="销售日报" name="daily" />
        <el-tab-pane label="销售周报" name="weekly" />
        <el-tab-pane label="SKU明细" name="sku" />
        <el-tab-pane label="库存快照" name="snapshot" />
        <el-tab-pane label="经营简报" name="digest" />
      </el-tabs>
      <el-form inline>
        <template v-if="activeTab === 'snapshot'">
          <el-form-item label="快照日">
            <el-date-picker
              v-model="snapshotDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="缺省=最新快照日"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadSnapshot">查询</el-button>
            <el-button type="success" @click="exportSnapshot">导出 Excel</el-button>
          </el-form-item>
        </template>
        <template v-else-if="activeTab === 'digest'">
          <el-form-item label="周期">
            <el-radio-group v-model="digestPeriod">
              <el-radio-button value="DAILY">日报(昨日)</el-radio-button>
              <el-radio-button value="WEEKLY">周报(上周)</el-radio-button>
              <el-radio-button value="MONTHLY">月报(上月)</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadDigest">预览</el-button>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="统计窗口">
            <el-date-picker
              v-model="salesRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="起"
              end-placeholder="止"
              style="width: 300px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadSales">查询</el-button>
            <el-button type="success" @click="exportSales">导出 Excel</el-button>
          </el-form-item>
        </template>
      </el-form>
    </el-card>

    <!-- 销售日报/周报 -->
    <template v-if="activeTab === 'daily' || activeTab === 'weekly'">
      <el-card v-if="periodRows.length > 0" shadow="never" class="summary-card">
        <div class="summary-row">
          <div class="summary-item">
            <span class="summary-label">窗口销量合计(件)</span>
            <span class="summary-value">{{ periodTotalQty }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">{{ isWeekly ? '动销周数' : '动销天数' }}</span>
            <span class="summary-value">
              {{ periodActive }} <span class="summary-sub">/ {{ periodRows.length }} {{ isWeekly ? '周' : '天' }}</span>
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">{{ isWeekly ? '周均销量(件)' : '日均销量(件)' }}</span>
            <span class="summary-value">{{ periodAvg }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">{{ isWeekly ? '峰值周' : '峰值日' }}</span>
            <span class="summary-value">
              {{ periodPeak?.qty ?? '-' }}
              <span v-if="periodPeak" class="summary-sub">@{{ periodPeak.label }}</span>
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">窗口</span>
            <span class="summary-value summary-window">{{ periodWindowLabel }}</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-header">
            <span>{{ isWeekly ? '周销量趋势' : '日销量趋势' }}</span>
            <span class="legend">
              <i class="legend-bar" />销量(件) <i class="legend-line" />有销量{{
                isWeekly ? 'SKU数' : 'SKU数'
              }}(独立刻度)
            </span>
          </div>
        </template>
        <div v-if="periodRows.length === 0" class="chart-empty">窗口内无销量数据</div>
        <svg v-else class="trend-svg" viewBox="0 0 680 220" preserveAspectRatio="none">
          <line x1="40" y1="180" x2="670" y2="180" stroke="var(--el-border-color)" stroke-width="1" />
          <text x="4" y="24" class="axis-text">{{ periodAxisMaxLabel }}</text>
          <text x="4" y="180" class="axis-text">0</text>
          <g v-for="(bar, i) in periodBars" :key="i">
            <rect :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" fill="var(--el-color-primary)" rx="2">
              <title>{{ bar.title }}</title>
            </rect>
          </g>
          <polyline
            :points="periodSkuLine"
            fill="none"
            stroke="var(--el-color-success)"
            stroke-width="2"
            stroke-dasharray="4 2"
          />
          <g v-for="(label, i) in periodXLabels" :key="'l' + i">
            <text :x="label.x" y="198" text-anchor="middle" class="axis-text">{{ label.text }}</text>
          </g>
        </svg>
      </el-card>
      <el-card shadow="never">
        <el-table :data="periodRows" stripe>
          <el-table-column :prop="isWeekly ? 'weekStart' : 'statDate'" label="统计周期" width="140" />
          <el-table-column prop="totalQty" label="销量合计(件)" width="140" />
          <el-table-column prop="skuCount" label="有销量SKU数" width="140" />
          <el-table-column label="销量占比" min-width="200">
            <template #default="{ row }">
              <div class="share-cell">
                <div class="share-track">
                  <div class="share-fill" :style="{ width: sharePct(row.totalQty, periodTotalQty) }" />
                </div>
                <span class="share-text">{{ sharePct(row.totalQty, periodTotalQty) }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- SKU 明细 -->
    <template v-else-if="activeTab === 'sku'">
      <el-card v-if="skuRows.length > 0" shadow="never" class="summary-card">
        <div class="summary-row">
          <div class="summary-item">
            <span class="summary-label">窗口内SKU数</span>
            <span class="summary-value">{{ skuRows.length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">窗口销量合计(件)</span>
            <span class="summary-value">{{ skuTotalQty }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Top1 集中度</span>
            <span class="summary-value">
              {{ sharePct(skuRows[0].totalQty, skuTotalQty) }}
              <span class="summary-sub">@{{ skuRows[0].skuCode ?? skuRows[0].skuId }}</span>
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Top5 集中度</span>
            <span class="summary-value">{{ skuTop5Share }}</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-header">
            <span>SKU 销量排行(Top 15)</span>
          </div>
        </template>
        <div v-if="skuRows.length === 0" class="chart-empty">窗口内无销量数据</div>
        <div v-else class="hbar-list">
          <div v-for="row in skuTop15" :key="row.skuId" class="hbar-row">
            <span class="hbar-label" :title="row.productName ?? ''">{{ row.skuCode ?? row.skuId }}</span>
            <div class="hbar-track">
              <div
                class="hbar-fill"
                :style="{ width: sharePct(row.totalQty, skuAxisMax) }"
                :title="`${row.skuCode ?? row.skuId} ${row.productName ?? ''}  销量 ${row.totalQty}  占比 ${sharePct(row.totalQty, skuTotalQty)}`"
              />
            </div>
            <span class="hbar-value">{{ row.totalQty }}</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never">
        <el-table :data="skuRows" stripe>
          <el-table-column type="index" label="#" width="55" />
          <el-table-column label="SKU编码" width="160">
            <template #default="{ row }">{{ row.skuCode ?? row.skuId }}</template>
          </el-table-column>
          <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="totalQty" label="销量合计(件)" width="140" />
          <el-table-column label="销量占比" min-width="200">
            <template #default="{ row }">
              <div class="share-cell">
                <div class="share-track">
                  <div class="share-fill" :style="{ width: sharePct(row.totalQty, skuTotalQty) }" />
                </div>
                <span class="share-text">{{ sharePct(row.totalQty, skuTotalQty) }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 库存快照 -->
    <template v-else-if="activeTab === 'snapshot'">
      <el-card v-if="snapshotRows.length > 0" shadow="never" class="summary-card">
        <div class="summary-row">
          <div class="summary-item">
            <span class="summary-label">仓库数</span>
            <span class="summary-value">{{ whRows.length }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">SKU数</span>
            <span class="summary-value">{{ snapshotSkuCount }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">在库合计(件)</span>
            <span class="summary-value">{{ snapshotSum('qtyOnHand') }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">占用合计(件)</span>
            <span class="summary-value">{{ snapshotSum('qtyLocked') }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">在途合计(件)</span>
            <span class="summary-value">{{ snapshotSum('qtyTransit') }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">可用合计(件)</span>
            <span class="summary-value">{{ snapshotSum('qtyAvailable') }}</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="chart-header">
            <span>各仓库库存构成</span>
            <span class="legend">
              <i class="dot dot-primary" />可用 <i class="dot dot-warning" />占用 <i class="dot dot-info" />在途
            </span>
          </div>
        </template>
        <div v-if="whRows.length === 0" class="chart-empty">暂无快照数据</div>
        <div v-else class="hbar-list">
          <div v-for="wh in whRows" :key="wh.id" class="hbar-row">
            <span class="hbar-label" :title="wh.name">{{ wh.name }}</span>
            <div class="stack-track">
              <div
                class="stack-fill"
                :style="{
                  width: stackPct(wh.available, 'left'),
                  background: 'var(--el-color-primary)',
                }"
                :title="`${wh.name}  可用 ${wh.available}`"
              />
              <div
                class="stack-fill"
                :style="{
                  width: stackPct(wh.locked, 'mid'),
                  background: 'var(--el-color-warning)',
                }"
                :title="`${wh.name}  占用 ${wh.locked}`"
              />
              <div
                class="stack-fill"
                :style="{
                  width: stackPct(wh.transit, 'right'),
                  background: 'var(--el-color-info)',
                }"
                :title="`${wh.name}  在途 ${wh.transit}`"
              />
            </div>
            <span class="hbar-value">{{ wh.available + wh.locked + wh.transit }}</span>
          </div>
        </div>
      </el-card>
      <el-card shadow="never">
        <el-alert
          v-if="snapshotRows.length === 0 && !loading"
          title="暂无快照数据(InventorySnapshotJob 每日低峰落当日快照,首跑前为空)"
          type="info"
          :closable="false"
          class="snapshot-empty"
        />
        <el-table :data="snapshotRows" stripe v-loading="loading">
          <el-table-column prop="statDate" label="快照日" width="110" />
          <el-table-column label="SKU编码" width="150">
            <template #default="{ row }">{{ row.skuCode ?? row.skuId }}</template>
          </el-table-column>
          <el-table-column prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="仓库" width="130">
            <template #default="{ row }">{{ row.whName ?? row.warehouseId }}</template>
          </el-table-column>
          <el-table-column prop="qtyOnHand" label="在库" width="90" />
          <el-table-column prop="qtyLocked" label="占用" width="90" />
          <el-table-column prop="qtyTransit" label="在途" width="90" />
          <el-table-column prop="qtyAvailable" label="可用" width="90" />
        </el-table>
      </el-card>
    </template>

    <!-- 经营简报(#23):预览三周期简报文本;定时推送/渠道开关由后端配置,页面只读不推送 -->
    <el-card v-else shadow="never">
      <el-alert
        title="简报由定时任务自动推送(站内通知+邮箱+Webhook,推送开关 erp.report.digest.enabled 默认关,渠道各自开关控制);本页预览为纯读侧零副作用"
        type="info"
        :closable="false"
        class="snapshot-empty"
      />
      <div v-if="digest" class="digest-box">
        <div class="digest-head">
          <span class="digest-title">{{ digest.title }}</span>
          <el-tag size="small" type="primary">{{ digestPeriodLabel }}</el-tag>
          <span class="digest-window">{{ digest.dateFrom }} ~ {{ digest.dateTo }}</span>
        </div>
        <pre class="digest-content">{{ digest.content }}</pre>
      </div>
      <el-empty v-else-if="!loading" description="选择周期后点击「预览」生成简报" :image-size="80" />
    </el-card>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'report-center-index' })

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { reportApi, saveBlob } from '@/api/apis/report/report'
import type {
  InventorySnapshotRow,
  ReportDigest,
  SalesDailyRow,
  SalesSkuRow,
  SalesWeeklyRow,
} from '@/api/interface/report/report'

type TabName = 'daily' | 'weekly' | 'sku' | 'snapshot' | 'digest'
const activeTab = ref<TabName>('daily')
const loading = ref(false)

// 销售面:窗口缺省近 30 天(后端钳制,前端留空即走缺省)
const salesRange = ref<[string, string] | null>(null)
const periodRows = ref<Array<SalesDailyRow | SalesWeeklyRow>>([])
const skuRows = ref<SalesSkuRow[]>([])

// 快照面:日期缺省=最新快照日(后端语义,前端留空即走缺省)
const snapshotDate = ref<string | null>(null)
const snapshotRows = ref<InventorySnapshotRow[]>([])

// 经营简报面(#23):窗口由周期语义在后端决定(日报=昨日/周报=上周/月报=上月)
const digestPeriod = ref<ReportDigest['period']>('DAILY')
const digest = ref<ReportDigest | null>(null)

const windowParams = () => ({
  dateFrom: salesRange.value?.[0],
  dateTo: salesRange.value?.[1],
})

// 日报/周报/SKU 三形态共用 periodRows:请求序号守卫,tab 快速切换时旧响应不得覆盖新 tab 数据(#26 二轮走查)
let salesSeq = 0
const loadSales = async () => {
  const seq = ++salesSeq
  loading.value = true
  try {
    if (activeTab.value === 'daily') {
      const rows = await reportApi.salesDaily(windowParams())
      if (seq === salesSeq) {
        periodRows.value = rows
      }
    } else if (activeTab.value === 'weekly') {
      const rows = await reportApi.salesWeekly(windowParams())
      if (seq === salesSeq) {
        periodRows.value = rows
      }
    } else {
      const rows = await reportApi.salesSku({ ...windowParams(), limit: 200 })
      if (seq === salesSeq) {
        skuRows.value = rows
      }
    }
  } finally {
    if (seq === salesSeq) {
      loading.value = false
    }
  }
}

// tab 切换即拉对应数据:销售三 tab 共用 loadSales(此前切 tab 不发请求,周报复用日报数据且周期列空白,#26 二轮走查);
// 快照/简报两 tab 保留手动查询语义(有显式按钮与空态引导)
const onTabChange = () => {
  if (activeTab.value === 'daily' || activeTab.value === 'weekly' || activeTab.value === 'sku') {
    loadSales()
  }
}

const loadSnapshot = async () => {
  loading.value = true
  try {
    snapshotRows.value = await reportApi.snapshot({ date: snapshotDate.value ?? undefined })
  } finally {
    loading.value = false
  }
}

const loadDigest = async () => {
  loading.value = true
  try {
    digest.value = await reportApi.digestPreview({ period: digestPeriod.value })
  } finally {
    loading.value = false
  }
}

const exportSales = () => reportApi.exportSales(windowParams()).then(blob => saveBlob(blob, salesFilename()))
const salesFilename = () => {
  const p = windowParams()
  const from = (p.dateFrom ?? '').replace(/-/g, '') || 'default'
  const to = (p.dateTo ?? '').replace(/-/g, '') || 'default'
  return `sales_${from}_${to}.xlsx`
}
const exportSnapshot = () => {
  const date = snapshotDate.value ?? undefined
  return reportApi
    .exportInventory({ date })
    .then(blob => saveBlob(blob, `inventory_snapshot_${(date ?? 'latest').replace(/-/g, '')}.xlsx`))
}

// ---- 销售日报/周报可视化(前端内存聚合,零后端改动) ----
const isWeekly = computed(() => activeTab.value === 'weekly')
const periodLabelOf = (row: SalesDailyRow | SalesWeeklyRow) =>
  isWeekly.value ? (row as SalesWeeklyRow).weekStart : (row as SalesDailyRow).statDate
const periodTotalQty = computed(() => periodRows.value.reduce((s, r) => s + Number(r.totalQty), 0))
const periodActive = computed(() => periodRows.value.filter(r => Number(r.totalQty) > 0).length)
const periodAvg = computed(() =>
  periodRows.value.length === 0 ? '-' : String(Math.round((periodTotalQty.value / periodRows.value.length) * 10) / 10)
)
const periodPeak = computed(() => {
  if (periodRows.value.length === 0) {
    return null
  }
  const peak = periodRows.value.reduce((best, r) => (Number(r.totalQty) > Number(best.totalQty) ? r : best))
  return { label: periodLabelOf(peak), qty: Number(peak.totalQty) }
})
const periodWindowLabel = computed(() => {
  if (periodRows.value.length === 0) {
    return '-'
  }
  return `${periodLabelOf(periodRows.value[0])} ~ ${periodLabelOf(periodRows.value[periodRows.value.length - 1])}`
})

// 占比/份额统一取整百分比文本
const sharePct = (qty: number | string, total: number | string) => {
  const t = Number(total)
  return t <= 0 ? '0%' : `${Math.round((Number(qty) / t) * 10000) / 100}%`
}

// SVG 趋势几何(同 #21/#22 手法):柱=销量,虚线折线=有销量SKU数(独立刻度,形状看趋势、数值看 tooltip)
const CHART_W = 680
const CHART_H = 180
const CHART_LEFT = 40
const BAR_GAP = 6
const periodAxisMax = computed(() => Math.max(1, ...periodRows.value.map(r => Number(r.totalQty))))
const periodAxisMaxLabel = computed(() => String(periodAxisMax.value))
const periodSkuMax = computed(() => Math.max(1, ...periodRows.value.map(r => Number(r.skuCount))))
const periodBarWidth = computed(() => {
  const n = periodRows.value.length
  return n === 0 ? 0 : Math.max(4, Math.min(36, (CHART_W - CHART_LEFT) / n - BAR_GAP))
})
const periodBars = computed(() =>
  periodRows.value.map((r, i) => {
    const n = periodRows.value.length
    const slot = (CHART_W - CHART_LEFT) / n
    const qty = Number(r.totalQty)
    const height = qty === 0 ? 1 : Math.max(2, (qty / periodAxisMax.value) * (CHART_H - 30))
    return {
      x: CHART_LEFT + i * slot + (slot - periodBarWidth.value) / 2,
      y: 180 - height,
      width: periodBarWidth.value,
      height,
      title: `${periodLabelOf(r)}  销量 ${qty} / 有销量SKU数 ${Number(r.skuCount)}`,
    }
  })
)
const periodSkuLine = computed(() =>
  periodRows.value
    .map((r, i) => {
      const n = periodRows.value.length
      const slot = (CHART_W - CHART_LEFT) / n
      const x = CHART_LEFT + i * slot + slot / 2
      const y = 180 - Math.max(2, (Number(r.skuCount) / periodSkuMax.value) * (CHART_H - 30))
      return `${x},${y}`
    })
    .join(' ')
)
const periodXLabels = computed(() => {
  const rows = periodRows.value
  const n = rows.length
  const step = Math.max(1, Math.ceil(n / 10))
  const slot = n === 0 ? 0 : (CHART_W - CHART_LEFT) / n
  return rows
    .filter((_, i) => i % step === 0)
    .map((r, j) => {
      const i = j * step
      return { x: CHART_LEFT + i * slot + slot / 2, text: (periodLabelOf(r) ?? '').slice(5) }
    })
})

// ---- SKU 明细可视化:集中度 + Top15 横向条形 ----
const skuTotalQty = computed(() => skuRows.value.reduce((s, r) => s + Number(r.totalQty), 0))
const skuTop5Share = computed(() => {
  const top5 = skuRows.value.slice(0, 5).reduce((s, r) => s + Number(r.totalQty), 0)
  return sharePct(top5, skuTotalQty.value)
})
const skuTop15 = computed(() => skuRows.value.slice(0, 15))
const skuAxisMax = computed(() => Math.max(1, ...skuRows.value.map(r => Number(r.totalQty))))

// ---- 库存快照可视化:仓库聚合 + 构成堆叠条(可用/占用/在途) ----
interface WhAgg {
  id: number
  name: string
  available: number
  locked: number
  transit: number
}
const whRows = computed<WhAgg[]>(() => {
  const map = new Map<number, WhAgg>()
  snapshotRows.value.forEach(r => {
    const agg = map.get(r.warehouseId) ?? {
      id: r.warehouseId,
      name: r.whName ?? `仓库#${r.warehouseId}`,
      available: 0,
      locked: 0,
      transit: 0,
    }
    agg.available += Number(r.qtyAvailable)
    agg.locked += Number(r.qtyLocked)
    agg.transit += Number(r.qtyTransit)
    map.set(r.warehouseId, agg)
  })
  return [...map.values()]
})
const snapshotSkuCount = computed(() => new Set(snapshotRows.value.map(r => r.skuId)).size)
const snapshotSum = (key: 'qtyOnHand' | 'qtyLocked' | 'qtyTransit' | 'qtyAvailable') =>
  snapshotRows.value.reduce((s, r) => s + Number(r[key]), 0)
const whMax = computed(() => Math.max(1, ...whRows.value.map(wh => wh.available + wh.locked + wh.transit)))
const stackPct = (qty: number, part: 'left' | 'mid' | 'right') => {
  if (qty <= 0) {
    return '0%'
  }
  const pct = (qty / whMax.value) * 100
  // 中段(占用)左右各收 0.5% 留白,避免相邻段无缝粘连
  const adjusted = part === 'mid' ? Math.max(0.5, pct - 1) : Math.max(1, pct - 0.5)
  return `${adjusted}%`
}

const digestPeriodLabel = computed(
  () => ({ DAILY: '日报', WEEKLY: '周报', MONTHLY: '月报' })[digest.value?.period ?? digestPeriod.value]
)

onMounted(loadSales)
</script>
<style scoped lang="scss">
// 页面纵向内容(汇总卡+图表+表格)常超一屏:允许页面滚动,卡片不参与 flex 压缩(全局 .table-box 为 height:100% flex 列 + overflow:hidden)
.table-box {
  overflow-y: auto;
  :deep(.el-card) {
    flex-shrink: 0;
  }
}
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
.snapshot-empty {
  margin-bottom: 12px;
}
.summary-row {
  display: flex;
  flex-wrap: wrap;
  gap: 36px;
  align-items: center;
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
  gap: 6px;
  align-items: center;
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
  margin-left: 8px;
  border-top: 2px dashed var(--el-color-success);
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin: 0 2px 0 8px;
  border-radius: 50%;
}
.dot:first-child {
  margin-left: 0;
}
.dot-primary {
  background: var(--el-color-primary);
}
.dot-warning {
  background: var(--el-color-warning);
}
.dot-info {
  background: var(--el-color-info);
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
  color: var(--el-text-color-secondary);
  text-align: center;
}
.share-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}
.share-track {
  flex: 1;
  min-width: 60px;
  height: 6px;
  overflow: hidden;
  background: var(--el-fill-color);
  border-radius: 3px;
}
.share-fill {
  height: 100%;
  background: var(--el-color-primary);
  border-radius: 3px;
  transition: width 0.3s ease;
}
.share-text {
  width: 52px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
  text-align: right;
}
.hbar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}
.hbar-row {
  display: flex;
  gap: 12px;
  align-items: center;
}
.hbar-label {
  width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
.hbar-track,
.stack-track {
  display: flex;
  flex: 1;
  height: 16px;
  overflow: hidden;
  background: var(--el-fill-color);
  border-radius: 3px;
}
.hbar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
  border-radius: 3px 0 0 3px;
  transition: width 0.3s ease;
}
.stack-fill {
  height: 100%;
  transition: width 0.3s ease;
}
.hbar-value {
  width: 64px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  text-align: right;
}
.digest-box {
  padding: 4px 2px;
}
.digest-head {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.digest-title {
  font-size: 15px;
  font-weight: 600;
}
.digest-window {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.digest-content {
  padding: 14px 18px;
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
  word-break: break-all;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
}
</style>
