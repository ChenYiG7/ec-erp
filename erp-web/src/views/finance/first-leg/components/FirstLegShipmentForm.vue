<!--
  头程发货单建/改表单(生成器外定制,#33):表头(国内仓→海外/FBA、分摊策略、备注)+ 装箱两级编辑
  (箱:箱号/毛重kg/外箱长宽高cm;箱内件:SkuSelector + 件数)。仅 DRAFT 可调(update 后端守卫);
  草稿允许先建空单后补箱,但箱号/箱内件一旦填写需通过行校验,装箱完成动作再守"至少1箱有件"。
-->

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="国内发货仓" prop="fromWarehouseId">
        <el-select v-model="formData.fromWarehouseId" placeholder="仅国内自仓(SELF)" filterable class="!w-full">
          <el-option v-for="w in fromOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="目的仓" prop="toWarehouseId">
        <el-select v-model="formData.toWarehouseId" placeholder="海外仓(OVERSEAS)/FBA仓" filterable class="!w-full">
          <el-option v-for="w in toOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="分摊策略" prop="allocateStrategy">
        <el-radio-group v-model="formData.allocateStrategy">
          <el-radio value="WEIGHT">按重量(qty×重量g,默认)</el-radio>
          <el-radio value="QTY">按数量</el-radio>
          <el-radio value="AMOUNT">按金额(最近采购价)</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入备注" clearable />
      </el-form-item>

      <el-form-item label="装箱明细">
        <div class="fl-boxes">
          <div v-for="(box, bi) in formData.boxes" :key="bi" class="fl-boxes__card">
            <div class="fl-boxes__head">
              <el-input v-model="box.boxNo" placeholder="箱号(单内唯一)" class="fl-boxes__no" />
              <el-input v-model.number="box.weight" type="number" placeholder="毛重kg" class="fl-boxes__dim" />
              <el-input v-model.number="box.lengthCm" type="number" placeholder="长cm" class="fl-boxes__dim" />
              <el-input v-model.number="box.widthCm" type="number" placeholder="宽cm" class="fl-boxes__dim" />
              <el-input v-model.number="box.heightCm" type="number" placeholder="高cm" class="fl-boxes__dim" />
              <el-button type="danger" link :icon="Delete" @click="formData.boxes.splice(bi, 1)" />
            </div>
            <div v-for="(item, ii) in box.items" :key="ii" class="fl-boxes__item">
              <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="fl-boxes__sku" />
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :precision="0"
                controls-position="right"
                class="fl-boxes__qty"
                placeholder="件数"
              />
              <el-button type="danger" link :icon="Delete" @click="box.items.splice(ii, 1)" />
            </div>
            <el-button type="primary" link :icon="CirclePlus" @click="box.items.push({ quantity: undefined })"
              >添加箱内 SKU</el-button
            >
          </div>
          <el-button type="primary" :icon="CirclePlus" @click="addBox">添加箱(草稿可先不填)</el-button>
        </div>
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
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { firstLegShipmentApi } from '@/api/apis/finance/firstLeg'
import { warehouseApi } from '@/api/apis/warehouse/warehouse'
import type { FirstLegShipmentResponse, FirstLegShipmentSaveRequest } from '@/api/interface/finance/firstLeg'
import SkuSelector from '@/components/SkuSelector/index.vue'

defineOptions({ name: 'FirstLegShipmentForm' })

const emit = defineEmits<{ saved: [] }>()

/** 箱草稿(箱号/数值均可空,提交前收敛校验) */
interface BoxDraft {
  boxNo?: string
  weight?: number
  lengthCm?: number
  widthCm?: number
  heightCm?: number
  items: { skuId?: number; quantity?: number }[]
}

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
// 流向选项按仓型过滤(头程:国内 SELF → OVERSEAS/FBA)
const fromOptions = ref<{ label: string; value: number }[]>([])
const toOptions = ref<{ label: string; value: number }[]>([])
const formData = ref<{
  fromWarehouseId?: number
  toWarehouseId?: number
  allocateStrategy: string
  remark?: string
  boxes: BoxDraft[]
}>({ allocateStrategy: 'WEIGHT', boxes: [] })

const rules: FormRules = {
  fromWarehouseId: [{ required: true, message: '请选择国内发货仓', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择目的仓', trigger: 'change' }],
  allocateStrategy: [{ required: true, message: '请选择分摊策略', trigger: 'change' }],
}

const title = ref('头程发货单')

const addBox = () => formData.value.boxes.push({ boxNo: `B${formData.value.boxes.length + 1}`, items: [] })

/** 打开弹窗;edit 取详情回填装箱树(列表行不带 boxes,update 仅 DRAFT) */
const open = async (m: 'add' | 'edit', row?: FirstLegShipmentResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + '头程发货单'
  formData.value = { allocateStrategy: 'WEIGHT', boxes: [] }
  visible.value = true
  const { list } = await warehouseApi.page({ pageNo: 1, pageSize: 500 })
  fromOptions.value = list
    .filter(w => w.status === 1 && w.whType === 'SELF')
    .map(w => ({ label: w.whName, value: w.id }))
  toOptions.value = list
    .filter(w => w.status === 1 && (w.whType === 'OVERSEAS' || w.whType === 'FBA'))
    .map(w => ({ label: `${w.whName}(${w.whType})`, value: w.id }))
  if (m === 'edit' && row) {
    const detail = await firstLegShipmentApi.detail(row.id)
    formData.value = {
      fromWarehouseId: detail.fromWarehouseId,
      toWarehouseId: detail.toWarehouseId,
      allocateStrategy: detail.allocateStrategy || 'WEIGHT',
      remark: detail.remark ?? undefined,
      boxes: (detail.boxes ?? []).map(b => ({
        boxNo: b.boxNo,
        weight: b.weight ?? undefined,
        lengthCm: b.lengthCm ?? undefined,
        widthCm: b.widthCm ?? undefined,
        heightCm: b.heightCm ?? undefined,
        items: (b.items ?? []).map(it => ({ skuId: it.skuId, quantity: it.quantity })),
      })),
    }
  }
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  if (formData.value.fromWarehouseId === formData.value.toWarehouseId) {
    ElMessage.warning('国内发货仓与目的仓不能相同')
    return
  }
  // 装箱校验:有箱则箱号必填且单内唯一、尺寸为正、每箱至少一行且行内 SKU/件数合法(空箱单允许,装箱动作再守)
  const boxNos = new Set<string>()
  for (const box of formData.value.boxes) {
    const no = box.boxNo?.trim()
    if (!no) {
      ElMessage.warning('存在未填箱号的箱,请补箱号或删除该箱')
      return
    }
    if (boxNos.has(no)) {
      ElMessage.warning(`箱号 ${no} 在单内重复`)
      return
    }
    boxNos.add(no)
    for (const dim of [box.lengthCm, box.widthCm, box.heightCm] as (number | undefined)[]) {
      if (dim != null && dim < 1) {
        ElMessage.warning(`箱 ${no} 的外箱长宽高必须为正整数(cm)`)
        return
      }
    }
    if (box.weight != null && box.weight < 0) {
      ElMessage.warning(`箱 ${no} 毛重不能为负`)
      return
    }
    if (!box.items.length) {
      ElMessage.warning(`箱 ${no} 至少添加一个 SKU;空箱请删除后再保存`)
      return
    }
    if (box.items.some(it => !it.skuId || !it.quantity || it.quantity < 1)) {
      ElMessage.warning(`箱 ${no} 存在未填 SKU 或件数非法的行`)
      return
    }
    const skuIds = box.items.map(it => it.skuId)
    if (new Set(skuIds).size !== skuIds.length) {
      ElMessage.warning(`箱 ${no} 内同一 SKU 重复,请合并为一行`)
      return
    }
  }
  submitting.value = true
  try {
    // 空箱单(草稿先建头)传 boxes:[];status/单号/运费列由后端回填,请求体不含
    const payload: FirstLegShipmentSaveRequest = {
      fromWarehouseId: formData.value.fromWarehouseId!,
      toWarehouseId: formData.value.toWarehouseId!,
      allocateStrategy: formData.value.allocateStrategy,
      remark: formData.value.remark,
      boxes: formData.value.boxes.map(b => ({
        boxNo: b.boxNo!.trim(),
        weight: b.weight,
        lengthCm: b.lengthCm,
        widthCm: b.widthCm,
        heightCm: b.heightCm,
        items: b.items.map(it => ({ skuId: it.skuId!, quantity: it.quantity! })),
      })),
    }
    if (mode.value === 'add') {
      await firstLegShipmentApi.create(payload)
    } else {
      await firstLegShipmentApi.update(editId.value!, payload)
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
.fl-boxes {
  width: 100%;
  &__card {
    padding: 10px 12px 4px;
    margin-bottom: 10px;
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
  }
  &__head {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }
  &__no {
    width: 150px;
  }
  &__dim {
    width: 110px;
  }
  &__item {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }
  &__sku {
    flex: 1.3;
  }
  &__qty {
    flex: 0.7;
  }
}
</style>
