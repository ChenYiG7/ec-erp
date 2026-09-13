<!--
  订单审核弹窗(#29 订单域补课人工槽,后端 POST /api/orders/{id}/review):
  裁定 通过(2)/驳回(3) + 备注;审核人/审核时间由后端 SecurityContext 与库 NOW() 回填,前端不传。
  已通过(2)为审核终态,列表页不显示审核按钮,本弹窗不再兜底拦截(状态机守卫在 SQL WHERE)
-->

<template>
  <el-dialog v-model="visible" title="订单审核" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="平台单号">
        <span class="review-dialog__text">{{ order?.platformOrderId || '-' }}</span>
      </el-form-item>
      <el-form-item v-if="order?.riskFlag" label="风控提示">
        <el-tag type="warning" effect="light">{{ order.riskFlag }}</el-tag>
      </el-form-item>
      <el-form-item label="裁定" required>
        <el-radio-group v-model="approve">
          <el-radio :value="true">通过</el-radio>
          <el-radio :value="false">驳回</el-radio>
        </el-radio-group>
        <span class="review-dialog__tip">通过后可创建发货单;驳回/待审核订单建单会被拦截</span>
      </el-form-item>
      <el-form-item label="审核备注">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="选填;驳回建议写明原因,便于复核"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElRadio, ElRadioGroup, ElTag } from 'element-plus'
import { ref } from 'vue'
import { shopOrderApi } from '@/api/apis/order/order'
import type { ShopOrderResponse } from '@/api/interface/order/order'

defineOptions({ name: 'ReviewDialog' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const order = ref<ShopOrderResponse>()
const approve = ref(true)
const remark = ref('')

/** 打开弹窗(编辑态不入参:人工裁定固定从"通过"起选) */
const open = (row: ShopOrderResponse) => {
  order.value = row
  approve.value = true
  remark.value = ''
  visible.value = true
}

const handleSubmit = async () => {
  const id = order.value?.id
  if (id == null) {
    return
  }
  submitting.value = true
  try {
    await shopOrderApi.review(id, {
      approve: approve.value,
      remark: remark.value.trim() || undefined,
    })
    ElMessage.success(approve.value ? '审核通过' : '已驳回')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.review-dialog {
  &__text {
    color: var(--el-text-color-regular);
  }
  &__tip {
    display: inline-block;
    width: 100%;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
