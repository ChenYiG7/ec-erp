<template>
  <!-- TODO(#31) 平台回款视图(查询面②):按店铺聚合期间 INCOME/PLATFORM 流水;
       非分页(店铺数有限),原币跨币种仅混合参考,精确口径看 CNY 列,缺汇率行计数不静默 -->
  <div class="card">
    <el-form :inline="true" class="platform-table__filter">
      <el-form-item label="收付款期间">
        <el-date-picker
          v-model="range"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column type="index" label="#" width="55" />
      <el-table-column prop="shopName" label="店铺" min-width="180">
        <template #default="{ row }">{{ row.shopName || `店铺ID ${row.shopId}` }}</template>
      </el-table-column>
      <el-table-column prop="receiptCount" label="回款笔数" width="100" />
      <el-table-column prop="amount" label="原币合计(混合参考)" width="170" />
      <el-table-column label="折合 CNY" width="170">
        <template #default="{ row }">
          {{ row.amountCny }}
          <el-tag v-if="row.missingRateCount > 0" type="warning" size="small">
            缺汇率 {{ row.missingRateCount }} 笔
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-payment-platform-table' })
import { onMounted, ref } from 'vue'
import { ElButton, ElDatePicker, ElForm, ElFormItem, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { paymentRecordApi } from '@/api/apis/finance/payment'
import type { PlatformReceiptRow } from '@/api/interface/finance/payment'

const loading = ref(false)
const rows = ref<PlatformReceiptRow[]>([])
const range = ref<[string, string] | null>(null)

const load = async () => {
  loading.value = true
  try {
    rows.value = await paymentRecordApi.platformParties({
      paidFrom: range.value?.[0],
      paidTo: range.value?.[1],
    })
  } finally {
    loading.value = false
  }
}

defineExpose({ refresh: load })
onMounted(load)
</script>
<style scoped lang="scss">
.platform-table__filter {
  margin-bottom: 4px;
}
</style>
