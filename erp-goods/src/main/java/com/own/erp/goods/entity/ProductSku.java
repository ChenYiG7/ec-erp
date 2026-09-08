package com.own.erp.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SKU(product_sku):库存/发货/财务的最小单位。
 *     跨境字段(hsCode/declaredValue/battery)见 docs/03-数据库设计.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属SPU(product.id) */
    private Long productId;

    /** 内部 SKU 编码,唯一 —— 各平台 seller_sku 通过 shop_product_sku 映射到它 */
    private String skuCode;

    /** 条形码(EAN/UPC) */
    private String barcode;

    /** 规格值(JSON),如 {"颜色":"黑","尺码":"L"} */
    private String attrsJson;

    /** 成本价 DECIMAL(12,4) */
    private BigDecimal costPrice;

    /** 重量(g) */
    private Integer weightG;

    // ---- 跨境申报字段 ----

    /** 海关 HS 编码 */
    private String hsCode;

    /** 申报价值(币种随订单 currency) */
    private BigDecimal declaredValue;

    /** 是否含电池 1/0 */
    private Integer battery;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
