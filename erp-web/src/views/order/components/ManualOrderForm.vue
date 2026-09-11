<!--
  内销订单录单/改单表单(#29 订单域补课人工槽):
  - 新增 POST /api/orders/manual:平台/单号(合成 MAN-*)/状态/金额/审核态全部后端派生,前端只收
    店铺 + 收货信息 + 明细;SKU 必绑(SkuSelector 选内部 SKU,存在性后端校验);
  - 编辑 PUT /api/orders/manual/{id}:仅 MANUAL + WAIT_SHIP 可改(列表页按钮已按此裁剪),明细整体替换;
  - 单价金额一律 string 直存直传,禁 Number 参与计算(docs/09 §6);金额后端按 Σ(单价×数量) 重算
-->

<template>
  <el-dialog v-model="visible" :title="title" width="880px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="店铺" prop="shopId">
        <el-select v-model="formData.shopId" placeholder="请选择店铺" filterable class="manual-form__field">
          <el-option v-for="s in shopOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <span class="manual-form__tip">平台取自店铺真实平台;内销单号由后端合成(MAN-*),与平台拉单互不覆盖</span>
      </el-form-item>
      <el-form-item label="买家留言" prop="buyerNote">
        <el-input
          v-model="formData.buyerNote"
          maxlength="512"
          placeholder="选填;命中风控关键词将置为待审核"
          class="manual-form__field"
        />
      </el-form-item>
      <el-form-item label="收货人" prop="receiverName">
        <el-input v-model="formData.receiverName" maxlength="64" placeholder="必填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="收货电话" prop="receiverPhone">
        <el-input v-model="formData.receiverPhone" maxlength="32" placeholder="必填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="收货国家" prop="receiverCountry">
        <el-input v-model="formData.receiverCountry" maxlength="8" placeholder="ISO 3166,如 CN" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="省/州" prop="receiverState">
        <el-input v-model="formData.receiverState" maxlength="64" placeholder="选填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="城市" prop="receiverCity">
        <el-input v-model="formData.receiverCity" maxlength="64" placeholder="必填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="详细地址" prop="receiverAddress">
        <el-input v-model="formData.receiverAddress" maxlength="512" placeholder="必填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="邮编" prop="receiverZip">
        <el-input v-model="formData.receiverZip" maxlength="32" placeholder="必填" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="币种" prop="currency">
        <el-input v-model="formData.currency" maxlength="3" placeholder="留空按 CNY" class="manual-form__field" />
      </el-form-item>
      <el-form-item label="订单明细" prop="items">
        <div class="manual-form__items">
          <div v-for="(item, index) in formData.items" :key="index" class="manual-form__row">
            <SkuSelector v-model="item.skuId" placeholder="搜商品名/SKU编码" class="manual-form__sku" />
            <el-input-number
              v-model="item.quantity"
              :min="1"
              :precision="0"
              controls-position="right"
              class="manual-form__qty"
              placeholder="数量"
            />
            <el-input v-model="item.unitPrice" placeholder="单价(如 19.90)" clearable class="manual-form__price" />
            <el-button type="danger" link :icon="Delete" @click="formData.items.splice(index, 1)" />
          </div>
          <el-button type="primary" link :icon="CirclePlus" @click="formData.items.push({ quantity: 1 })"
            >添加明细行</el-button
          >
          <span class="manual-form__tip"
            >仅能选内部 SKU(sku_id 必绑),行金额与订单金额由后端按 单价×数量 计算</span
          >
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
import { shopOrderApi } from '@/api/apis/order/order'
import { fetchShopOptions } from '@/api/apis/shop/options'
import SkuSelector from '@/components/SkuSelector/index.vue'
import type { ManualOrderItemSaveRequest, ShopOrderResponse } from '@/api/interface/order/order'

defineOptions({ name: 'ManualOrderForm' })

const emit = defineEmits<{ saved: [] }>()

/** 明细行草稿(skuId 待填;提交前校验收敛为 ManualOrderItemSaveRequest) */
interface ItemDraft {
  skuId?: number
  quantity?: number
  unitPrice?: string
}

/** 单价输入形态:非负数字,至多 4 位小数(DECIMAL(12,4));用正则在 string 上校验,禁 Number 参与计算 */
const MONEY_PATTERN = /^\d+(\.\d{1,4})?$/

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number>()
const title = ref('手工录单')
const shopOptions = ref<Awaited<ReturnType<typeof fetchShopOptions>>>([])
const formData = ref<{
  shopId?: number
  buyerNote?: string
  receiverName: string
  receiverPhone: string
  receiverCountry: string
  receiverState?: string
  receiverCity: string
  receiverAddress: string
  receiverZip: string
  currency?: string
  items: ItemDraft[]
}>({
  receiverName: '',
  receiverPhone: '',
  receiverCountry: 'CN',
  receiverCity: '',
  receiverAddress: '',
  receiverZip: '',
  currency: 'CNY',
  items: [],
})

const rules: FormRules = {
  shopId: [{ required: true, message: '请选择店铺', trigger: 'change' }],
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '请输入收货电话', trigger: 'blur' }],
  receiverCountry: [{ required: true, message: '请输入收货国家', trigger: 'blur' }],
  receiverCity: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  receiverAddress: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
  receiverZip: [{ required: true, message: '请输入邮编', trigger: 'blur' }],
}

