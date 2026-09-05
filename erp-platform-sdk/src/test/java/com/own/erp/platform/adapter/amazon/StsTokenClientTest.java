package com.own.erp.platform.adapter.amazon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : STS AssumeRole 客户端单测(AIR):JDK 自带 HttpServer 起本地假服务,不出网、不依赖外部环境;
 *     断言查询串编码排序 / sts 签名作用域 / 临时凭证解析;签名正确性本身由 SpApiSignerTest 对 botocore 官方实现验证。
 */
class StsTokenClientTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final SpApiSigner.AwsCredentials BASE = new SpApiSigner.AwsCredentials(
            "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);
    private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/sp-api-access";
    private static final String XML_OK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <AssumeRoleResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
              <AssumeRoleResult>
                <Credentials>
                  <AccessKeyId>ASIAEXAMPLE</AccessKeyId>
                  <SecretAccessKey>session-secret</SecretAccessKey>
                  <SessionToken>AQoEXAMPLETOKEN</SessionToken>
                  <Expiration>2026-09-05T01:00:00Z</Expiration>
                </Credentials>
              </AssumeRoleResult>
            </AssumeRoleResponse>
            """;

    private HttpServer server;
    private StsTokenClient client;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = XML_OK;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = new StsTokenClient("http://127.0.0.1:" + server.getAddress().getPort(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastQuery.set(exchange.getRequestURI().getRawQuery());
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void assumeRoleSignsStsRequestAndParsesSession() {
        StsTokenClient.StsSession session = client.assumeRole(BASE, ROLE_ARN, "erp-pull", "us-east-1");

        // 临时凭证解析(AssumeRoleResult/Credentials)
        assertEquals("ASIAEXAMPLE", session.credentials().accessKey());
        assertEquals("session-secret", session.credentials().secretKey());
        assertEquals("AQoEXAMPLETOKEN", session.credentials().sessionToken());
        assertEquals(Instant.parse("2026-09-05T01:00:00Z"), session.expiration());
        // 查询串:按 key 排序(Action<DurationSeconds<RoleArn<RoleSessionName<Version),
        // RoleArn 经 AWS UriEncode(:→%3A,/→%2F)
        assertEquals("Action=AssumeRole&DurationSeconds=3600"
                        + "&RoleArn=arn%3Aaws%3Aiam%3A%3A123456789012%3Arole%2Fsp-api-access"
                        + "&RoleSessionName=erp-pull&Version=2010-05-08",
                lastQuery.get());
        // 签名作用域 service=sts
        assertTrue(lastAuthorization.get().contains("/20260905/us-east-1/sts/aws4_request"),
                lastAuthorization.get());
    }

    @Test
    void rejectsTemporaryBaseCredentials() {
        SpApiSigner.AwsCredentials temporary =
                new SpApiSigner.AwsCredentials("a-key", "b-secret", "some-token");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> client.assumeRole(temporary, ROLE_ARN, "erp-pull", "us-east-1"));

        assertTrue(exception.getMessage().contains("长期 IAM 密钥"), exception.getMessage());
    }

    @Test
    void httpErrorWrappedWithoutEchoingRoleArnOrCredential() {
        responseStatus = 403;
        responseBody = "<ErrorResponse><Error><Type>AccessDenied</Type>"
                + "<Message>User is not authorized to perform: sts:AssumeRole on " + ROLE_ARN + "</Message></Error></ErrorResponse>";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.assumeRole(BASE, ROLE_ARN, "erp-pull", "us-east-1"));

        // 异常消息只带状态码,禁回显 RoleArn/密钥/响应原文(docs/07 §7)
        assertTrue(exception.getMessage().contains("HTTP 403"), exception.getMessage());
        assertFalse(exception.getMessage().contains(ROLE_ARN), exception.getMessage());
        assertFalse(exception.getMessage().contains(BASE.secretKey()), exception.getMessage());
    }
}
