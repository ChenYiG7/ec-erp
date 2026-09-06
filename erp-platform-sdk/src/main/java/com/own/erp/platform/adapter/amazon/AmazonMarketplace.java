package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon 站点↔币种静态映射(TODO(#3) 槽位收口,2026-09-06 无凭证落地):
 *         listing 报表(GET_MERCHANT_LISTINGS_ALL_DATA)无币色列,UnifiedProduct.Sku.currency
 *         由本表按 marketplaceId 推导(报表 createReport 单站点,值即配置 erp.adapter.amazon.marketplace-ids);
 *         23 站点全量收录,ID/币种按官方 SP-API「Store Identifiers」文档逐项核对(2026-09-06,
 *         含 2024 后新增南非/埃及/爱尔兰/比利时站;瑞典 A2NODRKZP88ZB9、沙特 A17E79C6D8DWNP 为易错 ID),
 *         真凭证到位后抽样核对即可;官方新增站点时补枚举一行并同步 AmazonMarketplacesTest 站点数守卫;
 *         未收录/未配置站点拉单即报错拒静默(禁猜币种落脏账,docs/07 §8),且 AmazonClient.pullProducts
 *         先于报表创建推导(未收录不空耗平台侧 15~60 分钟报表生成);
 *         AWS SigV4 region/SP-API host(NA/EU/FE 三端点)仍走配置,不在本枚举射程
 */
public enum AmazonMarketplace {

    /** 北美站 */
    US("ATVPDKIKX0DER", "USD"),
    CA("A2EUQ1WTGCTBG2", "CAD"),
    MX("A1AM78C64UM0Y8", "MXN"),
    BR("A2Q3Y263D00KWC", "BRL"),

    /** 欧洲/中东/南亚站 */
    UK("A1F83G8C2ARO7P", "GBP"),
    DE("A1PA6795UKMFR9", "EUR"),
    FR("A13V1IB3VIYZZH", "EUR"),
    IT("APJ6JRA9NG5V4", "EUR"),
    ES("A1RKKUPIHCS9HS", "EUR"),
    NL("A1805IZSGTT6HS", "EUR"),
    BE("AMEN7PMS3EDWL", "EUR"),
    SE("A2NODRKZP88ZB9", "SEK"),
    PL("A1C3SOZRARQ6R3", "PLN"),
    IE("A28R8C7NBKEWEA", "EUR"),
    TR("A33AVAJ2PDY3EV", "TRY"),
    AE("A2VIGQ35RCS4UG", "AED"),
    SA("A17E79C6D8DWNP", "SAR"),
    EG("ARBP9OOSHTCHU", "EGP"),
    ZA("AE08WJ6YKNBMC", "ZAR"),
    IN("A21TJRUUN4KGV", "INR"),

    /** 亚太站 */
    JP("A1VC38T7YXB528", "JPY"),
    AU("A39IBJ37TRP1C6", "AUD"),
    SG("A19VAU5U5O7RUS", "SGD");

    private final String marketplaceId;
    private final String currency;

    AmazonMarketplace(String marketplaceId, String currency) {
        this.marketplaceId = marketplaceId;
        this.currency = currency;
    }

    /** 站点 ID ↔ 枚举索引;toMap 撞键即类加载失败,封闭枚举天然防重复 */
    private static final Map<String, AmazonMarketplace> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(AmazonMarketplace::marketplaceId, Function.identity()));

    /** 按站点 ID 推导币种(ISO 4217);未配置/未收录即拒,禁静默落脏账 */
    public static String currencyOf(String marketplaceId) {
        if (StrUtil.isBlank(marketplaceId)) {
            throw new IllegalStateException("未配置 erp.adapter.amazon.marketplace-ids,无法推导 listing 币种");
        }
        AmazonMarketplace marketplace = BY_ID.get(marketplaceId);
        if (marketplace == null) {
            throw new IllegalStateException("未收录 Amazon 站点 marketplaceId=" + marketplaceId
                    + " 的币种映射,请在 AmazonMarketplace 补枚举(官方清单:SP-API Store Identifiers)");
        }
        return marketplace.currency;
    }

    public String marketplaceId() {
        return marketplaceId;
    }

    public String currency() {
        return currency;
    }
}
