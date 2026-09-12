<!-- 用户-店铺数据授权弹窗(#27① 数据权限方案A:店铺轴;api 收口 @/api/apis/system/user|shop,禁直连 axios) -->
<template>
  <el-dialog v-model="visible" :title="title" width="520px" :close-on-click-modal="false" destroy-on-close>
    <el-alert
      type="info"
      :closable="false"
      title="数据权限说明"
      description="仅对非 admin 用户生效:勾选后该用户只能看到授权店铺的订单/发货/售后/利润等数据;admin 天然全量;清空全部勾选 = 该用户不可见任何店铺数据。"
      class="mb12"
    />
    <div v-loading="loading" class="shop-list">
      <el-checkbox-group v-model="checkedShopIds">
        <el-checkbox v-for="s in shops" :key="s.id" :value="s.id" :disabled="s.status !== 1">
          [{{ s.platform }}] {{ s.shopName }}
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!loading && shops.length === 0" description="暂无店铺,请先在店铺管理中新增" :image-size="72" />
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElCheckbox, ElCheckboxGroup, ElDialog, ElEmpty, ElMessage, ElAlert } from 'element-plus'
import { sysUserApi } from '@/api/apis/system/user'
import { shopApi } from '@/api/apis/shop/shop'
import type { SysUserResponse } from '@/api/interface/system/user'
import type { ShopResponse } from '@/api/interface/shop/shop'

defineOptions({ name: 'UserShopDialog' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const title = ref('店铺授权')
const user = ref<SysUserResponse>()
const shops = ref<ShopResponse[]>([])
const checkedShopIds = ref<number[]>([])

/** 打开弹窗:并行拉用户已授权店铺 + 店铺全量(分页兜底 200 条,MVP 够用) */
const open = async (row: SysUserResponse) => {
  user.value = row
  title.value = `店铺授权 - ${row.username}`
  visible.value = true
  loading.value = true
  try {
    const [shopIds, shopPage] = await Promise.all([
      sysUserApi.getShops(row.id),
      shopApi.page({ pageNo: 1, pageSize: 200 }),
    ])
    shops.value = shopPage.list
    checkedShopIds.value = shopIds
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    await sysUserApi.putShops(user.value!.id, { shopIds: checkedShopIds.value })
    ElMessage.success('店铺授权成功(实时生效,无需重新登录)')
    emit('saved')
    visible.value = false
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
<style scoped lang="scss">
.shop-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 120px;
}
.mb12 {
  margin-bottom: 12px;
}
</style>
