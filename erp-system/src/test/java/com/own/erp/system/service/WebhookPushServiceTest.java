package com.own.erp.system.service;

import com.own.erp.system.config.WebhookProperties;
import com.own.erp.system.config.WebhookProperties.Channel;
import com.own.erp.system.event.NotifyPushedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : WebhookPushService 单测(AIR:spy 打桩 post 不出网,纯静态方法直测签名与报文):
 *     开关短路/三渠道报文形态/钉钉加签 URL/飞书体签名/企微截断/失败吞掉/AFTER_COMMIT 相位(docs/07 §10)
 */
class WebhookPushServiceTest {

    private static final String URL = "https://oapi.dingtalk.com/robot/send?access_token=t1";
    private static final String SECRET = "SECtest123";

    private WebhookProperties props;
    private WebhookPushService service;

    @BeforeEach
    void setUp() {
        props = new WebhookProperties();
        props.setEnabled(true);
        props.setUrl(URL);
        props.setSecret(SECRET);
        service = spy(new WebhookPushService(props));
        doReturn("{\"errcode\":0}").when(service).post(anyString(), anyString());
    }

    @Test
    void skipsWhenDisabled() {
        props.setEnabled(false);

        service.onNotifyPushed(new NotifyPushedEvent("PULL_FAIL", "标题", "内容"));

        verify(service, never()).post(anyString(), anyString());
    }

    @Test
    void skipsWhenUrlBlank() {
        props.setUrl("");

        service.onNotifyPushed(new NotifyPushedEvent("PULL_FAIL", "标题", "内容"));

        verify(service, never()).post(anyString(), anyString());
    }

    @Test
    void dingTalkAppendsTimestampAndSignToUrl() {
        service.onNotifyPushed(new NotifyPushedEvent("PULL_FAIL", "拉单告警", "连续 3 次失败"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(service).post(urlCap.capture(), bodyCap.capture());
        String url = urlCap.getValue();
        assertTrue(url.startsWith(URL + "&timestamp="));
        assertTrue(url.contains("&sign="));
        // base64 已 URL-encode:不含裸空格(+/= 均转义),URL 整体可直接请求
        assertFalse(url.contains(" "));
        String body = bodyCap.getValue();
        assertTrue(body.contains("\"msgtype\":\"markdown\""));
        assertTrue(body.contains("拉单告警"));
        assertTrue(body.contains("连续 3 次失败"));
    }

    @Test
    void dingTalkBlankSecretKeepsUrlUntouched() {
        props.setSecret("");

        service.onNotifyPushed(new NotifyPushedEvent("T", "标题", "内容"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(service).post(urlCap.capture(), anyString());
        assertEquals(URL, urlCap.getValue());
    }

    @Test
    void dingTalkSignMatchesIndependentMac() throws Exception {
        long ts = 1757337600000L;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder()
                .encodeToString(mac.doFinal((ts + "\n" + SECRET).getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, WebhookPushService.dingTalkSign(SECRET, ts));
    }

    @Test
    void feishuSendsSignedTextBodyWithEpochTimestamp() {
        props.setType(Channel.FEISHU);

        service.onNotifyPushed(new NotifyPushedEvent("REFUND_DIFF", "退款差异", "2 笔"));

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(service).post(urlCap.capture(), bodyCap.capture());
        // 飞书签名在报文体,URL 原样
        assertEquals(URL, urlCap.getValue());
        String body = bodyCap.getValue();
        assertTrue(body.contains("\"msg_type\":\"text\""));
        Matcher m = Pattern.compile("\"timestamp\":\"(\\d+)\"").matcher(body);
        assertTrue(m.find());
        // 体签名与体时间戳自洽:sign(secret, 体内 ts) 必须出现在报文里
        assertTrue(body.contains(WebhookPushService.feishuSign(SECRET, Long.parseLong(m.group(1)))));
        assertTrue(body.contains("退款差异"));
        assertTrue(body.contains("2 笔"));
    }

    @Test
    void wecomBodyTruncatesUnderSizeLimit() {
        String body = WebhookPushService.wecomBody("标题", "长".repeat(2000));

        // 截 1000 字符(含省略号)后,4000+ 字符原文必进不了报文,总长稳定在企微 4096 字节限内
        assertTrue(body.contains("..."));
        assertTrue(body.length() < 1200);
        assertTrue(body.contains("\"msgtype\":\"markdown\""));
    }

    @Test
    void pushFailureDoesNotPropagate() {
        doThrow(new RuntimeException("connection refused")).when(service).post(anyString(), anyString());

        assertDoesNotThrow(() -> service.onNotifyPushed(new NotifyPushedEvent("T", "标题", "内容")));
    }

    @Test
    void nonZeroErrcodeOnlyWarnsNotThrows() {
        doReturn("{\"errcode\":310000,\"errmsg\":\"sign not match\"}").when(service).post(anyString(), anyString());

        assertDoesNotThrow(() -> service.onNotifyPushed(new NotifyPushedEvent("T", "标题", "内容")));
    }

    @Test
    void listenerRunsAfterCommitWithFallback() throws Exception {
        Method m = WebhookPushService.class.getMethod("onNotifyPushed", NotifyPushedEvent.class);
        TransactionalEventListener anno = m.getAnnotation(TransactionalEventListener.class);

        assertNotNull(anno);
        assertEquals(TransactionPhase.AFTER_COMMIT, anno.phase());
        assertTrue(anno.fallbackExecution());
    }
}
