/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

<template>
  <el-dialog v-model="visible" :title="title" width="720px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="采购单号" prop="poNo">
        <el-input v-model="formData.poNo" placeholder="请输入采购单号" clearable />
      </el-form-item>
      <el-form-item label="供应商" prop="supplierId">
        <el-select v-model="formData.supplierId" placeholder="请选择供应商" filterable class="!w-full">
          <el-option v-for="s in supplierOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="收货仓库" prop="warehouseId">
        <el-select v-model="formData.warehouseId" placeholder="请选择收货仓库" filterable class="!w-full">
          <el-option v-for="w in warehouseOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="采购明细" prop="items">
        <!-- TODO(#10) 建单明细行编辑:行内 skuId/数量必填正数(提交前校验);后端兜底 SKU 存在/同行重复/仓库存在 -->
        <div class="po-items">
          <div v-for="(item, index) in formData.items" :key="index" class="po-items__row">
            <!-- SKU 搜索选择器(#16 收口裸 ID 手填):商品 keyword 搜索 → SPU 内 SKU,选中即 skuId;存在性校验在后端 -->
            <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="po-items__sku" />
            <el-input-number v-model="item.quantity" :min="1" :precision="0" controls-position="right" class="po-items__qty" placeholder="数量" />
            <el-input v-model="item.purchasePrice" placeholder="单价(选填)" clearable class="po-items__price" />
            <el-button type="danger" link :icon="Delete" @click="formData.items.splice(index, 1)" />
          </div>
          <el-button type="primary" link :icon="CirclePlus" @click="formData.items.push({ quantity: undefined })">添加明细行</el-button>
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
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElOption, ElSelect } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { CirclePlus, Delete } from '@element-plus/icons-vue'
import { purchaseOrderApi } from '@/api/apis/purchase/order'
import { fetchSupplierOptions } from '@/api/apis/purchase/options'
import { fetchWarehouseOptions } from '@/api/apis/warehouse/options'
import SkuSelector from '@/components/SkuSelector/index.vue'
import type { PurchaseOrderSaveRequest, PurchaseOrderResponse } from '@/api/interface/purchase/order'

defineOptions({ name: 'PurchaseOrderForm' })

const emit = defineEmits<{ saved: [] }>()

/** 明细行草稿(skuId 待填,提交前校验收敛为 PurchaseOrderItemSaveRequest) */
interface ItemDraft {
  skuId?: number
  quantity?: number
  purchasePrice?: string
}

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 供应商/仓库下拉选项(open 时拉取;回填值命中选项才显示 label)
const supplierOptions = ref<Awaited<ReturnType<typeof fetchSupplierOptions>>>([])
const warehouseOptions = ref<Awaited<ReturnType<typeof fetchWarehouseOptions>>>([])
// 类型断言收敛在表单初始化(空表单起填,提交前 rules + 明细校验 + 后端兜底校验)
const formData = ref<{ poNo: string; supplierId?: number; warehouseId?: number; remark?: string; items: ItemDraft[] }>({
  poNo: '',
  items: []
})

const rules: FormRules = {
  poNo: [{ required: true, message: '请输入采购单号', trigger: 'blur' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择收货仓库', trigger: 'change' }]
}

const title = ref('采购单')

/** 打开弹窗(mode:add/edit);edit 取详情回填(列表行不带明细,update 仅 DRAFT) */
const open = async (m: 'add' | 'edit', row?: PurchaseOrderResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '采购单'
  formData.value = { poNo: '', items: [] }
  visible.value = true
  if (m === 'edit' && row) {
    const detail = await purchaseOrderApi.detail(row.id)
    formData.value = {
      poNo: detail.poNo,
      supplierId: detail.supplierId,
      warehouseId: detail.warehouseId,
      remark: detail.remark,
      // 单价 DECIMAL(12,4) 序列化为 string,直存直显禁浮点(docs/09 §6)
      items: (detail.items ?? []).map(it => ({ skuId: it.skuId, quantity: it.quantity, purchasePrice: it.purchasePrice }))
    }
  }
  supplierOptions.value = await fetchSupplierOptions()
  warehouseOptions.value = await fetchWarehouseOptions()
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  // 明细行校验:至少一行 + 行内 skuId/数量必填正数(空串单价收敛 undefined,后端选填)
  if (!formData.value.items.length) {
    ElMessage.warning('请至少添加一条采购明细')
    return
  }
  if (formData.value.items.some(it => !it.skuId || !it.quantity || it.quantity < 1)) {
    ElMessage.warning('明细行需填写 SKU,数量须为正数')
    return
  }
  submitting.value = true
  try {
    const items = formData.value.items.map(it => ({ skuId: it.skuId!, quantity: it.quantity!, purchasePrice: it.purchasePrice || undefined }))
    // createdBy 后端接 SecurityContext 回填(CurrentUserApi),请求体不含该字段
    const payload = { ...formData.value, items } as PurchaseOrderSaveRequest
    if (mode.value === 'add') {
      await purchaseOrderApi.create(payload)
    } else {
      await purchaseOrderApi.update(editId.value!, payload)
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
.po-items {
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
  &__price {
    flex: 1;
  }
}
</style>
