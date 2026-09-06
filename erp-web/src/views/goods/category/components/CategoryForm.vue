<!--
  分类新增/编辑弹窗(手写,gen:page 不适用:树形域;程式对齐生成产 BrandForm)
  TODO(#7) 后端成环校验待补:编辑态禁改父级(parentId 只读展示),仅新增可选父级
-->

<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="110px">
      <el-form-item label="上级分类" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="parentOptions"
          :props="{ label: 'name' }"
          check-strictly
          clearable
          :disabled="mode === 'edit'"
          placeholder="不选=顶级分类"
        />
      </el-form-item>
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入分类名称" clearable />
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
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElOption, ElSelect, ElTreeSelect } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { categoryApi } from '@/api/apis/goods/category'
import type { CategoryNode, ProductCategorySaveRequest } from '@/api/interface/goods/category'

defineOptions({ name: 'CategoryForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
const formData = ref<ProductCategorySaveRequest>({} as ProductCategorySaveRequest)
// 父级候选 = 全量树(每次开弹窗现拉,新建节点即刻可选)
const parentOptions = ref<CategoryNode[]>([])

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const title = ref('分类')

/** 打开弹窗(mode:add/edit);row=父级(新增子级场景)或编辑行;逐字段显式回填,不 Object.assign(树行带 children) */
const open = async (m: 'add' | 'edit', row?: CategoryNode) => {
  mode.value = m
  editId.value = m === 'edit' ? row?.id : undefined
  title.value = (m === 'add' ? '新增' : '编辑') + '分类'
  if (m === 'add') {
    // 新增子级预置父级;顶级不传,后端按根处理
    formData.value = { parentId: row?.id, name: '' }
  } else if (row) {
    // parentId=0(根)转 undefined 仅作展示,update 缺省该键 → MP updateById 不动它
    formData.value = { parentId: row.parentId === 0 ? undefined : row.parentId, name: row.name, sort: row.sort, status: row.status }
  }
  parentOptions.value = (await categoryApi.tree()) ?? []
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'add') {
      await categoryApi.create(formData.value)
    } else {
      await categoryApi.update(editId.value!, formData.value)
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
