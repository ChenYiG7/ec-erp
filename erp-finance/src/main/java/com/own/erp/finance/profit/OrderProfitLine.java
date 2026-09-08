package com.own.erp.finance.profit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 利润主查询投影行(#19③):订单行 join 订单的裸数据(未经折算/聚合组装),
 *     ProfitQueryMapper.selectProfitLines 产物,由 ProfitQueryService 批量补成本/佣金/汇率后
 *     转契约 OrderProfitRow(投影不出 finance 包,契约模型纪律)
 */
public record OrderProfitLine(

        Long orderItemId,

        Long orderId,

        String platformOrderId,

        String platform,

        LocalDateTime orderTime,

        Long shopId,

        String platformOrderItemId,

        String platformSku,

        String productName,

        Long skuId,

        Integer quantity,

        BigDecimal itemAmount,

        String currency
) {
}
