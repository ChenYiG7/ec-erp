<template>
  <el-dialog v-model="dialogVisible" title="修改密码" width="500px" draggable destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineOptions({
  name: 'PasswordDialog'
})
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { UserApi } from '@/api/apis/system/user'
import { useUserStore } from '@/stores/modules/user'
import { logoutWithRedirect } from '@/utils'
import { useRoute } from 'vue-router'

const route = useRoute()
const userStore = useUserStore()
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
// TODO(#16) 密码强度规则待与后端约定后收口(当前仅后端校验非空)
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const openDialog = () => {
  dialogVisible.value = true
}

const submit = () => {
  formRef.value?.validate(async valid => {
    if (!valid) {
      return
    }
    submitting.value = true
    try {
      await UserApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
      ElMessage.success('密码修改成功，请重新登录')
      dialogVisible.value = false
      userStore.clearUserInfo()
      logoutWithRedirect(route)
    } finally {
      submitting.value = false
    }
  })
}

defineExpose({ openDialog })
</script>
