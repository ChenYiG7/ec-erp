<template>
  <!-- TODO(#11) 建发货单表单(#11 人工扩展槽,后端接口已备):选 WAIT_SHIP 订单(客户端裁 SELF_FULFILL)
       → 拉订单明细(仅 sku_id 已绑定行)逐行录发货量 → 新增(PENDING);出库仓必选(防幻影库存,存在性在后端);
       跨发货单累计超发预校验在后端(Σ 非 CANCELLED 明细 ≤ 订单行数量),前端仅做 ≤ 订单行数量的友好上限 -->
  <el-dialog v-model="visible" title="新建发货单" width="760px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="订单" required>
        <el-select
          v-model="orderId"
          placeholder="请选择待发货订单"
          filterable
          :loading="orderLoading"
          class="delivery-form__order"
          @change="onOrderChange"
        >
          <el-option v-for="o in orderOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <span class="delivery-form__tip"
          >仅列最近 100 单中的 WAIT_SHIP 订单,自发货(SELF_FULFILL)可建;FBA/海外仓平台履约不产生系统发货单</span
        >
      </el-form-item>
      <el-form-item label="出库仓" required>
        <el-select v-model="warehouseId" placeholder="请选择出库仓" filterable class="delivery-form__wh">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
        <span class="delivery-form__tip"
          >建单即从该仓占用库存(可用减少,缺货建单即拦);确认发货核销出库,取消/删除自动释放</span
        >
      </el-form-item>
      <el-form-item label="发货单号" required>
        <el-input
          v-model="deliveryNo"
          maxlength="64"
          placeholder="手工录入,重复单号后端唯一键拦截"
          class="delivery-form__no"
        />
      </el-form-item>
      <el-form-item label="发货明细" required>
        <el-table v-loading="loading" :data="lines" size="small" border max-height="320">
          <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
            <template #default="{ row: line }">{{ skuLabel(line.skuId) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="订单数量" width="100" />
          <el-table-column label="本次发货" width="150">
            <template #default="{ row: line }">
              <el-input-number
                v-model="line.shipQty"
                :min="0"
                :max="line.quantity"
                :controls="false"
                class="delivery-form__qty"
              />
            </template>
          </el-table-column>
        </el-table>
        <span class="delivery-form__tip"
          >仅列出 sku_id 已绑定的订单行(未绑定行不参与发货与发足判定);发货数量 0 的行不提交;历史累计超发由后端拦截</span
        >
      </el-form-item>
      <el-form-item label="物流公司">
        <el-input v-model="logisticsCompany" maxlength="64" placeholder="选填,可发货时后补" class="delivery-form__no" />
      </el-form-item>
      <el-form-item label="运单号">
        <el-input v-model="trackingNo" maxlength="64" placeholder="选填,可发货时后补" class="delivery-form__no" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存(待发货)</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'fulfill-delivery-create-form' })
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
import { deliveryOrderApi } from '@/api/apis/fulfill/delivery'
import { shopOrderApi } from '@/api/apis/order/order'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { ShopOrderResponse } from '@/api/interface/order/order'

/** 可发行本地视图(来自订单明细,仅 sku_id 已绑定行) */
interface ShippableLine {
  orderItemId: number
  skuId: number
  quantity: number
  shipQty: number
}

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const orderLoading = ref(false)
const orderId = ref<number>()
const warehouseId = ref<number>()
const deliveryNo = ref('')
const logisticsCompany = ref('')
const trackingNo = ref('')
const orderOptions = ref<{ label: string; value: number }[]>([])
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])
const lines = ref<ShippableLine[]>([])

const open = async () => {
  orderId.value = undefined
  warehouseId.value = undefined
  deliveryNo.value = ''
  logisticsCompany.value = ''
  trackingNo.value = ''
  lines.value = []
  visible.value = true
  // 并行拉出库仓选项 + 待发货订单候选(Query 支持 orderStatus 过滤,客户端再裁 SELF_FULFILL;一期人工低频,前 100 单够用)
  loading.value = true
  orderLoading.value = true
  try {
    const [whOptions, orders] = await Promise.all([
      fetchWarehouseOptions(),
      shopOrderApi.page({ pageNo: 1, pageSize: 100, orderStatus: 'WAIT_SHIP' }),
    ])
    warehouseOptions.value = whOptions
    orderOptions.value = orders.list
      .filter((o: ShopOrderResponse) => o.fulfillmentChannel === 'SELF_FULFILL')
      .map(o => ({ label: `${o.platform} · ${o.platformOrderId}`, value: o.id }))
  } finally {
    loading.value = false
    orderLoading.value = false
  }
}

// 选中订单 → 拉详情带明细,仅 sku_id 已绑定行可发,预填全量(可改)
const onOrderChange = async (id: number) => {
  loading.value = true
  lines.value = []
  try {
    const order = await shopOrderApi.detail(id)
    lines.value = (order.items ?? [])
      .filter(item => item.skuId != null)
      .map(item => ({
        orderItemId: item.id!,
        skuId: item.skuId!,
        quantity: item.quantity ?? 0,
        shipQty: item.quantity ?? 0,
      }))
    if (!lines.value.length) {
      ElMessage.warning('该订单无 sku_id 已绑定的明细行,不可发货(补绑 SKU 后再试)')
    }
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames(lines.value.map(l => l.skuId))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!orderId.value) {
    ElMessage.warning('请选择订单')
    return
  }
  if (!warehouseId.value) {
    ElMessage.warning('请选择出库仓')
    return
  }
  if (!deliveryNo.value.trim()) {
    ElMessage.warning('请录入发货单号')
    return
  }
  const items = lines.value
    .filter(line => line.shipQty > 0)
    .map(line => ({ orderItemId: line.orderItemId, shipQty: line.shipQty }))
  if (!items.length) {
    ElMessage.warning('请至少录入一行发货数量大于 0 的明细')
    return
  }
  submitting.value = true
  try {
    await deliveryOrderApi.create({
      deliveryNo: deliveryNo.value.trim(),
      orderId: orderId.value,
      warehouseId: warehouseId.value,
      type: 'SELF_FULFILL',
      logisticsCompany: logisticsCompany.value.trim() || undefined,
      trackingNo: trackingNo.value.trim() || undefined,
      items,
    })
    ElMessage.success('发货单已创建(待发货),库存已占用;确认发货核销出库,取消/删除自动释放')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.delivery-form {
  &__order {
    width: 360px;
  }
  &__wh {
    width: 240px;
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
