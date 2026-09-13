<!--
  FBA发货单建/改表单(fba-shipment,生成器外定制):表头(店铺/站点/国内发货仓/平台ShipmentId/备注)
  + 计划行子表(SkuSelector + 计划量,SHIPPED 装箱勾稽基准)+ 装箱两级编辑(箱:箱号/毛重/外箱尺寸;
  箱内件:SkuSelector + 件数)。仅 DRAFT 可调(update 后端守卫);
  箱内 SKU 必须在计划行内(后端守卫,前端预检)。
-->

<template>
  <el-dialog v-model="visible" :title="title" width="960px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="店铺" prop="shopId">
        <el-select v-model="formData.shopId" placeholder="归属店铺" filterable class="!w-full">
          <el-option v-for="s in shopOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="站点" prop="marketplace">
        <el-input v-model="formData.marketplace" placeholder="如 US/UK/DE" clearable class="!w-60" />
      </el-form-item>
      <el-form-item label="国内发货仓" prop="warehouseId">
        <el-select
          v-model="formData.warehouseId"
          placeholder="仅国内自仓(SELF),发出时在此仓出库"
          filterable
          class="!w-full"
        >
          <el-option v-for="w in whOptions" :key="w.value" :label="w.label" :value="w.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="平台ShipmentId" prop="platformShipmentId">
        <el-input v-model="formData.platformShipmentId" placeholder="可空;V2 SP-API 回填,V1 手填" clearable />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入备注" clearable />
      </el-form-item>

      <el-form-item label="计划行(SKU)">
        <div class="fba-plan">
          <div v-for="(item, pi) in formData.planItems" :key="pi" class="fba-plan__row">
            <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="fba-plan__sku" />
            <el-input-number
              v-model="item.planQty"
              :min="1"
              :precision="0"
              controls-position="right"
              class="fba-plan__qty"
              placeholder="计划量"
            />
            <el-button type="danger" link :icon="Delete" @click="formData.planItems.splice(pi, 1)" />
          </div>
          <el-button type="primary" link :icon="CirclePlus" @click="formData.planItems.push({})"
            >添加计划 SKU</el-button
          >
        </div>
      </el-form-item>

      <el-form-item label="装箱明细">
        <div class="fba-boxes">
          <div v-for="(box, bi) in formData.boxes" :key="bi" class="fba-boxes__card">
            <div class="fba-boxes__head">
              <el-input v-model="box.boxNo" placeholder="箱号(单内唯一)" class="fba-boxes__no" />
              <el-input v-model.number="box.weight" type="number" placeholder="毛重kg" class="fba-boxes__dim" />
              <el-input v-model.number="box.lengthCm" type="number" placeholder="长cm" class="fba-boxes__dim" />
              <el-input v-model.number="box.widthCm" type="number" placeholder="宽cm" class="fba-boxes__dim" />
              <el-input v-model.number="box.heightCm" type="number" placeholder="高cm" class="fba-boxes__dim" />
              <el-button type="danger" link :icon="Delete" @click="formData.boxes.splice(bi, 1)" />
            </div>
            <div v-for="(item, ii) in box.items" :key="ii" class="fba-boxes__item">
              <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="fba-boxes__sku" />
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :precision="0"
                controls-position="right"
                class="fba-boxes__qty"
                placeholder="件数"
              />
              <el-button type="danger" link :icon="Delete" @click="box.items.splice(ii, 1)" />
            </div>
            <el-button type="primary" link :icon="CirclePlus" @click="box.items.push({})">添加箱内 SKU</el-button>
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
  ElSelect,
} from 'element-plus'
import { ref } from 'vue'
import { fbaShipmentApi } from '@/api/apis/fulfill/fbaShipment'
import { shopApi } from '@/api/apis/shop/shop'
import { warehouseApi } from '@/api/apis/warehouse/warehouse'
import type { FbaShipmentResponse, FbaShipmentSaveRequest } from '@/api/interface/fulfill/fbaShipment'
import SkuSelector from '@/components/SkuSelector/index.vue'

defineOptions({ name: 'FbaShipmentForm' })

const emit = defineEmits<{ saved: [] }>()

