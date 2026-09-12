<template>
  <!-- TODO(#31) 流水详情(人工定制组件):主体字段 + 采购分摊行审计;结算回款/手工流水无分摊 -->
  <el-drawer v-model="visible" title="资金流水详情" size="520px">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="流水号">{{ detail.payment.paymentNo }}</el-descriptions-item>
        <el-descriptions-item label="方向/类型">
          {{ directionLabel(detail.payment.direction) }} / {{ bizTypeLabel(detail.payment.bizType) }}
        </el-descriptions-item>
        <el-descriptions-item label="往来方">
          {{ detail.payment.partyName || partyTypeLabel(detail.payment.partyType) }}
        </el-descriptions-item>
        <el-descriptions-item label="金额">
          {{ detail.payment.amount }} {{ detail.payment.currency }}
          <span v-if="detail.payment.amountCny">（≈ {{ detail.payment.amountCny }} CNY）</span>
          <el-tag v-else type="warning" size="small">折算缺失</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="收付款时间">{{ detail.payment.paidAt }}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{ detail.payment.method || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.payment.status === 'NORMAL' ? 'success' : 'danger'">
            {{ detail.payment.status === 'NORMAL' ? '正常' : '已作废' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.payment.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="detail?.allocs?.length">
        <div class="detail-drawer__title">采购单分摊</div>
        <el-table :data="detail.allocs" size="small" border>
          <el-table-column prop="poNo" label="采购单号" min-width="180" />
          <el-table-column prop="amount" label="分摊金额(CNY)" width="160" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-payment-detail-drawer' })
import { ref } from 'vue'
import { ElDescriptions, ElDescriptionsItem, ElDrawer, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { paymentRecordApi } from '@/api/apis/finance/payment'
import type { PaymentDetailResponse } from '@/api/interface/finance/payment'

const visible = ref(false)
const loading = ref(false)
const detail = ref<PaymentDetailResponse>()

const DIRECTION: Record<string, string> = { EXPENSE: '付款', INCOME: '回款' }
const BIZ: Record<string, string> = {
  PURCHASE_PAYMENT: '采购付款',
  SETTLEMENT_RECEIPT: '结算回款',
  MANUAL_ADJUST: '手工调整',
}
const PARTY: Record<string, string> = { SUPPLIER: '供应商', PLATFORM: '平台', OTHER: '其他' }
const directionLabel = (v: string) => DIRECTION[v] ?? v
const bizTypeLabel = (v: string) => BIZ[v] ?? v
const partyTypeLabel = (v: string) => PARTY[v] ?? v

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = await paymentRecordApi.detail(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.detail-drawer__title {
  margin: 16px 0 8px;
  font-weight: 600;
}
</style>
