package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : AWS STS AssumeRole 客户端(#3):长期 IAM 密钥 → SP-API 临时会话凭证(最小权限、可定期轮换)。
 *         - 请求经 {@link SpApiSigner} 签名(service=sts);全局端点 sts.amazonaws.com 用 us-east-1,
 *           区域端点(sts.&lt;region&gt;.amazonaws.com)传对应 region,由调用方配置决定
 *         - base-url 可注入:单测 JDK HttpServer 假服务不出网(AIR,docs/07 §10),LwaTokenClient 同款手法
 *         - 异常消息只带 HTTP 状态码,禁回显凭证/RoleArn/响应原文(docs/07 §7);XML 解析关 DTD/外部实体(XXE 防护)
 */
public class StsTokenClient {

    /** STS 临时会话有效期(秒):1 小时,足够单轮拉单窗口;刷新节奏随 SP-API 实调接线(#3) */
    private static final String SESSION_DURATION_SECONDS = "3600";

    private final URI baseUri;
    private final RestClient restClient;
    private final Clock clock;
    private final SpApiSigner signer = new SpApiSigner();

    public StsTokenClient(String baseUrl, Clock clock) {
        this.baseUri = URI.create(baseUrl);
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.clock = clock;
    }

    /** STS 临时会话:凭证 + 过期时间(刷新节奏由调用方定,随 SP-API 实调接线) */
    public record StsSession(SpApiSigner.AwsCredentials credentials, Instant expiration) {
    }

    public StsSession assumeRole(SpApiSigner.AwsCredentials baseCredentials,
                                 String roleArn, String roleSessionName, String region) {
        if (StrUtil.isNotBlank(baseCredentials.sessionToken())) {
            throw new IllegalArgumentException("STS AssumeRole 须用长期 IAM 密钥发起,不接受临时凭证");
        }
        // TreeMap:按键排序,与 SpApiSigner 规范查询串一致(TreeMap 已保证,这里只是复用结构)
        Map<String, String> query = new TreeMap<>();
        query.put("Action", "AssumeRole");
        query.put("Version", "2010-05-08");
        query.put("RoleArn", roleArn);
        query.put("RoleSessionName", roleSessionName);
        query.put("DurationSeconds", SESSION_DURATION_SECONDS);
        SpApiSigner.SignedHeaders signed = signer.sign(
                new SpApiSigner.SpApiRequest("GET", "/", query, Map.of("Host", baseUri.getHost()), null),
                baseCredentials, region, "sts", Instant.now(clock));
        // 查询串直接用签名器回传的 canonicalQueryString(与签名严格一致,禁二次拼参)
        String url = baseUri + "/?" + signed.canonicalQueryString();
        String response;
        try {
            response = restClient.get()
                    .uri(URI.create(url))
                    .header("Authorization", signed.authorization())
                    .header("X-Amz-Date", signed.xAmzDate())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 响应原文可能回显 RoleArn/策略详情,只透出状态码定位问题(docs/07 §7)
            throw new IllegalStateException("STS AssumeRole 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        return parseSession(response);
    }

    private StsSession parseSession(String xml) {
        if (StrUtil.isBlank(xml)) {
            throw new IllegalStateException("STS AssumeRole 响应为空");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String accessKey = firstText(document, "AccessKeyId");
            String secretKey = firstText(document, "SecretAccessKey");
            String sessionToken = firstText(document, "SessionToken");
            String expiration = firstText(document, "Expiration");
            if (StrUtil.hasBlank(accessKey, secretKey, sessionToken, expiration)) {
                throw new IllegalStateException("STS AssumeRole 响应缺失凭证字段");
            }
            return new StsSession(
                    new SpApiSigner.AwsCredentials(accessKey, secretKey, sessionToken),
                    Instant.parse(expiration.trim()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("STS AssumeRole 响应解析失败", e);
        }
    }

    private String firstText(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
}
