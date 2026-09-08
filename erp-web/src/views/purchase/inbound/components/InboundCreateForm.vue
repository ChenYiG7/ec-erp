<template>
  <!-- TODO(#10) 建入库单表单(#10 人工扩展槽,后端接口已备):选可收采购单(AUDITED/PARTIAL_RECEIVED)
       → 拉明细(剩余 = quantity - arrivedQty)逐行录收货量 → 新增(PENDING);入库仓 = 采购单收货仓(服务端定);
       剩余量超收预校验在后端,前端仅做 ≤ 剩余的友好上限;PO 无状态过滤参数,仅取前 100 单客户端裁剪 -->
  <el-dialog v-model="visible" title="新建入库单" width="760px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="采购单" required>
        <el-select v-model="poId" placeholder="请选择已审核/部分入库的采购单" filterable :loading="poLoading" class="inbound-form__po" @change="onPoChange">
          <el-option v-for="po in poOptions" :key="po.value" :label="po.label" :value="po.value" />
        </el-select>
        <span class="inbound-form__tip">仅列最近 100 单中的可收采购单;入库仓随采购单收货仓(服务端回填)</span>
      </el-form-item>
      <el-form-item v-if="warehouseLabel" label="入库仓">
        <span>{{ warehouseLabel }}</span>
      </el-form-item>
      <el-form-item label="入库单号" required>
        <el-input v-model="inboundNo" maxlength="64" placeholder="手工录入,重复单号后端唯一键拦截" class="inbound-form__no" />
      </el-form-item>
      <el-form-item label="收货明细" required>
        <el-table v-loading="loading" :data="lines" size="small" border max-height="320">
          <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
            <template #default="{ row: line }">{{ skuLabel(line.skuId) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="采购数量" width="100" />
          <el-table-column prop="arrivedQty" label="已收" width="90" />
          <el-table-column prop="remaining" label="剩余" width="90" />
          <el-table-column label="本次入库" width="150">
            <template #default="{ row: line }">
              <el-input-number v-model="line.inboundQty" :min="0" :max="line.remaining" :controls="false" class="inbound-form__qty" />
            </template>
          </el-table-column>
        </el-table>
        <span class="inbound-form__tip">已收满的行不再列出;入库数量 0 的行不提交;超收由后端按剩余量拦截</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="255" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存(待入库)</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'purchase-inbound-create-form' })
import { ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import { purchaseInboundApi } from '@/api/apis/purchase/inbound'
import { purchaseOrderApi } from '@/api/apis/purchase/order'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { PurchaseOrderResponse } from '@/api/interface/purchase/order'

/** 可收行本地视图(来自采购单明细,剩余 = quantity - arrivedQty) */
interface InboundLine {
  poItemId: number
  skuId: number
  quantity: number
  arrivedQty: number
  remaining: number
  inboundQty: number
}

const emit = defineEmits<{ saved: [] }>()

const STATUS_LABELS: Record<string, string> = { AUDITED: '已审核', PARTIAL_RECEIVED: '部分入库' }
const RECEIVABLE = Object.keys(STATUS_LABELS)

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const poLoading = ref(false)
const poId = ref<number>()
const inboundNo = ref('')
const remark = ref('')
const poOptions = ref<{ label: string; value: number }[]>([])
const warehouseLabel = ref('')
const lines = ref<InboundLine[]>([])

const open = async () => {
  poId.value = undefined
  inboundNo.value = ''
  remark.value = ''
  warehouseLabel.value = ''
  lines.value = []
  visible.value = true
  // 拉采购单候选(Query 无状态过滤参数,客户端裁剪可收状态;一期人工低频,前 100 单够用)
  poLoading.value = true
  try {
    const { list } = await purchaseOrderApi.page({ pageNo: 1, pageSize: 100 })
    poOptions.value = list
      .filter((po: PurchaseOrderResponse) => RECEIVABLE.includes(po.status))
      .map(po => ({ label: `${po.poNo} · ${STATUS_LABELS[po.status]}`, value: po.id }))
  } finally {
    poLoading.value = false
  }
}

// 选中采购单 → 拉详情带明细,剩余行预填全收(可改);入库仓标签随单带出
const onPoChange = async (id: number) => {
  loading.value = true
  lines.value = []
  warehouseLabel.value = ''
  try {
    const [po, whOptions] = await Promise.all([purchaseOrderApi.detail(id), fetchWarehouseOptions()])
    warehouseLabel.value = whOptions.find(w => w.value === po.warehouseId)?.label ?? `仓库ID ${po.warehouseId}`
    lines.value = (po.items ?? [])
      .map(item => {
        const remaining = (item.quantity ?? 0) - (item.arrivedQty ?? 0)
        return { poItemId: item.id!, skuId: item.skuId!, quantity: item.quantity ?? 0, arrivedQty: item.arrivedQty ?? 0, remaining, inboundQty: remaining }
      })
      .filter(line => line.remaining > 0)
    if (!lines.value.length) {
      ElMessage.warning('该采购单明细已收满,无可收行')
    }
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(lines.value.map(l => l.skuId))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!poId.value) {
    ElMessage.warning('请选择采购单')
    return
  }
  if (!inboundNo.value.trim()) {
    ElMessage.warning('请录入入库单号')
    return
  }
  const items = lines.value
    .filter(line => line.inboundQty > 0)
    .map(line => ({ poItemId: line.poItemId, inboundQty: line.inboundQty }))
  if (!items.length) {
    ElMessage.warning('请至少录入一行入库数量大于 0 的明细')
    return
  }
  submitting.value = true
  try {
    await purchaseInboundApi.create({ inboundNo: inboundNo.value.trim(), poId: poId.value, remark: remark.value.trim() || undefined, items })
    ElMessage.success('入库单已创建(待入库),确认入库后写库存')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.inbound-form {
  &__po {
    width: 320px;
  }
  &__no {
    width: 320px;
  }
  &__qty {
    width: 120px;
  }
  &__tip {
    display: inline-block;
    width: 100%;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
