<!--
  系统设置页(TODO#18 系统设置,手写页 gen:page 不适用——非 CRUD 列表页):
  分组面板(AI/ALERT/SALES 三组 tab);AI 组内按键前缀分小节 tab(基础/Agent/补货/异常,纯前端分栏,后端仍整组读写);
  标签/说明中文优先(CONFIG_ITEMS 词表 → 后端 remark 兜底);sys_config 全键种子默认值落库(schema INSERT 段),
  输入框展示当前生效值,placeholder 展示代码默认值(词表 def 槽位);值 ≠ 代码默认值标"已修改",否则标"默认值";
  保存后即时生效(后端缓存事件失效);
  写侧 admin 双闸:按钮 v-auth="'system:config:save'" + 后端 @PreAuthorize hasRole('admin')
-->

<template>
  <div class="card content-box">
    <el-tabs v-model="activeGroup" @tab-change="loadGroup">
      <el-tab-pane v-for="g in GROUPS" :key="g.name" :label="g.label" :name="g.name" />
    </el-tabs>
    <el-form label-width="160" v-loading="loading">
      <el-tabs v-if="activeGroup === 'AI'" v-model="activeSection">
        <el-tab-pane v-for="s in AI_SECTIONS" :key="s.name" :label="s.label" :name="s.name" />
      </el-tabs>
      <el-form-item v-for="row in visibleRows" :key="row.configKey" :label="labelOf(row)">
        <div class="config-row">
          <el-switch v-if="isBoolKey(row.configKey)" v-model="boolValues[row.configKey]" :disabled="!canSave" />
          <el-input
            v-else
            v-model="textValues[row.configKey!]"
            type="textarea"
            :rows="isPromptKey(row.configKey) ? 4 : 1"
            :autosize="isPromptKey(row.configKey) ? false : { minRows: 1, maxRows: 4 }"
            :placeholder="placeholderOf(row)"
            :disabled="!canSave"
          />
          <div class="config-remark">
            <span>{{ remarkOf(row) }}</span>
            <el-tag v-if="tagOf(row)" size="small" :type="tagOf(row) === '已修改' ? 'success' : 'info'">
              {{ tagOf(row) }}
            </el-tag>
          </div>
        </div>
      </el-form-item>
      <el-form-item v-if="visibleRows.length">
        <el-button v-auth="'system:config:save'" type="primary" :loading="saving" @click="onSave"
          >保存本组参数</el-button
        >
        <el-button :disabled="!canSave" @click="loadGroup">重置改动</el-button>
        <span class="config-tip">保存即时生效(后端短缓存失效);标签"已修改"表示当前值与出厂默认不同</span>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-config-index' })
// Element Plus 按需显式导入(main.ts 不再全量 app.use):漏导入的组件会被 Vue 当原生自定义元素渲染,整页空白/裸标签
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElSwitch, ElTabPane, ElTabs, ElTag } from 'element-plus'
import { ref, computed, reactive } from 'vue'
import { SystemConfigApi } from '@/api/apis/system/config'
import type { SysConfig } from '@/api/interface'

/** 参数组(与后端 ConfigConsts.GROUPS 对齐;label 为前端展示名) */
const GROUPS = [
  { name: 'AI', label: '大模型 / AI 工作流' },
  { name: 'ALERT', label: '库存预警' },
  { name: 'SALES', label: '销量统计' },
] as const

/** 布尔键词表(与后端 SystemConfigService.ValueType.BOOL 对齐) */
const BOOL_KEYS = new Set(['erp.alert.enabled', 'erp.sales.enabled'])
/** 多行 prompt 键(渲染 textarea 4 行) */
const PROMPT_KEYS = new Set([
  'erp.ai.system-prompt',
  'erp.ai.replenish.summary-prompt',
  'erp.ai.anomaly.score-prompt',
  'erp.ai.agent.support-prompt',
  'erp.ai.agent.ops-prompt',
])
/**
 * 键→展示条目词表(与后端 ConfigConsts 词表逐键对齐,后端新增键需同步补条目;
 * 词表只登记后端白名单键——cron/interval-ms 等调度参数走 yml 不入 sys_config,不登记):
 * label 中文标签(小节内已含上下文,不带组前缀;缺省回落 remark/原键);desc 说明文字(仅在后端 remark 为空时兜底展示);
 * def 代码默认值(输入框 placeholder 展示 + "默认值/已修改"标签判定基准;值与 DB 种子、后端 Properties 逐字对齐)
 */
