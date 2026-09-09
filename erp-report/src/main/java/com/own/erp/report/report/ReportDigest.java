package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 经营简报(#23 智能报表 V1,四期 BI 对标 OmniTrade「智能报表」自动化半边):
 *     定时生成的周期经营摘要——纯文本正文三渠道通用(站内通知/邮件 SimpleMailMessage/Webhook 群报文,
 *     均消费 title+content,NotifyPushedEvent 同构);窗口由周期语义决定:
 *     日报=昨日单日/周报=上周一至上周日/月报=上月自然月(Asia/Shanghai);
 *     Excel 导出半边 #20 报表中心已有(手动),不重复造
 */
public record ReportDigest(

        /** 周期类型(DAILY/WEEKLY/MONTHLY) */
        String period,

        /** 通知类型(站内扇出 notifyType:REPORT_DAILY/REPORT_WEEKLY/REPORT_MONTHLY) */
        String notifyType,

        /** 简报标题(推送时即通知标题/邮件主题素材,[ERP] 前缀由渠道侧统一加) */
        String title,

        /** 简报正文(纯文本行) */
        String content,

        /** 窗口起(含) */
        LocalDate dateFrom,

        /** 窗口止(含) */
        LocalDate dateTo
) {
}
