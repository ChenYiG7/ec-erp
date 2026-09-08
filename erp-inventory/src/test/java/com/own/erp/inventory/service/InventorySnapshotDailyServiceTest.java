package com.own.erp.inventory.service;

import com.own.erp.inventory.entity.InventorySnapshotDaily;
import com.own.erp.inventory.mapper.InventorySnapshotDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : InventorySnapshotDailyService 单测(#6 库存快照数据面,AIR:mock mapper 不触库):
 *     快照委托、非法入参空集合防触库、limit 钳制(默认 365/超界钳 365/负数回落)
 */
class InventorySnapshotDailyServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 8);

    private InventorySnapshotDailyMapper mapper;
    private InventorySnapshotDailyService service;

    @BeforeEach
    void setUp() {
        mapper = mock(InventorySnapshotDailyMapper.class);
        service = new InventorySnapshotDailyService(mapper);
    }

    @Test
    void snapshotDelegatesToUpsertAndReturnsAffected() {
        when(mapper.upsertSnapshot(TO)).thenReturn(15);

        int affected = service.snapshot(TO);

        assertEquals(15, affected);
        verify(mapper).upsertSnapshot(TO);
    }

    @Test
    void listSeriesRejectsInvalidArgsWithoutTouchingDb() {
        assertTrue(service.listSeries(null, 1L, FROM, TO, 10).isEmpty(), "skuId 为空应空集合");
        assertTrue(service.listSeries(1L, 1L, null, TO, 10).isEmpty(), "from 为空应空集合");
        assertTrue(service.listSeries(1L, 1L, FROM, null, 10).isEmpty(), "to 为空应空集合");
        assertTrue(service.listSeries(1L, 1L, TO, FROM, 10).isEmpty(), "from 晚于 to 应空集合");
        verifyNoInteractions(mapper);
    }

    @Test
    void listSeriesDefaultsLimitTo365WhenNullOrNonPositive() {
        when(mapper.listSeries(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.listSeries(1L, 1L, FROM, TO, null);
        service.listSeries(1L, 1L, FROM, TO, 0);
        // 两次入参归一后相同,合并校验调用两次
        verify(mapper, times(2)).listSeries(1L, 1L, FROM, TO, 365);
    }

    @Test
    void listSeriesClampsLimitAbove365() {
        when(mapper.listSeries(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.listSeries(1L, 1L, FROM, TO, 10_000);

        verify(mapper).listSeries(1L, 1L, FROM, TO, 365);
    }

    @Test
    void listSeriesPassesThroughNormalArgsIncludingNullWarehouse() {
        InventorySnapshotDaily row = InventorySnapshotDaily.builder()
                .statDate(TO).skuId(1L).warehouseId(0L)
                .qtyOnHand(10).qtyLocked(2).qtyTransit(3).qtyAvailable(8)
                .build();
        when(mapper.listSeries(eq(1L), eq(1L), eq(FROM), eq(TO), eq(10))).thenReturn(List.of(row));

        List<InventorySnapshotDaily> single = service.listSeries(1L, 1L, FROM, TO, 10);
        assertEquals(1, single.size());
        assertEquals(row, single.get(0));

        when(mapper.listSeries(eq(1L), eq(null), eq(FROM), eq(TO), eq(10))).thenReturn(List.of(row));
        List<InventorySnapshotDaily> aggregated = service.listSeries(1L, null, FROM, TO, 10);
        assertEquals(1, aggregated.size());
        verify(mapper).listSeries(1L, null, FROM, TO, 10);
    }
}
