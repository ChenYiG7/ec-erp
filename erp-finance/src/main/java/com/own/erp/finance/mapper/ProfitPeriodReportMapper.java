package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.ProfitPeriodReport;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润报告 Mapper(#19 三口径第二层):BaseMapper 通用 CRUD + 周期行 ODKU upsert
 *     (XML upsertPeriod,uk(shop_id,settlement_id) 幂等,校差算法 #32 落地随 rebuildForReport 调用)
 */
public interface ProfitPeriodReportMapper extends BaseMapper<ProfitPeriodReport> {

    /**
     * 周期行幂等 upsert:uk(shop_id,settlement_id) 冲突即整行覆盖(重算刷新不重复建行),
     * MySQL 9.7.2 原生形态(VALUES 行别名 AS new,ODKU 内新列 new. 限定);created_at/updated_at 不在列清单
     */
    int upsertPeriod(ProfitPeriodReport row);
}
