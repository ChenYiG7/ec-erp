<!--
  部门新增/编辑弹窗(#27③ 手写,树形域;程式对齐生成产 CategoryForm)
  成环校验在后端(父链沿线上走命中自己即拒),编辑态允许改父级,后端消息直出
-->

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="上级部门" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="parentOptions"
          :props="{ label: 'deptName' }"
          check-strictly
          clearable
          placeholder="不选=顶级部门"
        />
      </el-form-item>
      <el-form-item label="部门名称" prop="deptName">
        <el-input v-model="formData.deptName" placeholder="请输入部门名称" clearable />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" controls-position="right" class="!w-full" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="formData.status" clearable placeholder="请选择状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
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
  ElTreeSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { sysDeptApi } from '@/api/apis/system/dept'
import type { DeptNode, SysDeptSaveRequest } from '@/api/interface/system/dept'

defineOptions({ name: 'DeptForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
const formData = ref<SysDeptSaveRequest>({} as SysDeptSaveRequest)
// 父级候选 = 全量树(每次开弹窗现拉,新建节点即刻可选)
const parentOptions = ref<DeptNode[]>([])

const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

const title = ref('部门')

/** 打开弹窗(mode:add/edit);row=父级(新增子级场景)或编辑行;逐字段显式回填,不 Object.assign(树行带 children) */
const open = async (m: 'add' | 'edit', row?: DeptNode) => {
  mode.value = m
  editId.value = m === 'edit' ? row?.id : undefined
  title.value = (m === 'add' ? '新增' : '编辑') + '部门'
  if (m === 'add') {
    // 新增子级预置父级;顶级不传,后端按根处理
    formData.value = { parentId: row?.id, deptName: '' }
  } else if (row) {
    // parentId=0(根)转 undefined 仅作展示,update 缺省该键 → MP updateById 不动它
    formData.value = {
      parentId: row.parentId === 0 ? undefined : row.parentId,
      deptName: row.deptName,
      sort: row.sort,
      status: row.status,
    }
  }
  parentOptions.value = (await sysDeptApi.tree()) ?? []
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'add') {
      await sysDeptApi.create(formData.value)
    } else {
      await sysDeptApi.update(editId.value!, formData.value)
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
