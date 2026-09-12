package com.own.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.system.entity.SysOperLog;
import com.own.erp.system.event.SysOperLogEvent;
import com.own.erp.system.mapper.SysOperLogMapper;
import com.own.erp.system.request.query.SysOperLogQuery;
import com.own.erp.system.response.SysOperLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计日志(#27②,sys_oper_log)域整域收口(docs/07 §2.1):
 *     写侧唯一入口 = 审计事件监听(AFTER_COMMIT + fallbackExecution,同 WebhookPushService 拍板——
 *     业务事务提交后才落审计,落库失败只记日志绝不回滚业务);读侧 = admin 分页查询(只增流水禁改禁删)。
 *     系统 Job 自动动作不记操作审计(操作审计=人工动作,切面只在 @OperLog 标注的 Controller 生效)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogService {

    private final SysOperLogMapper operLogMapper;

    /**
     * 审计事件落库:phase=AFTER_COMMIT 保证业务事务提交后才写;fallbackExecution=true 兜底
     * 无事务调用链(Controller 直返场景)立即执行——两种形态下审计均不参与业务事务
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOperLog(SysOperLogEvent event) {
        try {
            operLogMapper.insert(SysOperLog.builder()
                    .userId(event.userId())
                    .username(event.username())
                    .module(event.module())
                    .action(event.action())
                    .bizType(event.bizType())
                    .bizId(event.bizId())
                    .paramsJson(event.paramsJson())
                    .resultStatus(event.resultStatus())
                    .errorMsg(event.errorMsg())
                    .ip(event.ip())
                    .traceId(event.traceId())
                    .costMs(event.costMs())
                    .build());
        } catch (Exception ex) {
            log.error("操作审计落库失败(不影响业务): module={} action={} user={}",
                    event.module(), event.action(), event.username(), ex);
        }
    }

    /** 分页查询(admin):module/resultStatus 精确、username 模糊、时间窗闭区间,时间倒序 */
    public Page<SysOperLogResponse> page(SysOperLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(StringUtils.hasText(query.getModule()), SysOperLog::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getResultStatus()), SysOperLog::getResultStatus, query.getResultStatus())
                .like(StringUtils.hasText(query.getUsername()), SysOperLog::getUsername, query.getUsername())
                .ge(query.getBeginTime() != null, SysOperLog::getCreatedAt, query.getBeginTime())
                .le(query.getEndTime() != null, SysOperLog::getCreatedAt, query.getEndTime())
                .orderByDesc(SysOperLog::getCreatedAt)
                .orderByDesc(SysOperLog::getId);
        Page<SysOperLog> result = operLogMapper.selectPage(
                new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        List<SysOperLogResponse> records = result.getRecords().stream().map(SysOperLogResponse::from).toList();
        Page<SysOperLogResponse> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(records);
        return response;
    }
}
