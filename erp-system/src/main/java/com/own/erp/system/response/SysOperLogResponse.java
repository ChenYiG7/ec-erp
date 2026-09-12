package com.own.erp.system.response;

import com.own.erp.system.entity.SysOperLog;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计对外结构(#27②;docs/07 §1:record+@Builder 读侧不可变,from 显式逐字段映射)
 */
@Builder
public record SysOperLogResponse(

        /** 主键 */
        Long id,

        /** 操作人ID(sys_user.id) */
        Long userId,

        /** 操作人登录名快照 */
        String username,

        /** 业务模块 */
        String module,

        /** 动作标识 */
        String action,

        /** 关联业务类型 */
        String bizType,

        /** 关联业务主键 */
        Long bizId,

        /** 方法参数 JSON(已截断脱敏) */
        String paramsJson,

        /** 结果:OK/FAIL */
        String resultStatus,

        /** 异常摘要(FAIL 时) */
        String errorMsg,

        /** 操作人 IP */
        String ip,

        /** 链路ID */
        String traceId,

        /** 耗时毫秒 */
        Integer costMs,

        /** 操作时间 */
        LocalDateTime createdAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static SysOperLogResponse from(SysOperLog entity) {
        return SysOperLogResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .username(entity.getUsername())
                .module(entity.getModule())
                .action(entity.getAction())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId())
                .paramsJson(entity.getParamsJson())
                .resultStatus(entity.getResultStatus())
                .errorMsg(entity.getErrorMsg())
                .ip(entity.getIp())
                .traceId(entity.getTraceId())
                .costMs(entity.getCostMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
