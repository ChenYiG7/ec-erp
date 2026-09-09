package com.own.erp.system.service;

import cn.hutool.core.util.StrUtil;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.system.event.NotifyPushedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Properties;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 邮件通知渠道(#14 后续渠道 V2,2026-09-08 拍板只做邮箱推送):站内通知 pushAllUsers 出口外推邮件,
 *     与 WebhookPushService 同款挂监听(2026-09-04"不提前抽象"——各渠道一个监听器,不做渠道接口抽象)。
 *     <p>配置拍板:SMTP 参数全量进 sys_config(GROUP_NOTIFY 七键热更),非 yml/local.properties——
 *     授权码为首个入 sys_config 的凭证(SECRET 类型,docs/07 §7 范围例外):读侧回显脱敏 SECRET_MASK,
 *     消费侧 valueOf 恒取真值零感知。
 *     <p>收件人拍板:启用用户邮箱扇出(SysUserService.listEnabledUserEmails,与站内 pushAllUsers 同范式);
 *     邮箱未填的用户自然不在收件人之列;零收件人静默跳过(与"站内零用户也发布事件"对称——
 *     事件为源,渠道侧无收件人无动作)。
 *     <p>相位拍板:@TransactionalEventListener AFTER_COMMIT + fallbackExecution——事务提交后才外推
 *     (同 Webhook/#11),失败只记日志绝不回滚站内通知、不上抛阻断调用方;
 *     同步跑在发布方线程(告警量级每日个位数),connect 3s/read-write 5s 防 SMTP 慢响应拖死告警 Job。
 *     V1 纯文本邮件(SimpleMailMessage),主题加 [ERP] 前缀;HTML/模板化随需求再评估
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailPushService {

    /** SMTP 连接/读写超时(毫秒,对齐 Webhook 3s/5s 防拖死告警 Job) */
    private static final String CONNECT_TIMEOUT_MILLIS = "3000";
    private static final String IO_TIMEOUT_MILLIS = "5000";

    /** 缺省 SMTP 端口(SSL 465;未配端口按 SSL 开关取默认) */
    private static final int DEFAULT_PORT_SSL = 465;
    private static final int DEFAULT_PORT_PLAIN = 25;

    private static final String SUBJECT_PREFIX = "[ERP] ";
    private static final String EMPTY_BODY_PLACEHOLDER = "(无正文)";

    private final SystemConfigService configService;
    private final SysUserService sysUserService;

    /** pushAllUsers 出口的邮件消费位:AFTER_COMMIT 相位(提交后才外推),异常吞掉只记日志不阻断 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotifyPushed(NotifyPushedEvent event) {
        try {
            if (!"true".equalsIgnoreCase(StrUtil.trimToEmpty(configService.valueOf(ConfigConsts.KEY_MAIL_ENABLED)))) {
                return;
            }
            List<String> recipients = sysUserService.listEnabledUserEmails();
            if (recipients.isEmpty()) {
                log.info("邮件通知跳过:启用用户均未配置邮箱");
                return;
            }
            String host = configService.valueOf(ConfigConsts.KEY_MAIL_HOST);
            if (StrUtil.isBlank(host)) {
                log.warn("邮件通知跳过:SMTP 主机未配置(erp.mail.host)");
                return;
            }
            send(host, recipients, event);
        } catch (Exception e) {
            log.warn("邮件通知外推失败(失败不阻断站内通知): {}", e.getMessage());
        }
    }

    /** 按当前 sys_config 装配 JavaMailSender 并发送(包级私有供测试 spy 打桩,不出网) */
    void send(String host, List<String> recipients, NotifyPushedEvent event) {
        String username = configService.valueOf(ConfigConsts.KEY_MAIL_USERNAME);
        boolean ssl = "true".equalsIgnoreCase(StrUtil.trimToEmpty(configService.valueOf(ConfigConsts.KEY_MAIL_SSL)));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(StrUtil.trim(host));
        sender.setPort(resolvePort(configService.valueOf(ConfigConsts.KEY_MAIL_PORT), ssl));
        sender.setUsername(username);
        sender.setPassword(configService.valueOf(ConfigConsts.KEY_MAIL_PASSWORD));
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MILLIS);
        props.put("mail.smtp.timeout", IO_TIMEOUT_MILLIS);
        props.put("mail.smtp.writetimeout", IO_TIMEOUT_MILLIS);
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        sender.send(buildMessage(
                StrUtil.blankToDefault(configService.valueOf(ConfigConsts.KEY_MAIL_FROM), username),
                recipients, event.title(), event.content()));
        log.info("邮件通知已外推:收件人 {} 个,主题 = {}{}", recipients.size(), SUBJECT_PREFIX, event.title());
    }

    /** 报文装配(包级私有静态直测:主题前缀/正文占位/收件人与 From 透传) */
    static SimpleMailMessage buildMessage(String from, List<String> recipients, String title, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject(SUBJECT_PREFIX + StrUtil.nullToEmpty(title));
        message.setText(StrUtil.isBlank(content) ? EMPTY_BODY_PLACEHOLDER : content);
        return message;
    }

    /** 端口解析:空白/非法回落 SSL 开关对应默认值(465/25),配置错误不炸告警链路 */
    static int resolvePort(String portValue, boolean ssl) {
        if (StrUtil.isBlank(portValue)) {
            return ssl ? DEFAULT_PORT_SSL : DEFAULT_PORT_PLAIN;
        }
        try {
            return Integer.parseInt(portValue.trim());
        } catch (NumberFormatException e) {
            return ssl ? DEFAULT_PORT_SSL : DEFAULT_PORT_PLAIN;
        }
    }
}
