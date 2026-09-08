package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.SettlementReport;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : settlement_report 表 Mapper(#19 结算报告正本):写入侧仅解析链路 upsert(uk_shop_settlement 幂等,
 *         解析器/勾稽逻辑随结算报告接入切片落地);读侧按店铺+周期分页(周期利润数据源)
 */
public interface SettlementReportMapper extends BaseMapper<SettlementReport> {
}
