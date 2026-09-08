package com.own.erp.ai.tools;

import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购查询工具(#6 tools 扩容,qihang 11 类 checklist Purchase 类,随 PurchaseQueryApi
 *         契约开工具,#10 采购域数据面已齐)。只读铁律(铁律 7):方法全部走 PurchaseQueryApi 只读契约,
 *         无任何写路径;SQL 不经模型拼装(参数即工具入参);契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Component
public class PurchaseTools {

    private final PurchaseQueryApi purchaseQueryApi;

    public PurchaseTools(@Lazy PurchaseQueryApi purchaseQueryApi) {
        this.purchaseQueryApi = purchaseQueryApi;
    }

    @Tool(description = "分页查询采购单列表(按ID倒序)。过滤条件均可选,全部不传=全量分页;返回采购单摘要与总数")
    public QueryPage<PurchaseQueryApi.PurchaseOrderView> listPurchaseOrders(
            @ToolParam(required = false, description = "供应商ID,精确过滤") Long supplierId,
            @ToolParam(required = false, description = "收货仓ID,精确过滤") Long warehouseId,
            @ToolParam(required = false, description = "采购单状态:DRAFT/AUDITED/PARTIAL_RECEIVED/RECEIVED/CLOSED") String status,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return purchaseQueryApi.pagePurchaseOrders(PurchaseQueryApi.PurchaseOrderFilter.builder()
                .supplierId(supplierId)
                .warehouseId(warehouseId)
                .status(status)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }

    @Tool(description = "按采购单ID查采购单详情(含明细行:SKU/采购数量/已入库数量/单价)。采购单不存在返回 null")
    public PurchaseQueryApi.PurchaseOrderDetail getPurchaseOrder(
            @ToolParam(description = "采购单ID(purchase_order.id)") Long poId) {
        return purchaseQueryApi.getPurchaseOrderDetail(poId);
    }
}
