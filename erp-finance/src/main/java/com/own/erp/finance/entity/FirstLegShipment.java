package com.own.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单(#33 头程运费分摊:装箱数据面+运费按策略分摊到SKU;单据域物理删除,SHIPPED起禁删)(first_leg_shipment)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("first_leg_shipment")
public class FirstLegShipment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 头程单号 FL+yyyyMMdd+4位seq,服务端生成 */
    private String shipmentNo;

    /** 国内发货仓ID(warehouse.id,wh_type=SELF) */
    private Long fromWarehouseId;

    /** 目的仓ID(warehouse.id,wh_type=OVERSEAS/FBA) */
    private Long toWarehouseId;

    /** 物流商(本期手工,四期物流商API取价) */
    private String carrier;

    /** 运单号(SHIPPED 录运费时录入) */
    private String waybillNo;

    /** 计费重 kg(SHIPPED 录入) */
    private BigDecimal chargeWeight;

    /** 体积重 kg(SHIPPED 录入) */
    private BigDecimal volumeWeight;

    /** 头程运费原币(SHIPPED 录入,必须大于0) */
    private BigDecimal freightAmount;

    /** 运费币种(ISO 4217) */
    private String currency;

    /** 折算汇率快照(1 currency=rate CNY;SHIPPED 录入即冻结:手填优先,空=按 shipped_at resolveRate,无报价拦截禁猜) */
    private BigDecimal exchangeRate;

    /** 运费本位币(=freight_amount×exchange_rate,分摊基准;CNY=1) */
    private BigDecimal freightCny;

    /** 发货时间(SHIPPED 动作时点,汇率回溯锚点) */
    private LocalDateTime shippedAt;

    /** 分摊策略:QTY按数量/WEIGHT按重量(默认,箱内件qty×product_sku.weight_g)/AMOUNT按金额(qty×最近采购价回退cost_price) */
    private String allocateStrategy;

    /** 分摊说明(全0分母降级按数量等,不静默;实际生效策略看 first_leg_alloc.strategy) */
    private String allocRemark;

    /** 分摊确认时间(ALLOCATED 动作时点) */
    private LocalDateTime allocatedAt;

    /** DRAFT草稿/BOXED已装箱/SHIPPED已发货(运费已录)/ALLOCATED已分摊/CLOSED已关闭/CANCELED已取消 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
