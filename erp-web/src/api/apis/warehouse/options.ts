import { warehouseApi } from './warehouse'

/**
 * 启用仓库选项(跨页共享数据源):售后收退件/采购建单下拉、各列表 warehouseId 列翻译。
 * 基础配置数据量小,整页拉取一次即够;ProTable 列 enum 与 el-select 共用此形态 {label,value}
 */
export const fetchWarehouseOptions = () =>
  warehouseApi.page({ pageNo: 1, pageSize: 500 }).then(({ list }) =>
    list.filter(w => w.status === 1).map(w => ({ label: w.whName, value: w.id }))
  )
