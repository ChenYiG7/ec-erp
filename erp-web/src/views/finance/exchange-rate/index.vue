<!--
  汇率快照管理页(#19③ 数据面,手写页:ProTable + 新增弹窗,gen:page 不适用——非标准 CRUD 单动作)
  汇率是快照不是现值:利润折算按业务日回溯取最近报价;本位币 V1 固定 CNY(不录入,折算短路=1)
  写侧 admin 双闸:按钮 v-auth="'finance:rate:save'" + 后端 @PreAuthorize hasRole('admin')
-->

<template>
  <div class="table-box">
    <ProTable ref="proTableRef" page-id="/finance/exchange-rates" title="汇率快照" :columns="columns" :request-api="exchangeRateApi.page">
      <template #tableHeader>
        <el-button v-auth="'finance:rate:save'" type="primary" @click="openSave">录入快照</el-button>
      </template>
      <template #currency="{ row }">{{ row.currency }}</template>
    </ProTable>
    <el-dialog v-model="saveVisible" title="录入汇率快照" width="460px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="币种" required>
          <el-input v-model="form.currency" placeholder="ISO 4217,如 USD/EUR" maxlength="3" style="width: 220px" />
        </el-form-item>
        <el-form-item label="汇率" required>
          <el-input v-model="form.rate" placeholder="1 币种 = rate CNY,如 7.25" style="width: 220px" />
        </el-form-item>
        <el-form-item label="报价时间" required>
          <el-date-picker v-model="quotedAt" type="datetime" placeholder="折算回溯锚点" style="width: 220px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">取消</el-button>
        <el-button v-auth="'finance:rate:save'" type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
// 路由 name 由 component 路径派生,KeepAlive 生效前提是本名与其一致
defineOptions({ name: 'finance-exchange-rate-index' })
import { reactive, ref } from 'vue'
import { ElButton, ElDatePicker, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import ProTable from '@/components/ProTable/index.vue'
import type { ColumnProps } from '@/components/ProTable/interface'
import { exchangeRateApi } from '@/api/apis/finance/exchange-rate'
import type { ExchangeRateResponse } from '@/api/interface/finance/exchange-rate'

// ProTable 实例(getTableList 供刷新)
const proTableRef = ref<InstanceType<typeof ProTable>>()

// 列配置(汇率三位小数直显;来源 tag)
const columns: ColumnProps<ExchangeRateResponse>[] = [
  { type: 'index', label: '#', width: 55 },
  { prop: 'currency', label: '币种', width: 100 },
  { prop: 'rate', label: '汇率(→CNY)', width: 160 },
  { prop: 'quotedAt', label: '报价时间', width: 175 },
  { prop: 'source', label: '来源', width: 100, tag: true, enum: [{ label: '手工录入', value: 'MANUAL', tagType: 'info' }, { label: '行情接口', value: 'API', tagType: 'success' }] },
  { prop: 'createdAt', label: '创建时间', width: 175 },
]

// 录入弹窗:币种/汇率/报价时间必填,汇率>0(后端同校验兜底)
const saveVisible = ref(false)
const saving = ref(false)
const form = reactive({ currency: '', rate: '' })
const quotedAt = ref('')
const openSave = () => {
  form.currency = ''
  form.rate = ''
  quotedAt.value = ''
  saveVisible.value = true
}
const onSave = () => {
  if (!form.currency.trim() || !form.rate.trim() || !quotedAt.value) {
    ElMessage.warning('币种/汇率/报价时间必填')
    return
  }
  const rate = Number(form.rate)
  if (!Number.isFinite(rate) || rate <= 0) {
    ElMessage.warning('汇率必须为正数')
    return
  }
  saving.value = true
  exchangeRateApi
    .save({ currency: form.currency.trim().toUpperCase(), rate: form.rate.trim(), quotedAt: quotedAt.value })
    .then(() => {
      ElMessage.success('汇率快照已录入')
      saveVisible.value = false
      refreshTable()
    })
    .finally(() => (saving.value = false))
}

const refreshTable = () => proTableRef.value?.getTableList()
</script>
