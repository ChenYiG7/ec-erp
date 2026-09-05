package com.own.erp.aftersale.entity;

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
 * @Description : 售后退货明细(receive-return 人工录入实收,同事务 IN_RETURN 动账凭证;#12 激活补,2026-09-04 拍板:仅 sku_id 已绑定行可退,归属/数量预校验防超退)(aftersale_return_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("aftersale_return_item")
public class AftersaleReturnItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 售后单ID(aftersale_order.id) */
    private Long aftersaleId;

    /** 订单明细ID(shop_order_item.id,SKU 归属锚点) */
    private Long orderItemId;

    /** 内部SKU ID(product_sku.id,服务端按订单行回填,不入参) */
    private Long skuId;

    /** 实收退货数量(正数,仓库验件人工录入,可≠平台申明) */
    private Integer returnQty;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
