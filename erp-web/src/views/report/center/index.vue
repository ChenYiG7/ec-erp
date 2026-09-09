<!--
  报表中心页(#20 报表域 V1,手写页):销售面(日报/周报/SKU明细三 tab)+ 库存快照面 + Excel 导出;
  经营简报 tab(#23 智能报表 V1,四期 BI):周期预览,定时推送在后端 Job 走 #14 出口(站内+邮箱+Webhook)
  数据面 = order_sales_daily / inventory_snapshot_daily(系统已就位的两个日快照表,零 DDL 纯读侧);
  快照不可回溯(docs/03 §7.2),日期缺省=最新快照日;导出 xlsx 走 blob 下载
-->

<template>
  <div class="table-box">
    <el-card shadow="never" class="filter-card">
      <el-tabs v-model="activeTab">
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
    <el-card v-show="activeTab === 'daily' || activeTab === 'weekly'" shadow="never">
      <el-table :data="periodRows" stripe v-loading="loading">
        <el-table-column :prop="activeTab === 'daily' ? 'statDate' : 'weekStart'" label="统计周期" width="140" />
        <el-table-column prop="totalQty" label="销量合计(件)" width="140" />
        <el-table-column prop="skuCount" label="有销量SKU数" width="140" />
      </el-table>
    </el-card>

    <!-- SKU 明细 -->
    <el-card v-show="activeTab === 'sku'" shadow="never">
      <el-table :data="skuRows" stripe v-loading="loading">
        <el-table-column type="index" label="#" width="55" />
        <el-table-column label="SKU编码" width="160">
          <template #default="{ row }">{{ row.skuCode ?? row.skuId }}</template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="totalQty" label="销量合计(件)" width="140" />
      </el-table>
    </el-card>

    <!-- 库存快照 -->
    <el-card v-show="activeTab === 'snapshot'" shadow="never">
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

    <!-- 经营简报(#23):预览三周期简报文本;定时推送/渠道开关由后端配置,页面只读不推送 -->
    <el-card v-show="activeTab === 'digest'" shadow="never">
      <el-alert
        title="简报由定时任务自动推送(站内通知+邮箱+Webhook,推送开关 erp.report.digest.enabled 默认关,渠道各自开关控制);本页预览为纯读侧零副作用"
        type="info"
        :closable="false"
        class="snapshot-empty"
      />
      <div v-if="digest" class="digest-box">
        <div class="digest-title">{{ digest.title }}</div>
        <pre class="digest-content">{{ digest.content }}</pre>
      </div>
      <el-empty v-else-if="!loading" description="选择周期后点击「预览」生成简报" :image-size="80" />
    </el-card>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'report-center-index' })
import { onMounted, ref } from 'vue'
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
} from 'element-plus'
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

const loadSales = async () => {
  loading.value = true
  try {
    if (activeTab.value === 'daily') {
      periodRows.value = await reportApi.salesDaily(windowParams())
    } else if (activeTab.value === 'weekly') {
      periodRows.value = await reportApi.salesWeekly(windowParams())
    } else {
      skuRows.value = await reportApi.salesSku({ ...windowParams(), limit: 200 })
    }
  } finally {
    loading.value = false
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

onMounted(loadSales)
</script>
<style scoped lang="scss">
.filter-card {
  margin-bottom: 12px;
  :deep(.el-card__body) {
    padding: 14px 20px 0;
  }
}
.snapshot-empty {
  margin-bottom: 12px;
}
.digest-box {
  padding: 4px 2px;
}
.digest-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}
.digest-content {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-regular);
}
</style>
