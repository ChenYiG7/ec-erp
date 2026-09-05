package com.own.erp.system.response;

import com.own.erp.system.entity.SysNotification;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 站内通知对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record SysNotificationResponse(

        /** 主键 */
        Long id,

        /** 接收用户ID(sys_user.id),写侧扇出到全部启用用户 */
        Long userId,

        /** 通知标题 */
        String title,

        /** 通知内容(写侧截断) */
        String content,

        /** 通知类型:PULL_FAIL=拉单连续失败告警 */
        String notifyType,

        /** 关联业务类型:SHOP等 */
        String bizType,

        /** 关联业务ID(如店铺ID) */
        Long bizId,

        /** 已读状态:0未读 1已读 */
        Integer readStatus,

        /** 已读时间 */
        LocalDateTime readAt,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static SysNotificationResponse from(SysNotification entity) {
        return SysNotificationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .notifyType(entity.getNotifyType())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId())
                .readStatus(entity.getReadStatus())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
