package com.own.erp.shop.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.ShopReferenceApi;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.shop.entity.Shop;
import com.own.erp.shop.mapper.ShopMapper;
import com.own.erp.shop.request.query.ShopQuery;
import com.own.erp.shop.request.command.ShopSaveRequest;
import com.own.erp.shop.response.ShopResponse;
import com.own.erp.shop.security.CryptoException;
import com.own.erp.shop.security.CryptoService;
import com.own.erp.shop.security.OAuthStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺服务:平台校验、凭证加密写入、对外读出口脱敏、ShopSession 解密装配(TODO#2)、
 *     OAuth 回调与 Token 刷新(授权中心,#3,2026-09-04 落地)。
 *     API 模型收口(docs/07 §1):入参 ShopSaveRequest/ShopQuery,出参 ShopResponse——appSecret/refreshToken 无出参字段(编译期封死);
 *     凭证表例外(docs/07 §2.1):写入唯一入口 = createShop/updateShop/applyOAuthToken(授权回调与刷新同走加密链路),
 *     读出唯一出口 = pageShops/getShopById(出参必脱敏),Controller 不直连 ShopMapper;
 *     刷新节奏(docs/04 定案):getShopSession 读取时检查,过期前 10 分钟刷新,失败走 pull_log 连续失败告警;
 *     跨实例防双刷新 = DB CAS(WHERE token_expire_at &lt;=&gt; 旧值,条件更新即守卫;LWA refresh_token 不轮换,双跑无害)
 */
@Slf4j
@Service
public class ShopService {

    /** 返回脱敏后缀(ShopResponse.accessToken 同源),update 以此识别"前端原样回传的掩码值" */
    public static final String MASK_SUFFIX = "***";

    /** Token 提前刷新窗口(docs/04 2026-09-04 定案:过期前 10 分钟;LWA access_token 1 小时,过早=每次拉单都刷新) */
    private static final Duration REFRESH_AHEAD = Duration.ofMinutes(10);

    /** 店铺启用状态(shop.status:1启用0禁用) */
    private static final int STATUS_ENABLED = 1;

    /** 与 application.yml 的 serverTimezone=Asia/Shanghai 对齐;平台时区翻译归 adapter(#3 统一) */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ShopMapper shopMapper;
    private final CryptoService cryptoService;
    private final OAuthStateService oauthStateService;
    private final AdapterRegistry adapterRegistry;
    private final Clock clock;
    private final ShopProductService shopProductService;
    private final PullLogService pullLogService;
    private final ShopReferenceApi shopReferenceApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public ShopService(ShopMapper shopMapper,
                       CryptoService cryptoService,
                       OAuthStateService oauthStateService,
                       AdapterRegistry adapterRegistry,
                       Clock clock,
                       ShopProductService shopProductService,
                       PullLogService pullLogService,
                       @Lazy ShopReferenceApi shopReferenceApi) {
        this.shopMapper = shopMapper;
        this.cryptoService = cryptoService;
        this.oauthStateService = oauthStateService;
        this.adapterRegistry = adapterRegistry;
        this.clock = clock;
        this.shopProductService = shopProductService;
        this.pullLogService = pullLogService;
        this.shopReferenceApi = shopReferenceApi;
    }

    /** 新增店铺:平台编码校验 + 凭证 AES-GCM 加密落库;merchantId 服务端固定(不在入参模型内,防 mass assignment) */
    public Long createShop(ShopSaveRequest request) {
        Shop shop = request.toEntity();
        checkPlatform(shop.getPlatform());
        shop.setMerchantId(1L); // 一期单商户
        encryptCredentials(shop);
        shopMapper.insert(shop);
        return shop.getId();
    }

    /**
     * 更新店铺:平台编码校验;凭证三字段 null/空串=不改(MP updateById 忽略 null),掩码回传=忽略并记日志,真实新值才加密覆盖。
     * 注意:空串视同不改,本接口无法清空凭证(遗留项,见 TODO.md #2)
     */
    public void updateShop(Long id, ShopSaveRequest request) {
        Shop shop = request.toEntity();
        shop.setId(id);
        checkPlatform(shop.getPlatform());
        applyCredentialChanges(shop, "appSecret", shop::getAppSecret, shop::setAppSecret);
        applyCredentialChanges(shop, "accessToken", shop::getAccessToken, shop::setAccessToken);
        applyCredentialChanges(shop, "refreshToken", shop::getRefreshToken, shop::setRefreshToken);
        shopMapper.updateById(shop);
    }

    /**
     * 解密装配 ShopSession(erp-shop 授权中心,adapter 只消费不碰 shop 表):
     * 三凭证全空报"尚未配置授权凭证";解密失败(密钥不匹配/数据损坏)报"请重新授权",消息不含任何凭证内容;
     * 装配时检查 tokenExpireAt,过期前 {@link #REFRESH_AHEAD} 经 adapter 刷新后返回新会话(docs/04 定案,
     * 见 {@link #refreshIfNeeded})——刷新失败按业务异常上抛,由拉单调度记 pull_log 走连续失败告警
     */
    public ShopSession getShopSession(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在: " + shopId);
        }
        PlatformType platform = checkPlatform(shop.getPlatform());
        boolean noCredentials = StrUtil.isBlank(shop.getAppSecret()) && StrUtil.isBlank(shop.getAccessToken())
                && StrUtil.isBlank(shop.getRefreshToken());
        if (noCredentials) {
            throw new BusinessException(400, "店铺尚未配置授权凭证,shopId=" + shopId);
        }
        AuthToken token = new AuthToken();
        token.setAccessToken(decryptOrThrow(shopId, "accessToken", shop.getAccessToken()));
        token.setRefreshToken(decryptOrThrow(shopId, "refreshToken", shop.getRefreshToken()));
        token.setExpireAt(toInstant(shop.getTokenExpireAt()));
        return refreshIfNeeded(shop, platform, token);
    }

    /**
     * Token 刷新(#3,docs/04「读取时检查,过期前 10 分钟」):
     * 无 refreshToken(手动导入凭证平台)不刷新;未到窗口不刷新;adapter 未接入静默跳过
     * (对齐 #4"adapter 未接入不产生失败噪音");过期时间缺失视为未知,借刷新补记 tokenExpireAt 自愈。
     * 防跨实例双刷新:casRefreshToken 以读到的旧 tokenExpireAt 作守卫(WHERE &lt;=&gt; NULL 安全等值),
     * CAS 脱靶 = 他实例刚刷过(LWA refresh_token 不轮换,双方结果均有效),弃本次结果回库重读。
     * 单实例一期天然零竞争;专用分布式锁留给二期轮换型 refresh_token 平台(LockService javadoc 同口径)
     */
    private ShopSession refreshIfNeeded(Shop shop, PlatformType platform, AuthToken token) {
        ShopSession session = new ShopSession(shop.getId(), platform, token);
        if (StrUtil.isBlank(token.getRefreshToken())) {
            return session;
        }
        Instant now = Instant.now(clock);
        if (token.getExpireAt() != null && token.getExpireAt().isAfter(now.plus(REFRESH_AHEAD))) {
            return session;
        }
        Optional<PlatformClient> adapter = adapterRegistry.get(platform);
        if (adapter.isEmpty()) {
            log.debug("平台 adapter 未接入,跳过 Token 刷新 shopId={} platform={}", shop.getId(), platform);
            return session;
        }
        String appSecret = decryptOrThrow(shop.getId(), "appSecret", shop.getAppSecret());
        if (StrUtil.isBlank(shop.getAppKey()) || StrUtil.isBlank(appSecret)) {
            throw new BusinessException(400, "店铺缺少 appKey/appSecret,无法刷新 Token,shopId=" + shop.getId());
        }
        AuthToken newToken;
        try {
            newToken = adapter.get().refreshToken(token.getRefreshToken(), shop.getAppKey(), appSecret);
        } catch (RuntimeException e) {
            log.error("Token 刷新失败 shopId={} platform={}", shop.getId(), platform, e);
            throw new BusinessException(500, "Token 刷新失败(平台侧拒绝或网络异常),请检查授权或稍后重试,shopId=" + shop.getId());
        }
        LocalDateTime newExpireAt = toLocalDateTime(newToken.getExpireAt());
        int updated = shopMapper.casRefreshToken(shop.getId(), shop.getTokenExpireAt(),
                cryptoService.encrypt(newToken.getAccessToken()),
                StrUtil.isBlank(newToken.getRefreshToken()) ? null : cryptoService.encrypt(newToken.getRefreshToken()),
                newExpireAt);
        if (updated == 0) {
            // 他实例刚刷新:回库重读装配,禁止再触发刷新(本会话仅读)
            log.info("Token 刷新 CAS 脱靶(他实例已刷新),回库重读 shopId={}", shop.getId());
            Shop latest = shopMapper.selectById(shop.getId());
            if (latest == null) {
                throw new BusinessException(404, "店铺不存在: " + shop.getId());
            }
            AuthToken latestToken = new AuthToken();
            latestToken.setAccessToken(decryptOrThrow(shop.getId(), "accessToken", latest.getAccessToken()));
            latestToken.setRefreshToken(decryptOrThrow(shop.getId(), "refreshToken", latest.getRefreshToken()));
            latestToken.setExpireAt(toInstant(latest.getTokenExpireAt()));
            return new ShopSession(latest.getId(), platform, latestToken);
        }
        token.setAccessToken(newToken.getAccessToken());
        if (StrUtil.isNotBlank(newToken.getRefreshToken())) {
            token.setRefreshToken(newToken.getRefreshToken());
        }
        token.setExpireAt(newToken.getExpireAt());
        return session;
    }

    /**
     * 分页查询(对外读出口,出参必脱敏):凭证表例外(docs/07 §2.1)——Controller 禁止直连 ShopMapper,
     * 脱敏收口本类(先 mask 实体再 Response.from 映射),新增读接口自动继承,不靠人肉记得调 mask
     */
    public Page<ShopResponse> pageShops(ShopQuery query) {
        Page<Shop> result = shopMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<Shop>()
                        .eq(StrUtil.isNotBlank(query.getPlatform()), Shop::getPlatform, query.getPlatform())
                        .eq(query.getStatus() != null, Shop::getStatus, query.getStatus())
                        .orderByDesc(Shop::getId));
        result.getRecords().forEach(ShopService::mask);
        Page<ShopResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ShopResponse::from).toList());
        return responsePage;
    }

    /** 详情查询(对外读出口,出参已脱敏):不存在返回 null(沿用原语义) */
    public ShopResponse getShopById(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            return null;
        }
        mask(shop);
        return ShopResponse.from(shop);
    }

    /**
     * 启用状态店铺ID列表(拉单调度用,#4):仅取 id 列,不触碰凭证列;
     * 店铺级会话装配由调用方逐店走 getShopSession(解密失败单店跳过,不影响其他店铺)
     */
    public List<Long> listEnabledShopIds() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                        .select(Shop::getId)
                        .eq(Shop::getStatus, STATUS_ENABLED))
                .stream().map(Shop::getId).toList();
    }

    /**
     * 删除店铺:一期硬删。删除前引用校验先拦截(#3,2026-09-04 接口模块方案收口):
     * listing 与 pull_log 属本模块域内直查;订单/售后为跨域引用,经 erp-contract 接口模块
     * (实现收口 erp-api,本域禁横向依赖,铁律 2)。任一引用存在即禁删(防孤儿数据与误删凭证),可改状态停用代替
     */
    public void deleteShop(Long id) {
        long refs = shopProductService.countByShopIds(List.of(id))
                + pullLogService.countByShopIds(List.of(id))
                + shopReferenceApi.countOrderRefs(List.of(id))
                + shopReferenceApi.countAftersaleRefs(List.of(id));
        if (refs > 0) {
            throw new BusinessException("店铺被listing/pull_log/订单/售后引用共 " + refs + " 条,禁删,可改状态停用");
        }
        shopMapper.deleteById(id);
    }

    /**
     * 生成平台授权跳转地址(OAuth,#3):adapter 签发跳转 URL,state 由本服务加密签发
     * (10 分钟 TTL,回调 verify 校验,见 OAuthStateService);免授权平台不走此流程。
     * 调用前置条件:店铺档案已建 + appKey/appSecret 已配置(adapter 未接入/配置缺失即拒)
     */
    public String buildAuthUrl(Long shopId, String redirectUri) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在: " + shopId);
        }
        PlatformClient adapter = requireAdapter(checkPlatform(shop.getPlatform()));
        return adapter.buildAuthUrl(redirectUri, oauthStateService.issue(shopId));
    }

    /**
     * OAuth 回调处理(#3):state 校验(防伪造/过期)→ adapter 授权码换 Token →
     * 复用加密链路入库(applyOAuthToken,凭证写入唯一入口之一)并记 tokenExpireAt,回填平台侧卖家标识。
     * 回调端点无登录态,安全边界 = 加密 state(无密钥方不可伪造)+ LWA 授权码单次有效
     *
     * @return 完成授权的店铺 ID(供回调页展示)
     */
    public Long handleOAuthCallback(String state, String sellerId, String authCode, String redirectUri) {
        Long shopId = oauthStateService.verify(state);
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在: " + shopId);
        }
        PlatformClient adapter = requireAdapter(checkPlatform(shop.getPlatform()));
        String appSecret = decryptOrThrow(shopId, "appSecret", shop.getAppSecret());
        if (StrUtil.isBlank(shop.getAppKey()) || StrUtil.isBlank(appSecret)) {
            throw new BusinessException(400, "店铺缺少 appKey/appSecret,请先在店铺档案配置后再授权,shopId=" + shopId);
        }
        AuthToken token = adapter.exchangeToken(authCode, redirectUri, shop.getAppKey(), appSecret);
        applyOAuthToken(shop, token, sellerId);
        return shopId;
    }

    /**
     * OAuth 凭证落库(加密链路唯一入口,同 createShop/updateShop 红线):accessToken 必写,
     * refreshToken 空不覆盖(如 LWA 刷新响应不回传 refresh_token,保留原值),tokenExpireAt 按库内时区记
     */
    private void applyOAuthToken(Shop shop, AuthToken token, String sellerId) {
        if (StrUtil.isNotBlank(sellerId)) {
            shop.setSellerId(sellerId);
        }
        shop.setAccessToken(cryptoService.encrypt(token.getAccessToken()));
        if (StrUtil.isNotBlank(token.getRefreshToken())) {
            shop.setRefreshToken(cryptoService.encrypt(token.getRefreshToken()));
        }
        shop.setTokenExpireAt(toLocalDateTime(token.getExpireAt()));
        shopMapper.updateById(shop);
    }

    /** 平台 adapter 必须已接入(未接入的店铺无法走 OAuth/刷新,友好提示而非裸 Optional 语义) */
    private PlatformClient requireAdapter(PlatformType platform) {
        return adapterRegistry.get(platform)
                .orElseThrow(() -> new BusinessException(400, "平台 adapter 未接入,无法进行授权: " + platform));
    }

    /**
     * 更新时的单字段凭证处理:null=未传不动;blank=置 null 视同不改;
     * 掩码回传(前端把 GET 的脱敏值原样提交)=置 null 忽略并记日志;其余按真实新值加密
     */
    private void applyCredentialChanges(Shop shop, String fieldName,
                                        Supplier<String> getter, java.util.function.Consumer<String> setter) {
        String value = getter.get();
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            setter.accept(null);
            return;
        }
        if (value.endsWith(MASK_SUFFIX)) {
            log.info("update shop={} 字段 {} 为掩码回传,忽略覆盖", shop.getId(), fieldName);
            setter.accept(null);
            return;
        }
        setter.accept(cryptoService.encrypt(value));
    }

    /** 新增时的凭证加密:三字段原地加密;null/blank 规范化为 null(可选字段不参与,且不留空格脏数据) */
    private void encryptCredentials(Shop shop) {
        shop.setAppSecret(encryptIfPresent(shop.getAppSecret()));
        shop.setAccessToken(encryptIfPresent(shop.getAccessToken()));
        shop.setRefreshToken(encryptIfPresent(shop.getRefreshToken()));
    }

    private String encryptIfPresent(String value) {
        return StrUtil.isBlank(value) ? null : cryptoService.encrypt(value);
    }

    /** 解密单字段;失败转业务异常,日志与消息只带 shopId/字段名,禁带凭证内容 */
    private String decryptOrThrow(Long shopId, String field, String cipherText) {
        if (StrUtil.isBlank(cipherText)) {
            return null;
        }
        try {
            return cryptoService.decrypt(cipherText);
        } catch (CryptoException e) {
            log.error("店铺凭证解密失败 shop={} field={}", shopId, field, e);
            throw new BusinessException(500, "店铺凭证解密失败(密钥不匹配或数据损坏),请重新授权,shopId=" + shopId);
        }
    }

    /** 凭证脱敏(对外返回唯一出口):accessToken 只留密文前 6 位;掩码后缀与 update 防回写规则同源(MASK_SUFFIX) */
    private static void mask(Shop shop) {
        if (StrUtil.isNotBlank(shop.getAccessToken())) {
            shop.setAccessToken(StrUtil.subPre(shop.getAccessToken(), 6) + MASK_SUFFIX);
        }
        shop.setAppSecret(null);
        shop.setRefreshToken(null);
    }

    /** 平台编码校验,返回枚举;非法即拒(创建/更新/装配 ShopSession 共用) */
    private PlatformType checkPlatform(String platform) {
        try {
            return PlatformType.valueOf(platform);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("非法平台编码: " + platform);
        }
    }

    /**
     * 库内 DATETIME(会话时区 Asia/Shanghai)→ Instant。
     * 不用 Hutool 的 TemporalAccessorUtil.toInstant:其 LocalDateTime 分支取 ZoneId.systemDefault(),
     * 服务器时区漂移会算错平台 token 到期;此处必须显式 Asia/Shanghai。跨境平台到期语义留 #3 统一
     */
    private java.time.Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZONE).toInstant();
    }

    /** Instant → 库内 DATETIME(会话时区 Asia/Shanghai),tokenExpireAt 落库专用(toInstant 的逆变换) */
    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }
}
