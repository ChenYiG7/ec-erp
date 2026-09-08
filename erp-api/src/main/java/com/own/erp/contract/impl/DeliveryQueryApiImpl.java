package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.DeliveryQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.fulfill.request.query.DeliveryOrderQuery;
import com.own.erp.fulfill.response.DeliveryOrderItemResponse;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import com.own.erp.fulfill.service.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : DeliveryQueryApi 实现(#6 tools 扩容,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-fulfill DeliveryOrderService,过滤/分页参数在域 Query 侧沿用既有钳制;
 *         entity→契约 record 显式逐字段映射(经域 Response 中转,禁反射拷贝)
 */
@Component
@RequiredArgsConstructor
public class DeliveryQueryApiImpl implements DeliveryQueryApi {

    private final DeliveryOrderService deliveryOrderService;

    @Override
    public QueryPage<DeliveryView> pageDeliveries(DeliveryFilter filter) {
        DeliveryOrderQuery query = new DeliveryOrderQuery();
        query.setDeliveryNo(filter.deliveryNo());
        query.setOrderId(filter.orderId());
        query.setShopId(filter.shopId());
        query.setStatus(filter.status());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<DeliveryOrderResponse> page = deliveryOrderService.page(query);
        return QueryPage.of(page.getRecords().stream().map(DeliveryQueryApiImpl::toView).toList(),
                page.getTotal());
    }

    @Override
    public DeliveryDetail getDeliveryDetail(Long deliveryId) {
        DeliveryOrderResponse delivery = deliveryOrderService.getById(deliveryId);
        if (delivery == null) {
            return null;
        }
        List<DeliveryDetail.Item> items = delivery.items() == null ? List.of() : delivery.items().stream()
                .map(DeliveryQueryApiImpl::toItem)
                .toList();
        return DeliveryDetail.builder().order(toView(delivery)).items(items).build();
    }

    /** Response → 行视图显式逐字段映射(契约不依赖域 Response 类型,漏字段编译期可见) */
    private static DeliveryView toView(DeliveryOrderResponse d) {
        return DeliveryView.builder()
                .id(d.id())
                .deliveryNo(d.deliveryNo())
                .orderId(d.orderId())
                .shopId(d.shopId())
                .warehouseId(d.warehouseId())
                .type(d.type())
                .status(d.status())
                .logisticsCompany(d.logisticsCompany())
                .trackingNo(d.trackingNo())
                .shipByTime(d.shipByTime())
                .shippedAt(d.shippedAt())
                .build();
    }

    private static DeliveryDetail.Item toItem(DeliveryOrderItemResponse i) {
        return DeliveryDetail.Item.builder()
                .deliveryItemId(i.id())
                .orderItemId(i.orderItemId())
                .skuId(i.skuId())
                .shipQty(i.shipQty())
                .build();
    }
}
