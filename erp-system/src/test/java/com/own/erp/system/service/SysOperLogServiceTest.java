package com.own.erp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.system.entity.SysOperLog;
import com.own.erp.system.event.SysOperLogEvent;
import com.own.erp.system.mapper.SysOperLogMapper;
import com.own.erp.system.request.query.SysOperLogQuery;
import com.own.erp.system.response.SysOperLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SysOperLogService 单测(#27②,AIR:mock Mapper,不依赖数据库):
 *     审计事件→实体逐字段映射、落库失败吞异常不传播(审计失败不影响业务铁律)、分页实体→Response 映射。
 *     注:分页过滤走 eq 路径(docs/07 §10 MP Wrapper 急切坑规避口径,username 模糊路径不进纯单测)
 */
class SysOperLogServiceTest {

    private SysOperLogMapper operLogMapper;
    private SysOperLogService operLogService;

    @BeforeEach
    void setUp() {
        operLogMapper = mock(SysOperLogMapper.class);
        operLogService = new SysOperLogService(operLogMapper);
    }

    private SysOperLogEvent event() {
        return new SysOperLogEvent(9L, "admin", "order", "review", "order", 5L,
                "{\"orderId\":5}", "OK", null, "203.0.113.9", "trace0123456789ab", 12);
    }

    @Test
    void listenerMapsEventToEntity() {
        operLogService.onOperLog(event());

        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogMapper).insert(captor.capture());
        SysOperLog entity = captor.getValue();
        assertEquals(9L, entity.getUserId());
        assertEquals("admin", entity.getUsername());
        assertEquals("order", entity.getModule());
        assertEquals("review", entity.getAction());
        assertEquals(5L, entity.getBizId());
        assertEquals("{\"orderId\":5}", entity.getParamsJson());
        assertEquals("OK", entity.getResultStatus());
        assertEquals("203.0.113.9", entity.getIp());
        assertEquals("trace0123456789ab", entity.getTraceId());
        assertEquals(12, entity.getCostMs());
    }

    @Test
    void listenerSwallowsInsertFailure() {
        when(operLogMapper.insert(any(SysOperLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> operLogService.onOperLog(event()));
    }

    @Test
    void pageMapsEntityToResponse() {
        SysOperLog entity = SysOperLog.builder()
                .id(1L).userId(9L).username("admin").module("order").action("review")
                .bizType("order").bizId(5L).paramsJson("{}").resultStatus("OK")
                .ip("203.0.113.9").traceId("t").costMs(10)
                .createdAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .build();
        Page<SysOperLog> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(entity));
        when(operLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        SysOperLogQuery query = new SysOperLogQuery();
        query.setModule("order");
        query.setResultStatus("OK");

        Page<SysOperLogResponse> result = operLogService.page(query);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        SysOperLogResponse row = result.getRecords().get(0);
        assertEquals("admin", row.username());
        assertEquals("review", row.action());
        assertEquals("OK", row.resultStatus());
        assertEquals(LocalDateTime.of(2026, 9, 12, 10, 0), row.createdAt());
    }
}
