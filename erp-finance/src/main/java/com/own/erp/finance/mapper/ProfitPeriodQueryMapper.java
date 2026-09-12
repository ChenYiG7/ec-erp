package com.own.erp.finance.mapper;

import com.own.erp.finance.profit.SettlementFeeSum;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润自定义只读查询(#19 三口径第二层,XML 见 resources/mapper/ProfitPeriodQueryMapper.xml):
 *     结算侧费种聚合收口本接口(finance 拥有周期视图 SQL,跨域表只读同 ProfitQueryMapper 先例);
 *     订单侧周期窗聚合复用 ProfitQueryService.summarize(同 §6.1 归集管线,不另写 SQL);
 *     纯读不写,周期行 upsert 随校差算法 TODO#32 落 ProfitPeriodReportService
 */
public interface ProfitPeriodQueryMapper {

    /**
     * 结算侧分费种聚合:单份报告明细按 fee_type 分组 SUM(amount)(报告原币、带符号)。
     * 报告级幂等(重拉先删后插),重算直接全量聚合无增量口径
     */
    List<SettlementFeeSum> sumSettlementFees(@Param("reportId") Long reportId);
}
