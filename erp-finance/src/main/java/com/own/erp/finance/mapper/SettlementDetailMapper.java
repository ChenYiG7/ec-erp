package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.SettlementDetail;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : settlement_detail 表 Mapper(#19 结算明细流水):写入侧仅解析链路先删后插(报告级幂等,
 *         行级不设唯一键);读侧 SKU 级利润按 (shop_id, order_item_id) 归集、勾稽按 report_id Σamount
 */
public interface SettlementDetailMapper extends BaseMapper<SettlementDetail> {
}
