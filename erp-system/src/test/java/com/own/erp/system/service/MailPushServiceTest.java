package com.own.erp.system.service;

import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.system.event.NotifyPushedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : MailPushService 单测(#14 邮件渠道,AIR:spy 打桩 send 不出网,纯静态方法直测报文/端口解析):
 *     开关短路/零收件人短路/缺主机短路/外推触发/失败吞掉/AFTER_COMMIT 相位(docs/07 §10)
 */
class MailPushServiceTest {

    private static final String HOST = "smtp.exmail.qq.com";
    private static final NotifyPushedEvent EVENT = new NotifyPushedEvent("PULL_FAIL", "拉单告警", "连续 3 次失败");

    private SystemConfigService configService;
    private SysUserService sysUserService;
    private MailPushService service;

    @BeforeEach
    void setUp() {
        configService = mock(SystemConfigService.class);
        sysUserService = mock(SysUserService.class);
        service = spy(new MailPushService(configService, sysUserService));
        doReturn(HOST).when(configService).valueOf(ConfigConsts.KEY_MAIL_HOST);
        doReturn("true").when(configService).valueOf(ConfigConsts.KEY_MAIL_ENABLED);
        doReturn(List.of("a@example.com", "b@example.com")).when(sysUserService).listEnabledUserEmails();
    }

    private void stubSend() {
        doNothing().when(service).send(anyString(), anyList(), eq(EVENT));
    }

    @Test
    void skipsWhenDisabled() {
        doReturn("false").when(configService).valueOf(ConfigConsts.KEY_MAIL_ENABLED);

        service.onNotifyPushed(EVENT);

        verify(service, never()).send(anyString(), anyList(), eq(EVENT));
    }

    @Test
    void skipsWhenEnabledKeyMissing() {
        doReturn(null).when(configService).valueOf(ConfigConsts.KEY_MAIL_ENABLED);

        service.onNotifyPushed(EVENT);

        verify(service, never()).send(anyString(), anyList(), eq(EVENT));
    }

    @Test
    void skipsWhenNoRecipients() {
        doReturn(List.of()).when(sysUserService).listEnabledUserEmails();

        service.onNotifyPushed(EVENT);

        verify(service, never()).send(anyString(), anyList(), eq(EVENT));
    }

    @Test
    void skipsWhenHostBlank() {
        doReturn("  ").when(configService).valueOf(ConfigConsts.KEY_MAIL_HOST);

        service.onNotifyPushed(EVENT);

        verify(service, never()).send(anyString(), anyList(), eq(EVENT));
    }

    @Test
    void sendsToEnabledUserEmails() {
        stubSend();

        service.onNotifyPushed(EVENT);

        verify(service).send(eq(HOST), eq(List.of("a@example.com", "b@example.com")), eq(EVENT));
    }

    @Test
    void failureIsSwallowed() {
        stubSend();
        doThrow(new RuntimeException("SMTP connect failed"))
                .when(service).send(anyString(), anyList(), eq(EVENT));

        assertDoesNotThrow(() -> service.onNotifyPushed(EVENT));
    }

    @Test
    void configLookupFailureIsSwallowed() {
        doThrow(new RuntimeException("db down")).when(configService).valueOf(ConfigConsts.KEY_MAIL_ENABLED);

        assertDoesNotThrow(() -> service.onNotifyPushed(EVENT));
    }

    @Test
    void listensAfterCommitWithFallback() throws Exception {
        Method method = MailPushService.class.getMethod("onNotifyPushed", NotifyPushedEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertTrue(annotation.fallbackExecution());
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }

    @Test
    void buildMessageCarriesPrefixFromRecipientsAndPlaceholder() {
        SimpleMailMessage message = MailPushService.buildMessage(
                "erp@example.com", List.of("a@example.com", "b@example.com"), "拉单告警", "连续 3 次失败");

        assertEquals("[ERP] 拉单告警", message.getSubject());
        assertEquals("erp@example.com", message.getFrom());
        assertArrayEquals(new String[]{"a@example.com", "b@example.com"}, message.getTo());
        assertEquals("连续 3 次失败", message.getText());
    }

    @Test
    void buildMessageReplacesBlankContentWithPlaceholder() {
        SimpleMailMessage message = MailPushService.buildMessage(
                "erp@example.com", List.of("a@example.com"), "标题", "  ");

        assertEquals("(无正文)", message.getText());
    }

    @Test
    void resolvePortParsesAndFallsBackBySslSwitch() {
        assertEquals(465, MailPushService.resolvePort(null, true));
        assertEquals(25, MailPushService.resolvePort(null, false));
        assertEquals(587, MailPushService.resolvePort("587", false));
        assertEquals(465, MailPushService.resolvePort("abc", true));
    }
}
