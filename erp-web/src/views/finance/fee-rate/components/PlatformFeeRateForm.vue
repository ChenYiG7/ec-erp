/** * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照) * 默认存在即跳过:人工改动不会被 --force
之外的任何方式覆盖;重新生成前先 diff 人工改动 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md */

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="平台" prop="platform">
        <Dict v-model="formData.platform" code="shop_platform" type="select" />
      </el-form-item>
      <el-form-item label="费种" prop="feeType">
        <el-select v-model="formData.feeType" clearable placeholder="请选择费种">
          <el-option label="佣金" :value="'COMMISSION'" />
        </el-select>
      </el-form-item>
      <el-form-item label="费率" prop="rate">
        <el-input v-model="formData.rate" placeholder="0~1 小数,如 0.15=15%" clearable />
        <!-- 费率 DECIMAL(8,6) 字符串直存直显(docs/09 §6 禁浮点计算);后端校验 0<rate<1 -->
      </el-form-item>
      <el-form-item label="生效起" prop="effFrom">
        <el-date-picker v-model="formData.effFrom" type="date" value-format="YYYY-MM-DD" placeholder="选择生效起" />
      </el-form-item>
      <el-form-item label="生效止(空=长期)" prop="effTo">
        <el-date-picker
          v-model="formData.effTo"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="留空=长期有效"
          clearable
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入备注" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { platformFeeRateApi } from '@/api/apis/finance/fee-rate'
import type { PlatformFeeRateResponse, PlatformFeeRateSaveRequest } from '@/api/interface/finance/fee-rate'
import Dict from '@/components/Dict/index.vue'

defineOptions({ name: 'PlatformFeeRateForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)
const formData = ref<PlatformFeeRateSaveRequest>({} as PlatformFeeRateSaveRequest)

const rules: FormRules = {
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  feeType: [{ required: true, message: '请选择费种', trigger: 'change' }],
  rate: [{ required: true, message: '请输入费率', trigger: 'blur' }],
  effFrom: [{ required: true, message: '请输入生效起', trigger: 'blur' }],
}

const title = ref('平台费率')

/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */
const open = (m: 'add' | 'edit', row?: PlatformFeeRateResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '平台费率'
  formData.value = {} as PlatformFeeRateSaveRequest
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
      await platformFeeRateApi.create(formData.value)
    } else {
      await platformFeeRateApi.update(editId.value!, formData.value)
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
