import { supplierApi } from './supplier'

/**
 * 启用供应商选项(跨页共享数据源):采购建单下拉等。
 * 基础配置数据量小,整页拉取一次即够;ProTable 列 enum 与 el-select 共用此形态 {label,value}
 */
export const fetchSupplierOptions = () =>
  supplierApi.page({ pageNo: 1, pageSize: 500 }).then(({ list }) =>
    list.filter(s => s.status === 1).map(s => ({ label: s.name, value: s.id }))
  )
