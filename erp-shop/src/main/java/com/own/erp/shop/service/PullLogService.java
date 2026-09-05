package com.own.erp.shop.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.shop.entity.PullLog;
import com.own.erp.shop.mapper.PullLogMapper;
import com.own.erp.shop.request.query.PullLogQuery;
import com.own.erp.shop.response.PullLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 拉取日志服务:pull_log 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     pull_log 为系统写入表(拉单任务落记录),只增不更新不删除,不开放人工写接口;
 *     写入侧 recordSuccess/recordFailure(#4):成功记录的 window_end 即新游标,失败记录不推进游标;
 *     排障第一入口是本表(docs/07 §3):duration_ms/error_msg 观测字段落库时必须写全
 */
@Service
@RequiredArgsConstructor
public class PullLogService {

    /** 拉取成功 */
    private static final int SUCCESS = 1;
    /** 拉取失败 */
    private static final int FAILED = 0;
    /** error_msg 列 TEXT,超长堆栈信息截断,防单条日志撑爆行 */
    private static final int ERROR_MSG_MAX_LENGTH = 2000;

    private final PullLogMapper pullLogMapper;

    /** 分页查询(过滤:店铺/类型/成功与否) */
    public Page<PullLogResponse> page(PullLogQuery query) {
        LambdaQueryWrapper<PullLog> wrapper = new LambdaQueryWrapper<PullLog>()
                .eq(query.getShopId() != null, PullLog::getShopId, query.getShopId())
                .eq(StrUtil.isNotBlank(query.getDataType()), PullLog::getDataType, query.getDataType())
                .eq(query.getSuccess() != null, PullLog::getSuccess, query.getSuccess())
                .orderByDesc(PullLog::getId);
        Page<PullLog> result = pullLogMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<PullLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(PullLogResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public PullLogResponse getById(Long id) {
        PullLog pullLog = pullLogMapper.selectById(id);
        return pullLog == null ? null : PullLogResponse.from(pullLog);
    }

    /** 拉取记录计数(#3 店铺删除校验,域内调用):shopIds 的 pull_log 行数(含失败记录,有拉取历史即有痕迹) */
    public long countByShopIds(Collection<Long> shopIds) {
        if (CollUtil.isEmpty(shopIds)) {
            return 0;
        }
        Long count = pullLogMapper.selectCount(new LambdaQueryWrapper<PullLog>()
                .in(PullLog::getShopId, shopIds));
        return count == null ? 0L : count;
    }

    /**
     * 拉单游标:该店该类型最近一次成功记录的 window_end(docs/07 核心流程);
     * 无成功记录返回 null(首次拉取,窗口由调用方定)
     */
    public LocalDateTime findLastSuccessWindowEnd(Long shopId, String dataType) {
        PullLog last = pullLogMapper.selectOne(new LambdaQueryWrapper<PullLog>()
                .eq(PullLog::getShopId, shopId)
                .eq(PullLog::getDataType, dataType)
                .eq(PullLog::getSuccess, SUCCESS)
                .orderByDesc(PullLog::getWindowEnd)
                .last("LIMIT 1"));
        return last == null ? null : last.getWindowEnd();
    }

    /**
     * 记录一次成功拉取(#4 写入侧):windowEnd 即新游标,下次窗口起点 = 它左叠 WINDOW_OVERLAP_MINUTES
     */
    public void recordSuccess(Long shopId, String dataType, LocalDateTime windowStart, LocalDateTime windowEnd,
                              int pulledCount, long durationMs, String pullWay) {
        pullLogMapper.insert(build(shopId, dataType, windowStart, windowEnd, pulledCount, SUCCESS, null, durationMs, pullWay));
    }

    /**
     * 记录一次失败拉取(#4):success=0 不计入游标(findLastSuccessWindowEnd 只取成功记录),下轮自动重试重叠窗口
     */
    public void recordFailure(Long shopId, String dataType, LocalDateTime windowStart, LocalDateTime windowEnd,
                              String errorMsg, long durationMs, String pullWay) {
        pullLogMapper.insert(build(shopId, dataType, windowStart, windowEnd, 0, FAILED, errorMsg, durationMs, pullWay));
    }

    /**
     * 连续失败告警判定(#14,无状态,靠 pull_log 本身去重):最近 threshold 次全部失败,
     * 且紧邻其前的一条(第 threshold+1 新)不存在或为成功——即"恰好跌满阈值的那个失败轮"才返回 true,
     * 连续段加深的后续轮次不重复告警;任何一次成功即重新计数
     */
    public boolean shouldAlertContinuousFailure(Long shopId, String dataType, int threshold) {
        List<PullLog> recent = pullLogMapper.selectList(new LambdaQueryWrapper<PullLog>()
                .eq(PullLog::getShopId, shopId)
                .eq(PullLog::getDataType, dataType)
                .orderByDesc(PullLog::getId)
                .last("LIMIT " + (threshold + 1)));
        if (recent.size() < threshold) {
            return false;
        }
        boolean streakAllFailed = recent.stream()
                .limit(threshold)
                .allMatch(row -> row.getSuccess() != null && row.getSuccess() == FAILED);
        if (!streakAllFailed) {
            return false;
        }
        return recent.size() == threshold || recent.get(threshold).getSuccess() == SUCCESS;
    }

    /** 行装配统一走 builder(docs/07 §1 模型可变性分级):一次成型,无中间可变态 */
    private PullLog build(Long shopId, String dataType, LocalDateTime windowStart, LocalDateTime windowEnd,
                          int pulledCount, int success, String errorMsg, long durationMs, String pullWay) {
        return PullLog.builder()
                .shopId(shopId)
                .dataType(dataType)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .pulledCount(pulledCount)
                .success(success)
                .errorMsg(StrUtil.isBlank(errorMsg) ? null : StrUtil.maxLength(errorMsg, ERROR_MSG_MAX_LENGTH))
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .pullWay(pullWay)
                .build();
    }
}
