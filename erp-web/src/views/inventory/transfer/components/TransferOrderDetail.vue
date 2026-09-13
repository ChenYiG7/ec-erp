<!--
  调拨单详情弹窗(生成器外定制组件,2026-09-11 仓内作业 TODO#30):
  只读展示单头信息 + 明细行(V1 确认即达,确认后两腿动账凭证见库存流水 biz_type=TRANSFER_ORDER)
-->

<template>
  <el-dialog v-model="visible" title="调拨单详情" width="760px" :close-on-click-modal="true" destroy-on-close>
    <el-descriptions :column="3" border size="small">
      <el-descriptions-item label="调拨单号">{{ detail?.transferNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(detail?.status) }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ detail?.createdAt ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="调出仓">{{ warehouseLabel(detail?.fromWarehouseId) }}</el-descriptions-item>
      <el-descriptions-item label="调入仓">{{ warehouseLabel(detail?.toWarehouseId) }}</el-descriptions-item>
      <el-descriptions-item label="备注">{{ detail?.remark ?? '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="detail?.items ?? []" border size="small" class="transfer-detail__table">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column label="SKU" min-width="220">
        <template #default="{ row }">{{ skuLabel(row.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="调拨数量" width="110" />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElTable, ElTableColumn } from 'element-plus'
import { ref } from 'vue'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { transferOrderApi } from '@/api/apis/inventory/transfer'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { TransferOrderResponse } from '@/api/interface/inventory/transfer'

defineOptions({ name: 'TransferOrderDetail' })

const visible = ref(false)
const detail = ref<TransferOrderResponse>()
// 仓库翻译(仅展示用;选项拉取失败不阻断弹窗,回落裸 ID)
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  CANCELED: '已取消',
}
const statusLabel = (status?: string) => (status ? (STATUS_LABELS[status] ?? status) : '-')
const warehouseLabel = (id?: number) =>
  warehouseOptions.value.find(w => w.value === id)?.label ?? (id == null ? '-' : String(id))

/** 打开弹窗(取详情带明细;SKU 名批量预取翻译)。
 * 先重置上一单数据再拉详情:详情接口慢/失败时弹窗不留残影(防串单展示) */
const open = async (row: TransferOrderResponse) => {
  visible.value = true
  detail.value = undefined
  detail.value = await transferOrderApi.detail(row.id)
  fetchSkuNames((detail.value.items ?? []).map(it => it.skuId))
  fetchWarehouseOptions()
    .then(opts => (warehouseOptions.value = opts))
    .catch(() => {})
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.transfer-detail__table {
  width: 100%;
  margin-top: 12px;
}
</style>
