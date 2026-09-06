import { shopApi } from './shop'

/**
 * 启用店铺选项(跨页共享数据源):订单/拉单日志/售后单/发货单/平台商品 shopId 列翻译。
 * 同 warehouse/options 形态 {label,value};ShopQuery 支持 status 服务端过滤,停用店铺不参与翻译
 */
export const fetchShopOptions = () =>
  shopApi.page({ pageNo: 1, pageSize: 500, status: 1 }).then(({ list }) =>
    list.map(s => ({ label: s.shopName, value: s.id }))
  )
