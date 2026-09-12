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
 * @Description : FBA发货单(V1 内部数据面:计划→装箱→发出动账→平台收货登记→对账;单据域物理删除,SHIPPED起禁删)(fba_shipment)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fba_shipment")
public class FbaShipment {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** FBA发货单号 FB+yyyyMMdd+4位seq,服务端生成 */
    private String shipmentNo;

    /** 店铺ID(shop.id,归属信息,V1 不做存在性校验) */
    private Long shopId;

    /** 站点(如 US/UK/DE,手填) */
    private String marketplace;

    /** 国内发货仓ID(warehouse.id,wh_type=SELF,SHIPPED 出库动账仓) */
    private Long warehouseId;

    /** 平台 ShipmentId(V2 SP-API 回填,V1 手填可空) */
    private String platformShipmentId;

    /** DRAFT草稿/BOXED已装箱/SHIPPED已发出(库存已出库动账)/RECEIVING收货登记中/CLOSED已关闭/CANCELED已取消 */
    private String status;

    /** 发出时间(SHIPPED 动作时点=库存动账时点) */
    private LocalDateTime shippedAt;

    /** 最近一次收货登记时间(RECEIVING 态可重复登记覆盖) */
    private LocalDateTime receivedAt;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
