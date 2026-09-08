<!--
  AI 知识库页(#6 AI 客服 RAG V1,2026-09-08,手写页:上传/粘贴接入 + 分块预览非标准 CRUD,gen:page 不适用)
  读侧登录即可;写侧(上传/粘贴/删除/重建)后端 admin 双闸,按钮不带 v-auth(同 AI 三页口径,越权由后端拦截提示)
-->
<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/ai/kb" title="AI知识库" :columns="columns" :request-api="aiKbApi.page">
      <!-- 工具栏左:接入与重建(admin 双闸在后端) -->
      <template #toolbarLeft>
        <el-button type="primary" :icon="Upload" @click="fileInputRef?.click()">上传文件</el-button>
        <el-button type="primary" plain :icon="DocumentAdd" @click="openPasteDialog">粘贴文本</el-button>
        <el-button :icon="RefreshRight" @click="onRebuild">重建索引</el-button>
        <input ref="fileInputRef" type="file" accept=".txt,.md,.markdown" class="hidden-input" @change="onFileChosen" />
      </template>

      <!-- 操作列(ProTable v2:type:'operation' 列必须提供本插槽) -->
      <template #operation="scope">
        <el-button type="primary" link :icon="View" @click="openDetail(scope.row)">分块预览</el-button>
        <el-button type="danger" link :icon="Delete" @click="onDelete(scope.row)">删除</el-button>
      </template>
    </ProTable>

    <!-- 上传确认:选文件后补标题(默认文件名去后缀),确认才上传 -->
    <el-dialog v-model="uploadVisible" title="上传知识文档" width="480px">
      <el-form label-width="72px">
        <el-form-item label="文件">
          <span>{{ uploadFileRef?.name }}({{ formatBytes(uploadFileRef?.size ?? 0) }})</span>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="uploadTitle" maxlength="128" placeholder="默认取文件名去后缀" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- 粘贴文本接入 -->
    <el-dialog v-model="pasteVisible" title="粘贴文本接入" width="560px">
      <el-form label-width="72px">
        <el-form-item label="标题">
          <el-input v-model="pasteTitle" maxlength="128" placeholder="默认取内容首行截断" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="pasteContent" type="textarea" :rows="10" placeholder="粘贴知识文本(如平台规则/FAQ/SOP)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pasteVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmPaste">确认接入</el-button>
      </template>
    </el-dialog>

    <!-- 分块预览抽屉:文档信息 + 全部分块 -->
    <el-drawer v-model="detailVisible" title="文档分块预览" size="560px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="标题">{{ detailRow.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detailRow.status === 'READY' ? '可检索' : '向量化失败(可重建索引)' }}
        </el-descriptions-item>
        <el-descriptions-item label="分块数 / 字符数">
          {{ detailRow.chunkCount }} / {{ detailRow.charCount }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-for="chunk in detailChunks" :key="chunk.id" class="chunk-block">
        <div class="chunk-title">#{{ chunk.chunkIndex }} · {{ chunk.charCount }} 字符</div>
        <pre class="chunk-pre">{{ chunk.content }}</pre>
      </div>
      <el-empty v-if="detailRow && !detailChunks.length" description="无分块" />
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'ai-kb-index' })
import { ref } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import { Delete, DocumentAdd, RefreshRight, Upload, View } from '@element-plus/icons-vue'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { aiKbApi } from '@/api/apis/ai/kb'
import type { AiKbDocumentResponse, AiKbChunkResponse } from '@/api/interface/ai/kb'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(status/tag 与 sourceType 走 enum 映射;title 支持模糊搜索)
const columns: ColumnProps<AiKbDocumentResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'title', label: '标题', search: { el: 'input' } },
  {
    prop: 'sourceType',
    label: '来源',
    width: 90,
    enum: [
      { label: '文件上传', value: 'UPLOAD', tagType: 'primary' },
      { label: '粘贴文本', value: 'TEXT', tagType: 'info' },
    ],
  },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    tag: true,
    search: { el: 'select' },
    enum: [
      { label: '可检索', value: 'READY', tagType: 'success' },
      { label: '向量化失败', value: 'FAILED', tagType: 'danger' },
    ],
  },
  { prop: 'charCount', label: '字符数', width: 100 },
  { prop: 'chunkCount', label: '分块数', width: 90 },
  { prop: 'createdAt', label: '接入时间', width: 170 },
  { prop: 'operation', label: '操作', fixed: 'right', width: 170 },
]

