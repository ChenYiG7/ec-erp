package com.own.erp.platform.unified;

import com.own.erp.platform.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 统一商品模型(平台侧商品,非 ERP 商品库)。
 *     拉取后进入"店铺商品"表,由人工/规则绑定到 ERP 商品 SKU。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedProduct {

    private String platformProductId;
    private Long shopId;
    private PlatformType platform;

    private String title;
    private String categoryId;
    private String status;

    private List<Sku> skus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sku {
        private String platformSkuId;
        private String props;
        private BigDecimal price;
        private Integer stock;
        /** 平台侧商家编码,自动匹配 ERP SKU 的首选依据 */
        private String sellerSku;
        /** 售价币种(ISO 4217),2026-09-04 #5 演进新增(只加字段不改语义) */
        private String currency;
    }
}
