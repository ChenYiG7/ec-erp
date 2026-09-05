package com.own.erp.goods.request.command;

import com.own.erp.goods.entity.ProductSku;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SKU 写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     刻意不含 id/createdAt/updatedAt(服务端管理);productId 创建时可由 SPU 事务内回填,
 *     单独新增 SKU 时由入参携带
 */
@Builder
public record ProductSkuSaveRequest(

        /** 所属SPU(product.id) */
        Long productId,

        /** 内部 SKU 编码,唯一——各平台 seller_sku 通过 shop_product_sku 映射到它 */
        @NotBlank(message = "SKU编码不能为空")
        String skuCode,

        /** 条形码(EAN/UPC) */
        String barcode,

        /** 规格值(JSON),如 {"颜色":"黑","尺码":"L"} */
        String attrsJson,

        /** 成本价 DECIMAL(12,4) */
        BigDecimal costPrice,

        /** 重量(g) */
        Integer weightG,

        /** 海关 HS 编码 */
        String hsCode,

        /** 申报价值(币种随订单 currency) */
        BigDecimal declaredValue,

        /** 是否含电池 1/0 */
        Integer battery,

        /** 1=启用 0=禁用 */
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public ProductSku toEntity() {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSkuCode(skuCode);
        sku.setBarcode(barcode);
        sku.setAttrsJson(attrsJson);
        sku.setCostPrice(costPrice);
        sku.setWeightG(weightG);
        sku.setHsCode(hsCode);
        sku.setDeclaredValue(declaredValue);
        sku.setBattery(battery);
        sku.setStatus(status);
        return sku;
    }
}
