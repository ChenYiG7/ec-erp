<template>
  <!-- TODO(#11) 发货单编辑(#11 人工扩展槽,后端接口已备):仅 PENDING 可编辑;
       后端 update = 行锁读 + 释放旧占用 + 替换明细 + 重占新占用,任一步失败整体回滚;
       本表单射程 = 后补/修正物流公司与运单号(建单时选填),仓库与明细只读展示——
       改仓/改量走"取消后重建"(前端不放开占用重算入口,防误操作);SKU 名称列翻译随 #7 专条收口(goods/options 批量端点) -->
  <el-dialog v-model="visible" title="编辑发货单" width="680px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="发货单号">
        <span>{{ detail?.deliveryNo }}</span>
        <span class="delivery-edit-form__meta">订单ID {{ detail?.orderId ?? '-' }} · 出库仓 {{ warehouseLabel }} · 仅待发货可编辑</span>
      </el-form-item>
      <el-form-item label="发货明细">
        <el-table v-if="detail?.items?.length" :data="detail.items" size="small" border max-height="220">
          <el-table-column label="内部SKU" width="170" show-overflow-tooltip>
            <template #default="{ row: item }">{{ skuLabel(item.skuId) }}</template>
          </el-table-column>
          <el-table-column prop="orderItemId" label="订单明细行ID" width="150" />
          <el-table-column prop="shipQty" label="发货数量" width="110" />
        </el-table>
        <span v-else class="delivery-edit-form__meta">无明细</span>
      </el-form-item>
      <el-form-item label="物流公司">
        <el-input v-model="logisticsCompany" maxlength="64" placeholder="选填" class="delivery-edit-form__input" />
      </el-form-item>
      <el-form-item label="运单号">
        <el-input v-model="trackingNo" maxlength="64" placeholder="选填,发货前后均可补录" class="delivery-edit-form__input" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'fulfill-delivery-edit-form' })
import { computed, ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElTable, ElTableColumn } from 'element-plus'
import { deliveryOrderApi } from '@/api/apis/fulfill/delivery'
import { fetchSkuNames, skuLabel } from '@/api/apis/goods/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { DeliveryOrderResponse } from '@/api/interface/fulfill/delivery'

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const detail = ref<DeliveryOrderResponse>()
const logisticsCompany = ref('')
const trackingNo = ref('')
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])

const warehouseLabel = computed(
  () => warehouseOptions.value.find(w => w.value === detail.value?.warehouseId)?.label ?? detail.value?.warehouseId ?? '-'
)

// 列表行仅缺 items,打开时拉详情补齐(提交须全量 SaveRequest,明细整体替换);业务报错弹窗由拦截器统一处理
const open = async (row: DeliveryOrderResponse) => {
  visible.value = true
  detail.value = undefined
  logisticsCompany.value = ''
  trackingNo.value = ''
  try {
    const [d, whOptions] = await Promise.all([deliveryOrderApi.detail(row.id), fetchWarehouseOptions()])
    detail.value = d
    warehouseOptions.value = whOptions
    // 内部SKU 列翻译(#7 专条):明细行去重批量预取,渲染读 options 模块缓存
    fetchSkuNames((d.items ?? []).map(i => i.skuId))
    logisticsCompany.value = d.logisticsCompany ?? ''
    trackingNo.value = d.trackingNo ?? ''
  } catch {
    visible.value = false
  }
}

const submit = async () => {
  const d = detail.value
  if (!d) {
    return
  }
  submitting.value = true
  try {
    await deliveryOrderApi.update(d.id, {
      deliveryNo: d.deliveryNo,
      orderId: d.orderId,
      warehouseId: d.warehouseId,
      type: d.type || undefined,
      shipByTime: d.shipByTime || undefined,
      logisticsCompany: logisticsCompany.value.trim() || undefined,
      trackingNo: trackingNo.value.trim() || undefined,
      waybillUrl: d.waybillUrl || undefined,
      items: (d.items ?? []).map(i => ({ orderItemId: i.orderItemId, shipQty: i.shipQty }))
    })
    ElMessage.success('已保存')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.delivery-edit-form {
  &__input {
    width: 320px;
  }
  &__meta {
    display: inline-block;
    width: 100%;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
