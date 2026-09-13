<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  仓内作业定制(2026-09-11,TODO#30):盘点仓下拉/范围单选/SKU 集行编辑联动;编辑回填从详情 items 取 skuIds
-->

<template>
  <el-dialog v-model="visible" :title="title" width="680px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="盘点单号" prop="stocktakeNo">
        <el-input v-model="formData.stocktakeNo" placeholder="请输入盘点单号(ST+日期+序号)" clearable />
      </el-form-item>
      <el-form-item label="盘点仓" prop="warehouseId">
        <el-select v-model="formData.warehouseId" placeholder="请选择盘点仓" filterable class="!w-full">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="盘点范围" prop="scopeType">
        <el-radio-group v-model="formData.scopeType">
          <el-radio value="ALL">全仓(按该仓现有库存行快照)</el-radio>
          <el-radio value="SKU_SET">选定 SKU 集</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.scopeType === 'SKU_SET'" label="SKU 集" prop="skuIds">
        <!-- SKU_SET 范围:逐行 SkuSelector 圈定(库位不做,只能按 SKU 集圈定);无库存行的 SKU 快照账面按 0 -->
        <div class="sku-ids">
          <div v-for="(skuId, index) in formData.skuIds" :key="index" class="sku-ids__row">
            <SkuSelector v-model="formData.skuIds[index]" placeholder="搜商品名/SKU编码" class="sku-ids__sku" />
            <el-button type="danger" link :icon="Delete" @click="formData.skuIds!.splice(index, 1)" />
          </div>
          <el-button type="primary" link :icon="CirclePlus" @click="formData.skuIds!.push(undefined)"
            >添加 SKU</el-button
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
import { CirclePlus, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { stocktakeOrderApi } from '@/api/apis/inventory/stocktake'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import type { StocktakeOrderResponse, StocktakeOrderSaveRequest } from '@/api/interface/inventory/stocktake'
import SkuSelector from '@/components/SkuSelector/index.vue'

defineOptions({ name: 'StocktakeOrderForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])
// 类型断言收敛在表单初始化(空表单起填,提交前 rules + SKU 集校验 + 后端兜底校验)
const formData = ref<{
  stocktakeNo: string
  warehouseId?: number
  scopeType: string
  remark?: string
  skuIds: (number | undefined)[]
}>({
  stocktakeNo: '',
  scopeType: 'ALL',
  skuIds: [],
})

const rules: FormRules = {
  stocktakeNo: [{ required: true, message: '请输入盘点单号', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择盘点仓', trigger: 'change' }],
  scopeType: [{ required: true, message: '请选择盘点范围', trigger: 'change' }],
}

const title = ref('盘点单')

/** 打开弹窗(mode:add/edit);edit 取详情回填(明细行重建快照,scopeType=SKU_SET 时 skuIds 取自 items) */
const open = async (m: 'add' | 'edit', row?: StocktakeOrderResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '盘点单'
  formData.value = { stocktakeNo: '', scopeType: 'ALL', skuIds: [] }
  visible.value = true
  if (m === 'edit' && row) {
    const detail = await stocktakeOrderApi.detail(row.id)
    formData.value = {
      stocktakeNo: detail.stocktakeNo,
      warehouseId: detail.warehouseId,
      scopeType: detail.scopeType,
      remark: detail.remark,
      skuIds: (detail.items ?? []).map(it => it.skuId),
    }
  }
  warehouseOptions.value = await fetchWarehouseOptions()
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  // SKU_SET 范围校验:至少一行 + 行行已选 + 去重(ALL 时忽略 skuIds)
  let skuIds: number[] | undefined
  if (formData.value.scopeType === 'SKU_SET') {
    const rows = formData.value.skuIds ?? []
    if (!rows.length) {
      ElMessage.warning('选定 SKU 集盘点时请至少添加一个 SKU')
      return
    }
    if (rows.some(id => !id)) {
      ElMessage.warning('SKU 集存在未选择的行,请补齐或删除')
      return
    }
    skuIds = rows as number[]
    if (new Set(skuIds).size !== skuIds.length) {
      ElMessage.warning('盘点 SKU 集存在重复,请去重')
      return
    }
  }
  submitting.value = true
  try {
    // createdBy/status 后端回填(固定 DRAFT + SecurityContext),请求体不含该字段
    const payload = {
      stocktakeNo: formData.value.stocktakeNo,
      warehouseId: formData.value.warehouseId,
      scopeType: formData.value.scopeType,
      remark: formData.value.remark,
      skuIds,
    } as StocktakeOrderSaveRequest
    if (mode.value === 'add') {
      await stocktakeOrderApi.create(payload)
    } else {
      await stocktakeOrderApi.update(editId.value!, payload)
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
.sku-ids {
  width: 100%;
  &__row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }
  &__sku {
    flex: 1;
  }
}
</style>
