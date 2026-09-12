<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/finance/fee-rates"
      title="平台费率"
      :columns="columns"
      :request-api="platformFeeRateApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'finance:fee-rate:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增平台费率</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button
          v-auth="'finance:fee-rate:edit'"
          type="primary"
          link
          :icon="EditPen"
          @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'finance:fee-rate:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
      </template>
    </ProTable>
    <PlatformFeeRateForm ref="formRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-fee-rate-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { useDictStore } from '@/stores/modules/dict'
import { platformFeeRateApi } from '@/api/apis/finance/fee-rate'
import type { PlatformFeeRateResponse } from '@/api/interface/finance/fee-rate'
import PlatformFeeRateForm from './components/PlatformFeeRateForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof PlatformFeeRateForm>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<PlatformFeeRateResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  {
    prop: 'platform',
    label: '平台',
    width: 120,
    enum: () =>
      useDictStore()
        .getDict('shop_platform')
        .then(list => list.map(item => ({ label: item.dictLabel, value: item.dictValue }))),
  },
  {
    prop: 'feeType',
    label: '费种',
    width: 100,
    tag: true,
    enum: [{ label: '佣金', value: 'COMMISSION', tagType: 'warning' }],
  },
  { prop: 'rate', label: '费率', width: 110 },
  { prop: 'effFrom', label: '生效起', width: 120 },
  // 生效止表单可编辑(spec role=form),列展示补回:空值显示"长期"
  { prop: 'effTo', label: '生效止', width: 120, render: ({ row }) => row.effTo ?? '长期' },
  {
    prop: 'source',
    label: '来源',
    width: 90,
    enum: [
      { label: '手工', value: 'MANUAL' },
      { label: '抓取', value: 'CRAWLED' },
    ],
  },
  { prop: 'remark', label: '备注', width: 200 },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 140 },
]

const openForm = (mode: 'add' | 'edit', row?: PlatformFeeRateResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: PlatformFeeRateResponse) => {
  await ElMessageBox.confirm('确认删除该平台费率吗?', '提示', { type: 'warning' })
  await platformFeeRateApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
