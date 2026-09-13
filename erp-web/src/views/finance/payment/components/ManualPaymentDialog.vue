<template>
  <!-- TODO(#31) 手工资金登记(人工定制组件):补录派生遗漏/非结算回款/其他收付款,biz_type 服务端固定 MANUAL_ADJUST;
       非 CNY 按 paidAt 回溯 resolveRate,无汇率报价时 CNY 列留空(列表"折算缺失"标记,缺口不静默) -->
  <el-dialog v-model="visible" title="手工资金登记" width="560px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="92px">
      <el-form-item label="资金方向" required>
        <el-radio-group v-model="direction">
          <el-radio value="INCOME">回款(流入)</el-radio>
          <el-radio value="EXPENSE">付款(流出)</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="往来方" required>
        <el-select v-model="partyType" class="manual-dialog__type" @change="onPartyTypeChange">
          <el-option label="供应商" value="SUPPLIER" />
          <el-option label="平台(店铺)" value="PLATFORM" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-input-number
          v-if="partyType !== 'OTHER'"
          v-model="partyId"
          :min="1"
          :controls="false"
          placeholder="往来方ID"
          class="manual-dialog__id"
        />
      </el-form-item>
      <el-form-item label="金额" required>
        <el-input v-model="amount" placeholder="原币金额,如 100.0000" class="manual-dialog__amount">
          <template #append>
            <el-input v-model="currency" maxlength="3" class="manual-dialog__currency" placeholder="CNY" />
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="收付款时间">
        <el-date-picker
          v-model="paidAt"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="空=当前时间(汇率回溯锚点)"
          class="manual-dialog__date"
        />
      </el-form-item>
      <el-form-item label="结算方式">
        <el-select v-model="method" placeholder="请选择" clearable class="manual-dialog__method">
          <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="255" placeholder="选填,建议写明登记原因" />
      </el-form-item>
      <div class="manual-dialog__tip">
        非 CNY 币种按收付款时间回溯汇率快照折算;该时点无报价则 CNY 列为空,不计入期间汇总。
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认登记</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-payment-manual-dialog' })

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { paymentRecordApi } from '@/api/apis/finance/payment'

const METHODS = ['银行转账', '支付宝', '微信', '平台打款', '承兑汇票', '现金', '其他']

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const direction = ref('INCOME')
const partyType = ref('PLATFORM')
const partyId = ref<number>()
const amount = ref('')
const currency = ref('CNY')
const paidAt = ref<Date | string>('')
const method = ref('')
const remark = ref('')

const onPartyTypeChange = () => {
  partyId.value = undefined
}

const open = () => {
  direction.value = 'INCOME'
  partyType.value = 'PLATFORM'
  partyId.value = undefined
  amount.value = ''
  currency.value = 'CNY'
  paidAt.value = ''
  method.value = ''
  remark.value = ''
  visible.value = true
}

const isPositiveAmount = (v: string) => /^\d+(\.\d{1,4})?$/.test(v.trim()) && v.trim() !== '0'

const submit = async () => {
  if (!isPositiveAmount(amount.value)) {
    ElMessage.warning('请录入正确的金额(数字,最多 4 位小数)')
    return
  }
  if (partyType.value !== 'OTHER' && !partyId.value) {
    ElMessage.warning('供应商/平台往来方必须填写 ID')
    return
  }
  // 币种大写归一 + ISO 4217 三字母约束:小写/乱串会使回溯汇率解析失败留缺口
  const cur = currency.value.trim().toUpperCase() || 'CNY'
  if (!/^[A-Z]{3}$/.test(cur)) {
    ElMessage.warning('币种请填 3 位字母 ISO 代码(如 CNY/USD)')
    return
  }
  submitting.value = true
  try {
    await paymentRecordApi.registerManual({
      direction: direction.value,
      partyType: partyType.value,
      partyId: partyType.value === 'OTHER' ? undefined : partyId.value,
      amount: amount.value.trim(),
      currency: cur,
      paidAt: paidAt.value ? String(paidAt.value) : undefined,
      method: method.value || undefined,
      remark: remark.value.trim() || undefined,
    })
    ElMessage.success('手工资金已登记')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.manual-dialog {
  &__type {
    width: 150px;
    margin-right: 8px;
  }
  &__id {
    width: 200px;
  }
  &__amount {
    width: 300px;
  }
  &__currency {
    width: 80px;
  }
  &__date {
    width: 300px;
  }
  &__method {
    width: 200px;
  }
  &__tip {
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}
</style>
