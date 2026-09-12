<template>
  <!-- TODO(#19) 周期详情对照(人工定制组件):订单口径 vs 结算口径双侧金额 + 校差;校差算法 TODO#32 落地前多列为空 -->
  <el-drawer v-model="visible" title="周期利润详情" size="640px">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="店铺ID">{{ detail.shopId }}</el-descriptions-item>
        <el-descriptions-item label="结算报告ID">{{ detail.settlementId }}</el-descriptions-item>
        <el-descriptions-item label="周期起">{{ detail.periodStart }}</el-descriptions-item>
        <el-descriptions-item label="周期止">{{ detail.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="结算币种">{{ detail.currency }}</el-descriptions-item>
        <el-descriptions-item label="折算汇率">
          {{ detail.rateUsed ?? '—' }}
          <el-tag v-if="detail.rateMissing === 1" type="danger" size="small">缺汇率</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          <el-tag v-if="detail.diffFlag === 1" type="danger" size="small" style="margin-left: 8px">校差超容差</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="detail" class="period-drawer__title">双侧对照(CNY;结算侧带报告原符号,收入正/费用负)</div>
      <el-table v-if="detail" :data="compareRows" size="small" border>
        <el-table-column prop="item" label="项目" min-width="140" />
        <el-table-column prop="order" label="订单口径" width="150" />
        <el-table-column prop="settle" label="结算口径" width="150" />
      </el-table>

      <el-descriptions v-if="detail" :column="1" border style="margin-top: 12px">
        <el-descriptions-item label="收入校差">{{ money(detail.diffIncome) }}</el-descriptions-item>
        <el-descriptions-item label="佣金校差">{{ money(detail.diffCommission) }}</el-descriptions-item>
        <el-descriptions-item label="校差说明">{{ detail.diffRemark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-profit-period-detail-drawer' })
import { computed, ref } from 'vue'
import { ElDescriptions, ElDescriptionsItem, ElDrawer, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { profitPeriodReportApi } from '@/api/apis/finance/profit-period'
import type { ProfitPeriodReportResponse } from '@/api/interface/finance/profit-period'

const visible = ref(false)
const loading = ref(false)
const detail = ref<ProfitPeriodReportResponse>()

const money = (v?: string | number | null) => (v === null || v === undefined || v === '' ? '—' : String(v))

const STATUS: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  OK: { label: '勾稽平', type: 'success' },
  DIFF: { label: '有差异', type: 'danger' },
  RATE_MISSING: { label: '缺汇率', type: 'warning' },
}
const statusLabel = (v: string) => STATUS[v]?.label ?? v
const statusType = (v: string) => STATUS[v]?.type ?? 'info'

// 对照行:收入/佣金/利润 订单侧有值;结算侧另有 FBA/其他费用
const compareRows = computed(() => {
  const d = detail.value
  if (!d) {
    return []
  }
  return [
    { item: '收入/回款', order: money(d.orderIncome), settle: money(d.settleIncome) },
    { item: '佣金', order: money(d.orderCommission), settle: money(d.settleCommission) },
    { item: 'FBA 系费用', order: '—', settle: money(d.fbaFee) },
    { item: '其他费用', order: '—', settle: money(d.otherFee) },
    { item: '利润(订单口径)', order: money(d.orderProfit), settle: '—' },
  ]
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = await profitPeriodReportApi.detail(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.period-drawer__title {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
