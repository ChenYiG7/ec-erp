package com.own.erp.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : Webhook 通知渠道配置(#14 后续渠道 V1):enabled/type/url/secret 四键同域收口本前缀,
 *         **不进 sys_config**——url/secret 是凭证类(hook 地址内嵌 access_token/key、钉钉加签密钥,
 *         docs/07 §7 红线禁入 sys_config),经 local.properties/环境变量提供
 *         (ERP_NOTIFY_WEBHOOK_URL / ERP_NOTIFY_WEBHOOK_SECRET);type 与 enabled 同域放部署级配置,
 *         避免运营改 type 而凭证仍需重启的半热更错位(V2 如需运营级开关再把 enabled 提进 sys_config)。
 *         enabled 默认 false:外呼渠道保护性默认关(与 erp.alert 内呼扫描默认开反向),防开发环境误推群消息;
 *         type 用枚举绑定,非法值启动即 fail-fast
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.notify.webhook")
public class WebhookProperties {

    /** 渠道类型(企微免签,钉钉/飞书自定义机器人加签) */
    public enum Channel {
        /** 钉钉群自定义机器人(加签) */
        DINGTALK,
        /** 飞书群自定义机器人(加签) */
        FEISHU,
        /** 企微群机器人(免签) */
        WECOM
    }

    /** 总开关(false 时 WebhookPushService 直通返回,不出网) */
    private boolean enabled = false;

    /** 渠道类型 */
    private Channel type = Channel.DINGTALK;

    /** 机器人 webhook 地址(凭证类,禁入 sys_config/仓库) */
    private String url = "";

    /** 加签密钥(钉钉 SEC 开头/飞书签名 secret;企微留空) */
    private String secret = "";
}
