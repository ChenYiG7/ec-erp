<!--
  #31 资金流水页(gen:page 骨架 + 人工槽位):
  - 流水列表:方向/类型/往来方/期间/含作废筛选,期间汇总卡(CNY 口径,缺汇率计数不静默)
  - 采购付款登记/手工登记为定制对话框(一付多单分摊;非标准 CRUD 无生成器表单)
  - 作废仅 NORMAL(条件更新即守卫,后端 admin 双闸);详情抽屉看采购分摊
  - 往来方 tab:供应商应付/平台回款聚合视图
-->
<template>
  <el-tabs v-model="activeTab" class="payment-page">
    <el-tab-pane label="流水列表" name="list">
      <ProTable
        ref="proTableRef"
        page-id="/finance/payments"
        title="资金流水"
        :columns="columns"
        :request-api="loadPayments"
      >
        <template #toolbarLeft>
          <el-button v-auth="'finance:payment:purchase'" type="primary" :icon="Coin" @click="purchaseDialogRef?.open()">
            采购付款登记
          </el-button>
          <el-button v-auth="'finance:payment:manual'" type="success" :icon="Money" @click="manualDialogRef?.open()">
            手工登记
          </el-button>
        </template>
        <template #amountCny="{ row }">
          <span>{{ row.amountCny || '—' }}</span>
          <el-tag v-if="!row.amountCny" type="warning" size="small">折算缺失</el-tag>
        </template>
        <template #operation="{ row }">
          <el-button type="primary" link @click="detailDrawerRef?.open(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 'NORMAL'"
            v-auth="'finance:payment:void'"
            type="warning"
            link
            @click="onVoid(row)"
          >
            作废
          </el-button>
        </template>
      </ProTable>
      <el-row v-if="summary" :gutter="12" class="payment-page__summary">
        <el-col :span="6"
          ><el-card shadow="never"
            >期间回款(CNY)
            <div class="sum-val">{{ summary.incomeCny }}</div></el-card
          ></el-col
        >
        <el-col :span="6"
          ><el-card shadow="never"
            >期间付款(CNY)
            <div class="sum-val">{{ summary.expenseCny }}</div></el-card
          ></el-col
        >
        <el-col :span="6">
          <el-card shadow="never">
            净额(CNY)
            <div class="sum-val" :class="negativeNet ? 'sum-val--neg' : ''">{{ summary.netCny }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            流水 {{ summary.totalCount }} 笔
            <el-tag v-if="summary.missingRateCount > 0" type="warning" size="small" class="ml-4px">
              缺汇率 {{ summary.missingRateCount }} 笔
            </el-tag>
          </el-card>
        </el-col>
      </el-row>
    </el-tab-pane>
    <el-tab-pane label="供应商应付" name="supplier">
      <SupplierPayableTable ref="supplierTableRef" />
    </el-tab-pane>
    <el-tab-pane label="平台回款" name="platform">
      <PlatformReceiptTable ref="platformTableRef" />
    </el-tab-pane>
  </el-tabs>

  <PurchasePaymentDialog ref="purchaseDialogRef" @saved="onChanged" />
  <ManualPaymentDialog ref="manualDialogRef" @saved="onChanged" />
  <PaymentDetailDrawer ref="detailDrawerRef" />
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-payment-index' })
import { computed, ref } from 'vue'
import { Coin, Money } from '@element-plus/icons-vue'
import { ElButton, ElCard, ElCol, ElMessage, ElMessageBox, ElRow, ElTabPane, ElTabs, ElTag } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { paymentRecordApi } from '@/api/apis/finance/payment'
import type { PaymentRecordQuery, PaymentRecordResponse, PaymentSummaryResponse } from '@/api/interface/finance/payment'
import PurchasePaymentDialog from './components/PurchasePaymentDialog.vue'
import ManualPaymentDialog from './components/ManualPaymentDialog.vue'
import PaymentDetailDrawer from './components/PaymentDetailDrawer.vue'
import SupplierPayableTable from './components/SupplierPayableTable.vue'
import PlatformReceiptTable from './components/PlatformReceiptTable.vue'

const proTableRef = ref<InstanceType<typeof ProTable>>()
const purchaseDialogRef = ref<InstanceType<typeof PurchasePaymentDialog>>()
const manualDialogRef = ref<InstanceType<typeof ManualPaymentDialog>>()
const detailDrawerRef = ref<InstanceType<typeof PaymentDetailDrawer>>()
const supplierTableRef = ref<InstanceType<typeof SupplierPayableTable>>()
const platformTableRef = ref<InstanceType<typeof PlatformReceiptTable>>()

const activeTab = ref('list')
const summary = ref<PaymentSummaryResponse>()

const directionEnum = [
  { label: '付款', value: 'EXPENSE', tagType: 'danger' },
  { label: '回款', value: 'INCOME', tagType: 'success' },
]
const bizTypeEnum = [
  { label: '采购付款', value: 'PURCHASE_PAYMENT', tagType: 'warning' },
  { label: '结算回款', value: 'SETTLEMENT_RECEIPT', tagType: 'success' },
  { label: '手工调整', value: 'MANUAL_ADJUST', tagType: 'info' },
]
const partyTypeEnum = [
  { label: '供应商', value: 'SUPPLIER' },
  { label: '平台', value: 'PLATFORM' },
  { label: '其他', value: 'OTHER' },
]
const statusEnum = [
  { label: '正常', value: 'NORMAL', tagType: 'success' },
  { label: '已作废', value: 'VOIDED', tagType: 'danger' },
]

const columns: ColumnProps<PaymentRecordResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'paymentNo', label: '流水号', width: 170 },
  { prop: 'direction', label: '方向', width: 90, tag: true, enum: directionEnum, search: { el: 'select', order: 1 } },
  { prop: 'bizType', label: '类型', width: 120, tag: true, enum: bizTypeEnum, search: { el: 'select', order: 2 } },
  { prop: 'partyType', label: '往来方类型', width: 110, enum: partyTypeEnum, search: { el: 'select', order: 3 } },
  { prop: 'partyName', label: '往来方', minWidth: 160 },
  { prop: 'amount', label: '原币金额', width: 130 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'amountCny', label: '折算CNY', width: 140 },
  { prop: 'paidAt', label: '收付款时间', width: 170 },
  { prop: 'method', label: '结算方式', width: 110 },
  { prop: 'status', label: '状态', width: 100, tag: true, enum: statusEnum },
  { prop: 'remark', label: '备注', minWidth: 180, showOverflowTooltip: true },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  // 搜索-only 虚拟列:期间范围/含作废开关(isShow=false 不进表格,参数在 loadPayments 拆分)
  {
    prop: 'paidRange',
    label: '收付款期间',
    isShow: false,
    search: {
      el: 'date-picker',
      order: 4,
      attrs: { type: 'datetimerange', valueFormat: 'YYYY-MM-DD HH:mm:ss' },
    },
  },
  {
    prop: 'includeVoided',
    label: '含已作废',
    isShow: false,
    search: { el: 'switch', order: 5, defaultValue: false },
  },
  { prop: 'operation', label: '操作', fixed: 'right', width: 130 },
]

