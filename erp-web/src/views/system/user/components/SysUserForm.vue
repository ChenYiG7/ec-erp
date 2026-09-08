/** * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照) * 默认存在即跳过:人工改动不会被 --force
之外的任何方式覆盖;重新生成前先 diff 人工改动 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md */

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="formData.username" placeholder="请输入用户名" clearable />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="formData.nickname" placeholder="请输入昵称" clearable />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="formData.email" placeholder="请输入邮箱" clearable />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="formData.phone" placeholder="请输入手机号" clearable />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="formData.password" placeholder="请输入密码" clearable />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" clearable placeholder="请选择状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sysUserApi } from '@/api/apis/system/user'
import type { SysUserSaveRequest, SysUserResponse } from '@/api/interface/system/user'

defineOptions({ name: 'SysUserForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)
const formData = ref<SysUserSaveRequest>({} as SysUserSaveRequest)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
}

const title = ref('用户管理')

/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */
const open = (m: 'add' | 'edit', row?: SysUserResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '用户管理'
  formData.value = {} as SysUserSaveRequest
  if (row) {
    Object.assign(formData.value, row)
  }
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'add') {
      await sysUserApi.create(formData.value)
    } else {
      await sysUserApi.update(editId.value!, formData.value)
    }
    ElMessage.success('保存成功')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
