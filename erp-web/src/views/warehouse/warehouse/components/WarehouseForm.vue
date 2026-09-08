/** * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照) * 默认存在即跳过:人工改动不会被 --force
之外的任何方式覆盖;重新生成前先 diff 人工改动 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md */

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="仓库名称" prop="whName">
        <el-input v-model="formData.whName" placeholder="请输入仓库名称" clearable />
      </el-form-item>
      <el-form-item label="仓库类型" prop="whType">
        <el-select v-model="formData.whType" clearable placeholder="请选择仓库类型">
          <el-option label="自仓" :value="'SELF'" />
          <el-option label="FBA" :value="'FBA'" />
          <el-option label="海外仓" :value="'OVERSEAS'" />
          <el-option label="虚拟仓" :value="'VIRTUAL'" />
        </el-select>
      </el-form-item>
      <el-form-item label="国家" prop="country">
        <el-input v-model="formData.country" placeholder="请输入国家" clearable />
      </el-form-item>
      <el-form-item label="仓库地址" prop="address">
        <el-input v-model="formData.address" placeholder="请输入仓库地址" clearable />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" clearable placeholder="请选择状态">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
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
import { warehouseApi } from '@/api/apis/warehouse/warehouse'
import type { WarehouseSaveRequest, WarehouseResponse } from '@/api/interface/warehouse/warehouse'

defineOptions({ name: 'WarehouseForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)
const formData = ref<WarehouseSaveRequest>({} as WarehouseSaveRequest)

const rules: FormRules = {
  whName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  whType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
}

const title = ref('仓库管理')

/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */
const open = (m: 'add' | 'edit', row?: WarehouseResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '仓库管理'
  formData.value = {} as WarehouseSaveRequest
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
      await warehouseApi.create(formData.value)
    } else {
      await warehouseApi.update(editId.value!, formData.value)
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
