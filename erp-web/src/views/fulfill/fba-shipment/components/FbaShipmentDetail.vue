<!--
  FBA发货单详情抽屉(fba-shipment,生成器外定制):单头(店铺/站点/发货仓/平台ShipmentId/状态时间)
  + 计划行(SKU 清单,勾稽基准)+ 装箱树(箱+内件,后端已回填 skuCode)+ 收货对账差异行
  (SHORT 缺收/EXTRA 多收/OK 一致;RECEIVING 可重复登记覆盖,CLOSED 冻结)。
-->

<template>
  <el-drawer v-model="visible" :title="`FBA发货单详情${detail ? ' · ' + detail.shipmentNo : ''}`" size="72%">
    <div v-if="detail" class="fba-detail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_TAG[detail.status] ?? 'info'" size="small">{{
            STATUS_LABELS[detail.status] ?? detail.status
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="店铺">{{ detail.shopName || detail.shopId }}</el-descriptions-item>
        <el-descriptions-item label="站点">{{ detail.marketplace }}</el-descriptions-item>
        <el-descriptions-item label="国内发货仓">{{ detail.warehouseName || detail.warehouseId }}</el-descriptions-item>
        <el-descriptions-item label="平台ShipmentId">{{ detail.platformShipmentId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发出时间">{{ detail.shippedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近收货登记">{{ detail.receivedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4 class="fba-detail__title">计划行({{ detail.planItems?.length ?? 0 }} 个 SKU)</h4>
      <el-table :data="detail.planItems ?? []" size="small" border>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="skuCode" label="SKU 编码" min-width="200">
          <template #default="{ row }">{{ row.skuCode || row.skuId }}</template>
        </el-table-column>
        <el-table-column prop="planQty" label="计划量" width="100" />
      </el-table>

      <h4 class="fba-detail__title">装箱({{ detail.boxes?.length ?? 0 }} 箱)</h4>
      <el-empty v-if="!detail.boxes?.length" description="暂无装箱明细(草稿可先建计划后补箱)" :image-size="60" />
      <div v-for="box in detail.boxes ?? []" :key="box.id" class="fba-detail__box">
        <div class="fba-detail__box-head">
          <el-tag size="small" type="info">{{ box.boxNo }}</el-tag>
          <span v-if="box.weight != null">毛重 {{ box.weight }}kg</span>
          <span v-if="box.lengthCm">外箱 {{ box.lengthCm }}×{{ box.widthCm }}×{{ box.heightCm }}cm</span>
        </div>
        <el-table :data="box.items ?? []" size="small" border>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="skuCode" label="SKU 编码" min-width="200">
            <template #default="{ row }">{{ row.skuCode || row.skuId }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="件数" width="100" />
        </el-table>
      </div>

      <template v-if="detail.diffs?.length">
        <h4 class="fba-detail__title">收货对账差异({{ detail.diffs.length }} 个 SKU)</h4>
        <el-table :data="detail.diffs" size="small" border>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="skuCode" label="SKU 编码" min-width="200">
            <template #default="{ row }">{{ row.skuCode || row.skuId }}</template>
          </el-table-column>
          <el-table-column prop="shippedQty" label="发出量" width="90" />
          <el-table-column prop="receivedQty" label="收货量" width="90" />
          <el-table-column label="差异" width="110">
            <template #default="{ row }">
              <el-tag :type="DIFF_TAG[row.diffType] ?? 'info'" size="small">{{
                DIFF_LABELS[row.diffType] ?? row.diffType
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="checkedAt" label="核对时间" width="170" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElDescriptions, ElDescriptionsItem, ElDrawer, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { fbaShipmentApi } from '@/api/apis/fulfill/fbaShipment'
import type { FbaShipmentResponse } from '@/api/interface/fulfill/fbaShipment'

defineOptions({ name: 'FbaShipmentDetail' })

const visible = ref(false)
const detail = ref<FbaShipmentResponse>()

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  BOXED: '已装箱',
  SHIPPED: '已发出',
  RECEIVING: '收货登记中',
  CLOSED: '已关闭',
  CANCELED: '已取消',
}
const STATUS_TAG: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  DRAFT: 'info',
  BOXED: 'warning',
  SHIPPED: 'primary',
  RECEIVING: 'warning',
  CLOSED: 'success',
  CANCELED: 'danger',
}
const DIFF_LABELS: Record<string, string> = {
  SHORT: '缺收',
  EXTRA: '多收',
  OK: '一致',
}
const DIFF_TAG: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  SHORT: 'danger',
  EXTRA: 'warning',
  OK: 'success',
}

const open = async (row: FbaShipmentResponse) => {
  visible.value = true
  detail.value = await fbaShipmentApi.detail(row.id)
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.fba-detail {
  padding: 0 4px;
  &__title {
    margin: 18px 0 10px;
  }
  &__box {
    margin-bottom: 12px;
  }
  &__box-head {
    display: flex;
    gap: 14px;
    align-items: center;
    margin-bottom: 6px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}
</style>
