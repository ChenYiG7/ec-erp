<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/finance/profit-periods"
      title="周期利润"
      :columns="columns"
      :request-api="profitPeriodReportApi.page"
    >
      <!-- 操作列:周期报告系统写入只读,唯一动作=详情对照抽屉(订单口径 vs 结算口径) -->
      <template #operation="scope">
        <el-button type="primary" link :icon="View" @click="openDetail(scope.row)">详情</el-button>
      </template>
    </ProTable>
    <PeriodDetailDrawer ref="drawerRef" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-profit-period-index' })
import { ref } from 'vue'
import { View } from '@element-plus/icons-vue'
import { ElButton } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { profitPeriodReportApi } from '@/api/apis/finance/profit-period'
import type { ProfitPeriodReportResponse } from '@/api/interface/finance/profit-period'
import PeriodDetailDrawer from './components/PeriodDetailDrawer.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
// TODO(#19) 周期详情对照抽屉(人工槽位:GET /{id} 双侧分组 + 校差说明)
const drawerRef = ref<InstanceType<typeof PeriodDetailDrawer>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ProfitPeriodReportResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'shopId', label: '店铺ID', width: 90 },
  { prop: 'periodStart', label: '周期起', width: 160 },
  { prop: 'periodEnd', label: '周期止', width: 160 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'rateUsed', label: '折算汇率', width: 100 },
  { prop: 'orderIncome', label: '订单收入', width: 120 },
  { prop: 'settleIncome', label: '结算回款', width: 120 },
  { prop: 'orderCommission', label: '订单佣金', width: 120 },
  { prop: 'settleCommission', label: '结算佣金', width: 120 },
  { prop: 'fbaFee', label: 'FBA费用', width: 110 },
  { prop: 'otherFee', label: '其他费用', width: 110 },
  { prop: 'orderProfit', label: '订单利润', width: 120 },
  { prop: 'diffIncome', label: '收入校差', width: 110 },
  { prop: 'diffCommission', label: '佣金校差', width: 110 },
  {
    prop: 'rateMissing',
    label: '缺汇率',
    width: 90,
    tag: true,
    enum: [
      { label: '否', value: 0, tagType: 'success' },
      { label: '是', value: 1, tagType: 'danger' },
    ],
  },
  {
    prop: 'diffFlag',
    label: '差异',
    width: 80,
    tag: true,
    enum: [
      { label: '平', value: 0, tagType: 'success' },
      { label: '有差', value: 1, tagType: 'danger' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    tag: true,
    enum: [
      { label: '勾稽平', value: 'OK', tagType: 'success' },
      { label: '有差异', value: 'DIFF', tagType: 'danger' },
      { label: '缺汇率', value: 'RATE_MISSING', tagType: 'warning' },
    ],
  },
  { prop: 'operation', label: '操作', fixed: 'right', width: 90 },
]

const openDetail = (row: ProfitPeriodReportResponse) => drawerRef.value?.open(row.id)

const refreshTable = () => proTableRef.value?.getTableList()
</script>
