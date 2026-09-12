package com.own.erp.fulfill.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.fulfill.request.query.FbaShipmentQuery;
import com.own.erp.fulfill.response.FbaShipmentResponse;
import org.apache.ibatis.annotations.Param;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 发货单查询 Mapper(docs/plans/fba-shipment.md,XML 见 resources/mapper/FbaQueryMapper.xml):
 *     发货单分页(仓名/店名联表投影)。跨域表(warehouse/shop/product_sku)只读 join,
 *     fulfill 拥有查询视图 SQL,Java 依赖仍走契约(同 FirstLegQueryMapper 先例,不破坏铁律 2)
 */
public interface FbaQueryMapper {

    /** FBA 发货单分页(id 倒序;单号模糊/状态/店铺/站点/发货仓过滤;店名仓名 join 投影) */
    Page<FbaShipmentResponse> pageRows(Page<FbaShipmentResponse> page,
                                       @Param("query") FbaShipmentQuery query);
}
