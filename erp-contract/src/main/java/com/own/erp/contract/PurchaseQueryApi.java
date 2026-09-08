package com.own.erp.contract;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购只读查询契约(#6 tools 扩容,#10 采购域数据面已齐):erp-ai 工具取数唯一正道
 *         (铁律 2,禁横向依赖 erp-purchase),实现收口 erp-api(PurchaseQueryApiImpl,
 *         委托 PurchaseOrderService.page/getById)。参数/返回全 record 不引 MP 类型;
 *         行视图只带 AI 查询所需字段;AI 只读,@Tool 侧禁写操作(铁律 7)
 */
public interface PurchaseQueryApi {

    /**
     * 采购单分页查询(按 id 倒序,服务端口径);过滤条件全空 = 全量分页。
     * 分页大小钳制 1..100(工具侧足够,服务端 PageQuery ≤500 兜底)
     */
    QueryPage<PurchaseOrderView> pagePurchaseOrders(PurchaseOrderFilter filter);

    /** 采购单详情(带全部明细行,答"这单买了什么/到货多少"走这里;不存在返回 null) */
    PurchaseOrderDetail getPurchaseOrderDetail(Long poId);

    /**
     * SKU→最新供应商映射(#17 采购建议工作流取数,2026-09-08 扩容,只加方法不改语义):
     * 每 SKU 取最近一笔非 DRAFT 采购单的明细行(供应商/最新单价/单号/时间);
     * 无采购历史的 SKU 不在返回中(调用方按"无法定位供应商"自行处理);skuIds 为空返回空列表
     */
    List<SkuSupplierView> findLatestSupplierBySkuIds(Collection<Long> skuIds);

    /**
     * 过滤条件 + 分页入参(全 record):pageNo/pageSize 为 int,@Builder 不设时默认 0,
     * 经 page()/size() 归一后生效(AI 侧漏传分页按第 1 页 / 20 条执行)
     */
    @Builder
    record PurchaseOrderFilter(

            /** 供应商ID(supplier.id,精确,可空) */
            Long supplierId,

            /** 收货仓ID(warehouse.id,精确,可空) */
            Long warehouseId,

            /** 采购单状态:DRAFT/AUDITED/PARTIAL_RECEIVED/RECEIVED/CLOSED(精确,可空) */
            String status,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 20,上限 100) */
        public int size() {
            return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        }
    }

    /** 采购单行视图(列表摘要,不含明细) */
    @Builder
    record PurchaseOrderView(

            /** 采购单ID(purchase_order.id) */
            Long id,

            /** 采购单号,唯一 */
            String poNo,

            /** 供应商ID(supplier.id) */
            Long supplierId,

            /** 收货仓ID(warehouse.id) */
            Long warehouseId,

            /** DRAFT草稿/AUDITED已审核/PARTIAL_RECEIVED部分入库/RECEIVED已入库/CLOSED已关闭 */
            String status,

            /** 采购总金额(本位币,服务端按 Σ(数量×单价) 计算) */
            BigDecimal totalAmount,

            /** 创建时间 */
            LocalDateTime createdAt
    ) {
    }

    /** 采购单详情(视图 + 全量明细) */
    @Builder
    record PurchaseOrderDetail(

            /** 采购单主体 */
            PurchaseOrderView order,

            /** 采购明细行 */
            List<Item> items
    ) {

        /** 采购明细行视图 */
        @Builder
        public record Item(

                /** 采购明细ID(purchase_order_item.id) */
                Long poItemId,

                /** SKU ID(product_sku.id) */
                Long skuId,

                /** 采购数量 */
                Integer quantity,

                /** 已入库数量(入库核销累加) */
                Integer arrivedQty,

                /** 采购单价(本位币) */
                BigDecimal purchasePrice
        ) {
        }
    }

    /**
     * SKU→最新供应商映射行(#17 采购建议取数):该 SKU 最近一笔非 DRAFT 采购的快照。
     * 最新单价即预估采购成本口径(草稿未定案不算历史)
     */
    @Builder
    record SkuSupplierView(

            /** SKU ID(product_sku.id) */
            Long skuId,

            /** 供应商ID(supplier.id) */
            Long supplierId,

            /** 供应商名称(supplier.name,建议展示与 LLM 摘要可读) */
            String supplierName,

            /** 最新采购单价(本位币;历史行为无价单时可能为 null,调用方按 0 兜底) */
            BigDecimal lastPrice,

            /** 最近采购单号(溯源) */
            String lastPoNo,

            /** 最近采购单创建时间 */
            LocalDateTime lastPoAt
    ) {
    }
}
