package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.finance.request.query.FirstLegShipmentQuery;
import com.own.erp.finance.response.FirstLegShipmentResponse;
import com.own.erp.finance.response.FirstLegSkuAllocRow;
import com.own.erp.finance.response.FirstLegSkuAllocSumRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程运费分摊查询 Mapper(#33,XML 见 resources/mapper/FirstLegQueryMapper.xml):
 *     发货单分页(双仓名联表投影)+ SKU 维度头程费用汇总(利润第三层聚合源)。
 *     跨域表(warehouse/product_sku)只读 join,finance 拥有查询视图 SQL,Java 依赖仍走契约
 *     (同 PaymentQueryMapper 先例,不破坏铁律 2)
 */
public interface FirstLegQueryMapper {

    /** 头程发货单分页(id 倒序;单号模糊/状态/发货仓/目的仓过滤;双仓名 join 投影) */
    Page<FirstLegShipmentResponse> pageRows(Page<FirstLegShipmentResponse> page,
                                            @Param("query") FirstLegShipmentQuery query);

    /**
     * SKU 维度头程费用汇总:Σ ALLOCATED/CLOSED 单 first_leg_alloc 按 SKU 聚合,
     * 可按 skuId 精确过滤、按发货时间(shipped_at)开区间过滤;无分摊的 SKU 不在返回中
     */
    List<FirstLegSkuAllocRow> listSkuAllocSummary(@Param("skuId") Long skuId,
                                                  @Param("shippedFrom") LocalDateTime shippedFrom,
                                                  @Param("shippedTo") LocalDateTime shippedTo);

    /**
     * SKU 集合批量头程分摊合计(#33 利润第三层 enrichment,2026-09-12 方案 B 整窗摊入):
     * 口径与 listSkuAllocSummary 同源(ALLOCATED/CLOSED 主单,shipped_at 锚点);
     * 无分摊 SKU 不在返回中,调用方按 0 兜底;skuIds 空由调用方短路不触库
     */
    List<FirstLegSkuAllocSumRow> sumAllocBySkuIds(@Param("skuIds") Collection<Long> skuIds,
                                                  @Param("shippedFrom") LocalDateTime shippedFrom,
                                                  @Param("shippedTo") LocalDateTime shippedTo);
}
