package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon 站点↔币种静态表单测:官方 SP-API「Store Identifiers」文档核对值抽查
 *     (含瑞典/沙特两个易错 ID)/ 穷举完整性(非空 ID+币种、无重复站点)/ 未配置与未收录即拒
 */
class AmazonMarketplaceTest {

    @Test
    void resolvesSpotCheckedMarketplacesAgainstOfficialIdentifiers() {
        // 北美
        assertEquals("USD", AmazonMarketplace.currencyOf("ATVPDKIKX0DER"));
        assertEquals("CAD", AmazonMarketplace.currencyOf("A2EUQ1WTGCTBG2"));
        // 欧洲:欧元区多国同币种 + 英镑/克朗/兹罗提独立币种
        assertEquals("GBP", AmazonMarketplace.currencyOf("A1F83G8C2ARO7P"));
        assertEquals("EUR", AmazonMarketplace.currencyOf("A1PA6795UKMFR9"));
        assertEquals("EUR", AmazonMarketplace.currencyOf("AMEN7PMS3EDWL"));
        assertEquals("SEK", AmazonMarketplace.currencyOf("A2NODRKZP88ZB9"));
        assertEquals("PLN", AmazonMarketplace.currencyOf("A1C3SOZRARQ6R3"));
        // 中东/南亚:沙特 A17E79C6D8DWNP 与瑞典同为易错 ID(勿与旧记忆值混淆)
        assertEquals("SAR", AmazonMarketplace.currencyOf("A17E79C6D8DWNP"));
        assertEquals("AED", AmazonMarketplace.currencyOf("A2VIGQ35RCS4UG"));
        assertEquals("INR", AmazonMarketplace.currencyOf("A21TJRUUN4KGV"));
        // 亚太
        assertEquals("JPY", AmazonMarketplace.currencyOf("A1VC38T7YXB528"));
        assertEquals("AUD", AmazonMarketplace.currencyOf("A39IBJ37TRP1C6"));
        assertEquals("SGD", AmazonMarketplace.currencyOf("A19VAU5U5O7RUS"));
    }

    @Test
    void allEntriesCarryNonBlankIdAndCurrencyWithoutDuplicates() {
        Set<String> ids = new HashSet<>();
        for (AmazonMarketplace marketplace : AmazonMarketplace.values()) {
            assertFalse(StrUtil.isBlank(marketplace.marketplaceId()), "枚举 " + marketplace.name() + " 缺站点 ID");
            assertFalse(StrUtil.isBlank(marketplace.currency()), "枚举 " + marketplace.name() + " 缺币种");
            assertTrue(ids.add(marketplace.marketplaceId()), "站点 ID 重复:" + marketplace.marketplaceId());
        }
        // 官方新增站点补枚举时同步更新此守卫(官方清单:SP-API Store Identifiers)
        assertEquals(23, ids.size());
    }

    @Test
    void rejectsBlankOrUnknownMarketplaceId() {
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> AmazonMarketplace.currencyOf(" "));
        assertTrue(blank.getMessage().contains("marketplace-ids"), blank.getMessage());

        IllegalStateException unknown = assertThrows(IllegalStateException.class,
                () -> AmazonMarketplace.currencyOf("A0NOTREAL0XX"));
        assertTrue(unknown.getMessage().contains("A0NOTREAL0XX"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("AmazonMarketplace"), unknown.getMessage());
    }
}
