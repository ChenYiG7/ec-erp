<!--
  部门管理页(#27③ 手写,gen:page 不适用:树形域无分页端点,同分类管理先例;程式对齐生成页)
  成环/引用校验在后端(父链成环、有子部门/用户引用禁删),前端只做删除前确认
-->

<template>
  <div class="table-box">
    <div class="toolbar">
      <el-button v-auth="'system:dept:add'" type="primary" :icon="CirclePlus" @click="openForm('add')"
        >新增部门</el-button
      >
      <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
    </div>
    <el-table :data="treeData" row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="deptName" label="部门名称" min-width="240" />
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">{{
            scope.row.status === 1 ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="scope">
          <el-button v-auth="'system:dept:add'" type="primary" link @click="openForm('add', scope.row as DeptNode)"
            >新增子级</el-button
          >
          <el-button v-auth="'system:dept:edit'" type="primary" link @click="openForm('edit', scope.row as DeptNode)"
            >编辑</el-button
          >
          <el-button v-auth="'system:dept:remove'" type="danger" link @click="onRemove(scope.row as DeptNode)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <DeptForm ref="formRef" @saved="loadTree" />
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致(system/dept/index)
defineOptions({ name: 'system-dept-index' })

import { CirclePlus, Refresh } from '@element-plus/icons-vue'
import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { onMounted, ref } from 'vue'
import { sysDeptApi } from '@/api/apis/system/dept'
import type { DeptNode } from '@/api/interface/system/dept'
import DeptForm from './components/DeptForm.vue'

const treeData = ref<DeptNode[]>([])
const formRef = ref<InstanceType<typeof DeptForm>>()

const loadTree = async () => {
  treeData.value = (await sysDeptApi.tree()) ?? []
}

const openForm = (m: 'add' | 'edit', row?: DeptNode) => formRef.value?.open(m, row)

const onRemove = async (row: DeptNode) => {
  await ElMessageBox.confirm(`确认删除部门「${row.deptName}」?删除前需先迁移子部门与挂靠用户`, '提示', {
    type: 'warning',
  })
  await sysDeptApi.remove(row.id)
  ElMessage.success('删除成功')
  loadTree()
}

onMounted(loadTree)
</script>
