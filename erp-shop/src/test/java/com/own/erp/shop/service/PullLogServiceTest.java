package com.own.erp.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.shop.entity.PullLog;
import com.own.erp.shop.mapper.PullLogMapper;
import com.own.erp.shop.request.query.PullLogQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : PullLogService 单测(AIR:mock Mapper,不依赖数据库),覆盖游标读取
 */
class PullLogServiceTest {

    private PullLogMapper pullLogMapper;
    private PullLogService pullLogService;

    @BeforeEach
    void setUp() {
        pullLogMapper = mock(PullLogMapper.class);
        pullLogService = new PullLogService(pullLogMapper);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        PullLog pullLog = new PullLog();
        pullLog.setId(1L);
        when(pullLogMapper.selectById(1L)).thenReturn(pullLog);
        assertEquals(1L, pullLogService.getById(1L).id());
        assertNull(pullLogService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        PullLog pullLog = new PullLog();
        pullLog.setId(2L);
        Page<PullLog> page = new Page<>(1, 10);
        page.setRecords(List.of(pullLog));
        doReturn(page).when(pullLogMapper).selectPage(any(), any());
        assertEquals(2L, pullLogService.page(new PullLogQuery()).getRecords().get(0).id());
    }

    @Test
    void findLastSuccessWindowEndReturnsCursorOrNullWhenAbsent() {
        PullLog last = new PullLog();
        LocalDateTime cursor = LocalDateTime.of(2026, 9, 3, 12, 0);
        last.setWindowEnd(cursor);
        when(pullLogMapper.selectOne(any())).thenReturn(last);
        assertEquals(cursor, pullLogService.findLastSuccessWindowEnd(1L, "ORDER"));
        when(pullLogMapper.selectOne(any())).thenReturn(null);
        assertNull(pullLogService.findLastSuccessWindowEnd(1L, "ORDER"));
    }

    @Test
    void recordSuccessWritesFullObservabilityRow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 4, 8, 55);
        LocalDateTime end = LocalDateTime.of(2026, 9, 4, 10, 0);

        pullLogService.recordSuccess(1L, "ORDER", start, end, 7, 1234L, "JOB");

        ArgumentCaptor<PullLog> captor = ArgumentCaptor.forClass(PullLog.class);
        verify(pullLogMapper).insert(captor.capture());
        PullLog row = captor.getValue();
        assertEquals(1L, row.getShopId());
        assertEquals("ORDER", row.getDataType());
        assertEquals(start, row.getWindowStart());
        assertEquals(end, row.getWindowEnd());
        assertEquals(7, row.getPulledCount());
        assertEquals(1, row.getSuccess());
        assertNull(row.getErrorMsg());
        assertEquals(1234, row.getDurationMs());
        assertEquals("JOB", row.getPullWay());
    }

    @Test
    void recordFailureWritesZeroCountAndTruncatesLongMessage() {
        String hugeMessage = "x".repeat(5000);

        pullLogService.recordFailure(1L, "ORDER", LocalDateTime.of(2026, 9, 4, 8, 55),
                LocalDateTime.of(2026, 9, 4, 10, 0), hugeMessage, 99L, "JOB");

        ArgumentCaptor<PullLog> captor = ArgumentCaptor.forClass(PullLog.class);
        verify(pullLogMapper).insert(captor.capture());
        PullLog row = captor.getValue();
        assertEquals(0, row.getSuccess());
        assertEquals(0, row.getPulledCount());
        // 超长错误信息截断,防 TEXT 列被单条堆栈撑爆;截断后仍可读到前缀定位
        assertEquals(2003, row.getErrorMsg().length());
    }

    @Test
    void shouldAlertWhenStreakExactlyReachesThreshold() {
        // 最近 3 次全失败,第 4 新为成功 → 恰达阈值的失败轮,告警(#14)
        when(pullLogMapper.selectList(any())).thenReturn(List.of(failed(11), failed(10), failed(9), success(8)));

        assertTrue(pullLogService.shouldAlertContinuousFailure(1L, "ORDER", 3));
    }

    @Test
    void shouldNotAlertWhenStreakDeeperThanThreshold() {
        // 连续 4 次失败:阈值轮已告警过,连续段不重复推
        when(pullLogMapper.selectList(any())).thenReturn(List.of(failed(12), failed(11), failed(10), failed(9)));

        assertFalse(pullLogService.shouldAlertContinuousFailure(1L, "ORDER", 3));
    }

    @Test
    void shouldNotAlertWhenRecentHasSuccessOrTooFewRows() {
        // 中间夹成功:连续段被打断重新计数
        when(pullLogMapper.selectList(any())).thenReturn(List.of(failed(11), success(10), failed(9)));
        assertFalse(pullLogService.shouldAlertContinuousFailure(1L, "ORDER", 3));
        // 不足阈值次数
        when(pullLogMapper.selectList(any())).thenReturn(List.of(failed(11), failed(10)));
        assertFalse(pullLogService.shouldAlertContinuousFailure(1L, "ORDER", 3));
    }

    private PullLog failed(long id) {
        return PullLog.builder().id(id).success(0).build();
    }

    private PullLog success(long id) {
        return PullLog.builder().id(id).success(1).build();
    }
}
