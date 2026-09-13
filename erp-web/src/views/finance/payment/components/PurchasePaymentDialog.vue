<template>
  <!-- TODO(#31) 采购付款登记(人工定制组件,非标准 CRUD):一笔付款分摊多张已审核采购单(须同一供应商)。
       守卫在后端(未审核不可付/按单已付+本次≤总额/Σ分摊≤付款额允许部分挂账/超额拦截),
       前端只做空值与正数友好校验,金额全程 string 禁 parseFloat(docs/09 §6,精确口径后端判) -->
  <el-dialog v-model="visible" title="采购付款登记" width="720px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="92px">
      <el-form-item label="付款金额" required>
        <el-input v-model="amount" placeholder="CNY 金额,如 10000.0000" class="pay-dialog__amount">
          <template #append>CNY</template>
        </el-input>
      </el-form-item>
      <el-form-item label="收付款时间">
        <el-date-picker
          v-model="paidAt"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="空=当前时间"
          class="pay-dialog__date"
        />
      </el-form-item>
      <el-form-item label="结算方式">
        <el-select v-model="method" placeholder="请选择" clearable class="pay-dialog__method">
          <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购单分摊" required>
        <el-table :data="allocs" size="small" border max-height="300">
          <el-table-column label="采购单(仅已审核)" min-width="260">
            <template #default="{ row, $index }">
              <el-select
                v-model="row.poId"
                placeholder="选择采购单"
                filterable
                :loading="poLoading"
                class="pay-dialog__po"
                @change="(v: number) => onPoChange($index, v)"
              >
                <el-option
                  v-for="po in poOptions"
                  :key="po.value"
                  :label="po.label"
                  :value="po.value"
                  :disabled="allocs.some((a, i) => i !== $index && a.poId === po.value)"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="本次分摊(CNY)" width="190">
            <template #default="{ row }">
              <el-input v-model="row.amount" placeholder="如 6000.0000" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button type="danger" link :disabled="allocs.length <= 1" @click="removeRow($index)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="mt-8px" type="primary" link :icon="CirclePlus" @click="addRow">添加分摊行</el-button>
        <div class="pay-dialog__tip">
          一付多单必须属于同一供应商;同一采购单不可重复行;分摊合计 ≤ 付款金额(差额挂账);未审核/超额由后端拦截
        </div>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="255" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认登记</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
defineOptions({ name: 'finance-payment-purchase-dialog' })

import { CirclePlus } from '@element-plus/icons-vue'
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
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { ref } from 'vue'
import { paymentRecordApi } from '@/api/apis/finance/payment'
import { purchaseOrderApi } from '@/api/apis/purchase/order'
import type { PurchasePaymentAlloc } from '@/api/interface/finance/payment'
import type { PurchaseOrderResponse } from '@/api/interface/purchase/order'

const METHODS = ['银行转账', '支付宝', '微信', '承兑汇票', '现金', '其他']
// 可付款状态 = 非 DRAFT(服务端守卫同源词面)
const PAYABLE_STATUSES = ['AUDITED', 'PARTIAL_RECEIVED', 'RECEIVED', 'CLOSED']
const STATUS_LABELS: Record<string, string> = {
  AUDITED: '已审核',
  PARTIAL_RECEIVED: '部分入库',
  RECEIVED: '已入库',
  CLOSED: '已关闭',
}

interface AllocRow extends PurchasePaymentAlloc {}

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const poLoading = ref(false)
const amount = ref('')
const paidAt = ref<Date | string>('')
const method = ref('')
const remark = ref('')
const allocs = ref<AllocRow[]>([{ poId: 0, amount: '' }])
const poOptions = ref<{ label: string; value: number }[]>([])
const poMap = ref<Map<number, PurchaseOrderResponse>>(new Map())

const addRow = () => allocs.value.push({ poId: 0, amount: '' })
const removeRow = (index: number) => allocs.value.splice(index, 1)

const onPoChange = (index: number, poId: number) => {
  allocs.value[index].poId = poId
}

const open = async (preselectPoId?: number) => {
  amount.value = ''
  paidAt.value = ''
  method.value = ''
  remark.value = ''
  allocs.value = preselectPoId ? [{ poId: preselectPoId, amount: '' }] : [{ poId: 0, amount: '' }]
  visible.value = true
  poLoading.value = true
  try {
    // Query 无状态过滤参数,取最近 200 单客户端裁剪可付款状态(登记低频,V1 够用)
    const { list } = await purchaseOrderApi.page({ pageNo: 1, pageSize: 200 })
    const payable = list.filter((po: PurchaseOrderResponse) => PAYABLE_STATUSES.includes(po.status))
    poMap.value = new Map(payable.map(po => [po.id, po]))
    poOptions.value = payable.map(po => ({
      label: `${po.poNo} · ${STATUS_LABELS[po.status] ?? po.status} · 总额 ${po.totalAmount}`,
      value: po.id,
    }))
  } finally {
    poLoading.value = false
  }
}

const isPositiveAmount = (v: string) => /^\d+(\.\d{1,4})?$/.test(v.trim()) && !(v.trim() === '0')

const submit = async () => {
  if (!isPositiveAmount(amount.value)) {
    ElMessage.warning('请录入正确的付款金额(数字,最多 4 位小数)')
    return
  }
  const lines = allocs.value.filter(a => a.poId || a.amount.trim() !== '')
  if (!lines.length || lines.some(a => !a.poId || !isPositiveAmount(a.amount))) {
    ElMessage.warning('请补全每一行分摊(采购单 + 大于 0 的金额)')
    return
  }
  const suppliers = new Set(lines.map(a => poMap.value.get(a.poId)?.supplierId))
  if (suppliers.size > 1) {
    ElMessage.warning('一笔付款的分摊采购单必须属于同一供应商')
    return
  }
  submitting.value = true
  try {
    await paymentRecordApi.registerPurchase({
      amount: amount.value.trim(),
      paidAt: paidAt.value ? String(paidAt.value) : undefined,
      method: method.value || undefined,
      remark: remark.value.trim() || undefined,
      allocs: lines.map(a => ({ poId: a.poId, amount: a.amount.trim() })),
    })
    ElMessage.success('采购付款已登记')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.pay-dialog {
  &__amount {
    width: 260px;
  }
  &__date {
    width: 260px;
  }
  &__method {
    width: 200px;
  }
  &__po {
    width: 100%;
  }
  &__tip {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}
</style>
