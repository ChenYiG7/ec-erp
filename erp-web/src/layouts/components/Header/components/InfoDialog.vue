<template>
  <el-dialog v-model="dialogVisible" title="个人信息" width="500px" draggable>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="用户名">{{ userStore.userInfo.username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ userStore.userInfo.name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="角色">
        <el-tag v-for="role in userStore.userInfo.roles || []" :key="role" class="role-tag" size="small">
          {{ role }}
        </el-tag>
        <span v-if="!userStore.userInfo.roles?.length">-</span>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="dialogVisible = false">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InfoDialog',
})
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElTag } from 'element-plus'
import { ref } from 'vue'
import { useUserStore } from '@/stores/modules/user'

const userStore = useUserStore()
const dialogVisible = ref(false)
const openDialog = () => {
  dialogVisible.value = true
}

defineExpose({ openDialog })
</script>

<style scoped lang="scss">
.role-tag {
  margin-right: 6px;
}
</style>
