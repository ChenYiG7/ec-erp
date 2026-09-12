<!--
  FBA发货单收货登记弹窗(fba-shipment,生成器外定制):按 SKU 录平台收货数量。
  行集 = 发出明细(Σ箱内件逐 SKU)+ 既有 diff 行(RECEIVING 重复登记时回填平台收货量,含计划外 EXTRA 行);
  发出 SKU 必须全部在列(未登记显式填 0=SHORT,后端守卫缺行即拦);提交后生成/覆盖 SHORT/EXTRA/OK diff。
-->

<template>
  <el-dialog
    v-model="visible"
    :title="`收货登记 · ${row?.shipmentNo ?? ''}`"
    width="640px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-alert
      type="info"
      :closable="false"
      class="fba-recv__tip"
      title="按平台实际收货数量逐 SKU 录入;少于发出量生成 SHORT(缺收),计划外多出生成 EXTRA(多收),相等为 OK。"
    />
    <el-table :data="rows" size="small" border max-height="420">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="skuCode" label="SKU 编码" min-width="200">
        <template #default="{ row }">{{ row.skuCode || row.skuId }}</template>
      </el-table-column>
      <el-table-column prop="shippedQty" label="发出量" width="90" />
      <el-table-column label="平台收货量" width="160">
        <template #default="{ row }">
          <el-input-number
            v-model="row.receivedQty"
            :min="0"
            :precision="0"
            controls-position="right"
            class="!w-full"
          />
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交登记</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElInputNumber, ElMessage, ElTable, ElTableColumn } from 'element-plus'
import { fbaShipmentApi } from '@/api/apis/fulfill/fbaShipment'
import type { FbaShipmentResponse } from '@/api/interface/fulfill/fbaShipment'

defineOptions({ name: 'FbaReceiveDialog' })

const emit = defineEmits<{ registered: [] }>()

const visible = ref(false)
const submitting = ref(false)
const row = ref<FbaShipmentResponse>()
/** 登记行:发出量来自 Σ箱内件;receivedQty 预填既有 diff 值(重复登记)否则 0 */
const rows = ref<{ skuId: number; skuCode?: string; shippedQty: number; receivedQty: number }[]>([])

const open = async (r: FbaShipmentResponse) => {
  row.value = r
  rows.value = []
  visible.value = true
  const detail = await fbaShipmentApi.detail(r.id)
  // Σ箱内件逐 SKU = 发出量(与后端勾稽同口径)
  const shipped = new Map<number, number>()
  for (const box of detail.boxes ?? []) {
    for (const it of box.items ?? []) {
      shipped.set(it.skuId, (shipped.get(it.skuId) ?? 0) + (it.quantity ?? 0))
    }
  }
  const codeBySku = new Map<number, string>()
  for (const box of detail.boxes ?? []) {
    for (const it of box.items ?? []) {
      codeBySku.set(it.skuId, it.skuCode)
    }
  }
  // 既有 diff 行:预填平台收货量(重复登记覆盖场景,含计划外 SKU)
  const received = new Map<number, number>()
  const codeByDiff = new Map<number, string>()
  for (const d of detail.diffs ?? []) {
    received.set(d.skuId, d.receivedQty ?? 0)
    codeByDiff.set(d.skuId, d.skuCode)
  }
  const skuIds = new Set<number>([...shipped.keys(), ...received.keys()])
  rows.value = [...skuIds]
    .sort((a, b) => a - b)
    .map(skuId => ({
      skuId,
      skuCode: codeBySku.get(skuId) ?? codeByDiff.get(skuId),
      shippedQty: shipped.get(skuId) ?? 0,
      receivedQty: received.get(skuId) ?? 0,
    }))
}

const handleSubmit = async () => {
  if (!rows.value.length) {
    ElMessage.warning('无可登记行(装箱明细缺失)')
    return
  }
  if (rows.value.some(r => r.receivedQty == null || r.receivedQty < 0)) {
    ElMessage.warning('平台收货量必须 >= 0(未收到请填 0)')
    return
  }
  submitting.value = true
  try {
    await fbaShipmentApi.receive(row.value!.id, {
      items: rows.value.map(r => ({ skuId: r.skuId, receivedQty: r.receivedQty })),
    })
    ElMessage.success('登记成功,可在详情查看 SHORT/EXTRA/OK 差异行')
    emit('registered')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.fba-recv__tip {
  margin-bottom: 10px;
}
</style>