/** ProTable 数据源:拆期间范围虚拟字段 → paidFrom/paidTo;同参数拉期间汇总卡(汇总不阻断列表) */
const loadPayments = async (
  params: PaymentRecordQuery & { paidRange?: [string, string] | null; pageNo?: number; pageSize?: number }
) => {
  const { paidRange, ...rest } = params
  const query: PaymentRecordQuery = {
    ...rest,
    paidFrom: paidRange?.[0],
    paidTo: paidRange?.[1],
  }
  paymentRecordApi
    .summary({ paidFrom: query.paidFrom, paidTo: query.paidTo })
    .then(s => (summary.value = s))
    .catch(() => undefined)
  return paymentRecordApi.page(query)
}

// 净额负号判定走字符串前缀(金额计算禁 Number 参与,docs/09 §6;后端 BigDecimal 序列化为 number,String() 保前缀)
const negativeNet = computed(() => String(summary.value?.netCny ?? '').startsWith('-'))

const onVoid = async (row: PaymentRecordResponse) => {
  await ElMessageBox.confirm(
    `确认作废流水 ${row.paymentNo}?作废留痕不可删除,关联采购单的待付金额自动还原。`,
    '作废确认',
    { type: 'warning' }
  )
  await paymentRecordApi.void(row.id)
  ElMessage.success('流水已作废')
  onChanged()
}

/** 登记/作废后:刷流水与供应商应付;平台/汇总随下次查询刷新 */
const onChanged = () => {
  proTableRef.value?.getTableList()
  supplierTableRef.value?.refresh()
}
</script>
<style scoped lang="scss">
.payment-page {
  &__summary {
    margin-top: 12px;
  }
}
.sum-val {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 600;
  &--neg {
    color: var(--el-color-danger);
  }
}
</style>