/** 打开弹窗:不传 row = 新增内销单;传 row = 编辑内销单(拉详情回填明细) */
const open = async (row?: ShopOrderResponse) => {
  editId.value = row?.id
  title.value = row ? '编辑内销订单' : '手工录单'
  formData.value = {
    shopId: row?.shopId,
    buyerNote: row?.buyerNote,
    receiverName: row?.receiverName ?? '',
    receiverPhone: row?.receiverPhone ?? '',
    receiverCountry: row?.receiverCountry ?? 'CN',
    receiverState: row?.receiverState,
    receiverCity: row?.receiverCity ?? '',
    receiverAddress: row?.receiverAddress ?? '',
    receiverZip: row?.receiverZip ?? '',
    currency: row?.currency ?? 'CNY',
    items: [],
  }
  visible.value = true
  shopOptions.value = await fetchShopOptions()
  if (row) {
    const detail = await shopOrderApi.detail(row.id)
    formData.value.buyerNote = detail.buyerNote
    formData.value.items = (detail.items ?? []).map(it => ({
      skuId: it.skuId,
      quantity: it.quantity,
      // 单价 DECIMAL(12,4) 序列化为 string,直存直显禁浮点(docs/09 §6)
      unitPrice: String(it.unitPrice ?? ''),
    }))
  } else {
    formData.value.items = [{ quantity: 1 }]
  }
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  const items = formData.value.items
  if (!items.length) {
    ElMessage.warning('请至少添加一条明细')
    return
  }
  if (items.some(it => !it.skuId || !it.quantity || it.quantity < 1 || !it.unitPrice)) {
    ElMessage.warning('明细行需选择 SKU,并填写数量与单价')
    return
  }
  if (items.some(it => !MONEY_PATTERN.test(it.unitPrice!.trim()))) {
    ElMessage.warning('单价格式不合法(非负数字,最多 4 位小数)')
    return
  }
  const payload = {
    shopId: formData.value.shopId!,
    buyerNote: formData.value.buyerNote?.trim() || undefined,
    receiverName: formData.value.receiverName.trim(),
    receiverPhone: formData.value.receiverPhone.trim(),
    receiverCountry: formData.value.receiverCountry.trim(),
    receiverState: formData.value.receiverState?.trim() || undefined,
    receiverCity: formData.value.receiverCity.trim(),
    receiverAddress: formData.value.receiverAddress.trim(),
    receiverZip: formData.value.receiverZip.trim(),
    currency: formData.value.currency?.trim() || undefined,
    items: items.map<ManualOrderItemSaveRequest>(it => ({
      skuId: it.skuId!,
      quantity: it.quantity!,
      unitPrice: it.unitPrice!.trim(),
    })),
  }
  submitting.value = true
  try {
    if (editId.value == null) {
      await shopOrderApi.createManual(payload)
      ElMessage.success('内销订单一已录入(待发货,状态与审核态以列表为准)')
    } else {
      await shopOrderApi.updateManual(editId.value, payload)
      ElMessage.success('保存成功')
    }
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.manual-form {
  &__field {
    width: 420px;
  }
  &__items {
    width: 100%;
  }
  &__row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 8px;
  }
  &__sku {
    flex: 1.4;
  }
  &__qty {
    flex: 0.6;
  }
  &__price {
    flex: 1;
  }
  &__tip {
    display: inline-block;
    width: 100%;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
