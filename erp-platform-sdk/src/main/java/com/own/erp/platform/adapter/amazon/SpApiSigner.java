package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : AWS SigV4 请求签名器(#3,SP-API / STS 通用,纯 JDK 无状态):
 *         - 规范请求五段式(method/uri/query/canonicalHeaders/signedHeaders/payloadHash)+ 四轮 HMAC 派生签名密钥,
 *           算法依据 AWS 官方文档「Create a signed AWS API request」;
 *           期望签名值由 Amazon 官方实现 botocore SigV4Auth 冻结时钟生成做交叉验证(官方向量套件离线拉不到),禁 mock 自嗨(docs/07 §8)
 *         - 时间戳由调用方传入(固定时钟单测 AIR 不出网,docs/07 §10),本类禁取系统当前时间
 *         - STS 临时凭证:sessionToken 非空时 x-amz-security-token 参与规范头与签名,调用方需把该头随请求一并发出
 *         - 真实凭证只从环境变量/local.properties 进(安全红线 docs/07 §7),本类不含任何凭证获取逻辑
 */
public final class SpApiSigner {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final String HOST_HEADER = "host";
    private static final String DATE_HEADER = "x-amz-date";
    private static final String TOKEN_HEADER = "x-amz-security-token";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** AWS 凭证:长期 IAM 密钥(sessionToken 为 null)或 STS 临时凭证(带 sessionToken);record 自动 toString 会泄密,手写脱敏(docs/07 §1) */
    public record AwsCredentials(String accessKey, String secretKey, String sessionToken) {

        @Override
        public String toString() {
            return "AwsCredentials[accessKey=" + accessKey + ", secretKey=***, sessionToken=***]";
        }
    }

    /**
     * 待签名请求:host 必须在 headers 中(值不带 scheme,仅用于签名,发送方按连接自动补);
     * path 以 / 开头;queryParams 为解码后的键值(不支持同名重复参数,SP-API 无此需求);payload 为请求体原文,GET 传 null
     */
    public record SpApiRequest(String method, String path, Map<String, String> queryParams,
                               Map<String, String> headers, String payload) {
    }

    /** 签名产物:authorization/xAmzDate 作为请求头发出;canonicalQueryString 与签名严格一致,调用方原样拼到 URL(禁二次拼参) */
    public record SignedHeaders(String authorization, String xAmzDate, String canonicalQueryString) {
    }

    public SignedHeaders sign(SpApiRequest request, AwsCredentials credentials,
                              String region, String service, Instant instant) {
        if (credentials == null || StrUtil.isBlank(credentials.accessKey()) || StrUtil.isBlank(credentials.secretKey())) {
            throw new IllegalArgumentException("AWS 凭证缺失:accessKey/secretKey 必填");
        }
        String xAmzDate = AMZ_DATE.format(instant);
        String day = AMZ_DAY.format(instant);
        String scope = day + "/" + region + "/" + service + "/" + TERMINATOR;
        TreeMap<String, String> canonicalHeaders = canonicalHeaderMap(request.headers(), credentials);
        // X-Amz-Date 由签名器生成并参与签名(botocore 同款):调用方若自带则必须与签名时间一致
        String existingDate = canonicalHeaders.get(DATE_HEADER);
        if (existingDate != null && !existingDate.equals(xAmzDate)) {
            throw new IllegalArgumentException("请求头中的 X-Amz-Date 与签名时间不一致,该头由签名器生成,调用方勿传");
        }
        canonicalHeaders.put(DATE_HEADER, xAmzDate);
        String canonicalRequest = canonicalRequest(request, canonicalHeaders);
        String stringToSign = stringToSign(xAmzDate, scope, canonicalRequest);
        String signature = HexFormat.of().formatHex(hmac(deriveKey(credentials.secretKey(), day, region, service), stringToSign));
        String authorization = ALGORITHM + " Credential=" + credentials.accessKey() + "/" + scope
                + ", SignedHeaders=" + String.join(";", canonicalHeaders.keySet())
                + ", Signature=" + signature;
        return new SignedHeaders(authorization, xAmzDate, canonicalQuery(request.queryParams()));
    }

    /** 规范请求(包私有,单测断言中间产物):各段以 \n 分隔,headers 段自带尾 \n,故 headers 与 signedHeaders 之间为空行 */
    String canonicalRequest(SpApiRequest request, TreeMap<String, String> canonicalHeaders) {
        if (StrUtil.isBlank(request.path()) || !request.path().startsWith("/")) {
            throw new IllegalArgumentException("请求路径必须以 / 开头");
        }
        StringBuilder heads = new StringBuilder();
        for (Map.Entry<String, String> e : canonicalHeaders.entrySet()) {
            heads.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        return request.method().toUpperCase(Locale.ROOT) + "\n"
                + canonicalUri(request.path()) + "\n"
                + canonicalQuery(request.queryParams()) + "\n"
                + heads + "\n"
                + String.join(";", canonicalHeaders.keySet()) + "\n"
                + sha256Hex(request.payload() == null ? "" : request.payload());
    }

    String stringToSign(String xAmzDate, String scope, String canonicalRequest) {
        return ALGORITHM + "\n" + xAmzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);
    }

    /** 规范头:名字小写、按名排序;值 trim + 连续空格合一;必须含 host;临时凭证时并入 x-amz-security-token(与请求头不一致即拒) */
    private TreeMap<String, String> canonicalHeaderMap(Map<String, String> headers, AwsCredentials credentials) {
        TreeMap<String, String> canonical = new TreeMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String name = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("请求头名为空,无法签名");
                }
                canonical.put(name, e.getValue() == null ? "" : e.getValue().trim().replaceAll(" +", " "));
            }
        }
        if (!canonical.containsKey(HOST_HEADER)) {
            throw new IllegalArgumentException("签名必须包含 Host 头");
        }
        if (StrUtil.isNotBlank(credentials.sessionToken())) {
            String token = credentials.sessionToken().trim();
            String existing = canonical.get(TOKEN_HEADER);
            if (existing != null && !existing.equals(token)) {
                throw new IllegalArgumentException("x-amz-security-token 在请求头与凭证中不一致,拒绝签名");
            }
            canonical.put(TOKEN_HEADER, token);
        }
        return canonical;
    }

    /** 路径按段 UriEncode(/ 为分隔符不编码) */
    private String canonicalUri(String path) {
        String[] segments = path.split("/", -1);
        StringBuilder uri = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                uri.append('/');
            }
            uri.append(uriEncode(segments[i], false));
        }
        return uri.toString();
    }

    /** 查询串:键值各自 UriEncode 后按键排序(排序发生在编码之后),空表返回空串(占位换行仍在规范请求中) */
    private String canonicalQuery(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        queryParams.forEach((k, v) -> sorted.put(uriEncode(k, true), uriEncode(v == null ? "" : v, true)));
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(e.getKey()).append('=').append(e.getValue());
        }
        return query.toString();
    }

    /** 签名密钥四轮派生:AWS4+secret → day → region → service → aws4_request */
    private byte[] deriveKey(String secretKey, String day, String region, String service) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, TERMINATOR);
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SigV4 HMAC 计算失败", e);
        }
    }

    private String sha256Hex(String data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** AWS UriEncode:仅不保留字符(A-Za-z0-9-._~)直通,空格 %20(禁 +),十六进制大写;encodeSlash=false 时 / 直通 */
    private static String uriEncode(String value, boolean encodeSlash) {
        StringBuilder encoded = new StringBuilder(value.length() * 2);
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~'
                    || (c == '/' && !encodeSlash)) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format("%02X", c));
            }
        }
        return encoded.toString();
    }
}
