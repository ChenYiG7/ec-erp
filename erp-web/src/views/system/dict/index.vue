<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/dicts"
      title="字典管理"
      :columns="columns"
      :request-api="sysDictApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'system:dict:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增字典管理</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button v-auth="'system:dict:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'system:dict:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
      </template>
    </ProTable>
    <SysDictForm ref="formRef" @saved="onSaved" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-dict-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { sysDictApi } from '@/api/apis/system/dict'
import { useDictStore } from '@/stores/modules/dict'
import type { SysDictResponse } from '@/api/interface/system/dict'
import SysDictForm from './components/SysDictForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof SysDictForm>>()
const dictStore = useDictStore()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<SysDictResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'dictType', label: '字典类型', width: 180 },
  { prop: 'dictLabel', label: '字典标签', width: 160 },
  { prop: 'dictValue', label: '字典值', width: 140 },
  { prop: 'sort', label: '排序', width: 80 },
  {
    prop: 'status',
    label: '状态',
    width: 90,
    enum: [
      { label: '启用', value: 1, tagType: 'success' },
      { label: '停用', value: 0, tagType: 'danger' },
    ],
  },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 140 },
]

const openForm = (mode: 'add' | 'edit', row?: SysDictResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: SysDictResponse) => {
  await ElMessageBox.confirm('确认删除该字典项吗?', '提示', { type: 'warning' })
  await sysDictApi.remove(row.id)
  ElMessage.success('删除成功')
  dictStore.clearDict()
  refreshTable()
}

/** 表单保存成功:刷新列表 + 强刷字典缓存(否则全端下拉仍是旧值) */
const onSaved = () => {
  dictStore.clearDict()
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
