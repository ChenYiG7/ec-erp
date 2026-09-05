/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="平台" prop="platform">
        <Dict v-model="formData.platform" code="shop_platform" type="select" />
      </el-form-item>
      <el-form-item label="店铺名称" prop="shopName">
        <el-input v-model="formData.shopName" placeholder="请输入店铺名称" clearable />
      </el-form-item>
      <el-form-item label="卖家ID" prop="sellerId">
        <el-input v-model="formData.sellerId" placeholder="请输入卖家ID" clearable />
      </el-form-item>
      <el-form-item label="平台应用Key" prop="appKey">
        <el-input v-model="formData.appKey" placeholder="请输入平台应用Key" clearable />
      </el-form-item>
      <el-form-item label="平台应用Secret" prop="appSecret">
        <el-input v-model="formData.appSecret" placeholder="请输入平台应用Secret" clearable />
      </el-form-item>
      <el-form-item label="刷新令牌" prop="refreshToken">
        <el-input v-model="formData.refreshToken" placeholder="请输入刷新令牌" clearable />
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
import Dict from '@/components/Dict/index.vue'
import { shopApi } from '@/api/apis/shop/shop'
import type { ShopSaveRequest, ShopResponse } from '@/api/interface/shop/shop'

defineOptions({ name: 'ShopForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)
const formData = ref<ShopSaveRequest>({} as ShopSaveRequest)

const rules: FormRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }]
}

const title = ref('店铺管理')

/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */
const open = (m: 'add' | 'edit', row?: ShopResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '店铺管理'
  formData.value = {} as ShopSaveRequest
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
      await shopApi.create(formData.value)
    } else {
      await shopApi.update(editId.value!, formData.value)
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
