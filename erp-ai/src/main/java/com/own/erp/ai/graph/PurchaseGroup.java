package com.own.erp.ai.graph;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购建议工作流的供应商聚合组(#17):collect 产出的 SKU 级补货缺口按
 *     "最新采购供应商"分组聚合(映射来源 PurchaseQueryApi.findLatestSupplierBySkuIds 契约):
 *     行明细 = skuId/建议量/最新单价/行预估金额;组级 = 总件数/预估金额合计/是否含缺货 SKU;
 *     aggregate 节点产原始组(summary 空),summarize 节点回填摘要,persist 落 ai_suggestion
 *     (type=PURCHASE/refType=SUPPLIER/refId=supplierId,只产建议不碰采购单据)
 */
@Builder(toBuilder = true)
public record PurchaseGroup(

        /** 供应商ID(supplier.id) */
        Long supplierId,

        /** 供应商名称(supplier.name,展示与 LLM 摘要可读) */
        String supplierName,

        /** 建议采购总件数(Σ 行建议量) */
        int totalQty,

        /** 预估采购金额(Σ 最新单价×建议量;单价缺失行按 0 计) */
        BigDecimal estAmount,

        /** 是否含缺货 SKU(任一行可用≤0,persist 风险分级依据) */
        boolean hasStockout,

        /** 行明细 */
        List<Line> lines,

        /** 摘要(summarize 节点回填:LLM 文案或降级模板) */
        String summary
) {

        /** 行明细(供应商组内单个 SKU 的采购建议行) */
        @Builder(toBuilder = true)
        public record Line(

                /** SKU ID(product_sku.id) */
                Long skuId,

                /** 建议采购量(补货计算口径回填) */
                int suggestQty,

                /** 最新采购单价(本位币;无价历史行为 null,预估按 0) */
                BigDecimal lastPrice,

                /** 行预估金额(单价×建议量) */
                BigDecimal estAmount,

                /** 当前可用库存(跨仓合计,紧急度参考) */
                int qtyAvailable
        ) {
        }
}
