<!-- 用户-角色分配弹窗(页面私有组件;api 收口 @/api/apis/system/user|role,禁直连 axios) -->
<template>
  <el-dialog v-model="visible" :title="title" width="480px" :close-on-click-modal="false" destroy-on-close>
    <div v-loading="loading" class="role-list">
      <el-checkbox-group v-model="checkedRoleIds">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" :disabled="r.status !== 1">
          {{ r.roleName }}({{ r.roleKey }})
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!loading && roles.length === 0" description="暂无角色,请先在角色管理中新增" :image-size="72" />
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElCheckbox, ElCheckboxGroup, ElDialog, ElEmpty, ElMessage } from 'element-plus'
import { sysUserApi } from '@/api/apis/system/user'
import { sysRoleApi } from '@/api/apis/system/role'
import type { SysUserResponse } from '@/api/interface/system/user'
import type { SysRoleResponse } from '@/api/interface/system/role'

defineOptions({ name: 'UserRoleDialog' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const title = ref('分配角色')
const user = ref<SysUserResponse>()
const roles = ref<SysRoleResponse[]>([])
const checkedRoleIds = ref<number[]>([])

/** 打开弹窗:并行拉用户已分配角色 + 角色全量(分页兜底 200 条,MVP 够用) */
const open = async (row: SysUserResponse) => {
  user.value = row
  title.value = `分配角色 - ${row.username}`
  visible.value = true
  loading.value = true
  try {
    const [roleIds, rolePage] = await Promise.all([sysUserApi.getRoles(row.id), sysRoleApi.page({ pageNo: 1, pageSize: 200 })])
    roles.value = rolePage.list
    checkedRoleIds.value = roleIds
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    await sysUserApi.putRoles(user.value!.id, { roleIds: checkedRoleIds.value })
    ElMessage.success('角色分配成功(重新登录后生效)')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.role-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 120px;
}
</style>
