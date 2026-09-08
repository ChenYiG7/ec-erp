<!-- 菜单新增/编辑共用弹窗(树形例外页手写;控件随 menuType 联动显隐) -->
<template>
  <el-dialog v-model="visible" :title="title" width="560px" :close-on-click-modal="false" destroy-on-close>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="上级菜单" prop="parentId">
        <el-tree-select
          v-model="formData.parentId"
          :data="parentOptions"
          check-strictly
          node-key="id"
          :props="{ label: 'menuName', children: 'children' }"
          :render-after-expand="false"
          default-expand-all
          placeholder="根菜单选『根节点』"
          class="w-full"
        />
      </el-form-item>
      <el-form-item label="菜单类型" prop="menuType">
        <el-radio-group v-model="formData.menuType">
          <el-radio-button :value="1">目录</el-radio-button>
          <el-radio-button :value="2">菜单</el-radio-button>
          <el-radio-button :value="3">按钮</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="菜单名称" prop="menuName">
        <el-input v-model="formData.menuName" placeholder="请输入菜单名称" clearable />
      </el-form-item>
      <el-form-item v-if="formData.menuType === 3" label="权限标识" prop="permKey">
        <el-input v-model="formData.permKey" placeholder="如 system:user:add(与 v-auth 对应)" clearable />
      </el-form-item>
      <el-form-item v-if="formData.menuType !== 3" label="路由路径" prop="path">
        <el-input v-model="formData.path" placeholder="如 /system/users" clearable />
      </el-form-item>
      <el-form-item v-if="formData.menuType === 2" label="组件路径" prop="component">
        <el-input v-model="formData.component" placeholder="相对 src/views,如 system/user/index" clearable />
      </el-form-item>
      <el-form-item v-if="formData.menuType !== 3" label="菜单图标">
        <SelectIcon v-model:icon-value="formData.icon" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" :max="999" />
      </el-form-item>
      <el-form-item v-if="formData.menuType !== 3" label="是否显示">
        <el-radio-group v-model="formData.visible">
          <el-radio :value="1">显示</el-radio>
          <el-radio :value="0">隐藏</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formData.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadio, ElRadioButton, ElRadioGroup, ElTreeSelect } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import SelectIcon from '@/components/SelectIcon/index.vue'
import { sysMenuApi } from '@/api/apis/system/menu'
import type { SysMenuResponse, SysMenuSaveRequest } from '@/api/interface/system/menu'

defineOptions({ name: 'SysMenuForm' })

const emit = defineEmits<{ saved: [] }>()

const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const mode = ref<'add' | 'edit'>('add')
const editId = ref<number>()
const treeData = ref<SysMenuResponse[]>([])
// 类型断言收敛在表单初始化(空表单起填,提交前 rules 校验 + 后端兜底校验)
const formData = ref<SysMenuSaveRequest>({} as SysMenuSaveRequest)

const rules: FormRules = {
  parentId: [{ required: true, message: '请选择上级菜单', trigger: 'change' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }]
}

const title = computed(() => (mode.value === 'add' ? '新增菜单' : '编辑菜单'))

/** 按 id 深度查找菜单节点 */
const findNode = (nodes: SysMenuResponse[], id: number): SysMenuResponse | undefined => {
  for (const n of nodes) {
    if (n.id === id) {
      return n
    }
    const hit = n.children?.length ? findNode(n.children, id) : undefined
    if (hit) {
      return hit
    }
  }
  return undefined
}

/** 上级候选树:剔除按钮节点(menuType=3 不能挂子级);编辑时再剔除自身及子孙(防自环) */
const parentOptions = computed(() => {
  const exclude = new Set<number>()
  if (mode.value === 'edit' && editId.value) {
    exclude.add(editId.value)
    const stack = [...(findNode(treeData.value, editId.value)?.children ?? [])]
    while (stack.length) {
      const n = stack.pop()!
      exclude.add(n.id)
      stack.push(...(n.children ?? []))
    }
  }
  const walk = (nodes: SysMenuResponse[]): SysMenuResponse[] =>
    nodes.filter(n => n.menuType !== 3 && !exclude.has(n.id)).map(n => ({ ...n, children: n.children?.length ? walk(n.children) : [] }))
  const root: SysMenuResponse = {
    id: 0,
    parentId: -1,
    menuName: '根节点',
    menuType: 1,
    sort: 0,
    children: walk(treeData.value)
  }
  return [root]
})

/** 打开弹窗(mode:add/edit);edit 浅拷贝行数据,禁直接引用污染列表行 */
const open = async (m: 'add' | 'edit', row?: SysMenuResponse, parentId?: number) => {
  mode.value = m
  editId.value = row?.id
  treeData.value = await sysMenuApi.tree()
  formData.value = { menuType: 2, parentId: 0, sort: 0, visible: 1, status: 1 } as SysMenuSaveRequest
  if (m === 'edit' && row) {
    Object.assign(formData.value, row)
  } else if (parentId !== undefined) {
    formData.value.parentId = parentId
  }
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (mode.value === 'add') {
      await sysMenuApi.create(formData.value)
    } else {
      await sysMenuApi.update(editId.value!, formData.value)
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
