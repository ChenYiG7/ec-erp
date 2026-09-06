<template>
  <!-- TODO(#16) 人工绑定弹窗(#5 SKU 匹配收口裸 ID 手填):SKU 搜索选择器选内部 SKU → 回填 sku_id + match_status=2;
       存在性校验在后端(查无此 SKU 拒绝),重复绑定同一 SKU 幂等 -->
  <el-dialog v-model="visible" title="人工绑定内部 SKU" width="480px" :close-on-click-modal="false" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="卖家SKU">
        <span>{{ row?.sellerSku }}</span>
      </el-form-item>
      <el-form-item label="内部SKU" required>
        <SkuSelector ref="selectorRef" v-model="skuId" placeholder="搜商品名/SKU编码" />
        <span class="bind-sku__tip">商品库 product_sku,选定后回填并置为人工绑定</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">绑定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'shop-product-sku-bind-dialog' })
import { ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElMessage } from 'element-plus'
import { shopProductSkuApi } from '@/api/apis/shop/shop-product-sku'
import SkuSelector from '@/components/SkuSelector/index.vue'
import type { ShopProductSkuResponse } from '@/api/interface/shop/shop-product-sku'

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const row = ref<ShopProductSkuResponse>()
const skuId = ref<number>()
const selectorRef = ref<InstanceType<typeof SkuSelector>>()

const open = (data: ShopProductSkuResponse) => {
  row.value = data
  skuId.value = data.skuId ?? undefined
  visible.value = true
  // 编辑回显:回填的 skuId 无对应选项时补占位,保证 label 可见(重复绑定同一 SKU 幂等)
  selectorRef.value?.ensureOption(skuId.value)
}

const submit = async () => {
  if (!skuId.value) {
    ElMessage.warning('请选择内部 SKU')
    return
  }
  submitting.value = true
  try {
    await shopProductSkuApi.bind(row.value!.id, { skuId: skuId.value })
    ElMessage.success('绑定成功')
    visible.value = false
    emit('saved')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.bind-sku {
  &__tip {
    display: inline-block;
    width: 100%;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
