<!-- 菜单管理(树形例外页,手写:非分页 ProTable 用法,见 docs/09 §4 例外) -->
<template>
  <div class="table-box">
    <ProTable
      ref="proTableRef"
      page-id="/system/menus"
      title="菜单管理"
      :columns="columns"
      :request-api="requestTree"
      :pagination="ProTablePaginationEnum.NONE"
      default-expand-all
    >
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'system:menu:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增菜单</el-button
        >
      </template>

      <!-- 菜单类型 / 显示状态 自定义渲染 -->
      <template #menuType="scope">
        <el-tag :type="menuTypeTag(scope.row.menuType)">{{ menuTypeLabel(scope.row.menuType) }}</el-tag>
      </template>
      <template #visible="scope">
        <el-tag :type="scope.row.visible === 1 ? 'success' : 'info'">{{
          scope.row.visible === 1 ? '显示' : '隐藏'
        }}</el-tag>
      </template>

      <!-- 操作列(ProTable v2:自定义插槽渲染) -->
      <template #operation="scope">
        <el-button
          v-if="scope.row.menuType !== 3"
          v-auth="'system:menu:add'"
          type="primary"
          link
          :icon="CirclePlus"
          @click="openForm('add', undefined, scope.row.id)"
        >
          新增下级
        </el-button>
        <el-button v-auth="'system:menu:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'system:menu:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
      </template>
    </ProTable>
    <SysMenuForm ref="formRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'system-menu-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import { ProTablePaginationEnum } from '@/enums'
import type { ColumnProps } from '@/components/ProTable/interface'
import { sysMenuApi } from '@/api/apis/system/menu'
import type { SysMenuResponse } from '@/api/interface/system/menu'
import SysMenuForm from './components/SysMenuForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof SysMenuForm>>()

// 树形数据源:非分页,直接返回全量树(useTable 数组分支)
const requestTree = () => sysMenuApi.tree()

// 列配置(树形页无搜索区;menuType/visible 走插槽渲染)
const columns: ColumnProps<SysMenuResponse>[] = [
  { prop: 'menuName', label: '菜单名称', width: 200 },
  { prop: 'menuType', label: '类型', width: 90 },
  { prop: 'permKey', label: '权限标识', width: 190, showOverflowTooltip: true },
  { prop: 'path', label: '路由路径', width: 160, showOverflowTooltip: true },
  { prop: 'component', label: '组件路径', width: 200, showOverflowTooltip: true },
  { prop: 'sort', label: '排序', width: 70 },
  { prop: 'visible', label: '显示', width: 80 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 250 },
]

const menuTypeLabel = (t: number) => (({ 1: '目录', 2: '菜单', 3: '按钮' }) as Record<number, string>)[t] ?? t
const menuTypeTag = (t: number) => (({ 1: 'primary', 2: 'success', 3: 'warning' }) as Record<number, any>)[t] ?? 'info'

/** open(mode[, row][, parentId]):新增下级传 parentId;edit 浅拷贝行数据 */
const openForm = (mode: 'add' | 'edit', row?: SysMenuResponse, parentId?: number) => {
  formRef.value?.open(mode, row, parentId)
}

const handleDelete = async (row: SysMenuResponse) => {
  await ElMessageBox.confirm(`确认删除菜单【${row.menuName}】吗?(若其存在下级或已被角色引用,后端将拒绝)`, '提示', {
    type: 'warning',
  })
  await sysMenuApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
