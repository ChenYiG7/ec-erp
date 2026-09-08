package com.own.erp.system.service;

import cn.hutool.core.util.StrUtil;
import com.own.erp.system.config.WebhookProperties;
import com.own.erp.system.config.WebhookProperties.Channel;
import com.own.erp.system.event.NotifyPushedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : Webhook 通知渠道(#14 后续渠道 V1,2026-09-08):站内通知 pushAllUsers 出口外推群机器人,
 *     支持钉钉(自定义机器人 markdown,加签)/飞书(自定义机器人 text,加签)/企微(群机器人 markdown,免签)。
 *     落点拍板 = pushAllUsers 出口扩展(2026-09-04"不提前抽象"——后续邮件/短信同款各挂一个监听,不做渠道接口抽象);
 *     对标 qihang 渠道形态(#17 对标库),失败重试与分级外推 V1 不做。
 *     <p>相位拍板:@TransactionalEventListener AFTER_COMMIT + fallbackExecution——事务提交后才外推
 *     (同 #11 发货回传拍板),推送失败只记日志绝不回滚站内通知、不上抛阻断调用方;
 *     无事务调用方由 fallbackExecution 兜底立即执行(pushAllUsers 自带 @Transactional,常规路径必走 AFTER_COMMIT)。
 *     时效拍板:同步跑在发布方线程(告警量级每日个位数,不建独立线程池);connect 3s/read 5s 超时防机器人慢响应拖死告警 Job。
 *     <p>签名口径(官方文档):钉钉 sign=Base64(HmacSHA256(key=secret, data=timestamp+"\n"+secret)) 后 URL-encode
 *     追加到 URL;飞书 sign=Base64(HmacSHA256(key=timestamp+"\n"+secret, data=空串))(空负载是官方特例)放报文体;
 *     企微免签。
 */
@Slf4j
@Service
public class WebhookPushService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 企微 markdown 内容上限 4096 字节,UTF-8 中文 3 字节——截 1000 字符(含省略号)必在限内(站内写侧已截 1000,双保险) */
    private static final int WECOM_CONTENT_MAX = 1000;

    private final WebhookProperties properties;
    private final RestClient restClient;

    public WebhookPushService(WebhookProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** pushAllUsers 出口的唯一消费位:AFTER_COMMIT 相位(提交后才外推),异常吞掉只记日志不阻断 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotifyPushed(NotifyPushedEvent event) {
        if (!properties.isEnabled() || StrUtil.isBlank(properties.getUrl())) {
            return;
        }
        try {
            String body = switch (properties.getType()) {
                case DINGTALK -> dingTalkBody(event.title(), event.content());
                case FEISHU -> feishuBody(event.title(), event.content(), properties.getSecret(),
                        System.currentTimeMillis() / 1000);
                case WECOM -> wecomBody(event.title(), event.content());
            };
            // 钉钉签名在 URL(时间戳毫秒);飞书签名在报文体(秒);企微免签 URL 原样
            String url = properties.getType() == Channel.DINGTALK
                    ? dingTalkUrl(properties.getUrl(), properties.getSecret(), System.currentTimeMillis())
                    : properties.getUrl();
            checkResponse(post(url, body));
        } catch (Exception e) {
            log.warn("Webhook 通知外推失败(type={},失败不阻断站内通知): {}", properties.getType(), e.getMessage());
        }
    }

    /** POST JSON(包级私有供测试打桩;RestClient 带超时) */
    String post(String url, String body) {
        return restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /** 响应码尽力校验:钉钉/企微 errcode、飞书 code,非零记 warn;非法 JSON(网关页)不判失败(HTTP 层异常已兜) */
    private void checkResponse(String body) {
        if (StrUtil.isBlank(body)) {
            return;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            int code = node.hasNonNull("errcode") ? node.get("errcode").asInt()
                    : node.hasNonNull("code") ? node.get("code").asInt() : 0;
            if (code != 0) {
                log.warn("Webhook 渠道返回非零码: {}", body);
            }
        } catch (Exception ignore) {
            // 响应体非 JSON 不阻断(HTTP 状态异常已由 RestClientResponseException 走 catch)
        }
    }

    /** 钉钉报文:markdown(title 独立字段供会话列表 + text 正文) */
    static String dingTalkBody(String title, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", Map.of("title", orEmpty(title), "text", "### " + orEmpty(title) + "\n\n" + orEmpty(content)));
        body.put("at", Map.of("isAtAll", false));
        return json(body);
    }

    /** 钉钉加签 URL:追加 timestamp+sign(base64 URL-encode,="? 均转义) */
    static String dingTalkUrl(String url, String secret, long timestampMillis) {
        if (StrUtil.isBlank(secret)) {
            return url;
        }
        String sign = URLEncoder.encode(dingTalkSign(secret, timestampMillis), StandardCharsets.UTF_8);
        return url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestampMillis + "&sign=" + sign;
    }

    /** 钉钉签名:Base64(HmacSHA256(key=secret, data=timestamp+"\n"+secret)) */
    static String dingTalkSign(String secret, long timestampMillis) {
        String stringToSign = timestampMillis + "\n" + secret;
        return hmacSha256Base64(secret.getBytes(StandardCharsets.UTF_8), stringToSign.getBytes(StandardCharsets.UTF_8));
    }

    /** 飞书报文:text 类型,timestamp/sign 在体(URL 原样);签名用秒级时间戳 */
    static String feishuBody(String title, String content, String secret, long epochSecond) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", String.valueOf(epochSecond));
        body.put("sign", feishuSign(secret, epochSecond));
        body.put("msg_type", "text");
        body.put("content", Map.of("text", orEmpty(title) + "\n" + orEmpty(content)));
        return json(body);
    }

    /** 飞书签名:Base64(HmacSHA256(key=timestamp+"\n"+secret, data=空串))——空负载为官方特例 */
    static String feishuSign(String secret, long epochSecond) {
        String key = epochSecond + "\n" + secret;
        return hmacSha256Base64(key.getBytes(StandardCharsets.UTF_8), new byte[0]);
    }

    /** 企微报文:markdown 加粗标题 + 正文;内容截 1000 字符防超 4096 字节上限 */
    static String wecomBody(String title, String content) {
        String text = StrUtil.maxLength("**" + orEmpty(title) + "**\n" + orEmpty(content), WECOM_CONTENT_MAX);
        return json(Map.of("msgtype", "markdown", "markdown", Map.of("content", text)));
    }

    /** HmacSHA256→Base64 公共底座(JDK 原生,不引 hutool-crypto 新依赖);算法缺失属环境级错误直接抛 */
    static String hmacSha256Base64(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 签名失败", e);
        }
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String json(Map<String, Object> map) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook 报文序列化失败", e);
        }
    }
}
