<!--
  商品分类管理页(手写,gen:page 不适用:树形域无分页端点,README 边界"树形/子表布局人工";程式对齐生成页)
  TODO(#7) 后端删除引用拦截未落地:前端先挡"存在子分类"的删除,商品引用拦截仍靠后端兜底
-->

<template>
  <div class="table-box">
    <div class="toolbar">
      <el-button v-auth="'goods:category:add'" type="primary" :icon="CirclePlus" @click="openForm('add')">新增分类</el-button>
      <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
    </div>
    <el-table :data="treeData" row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="name" label="分类名称" min-width="240" />
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button v-auth="'goods:category:add'" type="primary" link @click="openForm('add', scope.row as CategoryNode)">新增子级</el-button>
          <el-button v-auth="'goods:category:edit'" type="primary" link @click="openForm('edit', scope.row as CategoryNode)">编辑</el-button>
          <el-button v-auth="'goods:category:remove'" type="danger" link @click="onRemove(scope.row as CategoryNode)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <CategoryForm ref="formRef" @saved="loadTree" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'goods-category-index' })
import { onMounted, ref } from 'vue'
import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { CirclePlus, Refresh } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/apis/goods/category'
import type { CategoryNode } from '@/api/interface/goods/category'
import CategoryForm from './components/CategoryForm.vue'

const treeData = ref<CategoryNode[]>([])
const formRef = ref<InstanceType<typeof CategoryForm>>()

const loadTree = async () => {
  treeData.value = (await categoryApi.tree()) ?? []
}

const openForm = (m: 'add' | 'edit', row?: CategoryNode) => formRef.value?.open(m, row)

// 删除前置:全量树内存可判,挡子节点存在的一级删除;商品引用拦截靠后端(一期硬删)
const onRemove = async (row: CategoryNode) => {
  if (row.children?.length) {
    ElMessage.warning('存在子分类,请先删除子级')
    return
  }
  await ElMessageBox.confirm(`确认删除分类「${row.name}」?一期为硬删,不可恢复`, '提示', { type: 'warning' })
  await categoryApi.remove(row.id)
  ElMessage.success('删除成功')
  loadTree()
}

onMounted(loadTree)
</script>
