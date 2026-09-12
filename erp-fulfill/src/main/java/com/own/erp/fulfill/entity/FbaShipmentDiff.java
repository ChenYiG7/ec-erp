package com.own.erp.fulfill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA收货对账差异(SHIPPED=发出量 vs 平台收货登记量,SHORT/EXTRA/OK 三态;仿 RefundReconciliation 纪律)(fba_shipment_diff)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fba_shipment_diff")
public class FbaShipmentDiff {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** FBA发货单ID(fba_shipment.id) */
    private Long shipmentId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 发出量(=Σfba_box_item 该SKU件数) */
    private Integer shippedQty;

    /** 平台收货登记量(登记时必填,未登记 SKU 按 0 计) */
    private Integer receivedQty;

    /** 差异类型:SHORT缺收(received<shipped)/EXTRA多收(received>shipped)/OK一致 */
    private String diffType;

    /** 对账核对时间(登记动作时点) */
    private LocalDateTime checkedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
