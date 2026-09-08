<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/users"
      title="用户管理"
      :columns="columns"
      :request-api="sysUserApi.page"
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'system:user:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增用户管理</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button v-auth="'system:user:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'system:user:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
        <el-button v-auth="'system:user:roles'" type="warning" link :icon="User" @click="openRoles(scope.row)"
          >角色分配</el-button
        >
        <el-button v-auth="'system:user:password'" type="warning" link :icon="Key" @click="onPassword(scope.row)"
          >重置密码</el-button
        >
      </template>
    </ProTable>
    <SysUserForm ref="formRef" @saved="refreshTable" />
    <UserRoleDialog ref="rolesRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-user-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete, User, Key } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { sysUserApi } from '@/api/apis/system/user'
import type { SysUserResponse } from '@/api/interface/system/user'
import SysUserForm from './components/SysUserForm.vue'
import UserRoleDialog from './components/UserRoleDialog.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof SysUserForm>>()
const rolesRef = ref<InstanceType<typeof UserRoleDialog>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<SysUserResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'username', label: '用户名', width: 140 },
  { prop: 'nickname', label: '昵称', width: 140 },
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
  { prop: 'operation', label: '操作', fixed: 'right', width: 350 },
]

const openForm = (mode: 'add' | 'edit', row?: SysUserResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: SysUserResponse) => {
  await ElMessageBox.confirm('确认删除该用户管理吗?', '提示', { type: 'warning' })
  await sysUserApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 角色分配弹窗
const openRoles = (row: SysUserResponse) => rolesRef.value?.open(row)

// 管理员重置密码(新密码 ElMessageBox.prompt 输入,后端 BCrypt 落库)
const onPassword = async (row: SysUserResponse) => {
  const { value } = await ElMessageBox.prompt(`请输入用户 ${row.username} 的新密码(≥6 位)`, '重置密码', {
    inputType: 'password',
    inputPattern: /^.{6,}$/,
    inputErrorMessage: '密码至少 6 位',
  })
  await sysUserApi.password(row.id, { newPassword: value })
  ElMessage.success('密码已重置')
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
