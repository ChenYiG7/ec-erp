<template>
  <!-- TODO(#12) 收退件复合表单:RETURNING→RETURN_RECEIVED,仓库ID + 逐行实收明细(仅 sku_id 已绑定订单行);
       超退预校验(历史累计+本次≤订单行数量)在后端,前端仅做 ≤ 订单行数量的友好上限;实收可≠平台申明 -->
  <el-dialog v-model="visible" title="收退件 + 退货入库" width="720px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="售后单号">
        <span>{{ row?.aftersaleNo }}</span>
      </el-form-item>
      <el-form-item label="入库仓" required>
        <el-select v-model="warehouseId" placeholder="请选择退货入库仓" filterable class="aftersale-rr__wh">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
        <span class="aftersale-rr__tip">仅列启用仓库(仓库存在性与动账校验在后端)</span>
      </el-form-item>
      <el-form-item label="实收明细" required>
        <el-table v-loading="loading" :data="lines" size="small" border max-height="320">
          <el-table-column prop="orderItemId" label="订单明细行ID" width="140" />
          <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
            <template #default="{ row: line }">{{ skuLabel(line.skuId) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="订单数量" width="100" />
          <el-table-column label="实收数量" width="150">
            <template #default="{ row: line }">
              <el-input-number
                v-model="line.returnQty"
                :min="0"
                :max="line.quantity"
                :controls="false"
                class="aftersale-rr__qty"
              />
            </template>
          </el-table-column>
        </el-table>
        <span class="aftersale-rr__tip"
          >仅列出 sku_id 已绑定的订单行(未绑定行不可退);实收数量 0 的行不提交;历史累计超退由后端拦截</span
        >
      </el-form-item>
      <el-form-item label="处理结果">
        <el-input
          v-model="result"
          type="textarea"
          :rows="2"
          maxlength="512"
          placeholder="选填,收退件留痕(COALESCE 追加)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认收退件</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'aftersale-receive-return-form' })
import { ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import { aftersaleOrderApi } from '@/api/apis/aftersale/order'
import { shopOrderApi } from '@/api/apis/order/order'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { AftersaleOrderResponse } from '@/api/interface/aftersale/order'

/** 可退行本地视图(来自订单明细,仅 sku_id 已绑定行) */
interface ReturnableLine {
  orderItemId: number
  skuId: number
  quantity: number
  returnQty: number
}

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const row = ref<AftersaleOrderResponse>()
const warehouseId = ref<number>()
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])
const result = ref('')
const lines = ref<ReturnableLine[]>([])

const open = async (data: AftersaleOrderResponse) => {
  row.value = data
  warehouseId.value = undefined
  result.value = ''
  lines.value = []
  visible.value = true
  loading.value = true
  try {
    // 并行拉仓库下拉选项 + 订单明细枚举可退行(仅 sku_id 已绑定行可退,#12 拍板);sku_id 服务端按订单行回填不入参
    const [whOptions, order] = await Promise.all([fetchWarehouseOptions(), shopOrderApi.detail(data.orderId)])
    warehouseOptions.value = whOptions
    lines.value = (order.items ?? [])
      .filter(item => item.skuId != null)
      .map(item => ({ orderItemId: item.id!, skuId: item.skuId!, quantity: item.quantity ?? 0, returnQty: 0 }))
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(lines.value.map(l => l.skuId))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!warehouseId.value) {
    ElMessage.warning('请选择入库仓')
    return
  }
  const items = lines.value
    .filter(line => line.returnQty > 0)
    .map(line => ({ orderItemId: line.orderItemId, returnQty: line.returnQty }))
  if (!items.length) {
    ElMessage.warning('请至少录入一行实收数量大于 0 的明细')
    return
  }
  submitting.value = true
  try {
    await aftersaleOrderApi.receiveReturn(row.value!.id, {
      warehouseId: warehouseId.value,
      items,
      result: result.value.trim() || undefined,
    })
    ElMessage.success('收退件成功')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.aftersale-rr {
  &__wh {
    width: 240px;
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
