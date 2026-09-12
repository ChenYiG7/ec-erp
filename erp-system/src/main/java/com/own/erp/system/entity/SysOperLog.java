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
 * @Date : 2026/9/12
 * @Description : 操作审计日志(#27②,sys_oper_log):人工业务动作只增流水,由 @OperLog 切面异步落库。
 *     只增不改不删——无逻辑删除列,不在 LogicalDeleteAnnotationTest 口径(非人工域管理表,是审计事实)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_oper_log")
public class SysOperLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID(sys_user.id) */
    private Long userId;

    /** 操作人登录名快照(防用户删除后断链) */
    private String username;

    /** 业务模块:order/inventory/fulfill/purchase/finance */
    private String module;

    /** 动作标识,@OperLog 声明 */
    private String action;

    /** 关联业务类型,本期默认同 module */
    private String bizType;

    /** 关联业务主键(路径变量 id) */
    private Long bizId;

    /** 方法参数 JSON(截断 2KB,凭证类字段名脱敏) */
    private String paramsJson;

    /** 结果:OK/FAIL */
    private String resultStatus;

    /** 异常摘要(result_status=FAIL 时,截断) */
    private String errorMsg;

    /** 操作人 IP(X-Forwarded-For 首段优先) */
    private String ip;

    /** 链路ID(TraceIdFilter MDC traceId) */
    private String traceId;

    /** 耗时毫秒 */
    private Integer costMs;

    /** 操作时间(created_at 数据库 DEFAULT 维护,实体不填) */
    private LocalDateTime createdAt;
}
