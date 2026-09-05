<!-- 角色-菜单授权树弹窗(页面私有组件;勾选回显只勾叶子、父级由级联推导,保存合并半选父 id) -->
<template>
  <el-dialog v-model="visible" :title="title" width="480px" :close-on-click-modal="false" destroy-on-close>
    <div v-loading="loading" class="menu-tree">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
      />
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElButton, ElDialog, ElMessage, ElTree } from 'element-plus'
import { sysMenuApi } from '@/api/apis/system/menu'
import type { SysMenuResponse, RoleMenuAssignRequest } from '@/api/interface/system/menu'
import type { SysRoleResponse } from '@/api/interface/system/role'

defineOptions({ name: 'RoleMenuDialog' })

type TreeInstance = InstanceType<typeof ElTree>

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const title = ref('菜单授权')
const roleId = ref<number>()
const treeRef = ref<TreeInstance>()
const menuTree = ref<SysMenuResponse[]>([])

/** 收集叶子节点 id(回显时只勾叶子,父级由 el-tree 级联自动补齐,避免父子状态矛盾) */
const leafIds = (nodes: SysMenuResponse[], acc: Set<number> = new Set()): Set<number> => {
  for (const n of nodes) {
    if (n.children?.length) {
      leafIds(n.children, acc)
    } else {
      acc.add(n.id)
    }
  }
  return acc
}

/** 打开弹窗:并行拉菜单全量树 + 该角色已授权 id 列表 */
const open = async (row: SysRoleResponse) => {
  roleId.value = row.id
  title.value = `菜单授权 - ${row.roleName}`
  visible.value = true
  loading.value = true
  try {
    const [tree, checkedIds] = await Promise.all([sysMenuApi.tree(), sysMenuApi.getRoleMenuIds(row.id)])
    menuTree.value = tree
    await nextTick()
    const leaves = leafIds(tree)
    treeRef.value?.setCheckedKeys(checkedIds.filter(id => leaves.has(id)))
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  const menuIds: number[] = [
    ...(treeRef.value?.getCheckedKeys() ?? []),
    ...(treeRef.value?.getHalfCheckedKeys() ?? [])
  ].map(Number)
  const data: RoleMenuAssignRequest = { menuIds }
  submitting.value = true
  try {
    await sysMenuApi.assignRoleMenus(roleId.value!, data)
    ElMessage.success('授权成功(重新登录后菜单生效)')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.menu-tree {
  max-height: 480px;
  overflow: auto;
}
</style>
