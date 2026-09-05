package com.own.erp.shop.security;

import com.own.erp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : OAuthStateService 单测(AIR:固定密钥 CryptoService + 固定时钟,可重复):
 *     签发→校验回读 shopId、TTL 过期拒、伪造/篡改拒、跨用途密文拒——回调端点无登录态,防伪造全靠这一层
 */
class OAuthStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T04:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static CryptoService cryptoService;
    private static OAuthStateService stateService;

    @BeforeAll
    static void setUp() {
        // 固定 32 字节密钥(全 0),仅测试夹具
        cryptoService = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));
        stateService = new OAuthStateService(cryptoService, Clock.fixed(NOW, ZONE));
    }

    @Test
    void issueThenVerifyRoundtripsShopId() {
        String state = stateService.issue(42L);

        assertEquals(42L, stateService.verify(state));
    }

    /** 过期(state 签发于 NOW,校验时钟已过 TTL)→ 拒并提示重新发起 */
    @Test
    void rejectsExpiredState() {
        String state = stateService.issue(42L);
        OAuthStateService later = new OAuthStateService(cryptoService,
                Clock.fixed(NOW.plusSeconds(OAuthStateService.TTL.toSeconds() + 1), ZONE));

        BusinessException e = assertThrows(BusinessException.class, () -> later.verify(state));
        assertTrue(e.getMessage().contains("已过期"));
    }

    @Test
    void rejectsTamperedForeignAndBlankStates() {
        // 篡改:合法 state 后追加内容,解密即失败
        String state = stateService.issue(42L);
        assertThrows(BusinessException.class, () -> stateService.verify(state + "x"));

        // 伪造:无密钥方造不出可解密 state,乱串拒
        assertThrows(BusinessException.class, () -> stateService.verify("garbage-state"));

        // 跨用途:同密钥其他用途密文不可当 state 用(用途段不符)
        String wrongPurpose = cryptoService.encrypt("other-purpose|42|" + NOW.toEpochMilli());
        assertThrows(BusinessException.class, () -> stateService.verify(wrongPurpose));

        // 缺失
        assertThrows(BusinessException.class, () -> stateService.verify(null));
        assertThrows(BusinessException.class, () -> stateService.verify(" "));
    }
}
