package com.own.erp.goods.request.command;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品整体创建入参:SPU + SKU 列表同事务保存(docs/07 §1 CQRS 分包;
 *     组合命令整体是一个 command,内部复用 ProductSaveRequest/ProductSkuSaveRequest)
 */
public record ProductCreateRequest(ProductSaveRequest product, List<ProductSkuSaveRequest> skus) {
}
