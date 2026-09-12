package com.own.erp.contract;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润查询入参(#19③ 三口径第一层,订单口径订单行粒度):
 *         已支付态三态(WAIT_SHIP/SHIPPED/COMPLETED)同销量日表口径;时间窗=下单时间 so.order_time;
 *         分页参数钳制随实现(pageSize 上限 200)
 */
public record OrderProfitQuery(

        /** 店铺ID(可空=全店铺) */
        Long shopId,

        /** 平台(PlatformType 枚举名,可空=全平台) */
        String platform,

        /** 内部SKU(可空=全SKU;未绑定 SKU 的订单行 sku_id 为 NULL,指定本参数时不返回该类行) */
        Long skuId,

        /** 下单时间起(含,可空) */
        LocalDateTime dateFrom,

        /** 下单时间止(不含,可空) */
        LocalDateTime dateTo,

        /** 页码(1 起) */
        Integer pageNo,

        /** 页大小(≤200) */
        Integer pageSize,

        /**
         * 数据权限授权店铺集(#27①,可空=null=不限;非空=IN 过滤;空列表=不可见任何店铺数据)。
         * HTTP 链路由 ProfitQueryApiImpl 强制装配 CurrentUserApi.currentShopIds()(AI/前端自带值被覆盖);
         * 系统内部调用(ProfitPeriodReportService 等)保持 null 不受数据权限约束
         */
        List<Long> shopIds
) {

    public int pageNoOrDefault() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, 200);
    }
}
