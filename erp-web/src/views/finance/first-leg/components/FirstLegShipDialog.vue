<!--
  确认发货并录运费弹窗(生成器外定制,#33):BOXED→SHIPPED 同动作录运费并冻结汇率。
  金额 string 直存(docs/09 §6);汇率留空时后端按发货日回溯 resolveRate,非 CNY 无报价后端拦截(禁猜);
  手填汇率 >0 优先;CNY 短路为 1。
-->

<template>
  <el-dialog v-model="visible" title="确认发货 · 录运费" width="640px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="头程单号">
        <span>{{ shipment?.shipmentNo }}</span>
      </el-form-item>
      <el-form-item label="物流商" prop="carrier">
        <el-input v-model="formData.carrier" placeholder="如:云途/海运货代(可空)" clearable />
      </el-form-item>
      <el-form-item label="运单号" prop="waybillNo">
        <el-input v-model="formData.waybillNo" placeholder="物流商运单号(可空)" clearable />
      </el-form-item>
      <el-form-item label="计费重 kg" prop="chargeWeight">
        <el-input v-model="formData.chargeWeight" type="number" placeholder="计费重(可空)" clearable />
      </el-form-item>
      <el-form-item label="体积重 kg" prop="volumeWeight">
        <el-input v-model="formData.volumeWeight" type="number" placeholder="体积重(可空)" clearable />
      </el-form-item>
      <el-form-item label="运费金额" prop="freightAmount">
        <el-input v-model="formData.freightAmount" type="number" placeholder="运费(必须大于0)">
          <template #append>
            <el-select v-model="formData.currency" style="width: 90px">
              <el-option label="CNY" value="CNY" />
              <el-option label="USD" value="USD" />
              <el-option label="EUR" value="EUR" />
              <el-option label="GBP" value="GBP" />
              <el-option label="JPY" value="JPY" />
            </el-select>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="汇率(手填)" prop="exchangeRate">
        <el-input
          v-model="formData.exchangeRate"
          type="number"
          placeholder="1 外币=? CNY;留空按发货日取汇率快照"
          clearable
        />
        <div class="fl-ship__hint">
          非 CNY 必填过汇率:要么此前已维护当日汇率快照,要么在此手填;无报价将被拦截(禁猜)。
        </div>
      </el-form-item>
      <el-form-item label="发货时间" prop="shippedAt">
        <el-date-picker
          v-model="formData.shippedAt"
          type="datetime"
          placeholder="留空=当前时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="!w-full"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认发货</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { firstLegShipmentApi } from '@/api/apis/finance/firstLeg'
import type { FirstLegShipmentResponse, FirstLegShipmentShipRequest } from '@/api/interface/finance/firstLeg'

defineOptions({ name: 'FirstLegShipDialog' })

const emit = defineEmits<{ shipped: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const shipment = ref<FirstLegShipmentResponse>()
const formData = ref({
  carrier: '',
  waybillNo: '',
  chargeWeight: '',
  volumeWeight: '',
  freightAmount: '',
  currency: 'CNY',
  exchangeRate: '',
  shippedAt: '',
})

const rules: FormRules = {
  freightAmount: [{ required: true, message: '请输入运费金额', trigger: 'blur' }],
}

/** 打开弹窗(仅 BOXED 状态行入口可达,后端 cas 兜底) */
const open = (row: FirstLegShipmentResponse) => {
  shipment.value = row
  formData.value = {
    carrier: row.carrier ?? '',
    waybillNo: row.waybillNo ?? '',
    chargeWeight: row.chargeWeight != null ? String(row.chargeWeight) : '',
    volumeWeight: row.volumeWeight != null ? String(row.volumeWeight) : '',
    freightAmount: '',
    currency: 'CNY',
    exchangeRate: '',
    shippedAt: '',
  }
  visible.value = true
}

/** 正数校验(仅用于表单拦截,不参与金额计算;金额以 string 直传后端 BigDecimal) */
const positive = (v: string) => v.trim() !== '' && Number(v) > 0

const handleSubmit = async () => {
  await formRef.value?.validate()
  if (!positive(formData.value.freightAmount)) {
    ElMessage.warning('运费金额必须大于 0')
    return
  }
  if (formData.value.exchangeRate.trim() !== '' && !positive(formData.value.exchangeRate)) {
    ElMessage.warning('手填汇率必须大于 0')
    return
  }
  for (const w of [formData.value.chargeWeight, formData.value.volumeWeight]) {
    if (w.trim() !== '' && Number(w) < 0) {
      ElMessage.warning('计费重/体积重不能为负')
      return
    }
  }
  submitting.value = true
  try {
    const payload: FirstLegShipmentShipRequest = {
      freightAmount: formData.value.freightAmount.trim(),
      currency: formData.value.currency,
      carrier: formData.value.carrier.trim() || undefined,
      waybillNo: formData.value.waybillNo.trim() || undefined,
      chargeWeight: formData.value.chargeWeight.trim() ? Number(formData.value.chargeWeight.trim()) : undefined,
      volumeWeight: formData.value.volumeWeight.trim() ? Number(formData.value.volumeWeight.trim()) : undefined,
      exchangeRate: formData.value.exchangeRate.trim() ? Number(formData.value.exchangeRate.trim()) : undefined,
      shippedAt: formData.value.shippedAt || undefined,
    }
    await firstLegShipmentApi.ship(shipment.value!.id, payload)
    ElMessage.success('已发货,运费与汇率已冻结')
    emit('shipped')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.fl-ship__hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
