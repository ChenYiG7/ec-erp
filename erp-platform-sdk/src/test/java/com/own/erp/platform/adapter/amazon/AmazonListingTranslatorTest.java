package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedProduct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon listing 报表翻译器单测:fixture 列名取自官方 GET_MERCHANT_LISTINGS_ALL_DATA 模板
 *     (self-made 样例,非官方脱敏报表文件),真凭证报表到位后 --force 校准(docs/07 §8);
 *     断言:按 asin1 分组/卖家编码为匹配依据/币种调用方透传/空值禁归零/必填列缺失即拒
 */
class AmazonListingTranslatorTest {

    private static final String LISTING_TSV = """
            seller-sku\titem-name\titem-description\tlisting-id\tprice\topen-date\tasin1\tquantity
            ERP-SKU-001\tWireless Earbuds\tBT5.3\tA1B2C3\t41.00\t2026-01-02\tB00EXAMPLE1\t7
            ERP-SKU-001-BLUE\tWireless Earbuds\tBT5.3\tA1B2C4\t43.50\t2026-01-02\tB00EXAMPLE1\t3
            ERP-SKU-002\tUSB-C Cable\t1m\tD4E5F6\t\t2026-02-01\tB00EXAMPLE2\t
            """;

    @Test
    void groupsRowsByAsinAndMapsSellerSkuAsMatchKey() {
        var products = AmazonListingTranslator.translate(LISTING_TSV, 7L, PlatformType.AMAZON, "USD");

        // 同 ASIN 两行(变体)归一个平台商品,单 ASIN 一行独立
        assertEquals(2, products.size());
        UnifiedProduct first = products.get(0);
        assertEquals("B00EXAMPLE1", first.getPlatformProductId());
        assertEquals(7L, first.getShopId());
        assertEquals(PlatformType.AMAZON, first.getPlatform());
        assertEquals("Wireless Earbuds", first.getTitle());
        assertEquals(2, first.getSkus().size());
        assertEquals("ERP-SKU-001", first.getSkus().get(0).getSellerSku());
        assertEquals(0, first.getSkus().get(0).getPrice().compareTo(new java.math.BigDecimal("41.00")));
        assertEquals(7, first.getSkus().get(0).getStock());
        assertEquals("ERP-SKU-001-BLUE", first.getSkus().get(1).getSellerSku());
        // 报表无币色列:调用方按站点静态表推导传入(AmazonMarketplace),翻译器透传
        assertEquals("USD", first.getSkus().get(0).getCurrency());
        assertEquals("USD", first.getSkus().get(1).getCurrency());

        // price/quantity 空串 = 平台无数据,置 null 禁静默归零
        UnifiedProduct second = products.get(1);
        assertNull(second.getSkus().get(0).getPrice());
        assertNull(second.getSkus().get(0).getStock());
    }

    @Test
    void rejectsBlankContentOrHeaderOnlyReport() {
        assertThrows(IllegalStateException.class,
                () -> AmazonListingTranslator.translate(" ", 7L, PlatformType.AMAZON, "USD"));
        IllegalStateException headerOnly = assertThrows(IllegalStateException.class,
                () -> AmazonListingTranslator.translate("seller-sku\tasin1\n", 7L, PlatformType.AMAZON, "USD"));
        assertTrue(headerOnly.getMessage().contains("只有表头"), headerOnly.getMessage());
    }

    @Test
    void rejectsMissingRequiredColumnWithActualHeader() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AmazonListingTranslator.translate("seller-sku\titem-name\nA\tX", 7L, PlatformType.AMAZON, "USD"));
        assertTrue(e.getMessage().contains("asin1"), e.getMessage());
        assertTrue(e.getMessage().contains("实际表头"), e.getMessage());
    }

    @Test
    void rejectsRowMissingAsinOrSku() {
        String tsv = "seller-sku\titem-name\tasin1\tprice\tquantity\n"
                + "\tNo ASIN and No SKU\tB00EXAMPLE1\t1.00\t1\n";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AmazonListingTranslator.translate(tsv, 7L, PlatformType.AMAZON, "USD"));
        assertTrue(e.getMessage().contains("缺 asin1/seller-sku"), e.getMessage());
    }
}
