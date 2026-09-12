package com.own.erp.fulfill.entity;

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
 * @Date : 2026/9/12
 * @Description : FBA箱(装箱单;明细域随主单物理删,改单先删后插)(fba_box)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fba_box")
public class FbaBox {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** FBA发货单ID(fba_shipment.id) */
    private Long shipmentId;

    /** 箱号(单内唯一) */
    private String boxNo;

    /** 整箱实重(毛重)kg(装箱记录面) */
    private BigDecimal weight;

    /** 外长 cm */
    private Integer lengthCm;

    /** 外宽 cm */
    private Integer widthCm;

    /** 外高 cm */
    private Integer heightCm;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
