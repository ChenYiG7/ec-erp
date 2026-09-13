<!--
  盘点实盘录入/详情弹窗(生成器外定制组件,2026-09-11 仓内作业 TODO#30):
  mode=count(COUNTING 录实盘:逐行填实盘数量,允许多次补录/修正,后端即时算展示 diff)/
  mode=view(全状态只读:建单快照 vs 实盘 vs 差异;差异以生成调整时的确认时点 re-diff 为准)
-->

<template>
  <el-dialog v-model="visible" :title="title" width="820px" :close-on-click-modal="false" destroy-on-close>
    <el-descriptions :column="3" border size="small" class="count-dialog__head">
      <el-descriptions-item label="盘点单号">{{ order?.stocktakeNo }}</el-descriptions-item>
      <el-descriptions-item label="盘点仓">{{ warehouseLabel(order?.warehouseId) }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(order?.status) }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="items" border size="small" max-height="420">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column label="SKU" min-width="220">
        <template #default="{ row }">{{ skuLabel(row.skuId) }}</template>
      </el-table-column>
      <el-table-column prop="bookQty" label="账面快照" width="100" />
      <el-table-column label="实盘数量" width="170">
        <template #default="{ row }">
          <el-input-number
            v-if="editable"
            v-model="row.countedQty"
            :min="0"
            :precision="0"
            controls-position="right"
            class="!w-full"
            placeholder="未盘"
          />
          <span v-else>{{ row.countedQty ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="差异" width="90">
        <template #default="{ row }">
          <el-tag
            v-if="row.diffQty != null"
            :type="row.diffQty > 0 ? 'warning' : row.diffQty < 0 ? 'danger' : 'info'"
            size="small"
          >
            {{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="snapshotAt" label="快照时点" width="170" />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">{{ editable ? '取消' : '关闭' }}</el-button>
      <el-button v-if="editable" type="primary" :loading="submitting" @click="handleSubmit">保存实盘</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { computed, ref } from 'vue'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { stocktakeOrderApi } from '@/api/apis/inventory/stocktake'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type {
  StocktakeItemResponse,
  StocktakeOrderCountsRequest,
  StocktakeOrderResponse,
} from '@/api/interface/inventory/stocktake'

defineOptions({ name: 'StocktakeCountDialog' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const mode = ref<'count' | 'view'>('view')
const order = ref<StocktakeOrderResponse>()
const items = ref<StocktakeItemResponse[]>([])
// 仓库翻译(仅展示用;选项拉取失败不阻断弹窗,回落裸 ID)
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])

// 仅盘点中可编辑实盘(其余状态一律只读,即使误以 count 打开)
const editable = computed(() => mode.value === 'count' && order.value?.status === 'COUNTING')

const title = computed(() => (mode.value === 'count' ? '录入实盘' : '盘点单详情'))

const warehouseLabel = (id?: number) =>
  warehouseOptions.value.find(w => w.value === id)?.label ?? (id == null ? '-' : String(id))
const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  COUNTING: '盘点中',
  PENDING_ADJUST: '待调整',
  ADJUSTED: '已调整',
  CLOSED: '已关闭',
  CANCELED: '已取消',
}
const statusLabel = (status?: string) => (status ? (STATUS_LABELS[status] ?? status) : '-')

/** 打开弹窗(count=录实盘需 COUNTING 状态,view=只读详情);实盘行按 skuId 预填已有录入。
 * 先重置上一单数据再拉详情:详情接口慢/失败时弹窗不留残影(防串单展示) */
const open = async (row: StocktakeOrderResponse, m: 'count' | 'view') => {
  mode.value = m
  visible.value = true
  order.value = undefined
  items.value = []
  const detail = await stocktakeOrderApi.detail(row.id)
  order.value = detail
  items.value = detail.items ?? []
  fetchSkuNames(items.value.map(it => it.skuId))
  fetchWarehouseOptions()
    .then(opts => (warehouseOptions.value = opts))
    .catch(() => {})
}

const handleSubmit = async () => {
  // 逐行收集已录入实盘(允许分批补录:未填行不提交,后端只覆盖入参行)
  const lines = items.value
    .filter(it => it.countedQty != null)
    .map(it => ({ skuId: it.skuId, countedQty: it.countedQty! }))
  if (!lines.length) {
    ElMessage.warning('请至少录入一行实盘数量')
    return
  }
  submitting.value = true
  try {
    const payload = { lines } as StocktakeOrderCountsRequest
    await stocktakeOrderApi.counts(order.value!.id, payload)
    ElMessage.success('实盘已保存')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
