<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/roles"
      title="角色管理"
      :columns="columns"
      :request-api="sysRoleApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'system:role:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增角色管理</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button v-auth="'system:role:menus'" type="warning" link :icon="Menu" @click="openMenus(scope.row)"
          >菜单授权</el-button
        >
        <el-button v-auth="'system:role:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'system:role:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
      </template>
    </ProTable>
    <SysRoleForm ref="formRef" @saved="refreshTable" />
    <RoleMenuDialog ref="menusRef" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-role-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete, Menu } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { sysRoleApi } from '@/api/apis/system/role'
import type { SysRoleResponse } from '@/api/interface/system/role'
import SysRoleForm from './components/SysRoleForm.vue'
import RoleMenuDialog from './components/RoleMenuDialog.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof SysRoleForm>>()
const menusRef = ref<InstanceType<typeof RoleMenuDialog>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<SysRoleResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'roleName', label: '角色名称', width: 180 },
  { prop: 'roleKey', label: '角色标识', width: 160 },
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
  { prop: 'operation', label: '操作', fixed: 'right', width: 240 },
]

const openForm = (mode: 'add' | 'edit', row?: SysRoleResponse) => {
  formRef.value?.open(mode, row)
}

// 菜单授权树弹窗
const openMenus = (row: SysRoleResponse) => menusRef.value?.open(row)

const handleDelete = async (row: SysRoleResponse) => {
  await ElMessageBox.confirm('确认删除该角色管理吗?', '提示', { type: 'warning' })
  await sysRoleApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
