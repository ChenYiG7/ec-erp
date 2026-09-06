package com.own.erp.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单销量日统计(order_sales_daily,#6 销量数据面,docs/03 §7.1):
 *     支付日×SKU 合计购买数量,已支付态(WAIT_SHIP/SHIPPED/COMPLETED)口径,未绑定 SKU 不统计;
 *     erp-api SalesSnapshotJob 每日窗口 upsert(uk_sku_date 幂等),读侧只读契约 SalesQueryApi
 *     (补货动销公式/预警滞销积压规则数据面)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射,@Builder 供纯构造装配位;
 * 双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_sales_daily")
public class OrderSalesDaily {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期(支付日口径,paid_time 所在日) */
    private LocalDate statDate;

    /** 内部SKU ID(product_sku.id),未绑定 SKU 不统计 */
    private Long skuId;

    /** 当日销量(购买数量合计,已支付态 WAIT_SHIP/SHIPPED/COMPLETED) */
    private Integer qtySold;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
