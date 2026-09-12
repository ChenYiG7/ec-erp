package com.own.erp.fulfill.response;

import com.own.erp.fulfill.entity.FbaShipment;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA发货单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位;
 *     wither 副本补详情装配位(计划行/装箱树/diff 行,同 FirstLegShipmentResponse.withBoxes 先例)
 */
@Builder
public record FbaShipmentResponse(

        /** 主键 */
        Long id,

        /** FBA发货单号 FB+yyyyMMdd+4位seq,服务端生成 */
        String shipmentNo,

        /** 店铺ID(shop.id,归属信息) */
        Long shopId,

        /** 店铺名称(join shop 只读投影,分页/详情装配) */
        String shopName,

        /** 站点(如 US/UK/DE,手填) */
        String marketplace,

        /** 国内发货仓ID(warehouse.id,wh_type=SELF,SHIPPED 出库动账仓) */
        Long warehouseId,

        /** 发货仓名(join warehouse 只读投影) */
        String warehouseName,

        /** 平台 ShipmentId(V2 SP-API 回填,V1 手填可空) */
        String platformShipmentId,

        /** DRAFT草稿/BOXED已装箱/SHIPPED已发出(库存已出库动账)/RECEIVING收货登记中/CLOSED已关闭/CANCELED已取消 */
        String status,

        /** 发出时间(SHIPPED 动作时点=库存动账时点) */
        LocalDateTime shippedAt,

        /** 最近一次收货登记时间(RECEIVING 态可重复登记覆盖) */
        LocalDateTime receivedAt,

        /** 备注 */
        String remark,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 计划行(SKU 清单;详情装配) */
        List<FbaPlanItemResponse> planItems,

        /** 装箱树(箱+内件,内件带 skuCode;详情装配) */
        List<FbaBoxResponse> boxes,

        /** 收货对账差异行(SHIPPED=发出量 vs 平台收货登记量;详情装配) */
        List<FbaDiffResponse> diffs

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static FbaShipmentResponse from(FbaShipment entity) {
        return FbaShipmentResponse.builder()
                .id(entity.getId())
                .shipmentNo(entity.getShipmentNo())
                .shopId(entity.getShopId())
                .marketplace(entity.getMarketplace())
                .warehouseId(entity.getWarehouseId())
                .platformShipmentId(entity.getPlatformShipmentId())
                .status(entity.getStatus())
                .shippedAt(entity.getShippedAt())
                .receivedAt(entity.getReceivedAt())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情装配计划行/装箱树/diff 行(禁为补字段回退可变模型,docs/07 §1 ③) */
    public FbaShipmentResponse withDetail(List<FbaPlanItemResponse> planItems,
                                          List<FbaBoxResponse> boxes,
                                          List<FbaDiffResponse> diffs) {
        return new FbaShipmentResponse(id, shipmentNo, shopId, shopName, marketplace, warehouseId, warehouseName,
                platformShipmentId, status, shippedAt, receivedAt, remark, createdBy, createdAt, updatedAt,
                planItems, boxes, diffs);
    }
}
