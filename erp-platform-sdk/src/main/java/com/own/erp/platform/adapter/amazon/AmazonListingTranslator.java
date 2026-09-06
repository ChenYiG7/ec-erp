package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon listing 报表 TSV → UnifiedProduct 翻译器(#3 联调预备骨架,防腐层内平台差异唯一容身处):
 *         - 输入 = GET_MERCHANT_LISTINGS_ALL_DATA 全量快照 flat file(TSV,首行列名);
 *           列名取自官方报表模板(seller-sku/item-name/asin1/price/quantity 等),
 *           **真实报表文件到位后须 --force 校准一轮**(docs/07 §8 官方样例单测纪律,
 *           现有单测 fixture 为模板推导的自制样例,非官方脱敏样本);
 *         - 行按 asin1 分组成 UnifiedProduct(平台商品=父体),行=平台 SKU(sellerSku 卖家编码
 *           即自动匹配 ERP SKU 的首选依据,#5);同 ASIN 取首行 item-name 为标题;
 *         - 币种拍板(2026-09-06,同日收口):listing 报表无币色列,currency 由调用方按站点静态表推导后
 *           传入(AmazonMarketplace 枚举,无凭证先落地,真凭证到位后抽样核对);
 *           旧口径"置空由落库侧按店铺站点推导"作废——平台知识归防腐层,落库侧零改动;
 *         - price/quantity 空串置 null(平台侧无数据 ≠ 0,禁静默归零);无 SKU 列缺失必填列(asin1/seller-sku)即拒,
 *           异常禁静默(docs/07 §8),上游拉单记 pull_log 走连续失败告警
 */
final class AmazonListingTranslator {

    private AmazonListingTranslator() {
    }

    /** TSV 全文 → UnifiedProduct 列表(按 asin1 分组,行序稳定);currency = 调用方按站点推导(AmazonMarketplace) */
    static List<UnifiedProduct> translate(String tsv, Long shopId, PlatformType platform, String currency) {
        if (StrUtil.isBlank(tsv)) {
            throw new IllegalStateException("listing 报表内容为空,拒绝静默落库");
        }
        String[] lines = tsv.split("\r?\n");
        if (lines.length < 2) {
            throw new IllegalStateException("listing 报表只有表头或为空,行数=" + lines.length);
        }
        Map<String, Integer> columnIndex = columnIndex(lines[0]);
        Map<String, UnifiedProduct> products = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            if (StrUtil.isBlank(lines[i])) {
                continue;
            }
            String[] cells = lines[i].split("\t", -1);
            String asin = cell(cells, columnIndex, "asin1");
            String sellerSku = cell(cells, columnIndex, "seller-sku");
            if (StrUtil.isBlank(asin) || StrUtil.isBlank(sellerSku)) {
                throw new IllegalStateException("listing 报表第 " + (i + 1) + " 行缺 asin1/seller-sku,拒绝静默落库");
            }
            UnifiedProduct.Sku sku = UnifiedProduct.Sku.builder()
                    .sellerSku(sellerSku)
                    .price(decimal(cell(cells, columnIndex, "price")))
                    .stock(integer(cell(cells, columnIndex, "quantity")))
                    .currency(currency) // 报表无币色列,调用方按站点静态表推导传入(拍板见类注释)
                    .build();
            UnifiedProduct product = products.get(asin);
            if (product == null) {
                product = UnifiedProduct.builder()
                        .platformProductId(asin)
                        .shopId(shopId)
                        .platform(platform)
                        .title(StrUtil.emptyToNull(cell(cells, columnIndex, "item-name")))
                        .skus(new ArrayList<>())
                        .build();
                products.put(asin, product);
            }
            product.getSkus().add(sku);
        }
        return new ArrayList<>(products.values());
    }

    /** 表头行 → 列名索引(缺失必填列即报错,带实际表头便于联调诊断模板差异) */
    private static Map<String, Integer> columnIndex(String headerLine) {
        String[] headers = headerLine.split("\t", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.putIfAbsent(headers[i].trim(), i);
        }
        for (String required : List.of("asin1", "seller-sku")) {
            if (!index.containsKey(required)) {
                throw new IllegalStateException("listing 报表缺必填列 " + required + ",实际表头:" + headerLine);
            }
        }
        return index;
    }

    /** 按列名取单元格(容错越界:短行按空处理,列定位以表头为准) */
    private static String cell(String[] cells, Map<String, Integer> columnIndex, String column) {
        Integer index = columnIndex.get(column);
        if (index == null || index >= cells.length) {
            return "";
        }
        return cells[index].trim();
    }

    private static BigDecimal decimal(String value) {
        return StrUtil.isBlank(value) ? null : new BigDecimal(value);
    }

    private static Integer integer(String value) {
        return StrUtil.isBlank(value) ? null : Integer.valueOf(value);
    }
}
