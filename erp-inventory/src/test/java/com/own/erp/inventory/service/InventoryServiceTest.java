package com.own.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.inventory.entity.Inventory;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryFlowMapper;
import com.own.erp.inventory.mapper.InventoryMapper;
import com.own.erp.inventory.request.query.InventoryQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : InventoryService 单测(AIR:mock Mapper,不依赖数据库);库存唯一入口 change() 必测(docs/07 §10),
 *     覆盖存量行原子 UPDATE / 首建 / 余额不足 / 并发首建撞 uk 重试 / 回查窗口重试(2026-09-04 并发原子化)
 */
class InventoryServiceTest {

    private InventoryMapper inventoryMapper;
    private InventoryFlowMapper inventoryFlowMapper;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryMapper = mock(InventoryMapper.class);
        inventoryFlowMapper = mock(InventoryFlowMapper.class);
        inventoryService = new InventoryService(inventoryMapper, inventoryFlowMapper);
    }

    @Test
    void changeInsertsNewRowAndFlowWhenInventoryMissing() {
        when(inventoryMapper.updateAvailableDelta(1L, 2L, 5)).thenReturn(0);
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        InventoryFlow flow = buildFlow(1L, 2L, 5);

        inventoryService.change(flow);

        ArgumentCaptor<Inventory> invCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryMapper).insert(invCaptor.capture());
        assertEquals(5, invCaptor.getValue().getQtyOnHand());
        assertEquals(5, invCaptor.getValue().getQtyAvailable());
        ArgumentCaptor<InventoryFlow> flowCaptor = ArgumentCaptor.forClass(InventoryFlow.class);
        verify(inventoryFlowMapper).insert(flowCaptor.capture());
        assertEquals(0, flowCaptor.getValue().getBeforeQty());
        assertEquals(5, flowCaptor.getValue().getAfterQty());
        verify(inventoryMapper, never()).updateById(any(Inventory.class));
    }

    @Test
    void changeUpdatesExistingRowAtomicallyAndWritesFlow() {
        // 原子 UPDATE 命中后同事务回读到的行(回读值即 after)
        Inventory after = new Inventory();
        after.setSkuId(1L);
        after.setWarehouseId(2L);
        after.setQtyOnHand(2);
        after.setQtyAvailable(1);
        when(inventoryMapper.updateAvailableDelta(1L, 2L, -3)).thenReturn(1);
        when(inventoryMapper.selectOne(any())).thenReturn(after);
        InventoryFlow flow = buildFlow(1L, 2L, -3);

        inventoryService.change(flow);

        // 存量行走原子条件更新(算术与负库存校验下 SQL,Java 不再读改写)
        verify(inventoryMapper).updateAvailableDelta(1L, 2L, -3);
        verify(inventoryMapper, never()).updateById(any(Inventory.class));
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        ArgumentCaptor<InventoryFlow> flowCaptor = ArgumentCaptor.forClass(InventoryFlow.class);
        verify(inventoryFlowMapper).insert(flowCaptor.capture());
        assertEquals(4, flowCaptor.getValue().getBeforeQty());
        assertEquals(1, flowCaptor.getValue().getAfterQty());
    }

    @Test
    void changeRejectsNegativeAvailable() {
        // 原子 UPDATE 未命中且行存在 = 余额不足
        Inventory existing = new Inventory();
        existing.setQtyAvailable(1);
        when(inventoryMapper.updateAvailableDelta(1L, 2L, -5)).thenReturn(0);
        when(inventoryMapper.selectOne(any())).thenReturn(existing);
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -5)));
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        verify(inventoryMapper, never()).updateById(any(Inventory.class));
        verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
    }

    @Test
    void changeRejectsNegativeQuantityWhenRowMissing() {
        // 无行 + 负数 = 负库存起步,拒绝建行
        when(inventoryMapper.updateAvailableDelta(1L, 2L, -5)).thenReturn(0);
        when(inventoryMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -5)));
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
    }

    @Test
    void concurrentFirstCreateRetriesViaAtomicUpdate() {
        // 首建撞 uk_sku_wh:他方先建行 → 捕 DuplicateKeyException → 回退原子 UPDATE 走存量分支
        Inventory after = new Inventory();
        after.setQtyAvailable(5);
        when(inventoryMapper.updateAvailableDelta(1L, 2L, 5)).thenReturn(0, 1);
        when(inventoryMapper.selectOne(any())).thenReturn(null, after);
        doThrow(new DuplicateKeyException("uk_sku_wh")).when(inventoryMapper).insert(any(Inventory.class));
        InventoryFlow flow = buildFlow(1L, 2L, 5);

        inventoryService.change(flow);

        verify(inventoryMapper, times(2)).updateAvailableDelta(1L, 2L, 5);
        verify(inventoryFlowMapper).insert(flow);
        assertEquals(0, flow.getBeforeQty());
        assertEquals(5, flow.getAfterQty());
    }

    @Test
    void rowCreatedBetweenUpdateAndLookupRetriesAtomicUpdate() {
        // 微秒级窗口:UPDATE 未命中后回查发现行已被他方首建且余额足够 → 重走原子 UPDATE
        Inventory fresh = new Inventory();
        fresh.setQtyAvailable(10);
        Inventory after = new Inventory();
        after.setQtyAvailable(7);
        when(inventoryMapper.updateAvailableDelta(1L, 2L, -3)).thenReturn(0, 1);
        when(inventoryMapper.selectOne(any())).thenReturn(fresh, after);
        InventoryFlow flow = buildFlow(1L, 2L, -3);

        inventoryService.change(flow);

        verify(inventoryMapper, times(2)).updateAvailableDelta(1L, 2L, -3);
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        assertEquals(10, flow.getBeforeQty());
        assertEquals(7, flow.getAfterQty());
    }

    @Test
    void persistentConflictAfterRetryThrowsBusinessException() {
        // 两轮仍冲突(极端竞争):按业务冲突上抛,调用方可重试
        when(inventoryMapper.updateAvailableDelta(1L, 2L, 5)).thenReturn(0, 0);
        when(inventoryMapper.selectOne(any())).thenReturn(null, null);
        doThrow(new DuplicateKeyException("uk_sku_wh")).when(inventoryMapper).insert(any(Inventory.class));
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 5)));
        verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
    }

    @Test
    void changeRejectsZeroQuantity() {
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 0)));
        verifyNoMapperInteractions();
    }

    @Test
    void changeRejectsMissingSkuOrWarehouse() {
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(null, 2L, 1)));
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, null, 1)));
        verifyNoMapperInteractions();
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        Inventory inventory = new Inventory();
        inventory.setId(1L);
        when(inventoryMapper.selectById(1L)).thenReturn(inventory);
        assertEquals(1L, inventoryService.getById(1L).id());
        assertNull(inventoryService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Inventory inventory = new Inventory();
        inventory.setId(2L);
        Page<Inventory> page = new Page<>(1, 10);
        page.setRecords(List.of(inventory));
        doReturn(page).when(inventoryMapper).selectPage(any(), any());
        assertEquals(2L, inventoryService.page(new InventoryQuery()).getRecords().get(0).id());
    }

    /** 入参装配走 builder(docs/07 §1 分级⑤):InventoryFlow @Builder 与 @Data 共存 */
    private InventoryFlow buildFlow(Long skuId, Long warehouseId, int quantity) {
        return InventoryFlow.builder()
                .skuId(skuId)
                .warehouseId(warehouseId)
                .flowType("ADJUST")
                .quantity(quantity)
                .build();
    }

    private void verifyNoMapperInteractions() {
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        verify(inventoryMapper, never()).updateById(any(Inventory.class));
        verify(inventoryMapper, never()).updateAvailableDelta(anyLong(), anyLong(), anyInt());
        verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
    }
}
