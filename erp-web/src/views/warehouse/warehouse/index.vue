<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/inventory/warehouses" title="仓库管理" :columns="columns" :request-api="warehouseApi.page">
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'warehouse:add'" type="primary" :icon="CirclePlus" @click="openForm('add')">新增仓库管理</el-button>
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button v-auth="'warehouse:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)">编辑</el-button>
        <el-button v-auth="'warehouse:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
      </template>
    </ProTable>
    <WarehouseForm ref="formRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'warehouse-warehouse-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { warehouseApi } from '@/api/apis/warehouse/warehouse'
import type { WarehouseResponse } from '@/api/interface/warehouse/warehouse'
import WarehouseForm from './components/WarehouseForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof WarehouseForm>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<WarehouseResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'whName', label: '仓库名称', width: 200 },
  { prop: 'whType', label: '仓库类型', width: 110, enum: [{ label: '自仓', value: "SELF" }, { label: 'FBA', value: "FBA" }, { label: '海外仓', value: "OVERSEAS" }, { label: '虚拟仓', value: "VIRTUAL" }] },
  { prop: 'country', label: '国家', width: 90 },
  { prop: 'address', label: '仓库地址' },
  { prop: 'status', label: '状态', width: 90, enum: [{ label: '启用', value: 1, tagType: 'success' }, { label: '禁用', value: 0, tagType: 'danger' }] },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 140 }
]

const openForm = (mode: 'add' | 'edit', row?: WarehouseResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: WarehouseResponse) => {
  await ElMessageBox.confirm('确认删除该仓库管理吗?', '提示', { type: 'warning' })
  await warehouseApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
