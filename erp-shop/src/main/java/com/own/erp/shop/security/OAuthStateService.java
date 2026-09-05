package com.own.erp.shop.security;

import cn.hutool.core.util.StrUtil;
import com.own.erp.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : OAuth 授权 state 签发与校验(#3 回调防伪造):state = AES-GCM 加密的
 *     "用途|shopId|到期毫秒",复用凭证加密同一密钥(ERP_TOKEN_KEY)——
 *     无密钥方无法伪造/篡改(伪造即解密失败拒),10 分钟 TTL 控重放窗口(授权码本身单次有效,
 *     回调重放至多走到 LWA 换码失败,无副作用)。免 Redis 依赖:erp-shop 无状态机,签名即防伪。
 *     state 随授权 URL 出现在平台侧与回调 URL 中,内容经加密不泄露 shopId 明文以外的任何凭证
 */
@Component
public class OAuthStateService {

    /** 用途隔离:防止其他 AES 用途的密文被拿来当 state 撞库 */
    static final String PURPOSE = "oauth-state";

    /** 授权流程用户停留时长上限,超时需重新发起 */
    static final Duration TTL = Duration.ofMinutes(10);

    private final CryptoService cryptoService;
    private final Clock clock;

    public OAuthStateService(CryptoService cryptoService, Clock clock) {
        this.cryptoService = cryptoService;
        this.clock = clock;
    }

    /** 为店铺签发一次性授权 state(回调时 verify 校验) */
    public String issue(Long shopId) {
        long expireAtMs = Instant.now(clock).plus(TTL).toEpochMilli();
        return cryptoService.encrypt(PURPOSE + "|" + shopId + "|" + expireAtMs);
    }

    /**
     * 校验回调 state 并取回 shopId:缺失/伪造/用途不符/过期一律业务异常,
     * 消息统一"重新发起授权",不区分失败原因(不向无登录态的回调端点泄露校验细节)
     */
    public Long verify(String state) {
        if (StrUtil.isBlank(state)) {
            throw new BusinessException(400, "授权状态缺失,请重新发起授权");
        }
        String payload;
        try {
            payload = cryptoService.decrypt(state);
        } catch (CryptoException e) {
            throw new BusinessException(400, "授权状态无效,请重新发起授权");
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3 || !PURPOSE.equals(parts[0])) {
            throw new BusinessException(400, "授权状态无效,请重新发起授权");
        }
        long shopId;
        long expireAtMs;
        try {
            shopId = Long.parseLong(parts[1]);
            expireAtMs = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "授权状态无效,请重新发起授权");
        }
        if (expireAtMs < Instant.now(clock).toEpochMilli()) {
            throw new BusinessException(400, "授权状态已过期,请重新发起授权");
        }
        return shopId;
    }
}
