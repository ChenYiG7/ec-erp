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
 * @Date : 2026/9/4
 * @Description : 发货单(tracking_no 可多条 NULL,MySQL UNIQUE 不约束 NULL;FBA/海外仓履约订单不产生系统内发货单)(delivery_order)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_order")
public class DeliveryOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发货单号,唯一 */
    private String deliveryNo;

    /** 平台订单ID(shop_order.id) */
    private Long orderId;

    /** 店铺ID(shop.id,服务端按订单回填) */
    private Long shopId;

    /** 出库仓ID(warehouse.id,发货指定仓,ship 时从该仓扣库存;#11 激活加列 2026-09-04) */
    private Long warehouseId;

    /** MANUAL手工/WAYBILL电子面单/SUPPLIER供应商代发/FBA/OVERSEAS海外仓 */
    private String type;

    /** PENDING待发货/SHIPPED已发货/DELIVERED已签收/CANCELLED已取消 */
    private String status;

    /** 承诺发货时限(平台侧快照,国内平台考核;#11 激活加列 2026-09-04) */
    private LocalDateTime shipByTime;

    /** 物流公司 */
    private String logisticsCompany;

    /** 运单号 */
    private String trackingNo;

    /** 电子面单文件地址 */
    private String waybillUrl;

    /** 发货时间(ship 确认时回写) */
    private LocalDateTime shippedAt;

    /** 创建人(sys_user.id,接 SecurityContext 随前端工程;#11 激活加列 2026-09-04) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
