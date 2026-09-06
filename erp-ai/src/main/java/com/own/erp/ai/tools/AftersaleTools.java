package com.own.erp.ai.tools;

import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 售后单查询工具(#6,启航 11 类 checklist:Aftersale 类已开;余量待随查询契约扩容)。
 *         只读铁律(铁律 7):售后处理动作(同意/拒绝/收退件/退款/完成)是人工五动作,
 *         走 erp-aftersale 自身接口,本工具只读无任何写路径;契约接口注入一律 @Lazy(docs/07 §2.2)
 */
@Component
public class AftersaleTools {

    private final AftersaleQueryApi aftersaleQueryApi;

    public AftersaleTools(@Lazy AftersaleQueryApi aftersaleQueryApi) {
        this.aftersaleQueryApi = aftersaleQueryApi;
    }

    @Tool(description = "分页查询售后单。过滤条件均可选;状态:PENDING待处理/APPROVED已同意/RETURNING待收退件/"
            + "RETURN_RECEIVED已收退件/REFUNDED已退款/COMPLETED已完成/REJECTED已拒绝/CANCELLED已取消;"
            + "类型:REFUND_ONLY仅退款/RETURN_REFUND退货退款/EXCHANGE换货/RESEND补发")
    public QueryPage<AftersaleQueryApi.AftersaleView> listAftersales(
            @ToolParam(required = false, description = "店铺ID,精确过滤") Long shopId,
            @ToolParam(required = false, description = "售后状态,精确过滤") String status,
            @ToolParam(required = false, description = "售后类型,精确过滤") String type,
            @ToolParam(required = false, description = "关联订单ID(shop_order.id),精确过滤") Long orderId,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return aftersaleQueryApi.pageAftersales(AftersaleQueryApi.AftersaleFilter.builder()
                .shopId(shopId)
                .status(status)
                .type(type)
                .orderId(orderId)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }
}
