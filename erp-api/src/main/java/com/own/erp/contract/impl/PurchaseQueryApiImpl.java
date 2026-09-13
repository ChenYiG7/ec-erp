package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.purchase.request.query.PurchaseOrderQuery;
import com.own.erp.purchase.response.PurchaseOrderItemResponse;
import com.own.erp.purchase.response.PurchaseOrderResponse;
import com.own.erp.purchase.response.PurchaseOverdueRow;
import com.own.erp.purchase.response.SkuSupplierRow;
import com.own.erp.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : PurchaseQueryApi 实现(#6 tools 扩容,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-purchase PurchaseOrderService,过滤/分页参数在域 Query 侧沿用既有钳制;
 *         entity→契约 record 显式逐字段映射(经域 Response 中转,禁反射拷贝)
 *         数据权限(#27①):显式不注入——采购挂供应商/仓库轴不挂店铺轴(计划书拍板)
 */
@Component
@RequiredArgsConstructor
public class PurchaseQueryApiImpl implements PurchaseQueryApi {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public QueryPage<PurchaseOrderView> pagePurchaseOrders(PurchaseOrderFilter filter) {
        PurchaseOrderQuery query = new PurchaseOrderQuery();
        query.setSupplierId(filter.supplierId());
        query.setWarehouseId(filter.warehouseId());
        query.setStatus(filter.status());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<PurchaseOrderResponse> page = purchaseOrderService.page(query);
        return QueryPage.of(page.getRecords().stream().map(PurchaseQueryApiImpl::toView).toList(),
                page.getTotal());
    }

    @Override
    public PurchaseOrderDetail getPurchaseOrderDetail(Long poId) {
        PurchaseOrderResponse po = purchaseOrderService.getById(poId);
        if (po == null) {
            return null;
        }
        List<PurchaseOrderDetail.Item> items = po.items() == null ? List.of() : po.items().stream()
                .map(PurchaseQueryApiImpl::toItem)
                .toList();
        return PurchaseOrderDetail.builder().order(toView(po)).items(items).build();
    }

    @Override
    public List<SkuSupplierView> findLatestSupplierBySkuIds(Collection<Long> skuIds) {
        return purchaseOrderService.findLatestSupplierBySkuIds(skuIds).stream()
                .map(PurchaseQueryApiImpl::toSkuSupplierView)
                .toList();
    }

    @Override
    public List<PurchasePayableView> findPurchasePayables(Collection<Long> poIds) {
        return purchaseOrderService.listByIds(poIds).stream()
                .map(PurchaseQueryApiImpl::toPayableView)
                .toList();
    }

    @Override
    public List<PurchaseOverdueView> listOverduePayables(LocalDate asOf) {
        return purchaseOrderService.listOverduePayables(asOf).stream()
                .map(PurchaseQueryApiImpl::toOverdueView)
                .toList();
    }

    /** 域投影行 → 契约视图显式逐字段映射(#31 账期提醒,漏字段编译期可见,禁反射拷贝) */
    private static PurchaseOverdueView toOverdueView(PurchaseOverdueRow row) {
        return PurchaseOverdueView.builder()
                .poId(row.getPoId())
                .poNo(row.getPoNo())
                .supplierId(row.getSupplierId())
                .supplierName(row.getSupplierName())
                .settleDays(row.getSettleDays())
                .auditTime(row.getAuditTime())
                .unpaidAmount(row.getUnpaidAmount())
                .build();
    }

    /** 域投影行 → 契约视图显式逐字段映射(漏字段编译期可见,禁反射拷贝) */
    private static SkuSupplierView toSkuSupplierView(SkuSupplierRow row) {
        return SkuSupplierView.builder()
                .skuId(row.getSkuId())
                .supplierId(row.getSupplierId())
                .supplierName(row.getSupplierName())
                .lastPrice(row.getLastPrice())
                .lastPoNo(row.getLastPoNo())
                .lastPoAt(row.getLastPoAt())
                .build();
    }

    /** Response → 行视图显式逐字段映射(契约不依赖域 Response 类型,漏字段编译期可见) */
    private static PurchaseOrderView toView(PurchaseOrderResponse po) {
        return PurchaseOrderView.builder()
                .id(po.id())
                .poNo(po.poNo())
                .supplierId(po.supplierId())
                .warehouseId(po.warehouseId())
                .status(po.status())
                .totalAmount(po.totalAmount())
                .createdAt(po.createdAt())
                .build();
    }

    private static PurchaseOrderDetail.Item toItem(PurchaseOrderItemResponse i) {
        return PurchaseOrderDetail.Item.builder()
                .poItemId(i.id())
                .skuId(i.skuId())
                .quantity(i.quantity())
                .arrivedQty(i.arrivedQty())
                .purchasePrice(i.purchasePrice())
                .build();
    }

    /** Response → 付款校验视图显式逐字段映射(#31,漏字段编译期可见) */
    private static PurchasePayableView toPayableView(PurchaseOrderResponse po) {
        return PurchasePayableView.builder()
                .id(po.id())
                .poNo(po.poNo())
                .supplierId(po.supplierId())
                .status(po.status())
                .totalAmount(po.totalAmount())
                .build();
    }
}
