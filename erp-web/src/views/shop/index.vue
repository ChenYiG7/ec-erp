<!--
  本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
  默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
  框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/shop" title="店铺管理" :columns="columns" :request-api="shopApi.page">
      <!-- 工具栏左:新增(按钮权限收口在页面侧 v-auth;toolbarLeft prop 的 auth 属性无效,禁用) -->
      <template #toolbarLeft>
        <el-button v-auth="'shop:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
          >新增店铺管理</el-button
        >
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button v-auth="'shop:edit'" type="primary" link :icon="EditPen" @click="openForm('edit', scope.row)"
          >编辑</el-button
        >
        <el-button v-auth="'shop:remove'" type="danger" link :icon="Delete" @click="handleDelete(scope.row)"
          >删除</el-button
        >
        <el-button v-auth="'shop:auth-url'" type="warning" link :icon="Link" @click="onAuthUrl(scope.row)"
          >平台授权</el-button
        >
        <!-- TODO(#16) 动作按钮:auth-url(api 已生成 authUrl()) -->
        <!-- <el-button v-auth="'shop:auth-url'" type="warning" link @click="onAuthUrl(scope.row)">生成平台授权跳转地址</el-button> -->
      </template>
    </ProTable>
    <ShopForm ref="formRef" @saved="refreshTable" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'shop-index' })
import { ref } from 'vue'
import { CirclePlus, EditPen, Delete, Link } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { useDictStore } from '@/stores/modules/dict'
import { shopApi } from '@/api/apis/shop/shop'
import type { ShopResponse } from '@/api/interface/shop/shop'
import ShopForm from './components/ShopForm.vue'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()
const formRef = ref<InstanceType<typeof ShopForm>>()

// 列配置(gen:page 按 spec role=column/all 产出;enum/dict 选项同时供搜索下拉)
const columns: ColumnProps<ShopResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  {
    prop: 'platform',
    label: '平台',
    width: 120,
    enum: () =>
      useDictStore()
        .getDict('shop_platform')
        .then(list => list.map(item => ({ label: item.dictLabel, value: item.dictValue }))),
  },
  { prop: 'shopName', label: '店铺名称', width: 200 },
  { prop: 'sellerId', label: '卖家ID', width: 150 },
  { prop: 'accessToken', label: '授权凭证', width: 160 },
  { prop: 'tokenExpireAt', label: '令牌过期时间', width: 170 },
  {
    prop: 'status',
    label: '状态',
    width: 90,
    enum: [
      { label: '启用', value: 1, tagType: 'success' },
      { label: '停用', value: 0, tagType: 'danger' },
    ],
  },
  { prop: 'createdAt', label: '创建时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 210 },
]

const openForm = (mode: 'add' | 'edit', row?: ShopResponse) => {
  formRef.value?.open(mode, row)
}

const handleDelete = async (row: ShopResponse) => {
  await ElMessageBox.confirm('确认删除该店铺管理吗?', '提示', { type: 'warning' })
  await shopApi.remove(row.id)
  ElMessage.success('删除成功')
  refreshTable()
}

// 动作 auth-url:拿授权地址后新窗口打开(回调页由 8088 直出 HTML,完成后手动返回列表刷新)
const onAuthUrl = async (row: ShopResponse) => {
  const url = await shopApi.authUrl(row.id)
  if (!url) {
    ElMessage.warning('未获取到授权地址,请检查店铺平台与应用配置')
    return
  }
  window.open(url, '_blank')
  ElMessage.info('授权完成后请手动返回本页并刷新列表')
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
