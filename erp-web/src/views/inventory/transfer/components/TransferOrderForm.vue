<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  仓内作业定制(2026-09-11,TODO#30):调出/调入仓下拉 + 明细子表行编辑(生成器不做子表布局,对齐 PurchaseOrderForm)
-->

<template>
  <el-dialog v-model="visible" :title="title" width="720px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="调拨单号" prop="transferNo">
        <el-input v-model="formData.transferNo" placeholder="请输入调拨单号(TR+日期+序号)" clearable />
      </el-form-item>
      <el-form-item label="调出仓" prop="fromWarehouseId">
        <el-select v-model="formData.fromWarehouseId" placeholder="请选择调出仓" filterable class="!w-full">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="调入仓" prop="toWarehouseId">
        <el-select v-model="formData.toWarehouseId" placeholder="请选择调入仓" filterable class="!w-full">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="调拨明细" prop="items">
        <!-- 明细行编辑:行内 skuId/数量必填正数(提交前校验);确认时调出仓可用不足由后端整单回滚 -->
        <div class="tf-items">
          <div v-for="(item, index) in formData.items" :key="index" class="tf-items__row">
            <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="tf-items__sku" />
            <el-input-number
              v-model="item.quantity"
              :min="1"
              :precision="0"
              controls-position="right"
              class="tf-items__qty"
              placeholder="数量"
            />
            <el-button type="danger" link :icon="Delete" @click="formData.items.splice(index, 1)" />
          </div>
          <el-button type="primary" link :icon="CirclePlus" @click="formData.items.push({ quantity: undefined })"
            >添加明细行</el-button
          >
        </div>
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
import { ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { CirclePlus, Delete } from '@element-plus/icons-vue'
import { transferOrderApi } from '@/api/apis/inventory/transfer'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import SkuSelector from '@/components/SkuSelector/index.vue'
import type { TransferOrderSaveRequest, TransferOrderResponse } from '@/api/interface/inventory/transfer'

defineOptions({ name: 'TransferOrderForm' })

const emit = defineEmits<{ saved: [] }>()

/** 明细行草稿(skuId 待填,提交前校验收敛为 TransferOrderItemSaveRequest) */
interface ItemDraft {
  skuId?: number
  quantity?: number
}

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])
// 类型断言收敛在表单初始化(空表单起填,提交前 rules + 明细校验 + 后端兜底校验)
const formData = ref<{
  transferNo: string
  fromWarehouseId?: number
  toWarehouseId?: number
  remark?: string
  items: ItemDraft[]
}>({
  transferNo: '',
  items: [],
})

const rules: FormRules = {
  transferNo: [{ required: true, message: '请输入调拨单号', trigger: 'blur' }],
  fromWarehouseId: [{ required: true, message: '请选择调出仓', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓', trigger: 'change' }],
}

const title = ref('调拨单')

/** 打开弹窗(mode:add/edit);edit 取详情回填(列表行不带明细,update 仅 DRAFT) */
const open = async (m: 'add' | 'edit', row?: TransferOrderResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '调拨单'
  formData.value = { transferNo: '', items: [] }
  visible.value = true
  if (m === 'edit' && row) {
    const detail = await transferOrderApi.detail(row.id)
    formData.value = {
      transferNo: detail.transferNo,
      fromWarehouseId: detail.fromWarehouseId,
      toWarehouseId: detail.toWarehouseId,
      remark: detail.remark,
      items: (detail.items ?? []).map(it => ({ skuId: it.skuId, quantity: it.quantity })),
    }
  }
  warehouseOptions.value = await fetchWarehouseOptions()
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  // 调出仓 ≠ 调入仓(后端兜底同规则,前端先拦一次省一次往返)
  if (formData.value.fromWarehouseId === formData.value.toWarehouseId) {
    ElMessage.warning('调出仓与调入仓不能相同')
    return
  }
  // 明细行校验:至少一行 + 行内 skuId/数量必填正数
  if (!formData.value.items.length) {
    ElMessage.warning('请至少添加一条调拨明细')
    return
  }
  if (formData.value.items.some(it => !it.skuId || !it.quantity || it.quantity < 1)) {
    ElMessage.warning('明细行需填写 SKU,数量须为正数')
    return
  }
  submitting.value = true
  try {
    // createdBy/status 后端回填(固定 DRAFT + SecurityContext),请求体不含该字段
    const payload = {
      transferNo: formData.value.transferNo,
      fromWarehouseId: formData.value.fromWarehouseId,
      toWarehouseId: formData.value.toWarehouseId,
      remark: formData.value.remark,
      items: formData.value.items.map(it => ({ skuId: it.skuId!, quantity: it.quantity! })),
    } as TransferOrderSaveRequest
    if (mode.value === 'add') {
      await transferOrderApi.create(payload)
    } else {
      await transferOrderApi.update(editId.value!, payload)
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
<style scoped lang="scss">
.tf-items {
  width: 100%;
  &__row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }
  &__sku {
    flex: 1.2;
  }
  &__qty {
    flex: 0.8;
  }
}
</style>
