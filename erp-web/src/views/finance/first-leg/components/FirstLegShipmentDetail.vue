<!--
  头程发货单详情抽屉(生成器外定制,#33):单头(双仓/运费/汇率/状态)+ 装箱树(箱+内件,后端已回填 skuCode)
  + 分摊结果行(CNY 金额/基数快照/实际策略;降级时与主单策略不同)。
-->

<template>
  <el-drawer v-model="visible" :title="`头程发货单详情${detail ? ' · ' + detail.shipmentNo : ''}`" size="72%">
    <div v-if="detail" class="fl-detail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_TAG[detail.status] ?? 'info'" size="small">{{
            STATUS_LABELS[detail.status] ?? detail.status
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="国内发货仓">{{
          detail.fromWarehouseName || detail.fromWarehouseId
        }}</el-descriptions-item>
        <el-descriptions-item label="目的仓">{{ detail.toWarehouseName || detail.toWarehouseId }}</el-descriptions-item>
        <el-descriptions-item label="物流商">{{ detail.carrier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运单号">{{ detail.waybillNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发货时间">{{ detail.shippedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运费原币">
          {{ detail.freightAmount ?? '-' }} {{ detail.currency }}
        </el-descriptions-item>
        <el-descriptions-item label="汇率">{{ detail.exchangeRate ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="运费CNY">{{ detail.freightCny ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="计费重 kg">{{ detail.chargeWeight ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="体积重 kg">{{ detail.volumeWeight ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="分摊策略">{{
          STRATEGY_LABELS[detail.allocateStrategy] ?? detail.allocateStrategy
        }}</el-descriptions-item>
        <el-descriptions-item label="分摊时间">{{ detail.allocatedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.allocRemark" label="分摊说明" :span="3">
          <span class="fl-detail__remark">{{ detail.allocRemark }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="fl-detail__title">装箱({{ detail.boxes?.length ?? 0 }} 箱)</h4>
      <el-empty v-if="!detail.boxes?.length" description="暂无装箱明细(草稿可先建单后补箱)" :image-size="60" />
      <div v-for="box in detail.boxes ?? []" :key="box.id" class="fl-detail__box">
        <div class="fl-detail__box-head">
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

      <template v-if="detail.allocs?.length">
        <h4 class="fl-detail__title">运费分摊结果({{ detail.allocs.length }} 个 SKU)</h4>
        <el-table :data="detail.allocs" size="small" border>
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="skuCode" label="SKU 编码" min-width="200">
            <template #default="{ row }">{{ row.skuCode || row.skuId }}</template>
          </el-table-column>
          <el-table-column prop="allocBase" label="分摊基数" width="160" />
          <el-table-column label="实际策略" width="110">
            <template #default="{ row }">{{ STRATEGY_LABELS[row.strategy] ?? row.strategy }}</template>
          </el-table-column>
          <el-table-column prop="allocAmount" label="分摊运费(CNY)" width="160" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
import { ElDescriptions, ElDescriptionsItem, ElDrawer, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ref } from 'vue'
import { firstLegShipmentApi } from '@/api/apis/finance/firstLeg'
import type { FirstLegShipmentResponse } from '@/api/interface/finance/firstLeg'

defineOptions({ name: 'FirstLegShipmentDetail' })

const visible = ref(false)
const detail = ref<FirstLegShipmentResponse>()

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  BOXED: '已装箱',
  SHIPPED: '已发货',
  ALLOCATED: '已分摊',
  CLOSED: '已关闭',
  CANCELED: '已取消',
}
const STATUS_TAG: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  DRAFT: 'info',
  BOXED: 'warning',
  SHIPPED: 'primary',
  ALLOCATED: 'success',
  CLOSED: 'success',
  CANCELED: 'danger',
}
const STRATEGY_LABELS: Record<string, string> = {
  QTY: '按数量',
  WEIGHT: '按重量',
  AMOUNT: '按金额',
}

const open = async (row: FirstLegShipmentResponse) => {
  visible.value = true
  detail.value = undefined // 重开清旧数据:加载期/取数失败不闪现上一单内容(#26 六轮)
  detail.value = await firstLegShipmentApi.detail(row.id)
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.fl-detail {
  padding: 0 4px;
  &__title {
    margin: 18px 0 10px;
  }
  &__remark {
    color: var(--el-color-warning);
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
