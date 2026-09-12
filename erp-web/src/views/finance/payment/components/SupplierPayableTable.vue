<template>
  <!-- TODO(#31) 供应商应付视图(查询面②):应付=Σ非草稿采购单总额,已付=ΣNORMAL 采购付款分摊,待付=差 -->
  <ProTable
    ref="proTableRef"
    page-id="/finance/payments/suppliers"
    title="供应商应付"
    :columns="columns"
    :request-api="paymentRecordApi.supplierParties"
  />
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-payment-supplier-table' })
import { ref } from 'vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { paymentRecordApi } from '@/api/apis/finance/payment'
import type { SupplierPayableRow } from '@/api/interface/finance/payment'

const proTableRef = ref<InstanceType<typeof ProTable>>()

const columns: ColumnProps<SupplierPayableRow>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'supplierName', label: '供应商', minWidth: 200 },
  { prop: 'settleDays', label: '账期(天)', width: 100 },
  { prop: 'payableAmount', label: '应付(CNY)', width: 150 },
  { prop: 'paidAmount', label: '已付(CNY)', width: 150 },
  { prop: 'unpaidAmount', label: '待付(CNY)', width: 150 },
]

defineExpose({ refresh: () => proTableRef.value?.getTableList() })
</script>
