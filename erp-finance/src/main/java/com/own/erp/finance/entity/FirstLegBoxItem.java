package com.own.erp.finance.entity;

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
 * @Date : 2026/9/11
 * @Description : 头程箱内件(分摊数量源:跨箱同SKU Σquantity)(first_leg_box_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("first_leg_box_item")
public class FirstLegBoxItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 箱ID(first_leg_box.id) */
    private Long boxId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 箱内件数(大于0;同一箱内同一SKU唯一,合并为一行) */
    private Integer quantity;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
