package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.GoodsQueryApi.ProductFilter;
import com.own.erp.contract.QueryPage;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 取数节点(#17 三期候选「产品描述生成」V1,SAA Graph 文案生成工作流):
 *     分页扫商品库启用商品(status=1,ProductFilter #17 扩容 status 过滤),只扫启用——
 *     停用商品无上架文案需求;去重(2026-09-07 拍板语义):同商品存在待确认(status=0)
 *     COPYWRITING 建议即跳过,采纳/忽略后可再产出,确认闭环自然运转;
 *     材料装配逐商品隔离(品牌/类目/SKU 查询失败只跳过该商品记日志,不殃及整轮);
 *     SKU 行截前 {@link #SKU_PROMPT_MAX} 条防超变体商品拉爆 prompt(硬护栏属实现细节,不入配置,
 *     拍板记录 devlog);取数只走只读契约(铁律 2/7),扫描护栏走 erp.ai.copy.*(yml)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CopyCollectNode implements NodeAction {

    /** 单商品进 prompt 的 SKU 行上限(超变体截断,防 prompt 拉爆;截断仅丢行不丢商品) */
    static final int SKU_PROMPT_MAX = 20;

    private final @Lazy GoodsQueryApi goodsQueryApi;
    private final ErpAiProperties props;
    private final AiSuggestionService aiSuggestionService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<CopyItem> items = new ArrayList<>();
        int scanned = scanEnabledProducts(items);
        int skipped = dedupPending(items);
        log.info("文案生成取数完成:扫描启用商品 {} 个,去重跳过 {} 个,待生成 {} 个",
                scanned, skipped, items.size());
        return Map.of(CopyStateKeys.KEY_ITEMS, items,
                CopyStateKeys.KEY_SCANNED, scanned, CopyStateKeys.KEY_SKIPPED, skipped);
    }

    /** 分页扫启用商品并装配材料,失败只记日志返回已扫数(单轮隔离,已扫部分照常生效) */
    private int scanEnabledProducts(List<CopyItem> items) {
        int scanPageSize = props.getCopy().getScanPageSize();
        int scanMaxRows = props.getCopy().getScanMaxRows();
        int scanned = 0;
        try {
            int pageNo = 1;
            while (scanned < scanMaxRows) {
                QueryPage<GoodsQueryApi.ProductView> page = goodsQueryApi.pageProducts(
                        ProductFilter.builder().status(1).pageNo(pageNo).pageSize(scanPageSize).build());
                List<GoodsQueryApi.ProductView> rows = page == null ? null : page.list();
                if (CollUtil.isEmpty(rows)) {
                    break;
                }
                for (GoodsQueryApi.ProductView row : rows) {
                    assembleMaterial(row, items);
                }
                scanned += rows.size();
                pageNo++;
                if (rows.size() < scanPageSize) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("文案生成商品扫描执行失败,本轮返回已扫部分 :{}", e.getMessage(), e);
        }
        return scanned;
    }

    /** 逐商品装配文案材料;材料查询失败只跳过该商品(逐商品隔离,同 AlertEngine 单规则隔离口径) */
    private void assembleMaterial(GoodsQueryApi.ProductView row, List<CopyItem> items) {
        if (row == null || row.id() == null) {
            return;
        }
        try {
            List<GoodsQueryApi.SkuView> skus = goodsQueryApi.listSkusByProductId(row.id());
            List<CopyItem.SkuLine> lines = CollUtil.isEmpty(skus) ? List.of()
                    : skus.stream().limit(SKU_PROMPT_MAX)
                            .map(sku -> new CopyItem.SkuLine(sku.skuCode(), sku.attrsJson(),
                                    sku.weightG(), sku.battery()))
                            .toList();
            items.add(CopyItem.builder()
                    .productId(row.id())
                    .spuCode(row.spuCode())
                    .productName(row.name())
                    .brandName(goodsQueryApi.findBrandNameById(row.brandId()))
                    .categoryName(goodsQueryApi.findCategoryNameById(row.categoryId()))
                    .attrsJson(row.attrsJson())
                    .skus(lines)
                    .build());
        } catch (Exception e) {
            log.warn("商品[{}]文案材料装配失败,本轮跳过 :{}", row.id(), e.getMessage());
        }
    }

    /** 同商品已存在待确认建议则移除(定时/手动统一口径),返回移除数;无商品不查库 */
    private int dedupPending(List<CopyItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        Set<Long> pending = aiSuggestionService.findPendingRefIds(
                AiConsts.TYPE_COPYWRITING, AiConsts.REF_TYPE_GOODS_PRODUCT);
        int before = items.size();
        items.removeIf(item -> pending.contains(item.productId()));
        return before - items.size();
    }
}
