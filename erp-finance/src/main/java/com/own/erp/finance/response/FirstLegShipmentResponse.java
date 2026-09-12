package com.own.erp.finance.response;

import com.own.erp.finance.entity.FirstLegShipment;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单对外结构(docs/07 §1:record+@Builder 读侧不可变);
 *     列表分页由 XML 联表投影直接带出双仓名(轻量读模型),详情用 withBoxes/withAllocs 补装箱与分摊(wither 副本)
 */
@Builder
public record FirstLegShipmentResponse(

        /** 主键 */
        Long id,

        /** 头程单号 FL+yyyyMMdd+4位seq */
        String shipmentNo,

        /** 国内发货仓ID */
        Long fromWarehouseId,

        /** 目的仓ID */
        Long toWarehouseId,

        /** 国内发货仓名(XML join 投影;仓库删除后仍显名,跨域 join 不滤已删) */
        String fromWarehouseName,

        /** 目的仓名(XML join 投影) */
        String toWarehouseName,

        /** 物流商 */
        String carrier,

        /** 运单号 */
        String waybillNo,

        /** 计费重 kg */
        BigDecimal chargeWeight,

        /** 体积重 kg */
        BigDecimal volumeWeight,

        /** 头程运费原币 */
        BigDecimal freightAmount,

        /** 运费币种 */
        String currency,

        /** 折算汇率快照 */
        BigDecimal exchangeRate,

        /** 运费本位币(分摊基准) */
        BigDecimal freightCny,

        /** 发货时间 */
        LocalDateTime shippedAt,

        /** 主单记录的分摊策略:QTY/WEIGHT/AMOUNT */
        String allocateStrategy,

        /** 分摊说明(降级等) */
        String allocRemark,

        /** 分摊确认时间 */
        LocalDateTime allocatedAt,

        /** 状态:DRAFT/BOXED/SHIPPED/ALLOCATED/CLOSED/CANCELED */
        String status,

        /** 备注 */
        String remark,

        /** 创建人 */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 装箱(箱+内件;列表为 null,详情 withBoxes 填充) */
        List<FirstLegBoxResponse> boxes,

        /** 分摊结果行(列表为 null,详情 withAllocs 填充) */
        List<FirstLegAllocResponse> allocs

) {

    /** 实体 → Response(无联表时仓名为 null;boxes/allocs 缺省 null,由 wither 补) */
    public static FirstLegShipmentResponse from(FirstLegShipment entity) {
        return FirstLegShipmentResponse.builder()
                .id(entity.getId())
                .shipmentNo(entity.getShipmentNo())
                .fromWarehouseId(entity.getFromWarehouseId())
                .toWarehouseId(entity.getToWarehouseId())
                .carrier(entity.getCarrier())
                .waybillNo(entity.getWaybillNo())
                .chargeWeight(entity.getChargeWeight())
                .volumeWeight(entity.getVolumeWeight())
                .freightAmount(entity.getFreightAmount())
                .currency(entity.getCurrency())
                .exchangeRate(entity.getExchangeRate())
                .freightCny(entity.getFreightCny())
                .shippedAt(entity.getShippedAt())
                .allocateStrategy(entity.getAllocateStrategy())
                .allocRemark(entity.getAllocRemark())
                .allocatedAt(entity.getAllocatedAt())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 详情补装箱(wither 副本,禁回退可变模型,docs/07 §1 分级③) */
    public FirstLegShipmentResponse withBoxes(List<FirstLegBoxResponse> boxes) {
        return FirstLegShipmentResponse.builder()
                .id(id).shipmentNo(shipmentNo).fromWarehouseId(fromWarehouseId).toWarehouseId(toWarehouseId)
                .fromWarehouseName(fromWarehouseName).toWarehouseName(toWarehouseName)
                .carrier(carrier).waybillNo(waybillNo).chargeWeight(chargeWeight).volumeWeight(volumeWeight)
                .freightAmount(freightAmount).currency(currency).exchangeRate(exchangeRate).freightCny(freightCny)
                .shippedAt(shippedAt).allocateStrategy(allocateStrategy).allocRemark(allocRemark)
                .allocatedAt(allocatedAt).status(status).remark(remark).createdBy(createdBy)
                .createdAt(createdAt).updatedAt(updatedAt)
                .boxes(boxes).allocs(allocs)
                .build();
    }

    /** 详情补分摊结果(wither 副本) */
    public FirstLegShipmentResponse withAllocs(List<FirstLegAllocResponse> allocs) {
        return FirstLegShipmentResponse.builder()
                .id(id).shipmentNo(shipmentNo).fromWarehouseId(fromWarehouseId).toWarehouseId(toWarehouseId)
                .fromWarehouseName(fromWarehouseName).toWarehouseName(toWarehouseName)
                .carrier(carrier).waybillNo(waybillNo).chargeWeight(chargeWeight).volumeWeight(volumeWeight)
                .freightAmount(freightAmount).currency(currency).exchangeRate(exchangeRate).freightCny(freightCny)
                .shippedAt(shippedAt).allocateStrategy(allocateStrategy).allocRemark(allocRemark)
                .allocatedAt(allocatedAt).status(status).remark(remark).createdBy(createdBy)
                .createdAt(createdAt).updatedAt(updatedAt)
                .boxes(boxes).allocs(allocs)
                .build();
    }
}
