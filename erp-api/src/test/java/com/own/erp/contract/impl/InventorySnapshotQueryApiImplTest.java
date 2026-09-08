package com.own.erp.contract.impl;

import com.own.erp.contract.InventorySnapshotQueryApi;
import com.own.erp.inventory.entity.InventorySnapshotDaily;
import com.own.erp.inventory.service.InventorySnapshotDailyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : InventorySnapshotQueryApiImpl 单测(#6 库存快照数据面,AIR:mock 域服务):
 *     entity→契约 record 显式逐字段映射、空列表透传
 */
class InventorySnapshotQueryApiImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 8);

    private InventorySnapshotDailyService service;
    private InventorySnapshotQueryApiImpl impl;

    @BeforeEach
    void setUp() {
        service = mock(InventorySnapshotDailyService.class);
        impl = new InventorySnapshotQueryApiImpl(service);
    }

    @Test
    void mapsEntityToSnapshotViewFieldByField() {
        InventorySnapshotDaily entity = InventorySnapshotDaily.builder()
                .id(99L)
                .statDate(DAY)
                .skuId(1L)
                .warehouseId(2L)
                .qtyOnHand(10)
                .qtyLocked(2)
                .qtyTransit(3)
                .qtyAvailable(8)
                .build();
        when(service.listSeries(1L, 2L, DAY.minusDays(7), DAY, 10)).thenReturn(List.of(entity));

        List<InventorySnapshotQueryApi.SnapshotView> views =
                impl.listSeries(1L, 2L, DAY.minusDays(7), DAY, 10);

        assertEquals(1, views.size());
        InventorySnapshotQueryApi.SnapshotView view = views.get(0);
        assertEquals(DAY, view.statDate());
        assertEquals(1L, view.skuId());
        assertEquals(2L, view.warehouseId());
        assertEquals(10, view.qtyOnHand());
        assertEquals(2, view.qtyLocked());
        assertEquals(3, view.qtyTransit());
        assertEquals(8, view.qtyAvailable());
        verify(service).listSeries(1L, 2L, DAY.minusDays(7), DAY, 10);
    }

    @Test
    void returnsEmptyWhenServiceReturnsEmpty() {
        when(service.listSeries(1L, null, DAY.minusDays(1), DAY, 5)).thenReturn(List.of());

        List<InventorySnapshotQueryApi.SnapshotView> views =
                impl.listSeries(1L, null, DAY.minusDays(1), DAY, 5);

        assertTrue(views.isEmpty());
        verify(service).listSeries(1L, null, DAY.minusDays(1), DAY, 5);
    }
}