const refreshTable = () => proTableRef.value?.getTableList()

// ── 上传文件:隐藏 input 选文件 → 弹窗补标题 → FormData 上传 ──
const fileInputRef = ref<HTMLInputElement>()
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFileRef = ref<File | null>(null)
const uploadTitle = ref('')

const onFileChosen = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选择同一文件
  if (!file) {
    return
  }
  uploadFileRef.value = file
  uploadTitle.value = file.name.replace(/\.[^.]+$/, '')
  uploadVisible.value = true
}

const confirmUpload = async () => {
  if (!uploadFileRef.value) {
    return
  }
  uploading.value = true
  try {
    const doc = await aiKbApi.uploadFile(uploadFileRef.value, uploadTitle.value.trim() || undefined)
    notifyIngestResult(doc)
    uploadVisible.value = false
    refreshTable()
  } finally {
    uploading.value = false
  }
}

// ── 粘贴文本接入 ──
const pasteVisible = ref(false)
const pasteTitle = ref('')
const pasteContent = ref('')

const openPasteDialog = () => {
  pasteTitle.value = ''
  pasteContent.value = ''
  pasteVisible.value = true
}

const confirmPaste = async () => {
  if (!pasteContent.value.trim()) {
    ElMessage.warning('请填写文档内容')
    return
  }
  uploading.value = true
  try {
    const doc = await aiKbApi.uploadText({ title: pasteTitle.value.trim() || undefined, content: pasteContent.value })
    notifyIngestResult(doc)
    pasteVisible.value = false
    refreshTable()
  } finally {
    uploading.value = false
  }
}

// 接入结果提示:READY 直接受理;FAILED = 正本已留存但向量化失败(可重建)
const notifyIngestResult = (doc: AiKbDocumentResponse) => {
  if (doc.status === 'READY') {
    ElMessage.success(`已接入并进入检索:${doc.chunkCount} 个分块`)
  } else {
    ElMessage.warning('文档已保存但向量化失败,请检查 AI 配置后重建索引')
  }
}

// ── 删除/重建 ──
const onDelete = async (row: AiKbDocumentResponse) => {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」吗?其向量与分块将一并清除。`, '删除文档', { type: 'warning' })
  await aiKbApi.remove(row.id)
  ElMessage.success('已删除')
  refreshTable()
}

const onRebuild = async () => {
  await ElMessageBox.confirm(
    '将清空向量索引并按知识库正本重新向量化(换 embedding 模型/索引文件丢失后使用),确认继续吗?',
    '重建索引',
    { type: 'warning' }
  )
  const count = await aiKbApi.rebuild()
  ElMessage.success(`索引已重建,共 ${count} 个文档`)
}

// ── 分块预览抽屉 ──
const detailVisible = ref(false)
const detailRow = ref<AiKbDocumentResponse | null>(null)
const detailChunks = ref<AiKbChunkResponse[]>([])

const openDetail = async (row: AiKbDocumentResponse) => {
  detailRow.value = row
  detailChunks.value = []
  detailVisible.value = true
  detailChunks.value = await aiKbApi.chunks(row.id)
}

const formatBytes = (bytes: number) => {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped lang="scss">
.hidden-input {
  display: none;
}

.chunk-block {
  margin-top: 14px;

  .chunk-title {
    margin-bottom: 6px;
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  // 分块直显:等宽 + 限高滚动,禁横向撑破抽屉
  .chunk-pre {
    max-height: 220px;
    padding: 10px;
    overflow: auto;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    line-height: 1.6;
    word-break: break-all;
    white-space: pre-wrap;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;
  }
}
</style>
