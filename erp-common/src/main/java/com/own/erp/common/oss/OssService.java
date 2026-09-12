package com.own.erp.common.oss;

import cn.hutool.core.util.StrUtil;
import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.SystemConfigApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 统一对象存储服务(#25 RustFS,S3 兼容协议):上传/下载/预签名/删除/存在性收口,
 *     各业务模块(erp-report 导出归档 / erp-ai RAG 原文 / misc 静态资源)只依赖本类,禁直连 SDK——
 *     AWS SDK v2 类型不外漏(返回值/参数只用 byte[]/InputStream/String,防腐同款纪律)。
 *     配置读取顺序 = sys_config(GROUP_OSS,#18 热更口径)→ yml erp.oss.*(local.properties/环境变量可覆盖)
 *     → 均未配置时 enabled=false,调用即抛 BusinessException"对象存储未启用",不静默降级;
 *     secret-key 为 ValueType.SECRET(docs/07 §7 例外扩容,2026-09-11 拍板):回显掩码,消费侧恒取真值。
 *     客户端懒构建 + SystemConfigChangedEvent 监听失效重建(#18 先例),改配置秒级生效;
 *     S3 兼容端点(RustFS/MinIO 类)固定 path-style 寻址 + region 占位 us-east-1(端点不校验 region)。
 *     key 规范(前缀在本类拼装,业务方只传语义名):report/{yyyyMM}/{文件名}、kb/{documentId}/{文件名}、
 *     misc/{yyyyMM}/{uuid}-{文件名};文件名统一去路径符/控制符,防 key 注入
 */
@Service
@Slf4j
public class OssService implements EnvironmentAware {

    /** 预签名默认有效期(计划书拍板 30 分钟) */
    public static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(30);

    private static final Pattern UNSAFE_FILENAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final int FILE_NAME_MAX_LENGTH = 128;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final ObjectProvider<SystemConfigApi> systemConfigApi;
    private Environment environment;

    /** 配置快照与客户端成对缓存;配置变更事件整体失效,下次调用重建 */
    private volatile S3Runtime runtime;

    public OssService(@Lazy ObjectProvider<SystemConfigApi> systemConfigApi) {
        this.systemConfigApi = systemConfigApi;
    }

    /** 上传字节内容,返回对象 key(与入参一致) */
    public String upload(String key, byte[] data, String contentType) {
        S3Runtime rt = requireRuntime();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(rt.settings().bucket()).key(key).contentType(contentType).build();
        rt.client().putObject(request, RequestBody.fromBytes(data));
        return key;
    }

    /** 上传流内容(size 必传,S3 PutObject 需 Content-Length),返回对象 key */
    public String upload(String key, InputStream in, long size, String contentType) {
        S3Runtime rt = requireRuntime();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(rt.settings().bucket()).key(key).contentType(contentType).build();
        rt.client().putObject(request, RequestBody.fromInputStream(in, size));
        return key;
    }

    /** 下载对象字节;不存在抛 BusinessException */
    public byte[] download(String key) {
        S3Runtime rt = requireRuntime();
        try {
            ResponseBytes<GetObjectResponse> bytes = rt.client().getObjectAsBytes(
                    GetObjectRequest.builder().bucket(rt.settings().bucket()).key(key).build());
            return bytes.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BusinessException("对象不存在:" + key);
        }
    }

    /** 预签名 GET URL(默认 30 分钟有效期) */
    public String presignGetUrl(String key) {
        return presignGetUrl(key, DEFAULT_PRESIGN_TTL);
    }

    /** 预签名 GET URL(自定义有效期) */
    public String presignGetUrl(String key, Duration ttl) {
        S3Runtime rt = requireRuntime();
        PresignedGetObjectRequest presigned = rt.presigner().presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(rt.settings().bucket()).key(key).build())
                        .build());
        return presigned.url().toString();
    }

    /** 删除对象(幂等:对象不存在亦静默通过) */
    public void delete(String key) {
        S3Runtime rt = requireRuntime();
        rt.client().deleteObject(b -> b.bucket(rt.settings().bucket()).key(key));
    }

    /** 对象是否存在 */
    public boolean exists(String key) {
        S3Runtime rt = requireRuntime();
        try {
            rt.client().headObject(HeadObjectRequest.builder()
                    .bucket(rt.settings().bucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    // ── key 拼装(前缀收口,业务方只传语义名)──

    /** 报表导出归档 key:report/{yyyyMM}/{文件名} */
    public String reportKey(String fileName) {
        return "report/" + MONTH.format(LocalDate.now()) + "/" + sanitize(fileName);
    }

    /** RAG 知识库原文 key:kb/{documentId}/{文件名} */
    public String kbKey(Long documentId, String fileName) {
        return "kb/" + documentId + "/" + sanitize(fileName);
    }

    /** 通用静态资源 key:misc/{yyyyMM}/{uuid}-{文件名} */
    public String miscKey(String fileName) {
        return "misc/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID() + "-" + sanitize(fileName);
    }

    /** 配置热更失效(#18 先例):OSS 组或 erp.oss.* 键变更时整体失效,下次调用重建客户端 */
    @org.springframework.context.event.EventListener(SystemConfigChangedEvent.class)
    public void onConfigChanged(SystemConfigChangedEvent event) {
        boolean ossGroup = ConfigConsts.GROUP_OSS.equals(event.configGroup());
        boolean ossKey = event.configKeys().stream().anyMatch(key -> key.startsWith("erp.oss."));
        if (ossGroup || ossKey) {
            closeQuietly();
            log.info("OSS 配置已变更,客户端缓存失效待重建");
        }
    }

    // ── 内部:配置解析与客户端构建 ──

    private S3Runtime requireRuntime() {
        S3Runtime rt = runtime;
        if (rt == null) {
            synchronized (this) {
                rt = runtime;
                if (rt == null) {
                    rt = build();
                    runtime = rt;
                }
            }
        }
        OssSettings settings = rt.settings();
        if (!settings.enabled() || StrUtil.hasBlank(settings.endpoint(), settings.bucket(),
                settings.accessKey(), settings.secretKey())) {
            throw new BusinessException("对象存储未启用:请在 系统设置-对象存储 配置 endpoint/bucket/密钥并开启,或经 local.properties 配置 erp.oss.*");
        }
        return rt;
    }

    private S3Runtime build() {
        OssSettings settings = new OssSettings(
                readBool(ConfigConsts.KEY_OSS_ENABLED),
                read(ConfigConsts.KEY_OSS_ENDPOINT),
                read(ConfigConsts.KEY_OSS_BUCKET),
                read(ConfigConsts.KEY_OSS_ACCESS_KEY),
                read(ConfigConsts.KEY_OSS_SECRET_KEY));
        if (!settings.enabled() || StrUtil.hasBlank(settings.endpoint(), settings.bucket(),
                settings.accessKey(), settings.secretKey())) {
            // 未启用或半配置都不建客户端(零连接零炸点),友好拦截统一在 requireRuntime
            return new S3Runtime(settings, null, null);
        }
        URI endpoint = URI.create(settings.endpoint());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey(), settings.secretKey()));
        // S3 兼容存储(RustFS):path-style 寻址;SDK 必填 region,端点不校验,占位 us-east-1
        S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .build();
        return new S3Runtime(settings, client, presigner);
    }

    /** 单键解析:sys_config 覆盖值优先(SystemConfigApi,null 安全),空回落 yml erp.oss.* */
    private String read(String configKey) {
        String value = null;
        SystemConfigApi api = systemConfigApi.getIfAvailable();
        if (api != null) {
            value = api.valueOf(configKey);
        }
        if (StrUtil.isBlank(value) && environment != null) {
            value = environment.getProperty(configKey);
        }
        return StrUtil.trimToNull(value);
    }

    private boolean readBool(String configKey) {
        return Boolean.parseBoolean(read(configKey));
    }

    /** 文件名消毒:去路径分隔符/控制字符/保留符号,限长防 key 超列宽;空名兜底 file */
    private String sanitize(String fileName) {
        String cleaned = StrUtil.trim(UNSAFE_FILENAME
                .matcher(StrUtil.blankToDefault(fileName, "file"))
                .replaceAll("_"));
        if (StrUtil.isBlank(cleaned)) {
            cleaned = "file";
        }
        return cleaned.length() > FILE_NAME_MAX_LENGTH
                ? cleaned.substring(0, FILE_NAME_MAX_LENGTH) : cleaned;
    }

    private void closeQuietly() {
        S3Runtime old = runtime;
        runtime = null;
        if (old != null) {
            try {
                if (old.presigner() != null) {
                    old.presigner().close();
                }
                if (old.client() != null) {
                    old.client().close();
                }
            } catch (Exception e) {
                log.warn("OSS 旧客户端关闭失败(忽略):{}", e.getMessage());
            }
        }
    }

    /** 配置快照(client/presigner 允许 null = 未启用/半配置占位,拦截在 requireRuntime);包私有 = 单测同包可构造 */
    record S3Runtime(OssSettings settings, S3Client client, S3Presigner presigner) {
    }

    record OssSettings(boolean enabled, String endpoint, String bucket,
                       String accessKey, String secretKey) {
    }

    /** UTF-8 便捷上传(文本类资产) */
    public String uploadText(String key, String text, String contentType) {
        return upload(key, text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    /** yml 兜底配置源注入(EnvironmentAware;测试可手工喂 stub) */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
