<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/ai/suggestions" title="AI建议" :columns="columns" :request-api="aiSuggestionApi.page">
      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button type="primary" link :icon="View" @click="openDetail(scope.row)">详情</el-button>
        <!-- 后端登录即可(无按钮 permKey),动作不带 v-auth——同通知中心口径 -->
        <el-button v-if="scope.row.status === 0" type="success" link @click="onAdopt(scope.row)">采纳</el-button>
        <el-button v-if="scope.row.status === 0" type="warning" link @click="onIgnore(scope.row)">忽略</el-button>
      </template>
    </ProTable>
    <!-- 详情抽屉(生成器不产:payloadJson 结构化负载 + 确认信息,payload 为采纳回放依据) -->
    <el-drawer v-model="detailVisible" title="AI建议详情" size="520px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="建议类型">{{ detailRow.suggestionType }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">{{ detailRow.riskLevel }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailRow.status }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ detailRow.summary }}</el-descriptions-item>
        <el-descriptions-item label="店铺ID / 内部SKU">{{ detailRow.shopId ?? '-' }} / {{ detailRow.skuId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联业务">{{ detailRow.refType ?? '-' }}{{ detailRow.refId != null ? ` #${detailRow.refId}` : '' }}</el-descriptions-item>
        <el-descriptions-item label="确认人 / 确认时间">{{ detailRow.confirmedBy ?? '-' }} / {{ detailRow.confirmedAt ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailRow.createdAt }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detailRow?.payloadJson" class="payload-block">
        <div class="payload-title">结构化负载(payloadJson)</div>
        <pre class="payload-pre">{{ payloadPretty }}</pre>
      </div>
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'ai-ai-suggestion-index' })
import { ref } from 'vue'
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDrawer, ElMessage, ElMessageBox } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { aiSuggestionApi } from '@/api/apis/ai/ai-suggestion'
import type { AiSuggestionResponse } from '@/api/interface/ai/ai-suggestion'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<AiSuggestionResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'suggestionType', label: '建议类型', width: 110, enum: [{ label: '补货', value: "REPLENISH", tagType: 'primary' }, { label: '定价', value: "PRICING", tagType: 'warning' }, { label: '异常', value: "ANOMALY", tagType: 'danger' }, { label: '文案', value: "COPYWRITING", tagType: 'info' }, { label: '采购', value: "PURCHASE", tagType: 'success' }] },
  { prop: 'status', label: '状态', width: 90, tag: true, enum: [{ label: '待确认', value: 0, tagType: 'warning' }, { label: '已采纳', value: 1, tagType: 'success' }, { label: '已忽略', value: 2, tagType: 'info' }] },
  { prop: 'shopId', label: '店铺ID', width: 90 },
  { prop: 'skuId', label: '内部SKU', width: 90 },
  { prop: 'summary', label: '建议摘要' },
  { prop: 'riskLevel', label: '风险等级', width: 90, tag: true, enum: [{ label: '低', value: "LOW", tagType: 'success' }, { label: '中', value: "MID", tagType: 'warning' }, { label: '高', value: "HIGH", tagType: 'danger' }] },
  { prop: 'confirmedAt', label: '确认时间', width: 170 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 180 }
]

// TODO(#6) 动作 ignore:忽略(0→2 cas 守卫)确认弹窗后调用并刷新,仅 status=0 可见,不带 v-auth
// const onIgnore = async (row: AiSuggestionResponse) => { ... await aiSuggestionApi.ignore(row.id) ... }

// TODO(#6) 动作 adopt:采纳(0→1 cas 守卫)确认弹窗后调用并刷新,仅 status=0 可见,不带 v-auth
// const onAdopt = async (row: AiSuggestionResponse) => { ... await aiSuggestionApi.adopt(row.id) ... }

// 采纳(0→1 cas 守卫,脱靶/已处理报错由拦截器统一提示;仅落确认状态,业务动作人工走对应业务接口)
const onAdopt = async (row: AiSuggestionResponse) => {
  await ElMessageBox.confirm('确认采纳该建议吗?仅确认采纳,业务动作仍需到对应业务模块人工执行。', '采纳建议', { type: 'warning' })
  await aiSuggestionApi.adopt(row.id)
  ElMessage.success('已采纳')
  refreshTable()
}

// 忽略(0→2 cas 守卫,仅 status=0 可见)
const onIgnore = async (row: AiSuggestionResponse) => {
  await ElMessageBox.confirm('确认忽略该建议吗?', '忽略建议', { type: 'warning' })
  await aiSuggestionApi.ignore(row.id)
  ElMessage.success('已忽略')
  refreshTable()
}

// 详情抽屉:payloadJson 展示层美化(禁改行数据,parse 失败回落原文)
const detailVisible = ref(false)
const detailRow = ref<AiSuggestionResponse | null>(null)
const payloadPretty = ref('')
const openDetail = (row: AiSuggestionResponse) => {
  detailRow.value = row
  payloadPretty.value = row.payloadJson ?? ''
  try {
    payloadPretty.value = JSON.stringify(JSON.parse(row.payloadJson ?? ''), null, 2)
  } catch {
    // 非法 JSON 直显原文(展示用,不做容错改写)
  }
  detailVisible.value = true
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>

<style scoped lang="scss">
.payload-block {
  margin-top: 16px;
  .payload-title {
    margin-bottom: 8px;
    font-weight: 600;
  }

  // JSON 直显:等宽 + 限高滚动,禁横向撑破抽屉
  .payload-pre {
    max-height: 320px;
    padding: 12px;
    overflow: auto;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    line-height: 1.6;
    word-break: break-all;
    white-space: pre-wrap;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;
  }
}
</style>