/** 计划行/箱草稿(数值可空,提交前收敛校验) */
interface PlanDraft {
  skuId?: number
  planQty?: number
}
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
const shopOptions = ref<{ label: string; value: number }[]>([])
const whOptions = ref<{ label: string; value: number }[]>([])
const formData = ref<{
  shopId?: number
  marketplace?: string
  warehouseId?: number
  platformShipmentId?: string
  remark?: string
  planItems: PlanDraft[]
  boxes: BoxDraft[]
}>({ planItems: [{}], boxes: [] })

const rules: FormRules = {
  shopId: [{ required: true, message: '请选择店铺', trigger: 'change' }],
  marketplace: [{ required: true, message: '请填写站点', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择国内发货仓', trigger: 'change' }],
}

const title = ref('FBA发货单')

const addBox = () => formData.value.boxes.push({ boxNo: `B${formData.value.boxes.length + 1}`, items: [] })

/** 打开弹窗;edit 取详情回填计划行与装箱树(列表行不带子表,update 仅 DRAFT) */
const open = async (m: 'add' | 'edit', row?: FbaShipmentResponse) => {
  mode.value = m
  editId.value = row?.id
  title.value = (m === 'add' ? '新增' : '编辑') + 'FBA发货单'
  formData.value = { planItems: [{}], boxes: [] }
  visible.value = true
  // 店铺/仓库选项整页拉取(基础数据量小;仓库只给国内自仓 SELF,同后端守卫口径)
  const [shops, whs] = await Promise.all([
    shopApi.page({ pageNo: 1, pageSize: 500 }),
    warehouseApi.page({ pageNo: 1, pageSize: 500 }),
  ])
  shopOptions.value = shops.list.filter(s => s.status === 1).map(s => ({ label: s.shopName, value: s.id }))
  whOptions.value = whs.list
    .filter(w => w.status === 1 && w.whType === 'SELF')
    .map(w => ({ label: w.whName, value: w.id }))
  if (m === 'edit' && row) {
    const detail = await fbaShipmentApi.detail(row.id)
    formData.value = {
      shopId: detail.shopId,
      marketplace: detail.marketplace,
      warehouseId: detail.warehouseId,
      platformShipmentId: detail.platformShipmentId ?? undefined,
      remark: detail.remark ?? undefined,
      planItems: (detail.planItems ?? []).map(i => ({ skuId: i.skuId, planQty: i.planQty })),
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
  // 计划行校验:非空、SKU 必填且不重复、计划量>=1
  if (!formData.value.planItems.length) {
    ElMessage.warning('计划行不能为空,至少录入 1 行 SKU 清单')
    return
  }
  const planSkuIds = new Set<number>()
  for (const item of formData.value.planItems) {
    if (!item.skuId || !item.planQty || item.planQty < 1) {
      ElMessage.warning('计划行存在未填 SKU 或计划量非法的行')
      return
    }
    if (planSkuIds.has(item.skuId)) {
      ElMessage.warning('计划行同一 SKU 重复,请合并为一行')
      return
    }
    planSkuIds.add(item.skuId)
  }
  // 装箱校验:有箱则箱号必填且单内唯一、尺寸为正、每箱至少一行、箱内 SKU 必须在计划行内(空箱单允许,装箱动作再守)
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
    for (const it of box.items) {
      if (!it.skuId || !it.quantity || it.quantity < 1) {
        ElMessage.warning(`箱 ${no} 存在未填 SKU 或件数非法的行`)
        return
      }
      if (!planSkuIds.has(it.skuId)) {
        ElMessage.warning(`箱 ${no} 内含计划外 SKU,请先加入计划行`)
        return
      }
    }
    const skuIds = box.items.map(it => it.skuId)
    if (new Set(skuIds).size !== skuIds.length) {
      ElMessage.warning(`箱 ${no} 内同一 SKU 重复,请合并为一行`)
      return
    }
  }
  submitting.value = true
  try {
    // 空箱单(草稿先建计划)传 boxes:[];status/单号/时间列由后端回填,请求体不含
    const payload: FbaShipmentSaveRequest = {
      shopId: formData.value.shopId!,
      marketplace: formData.value.marketplace!.trim(),
      warehouseId: formData.value.warehouseId!,
      platformShipmentId: formData.value.platformShipmentId,
      remark: formData.value.remark,
      planItems: formData.value.planItems.map(i => ({ skuId: i.skuId!, planQty: i.planQty! })),
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
      await fbaShipmentApi.create(payload)
    } else {
      await fbaShipmentApi.update(editId.value!, payload)
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
.fba-plan {
  width: 100%;
  &__row {
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
.fba-boxes {
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
