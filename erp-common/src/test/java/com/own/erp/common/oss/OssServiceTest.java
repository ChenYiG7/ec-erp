package com.own.erp.common.oss;

import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.SystemConfigApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : OssService 单测(#25,AIR:sys_config 走 stub provider、S3 走 mock 客户端,全程不出网):
 *     key 拼装前缀与文件名消毒/未启用抛"未启用"业务异常/sys_config 覆盖优先 yml 兜底/
 *     OSS 组变更事件失效缓存(非 OSS 组不动)/上传下载存在性删除与预签名走 mock S3 断言参数
 */
class OssServiceTest {

    private SystemConfigApi configApi;
    private MockEnvironment environment;
    private OssService service;

    @BeforeEach
    void setUp() {
        configApi = mock(SystemConfigApi.class);
        when(configApi.valueOf(any(String.class))).thenReturn(null);
        environment = new MockEnvironment();
        @SuppressWarnings("unchecked")
        ObjectProvider<SystemConfigApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(configApi);
        service = new OssService(provider);
        service.setEnvironment(environment);
    }

    @Test
    void keysCarryPrefixAndSanitizeFileName() {
        String month = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        assertEquals("report/" + month + "/sales_20260901.xlsx", service.reportKey("sales_20260901.xlsx"));
        assertEquals("kb/101/退货规则.md", service.kbKey(101L, "退货规则.md"));
        // 文件名消毒:路径分隔符/保留符号替换为下划线,防 key 注入与越前缀
        String injected = service.kbKey(1L, "../etc/passwd");
        assertFalse(injected.contains("../"));
        assertTrue(injected.startsWith("kb/1/"));
        String misc = service.miscKey("shot.png");
        assertTrue(misc.startsWith("misc/" + month + "/"));
        assertTrue(misc.endsWith("-shot.png"));
    }

    @Test
    void rejectsWhenNotConfigured() {
        // sys_config 与 yml 均无值:enabled 默认 false,调用即业务异常(不静默)
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload("report/x/a.txt", "内容".getBytes(), "text/plain"));
        assertTrue(e.getMessage().contains("对象存储未启用"));
        assertThrows(BusinessException.class, () -> service.download("report/x/a.txt"));
        assertThrows(BusinessException.class, () -> service.presignGetUrl("report/x/a.txt"));
    }

    @Test
    void sysConfigOverridesYmlFallback() {
        // sys_config 覆盖值优先;bucket 无覆盖值回落 yml;enabled 解析自 sys_config
        when(configApi.valueOf(ConfigConsts.KEY_OSS_ENABLED)).thenReturn("true");
        when(configApi.valueOf(ConfigConsts.KEY_OSS_ENDPOINT)).thenReturn("http://cfg:9000");
        when(configApi.valueOf(ConfigConsts.KEY_OSS_BUCKET)).thenReturn(" ");
        environment.setProperty(ConfigConsts.KEY_OSS_BUCKET, "env-bucket");
        environment.setProperty(ConfigConsts.KEY_OSS_ACCESS_KEY, "ak");
        environment.setProperty(ConfigConsts.KEY_OSS_SECRET_KEY, "sk");
        // 反射触发真实懒构建(只构建不连接),取配置快照断言读取顺序
        OssService.S3Runtime rt = ReflectionTestUtils.invokeMethod(service, "build");
        OssService.OssSettings settings = rt.settings();
        assertTrue(settings.enabled());
        assertEquals("http://cfg:9000", settings.endpoint());
        assertEquals("env-bucket", settings.bucket());
    }

    @Test
    void ossConfigChangeInvalidatesRuntime() {
        ReflectionTestUtils.setField(service, "runtime", runtimeOf(true));
        // OSS 组变更 → 缓存失效(下次调用重建);非 OSS 组 → 不动
        service.onConfigChanged(new SystemConfigChangedEvent(ConfigConsts.GROUP_OSS, Set.of()));
        assertNull(ReflectionTestUtils.getField(service, "runtime"));
        ReflectionTestUtils.setField(service, "runtime", runtimeOf(true));
        service.onConfigChanged(new SystemConfigChangedEvent("NOTIFY", Set.of("erp.mail.host")));
        assertTrue(ReflectionTestUtils.getField(service, "runtime") instanceof OssService.S3Runtime);
        // 组名不匹配但键属 erp.oss.* → 同样失效
        service.onConfigChanged(new SystemConfigChangedEvent("", Set.of(ConfigConsts.KEY_OSS_BUCKET)));
        assertNull(ReflectionTestUtils.getField(service, "runtime"));
    }

    @Test
    void uploadDownloadExistsDeleteRoundTripOnMockClient() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        ReflectionTestUtils.setField(service, "runtime",
                new OssService.S3Runtime(new OssService.OssSettings(true, "http://e:9000", "bk", "ak", "sk"),
                        client, presigner));

        assertEquals("report/202609/a.txt", service.upload("report/202609/a.txt", "内容".getBytes(), "text/plain"));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "内容".getBytes()));
        assertEquals("内容", new String(service.download("report/202609/a.txt")));

        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        assertFalse(service.exists("report/202609/a.txt"));

        service.delete("report/202609/a.txt");
        Mockito.verify(client).deleteObject(any(java.util.function.Consumer.class));

        PresignedGetObjectRequest presigned = PresignedGetObjectRequest.builder()
                .httpRequest(SdkHttpRequest.builder()
                        .method(SdkHttpMethod.GET)
                        .protocol("http")
                        .host("e")
                        .port(9000)
                        .encodedPath("/bk/report/202609/a.txt")
                        .rawQueryParameters(Map.of("X-Amz-Signature", List.of("x")))
                        .build())
                .expiration(java.time.Instant.now().plusSeconds(60))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("e:9000")))
                .build();
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        assertTrue(service.presignGetUrl("report/202609/a.txt").startsWith("http://e:9000/"));
    }

    /** 走一次真实懒构建(enabled+全键已配),读回配置快照 */
    private OssService.OssSettings currentSettings() {
        Object rt = ReflectionTestUtils.getField(service, "runtime");
        return ((OssService.S3Runtime) rt).settings();
    }

    private OssService.S3Runtime runtimeOf(boolean enabled) {
        return new OssService.S3Runtime(
                new OssService.OssSettings(enabled, "http://e:9000", "bk", "ak", "sk"),
                mock(S3Client.class), mock(S3Presigner.class));
    }
}