const CONFIG_ITEMS: Record<string, { label: string; desc?: string; def?: string }> = {
  // —— AI 组 · 基础与对话 ——
  'erp.ai.system-prompt': {
    label: '对话系统提示词',
    desc: 'chat 对话(同步/流式)的 system 提示词',
  },
  'erp.ai.model': {
    label: 'AI 模型名称',
    desc: '对话/AI 工作流共用模型名;未配置回落环境变量 AI_MODEL(缺省 deepseek-chat)',
    def: 'deepseek-chat',
  },
  'erp.ai.tool-audit-max-length': {
    label: '工具审计内容截断长度',
    desc: 'TOOL 审计行内容(工具名+入参 JSON)的超长截断上限;先落库再委托,工具执行失败也留痕',
    def: '500',
  },
  // —— AI 组 · 智能体 Agent ——
  'erp.ai.agent.model': {
    label: '模型名称',
    desc: 'Agent 专用模型名;未配置回落环境变量 AI_MODEL(缺省 deepseek-chat)',
    def: 'deepseek-chat',
  },
  'erp.ai.agent.base-url': {
    label: '模型服务地址',
    desc: 'Agent 专用 OpenAI 兼容服务地址;未配置回落环境变量 OPENAI_BASE_URL(如 DashScope 兼容模式)',
    def: 'https://api.deepseek.com',
  },
  'erp.ai.agent.max-iters': {
    label: '单轮最大迭代次数',
    desc: 'ReAct 单次提问的工具调用迭代上限,防循环护栏',
    def: '10',
  },
  'erp.ai.agent.history-max-messages': {
    label: '历史重放条数上限',
    desc: '多轮会话重放进提示词的最近 N 行(仅 USER/AI,TOOL 行不重放,本轮提问恒在)',
    def: '40',
  },
  'erp.ai.agent.support-prompt': {
    label: '客服角色提示词',
    desc: 'SUPPORT 角色的 system 提示词(全量只读工具)',
  },
  'erp.ai.agent.ops-prompt': {
    label: '运营角色提示词',
    desc: 'OPS 角色的 system 提示词(库存/商品盘面)',
  },
  // —— AI 组 · 补货建议 ——
  'erp.ai.replenish.low-stock-threshold': { label: '低库存阈值', desc: '库存可用 ≤ 此值的 SKU 参与补货建议', def: '10' },
  'erp.ai.replenish.coverage-days': {
    label: '目标覆盖天数',
    desc: '建议补货量使库存可支撑的天数',
    def: '14',
  },
  'erp.ai.replenish.sales-window-days': {
    label: '动销统计窗口(天)',
    desc: '补货公式取近 N 天真实动销(order_sales_daily 支付日口径);零动销 SKU 剔除不硬补',
    def: '30',
  },
  'erp.ai.replenish.min-suggest-qty': {
    label: '最小建议补货量',
    desc: '单条补货建议的数量下限(仅对有动销 SKU 起下限作用)',
    def: '10',
  },
  'erp.ai.replenish.summary-prompt': { label: '摘要生成提示词' },
  // —— AI 组 · 订单异常检测 ——
  'erp.ai.anomaly.big-order-amount': { label: '大额订单阈值', desc: '本位币口径(下单金额×汇率)', def: '10000' },
  'erp.ai.anomaly.unpaid-hours': { label: '未支付超时(小时)', desc: '待支付超 N 小时命中', def: '48' },
  'erp.ai.anomaly.high-discount-ratio': { label: '高折扣比率(0~1)', desc: '折扣金额≥下单金额×此比率命中', def: '0.5' },
  'erp.ai.anomaly.llm-max-items': {
    label: '单轮送评上限',
    desc: '单轮送 LLM 评分的可疑单上限,超限按基线风险降序截断(成本护栏)',
    def: '20',
  },
  'erp.ai.anomaly.score-prompt': { label: '评分提示词' },
  // —— ALERT 组:库存预警 ——
  'erp.alert.enabled': { label: '总开关', def: 'true' },
  'erp.alert.quiet-hours': { label: '静默期(小时)', desc: '同类型告警窗口内只发一条防刷屏', def: '24' },
  'erp.alert.low-stock-threshold': { label: '低库存阈值', desc: '可用库存 ≤ 此值命中', def: '10' },
  'erp.alert.ship-timeout-hours': { label: '发货超时(小时)', desc: '待发货超 N 小时命中', def: '48' },
  'erp.alert.refund-window-hours': { label: '退款统计窗口(小时)', desc: '仅统计窗口内创建的退款单', def: '24' },
  'erp.alert.refund-count-threshold': { label: '退款次数阈值', desc: '单店铺窗口内退款单数 ≥ 此值命中', def: '5' },
  'erp.alert.top-n': { label: '通知明细条数上限', desc: '告警通知内容明细最大条数,超出以"等"收尾', def: '5' },
  'erp.alert.slow-moving-days': {
    label: '滞销判定窗口(天)',
    desc: '窗口内零销量且有库存判滞销(order_sales_daily 动销口径)',
    def: '30',
  },
  'erp.alert.overstock-days': { label: '积压阈值(天)', desc: '可用库存/日均销量 ≥ 此值判积压', def: '90' },
  // —— SALES 组:销量统计 ——
  'erp.sales.enabled': { label: '总开关', def: 'true' },
  'erp.sales.rebuild-days': { label: '回溯重算天数', desc: '每日 upsert 近 N 天,含今日', def: '30' },
}

