package com.own.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 站内通知(系统告警扇出+用户已读状态)(sys_notification)
 */
/** @Data 保留 setter = 模型可变性分级的 MP 框架例外(docs/07 §1);@Builder+双构造保无参构造(MP 反射实例化依赖) */
@Data
@TableName("sys_notification")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysNotification {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID(sys_user.id),写侧扇出到全部启用用户 */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容(写侧截断) */
    private String content;

    /** 通知类型:PULL_FAIL=拉单连续失败告警 */
    private String notifyType;

    /** 关联业务类型:SHOP等 */
    private String bizType;

    /** 关联业务ID(如店铺ID) */
    private Long bizId;

    /** 已读状态:0未读 1已读 */
    private Integer readStatus;

    /** 已读时间 */
    private LocalDateTime readAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