const activeGroup = ref<string>('AI')
const rows = ref<SysConfig[]>([])
const loading = ref(false)
const saving = ref(false)
const textValues = reactive<Record<string, string>>({})
const boolValues = reactive<Record<string, boolean>>({})

/** AI 组内小节(纯前端分栏:按键前缀归类;后端仍按 AI 组整组返回/整组保存) */
const AI_SECTIONS = [
  { name: 'base', label: '基础与对话' },
  { name: 'agent', label: '智能体 Agent' },
  { name: 'replenish', label: '补货建议' },
  { name: 'anomaly', label: '订单异常检测' },
] as const

const activeSection = ref<string>('base')
const sectionOf = (key?: string | null) => {
  if (key?.startsWith('erp.ai.agent.')) {
    return 'agent'
  }
  if (key?.startsWith('erp.ai.replenish.')) {
    return 'replenish'
  }
  if (key?.startsWith('erp.ai.anomaly.')) {
    return 'anomaly'
  }
  return 'base'
}
/** 当前应渲染的行:AI 组按小节过滤,其余组整组平铺 */
const visibleRows = computed(() =>
  activeGroup.value === 'AI'
    ? rows.value.filter(row => sectionOf(row.configKey) === activeSection.value)
    : rows.value
)

const canSave = computed(() => true) // 权限由 v-auth 收口按钮;输入态不按权限禁用(仅展示)

const isBoolKey = (key?: string | null) => !!key && BOOL_KEYS.has(key)
const isPromptKey = (key?: string | null) => !!key && PROMPT_KEYS.has(key)
/** 说明文字:后端 remark 优先,为空回落词表 desc,再兜底"(无说明)" */
const remarkOf = (row: SysConfig) =>
  row.remark || (row.configKey && CONFIG_ITEMS[row.configKey]?.desc) || '(无说明)'
/** 表单标签中文优先:词表 → 后端 remark → 原键兜底(未登记键不至于空白) */
const labelOf = (row: SysConfig) =>
  (row.configKey && CONFIG_ITEMS[row.configKey]?.label) || row.remark || row.configKey || ''

/** 输入框占位:展示代码默认值(与 DB 种子同源);未登记 def 的键(prompt 类)不显示占位 */
const placeholderOf = (row: SysConfig) => (row.configKey && CONFIG_ITEMS[row.configKey]?.def) || ''
/** 标签:值与代码默认值不同标"已修改",相同/未配置标"默认值";prompt 类(无 def 登记)不显示标签 */
const tagOf = (row: SysConfig) => {
  const item = row.configKey ? CONFIG_ITEMS[row.configKey] : undefined
  if (!item?.def) {
    return ''
  }
  const value = (row.configValue || '').trim()
  return value && value !== item.def ? '已修改' : '默认值'
}

const loadGroup = async () => {
  loading.value = true
  try {
    rows.value = (await SystemConfigApi.listByGroup(activeGroup.value)) || []
    activeSection.value = 'base'
    textValuesClear()
    rows.value.forEach(row => {
      if (row.configKey && isBoolKey(row.configKey)) {
        boolValues[row.configKey] = row.configValue === 'true'
      } else if (row.configKey) {
        textValues[row.configKey] = row.configValue || ''
      }
    })
  } finally {
    loading.value = false
  }
}

const textValuesClear = () => Object.keys(textValues).forEach(k => delete textValues[k])

const onSave = async () => {
  saving.value = true
  try {
    const values: Record<string, string> = {}
    rows.value.forEach(row => {
      if (!row.configKey) {
        return
      }
      values[row.configKey] = isBoolKey(row.configKey)
        ? String(boolValues[row.configKey])
        : (textValues[row.configKey] || '').trim()
    })
    const affected = await SystemConfigApi.saveGroup(activeGroup.value, values)
    ElMessage.success(`已保存,生效 ${affected} 项;模型/提示词/阈值即时生效`)
    await loadGroup()
  } finally {
    saving.value = false
  }
}

loadGroup()
</script>

<style scoped lang="scss">
.config-row {
  width: 100%;
}
.config-remark {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.config-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
