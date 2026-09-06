package com.own.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.inventory.entity.Inventory;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryFlowMapper;
import com.own.erp.inventory.mapper.InventoryMapper;
import com.own.erp.inventory.request.query.InventoryQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : InventoryService 单测(AIR:mock Mapper,不依赖数据库);库存唯一入口 change() 必测(docs/07 §10)。
 *     2026-09-06 #7 按 flow_type 差异化翻新:并发分支(原子 UPDATE/首建/回查窗口/持续冲突)以 ADJUST 代表通用形,
 *     列语义矩阵(在途/入库核销/占用/出库核销)逐类型断言 Mapper 分发、首建形态与守卫文案;transfer() 两腿组合
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

    /** 造行:默认数量列 0,按需覆写 */
    private Inventory row(Integer available, Integer locked, Integer transit, Integer onHand) {
        Inventory inventory = new Inventory();
        inventory.setQtyAvailable(available);
        inventory.setQtyLocked(locked);
        inventory.setQtyTransit(transit);
        inventory.setQtyOnHand(onHand);
        return inventory;
    }

    @Nested
    class GenericAvailable {

        @Test
        void changeInsertsNewRowAndFlowWhenInventoryMissing() {
            when(inventoryMapper.updateAvailableDelta(1L, 2L, 5)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            InventoryFlow flow = buildFlow(1L, 2L, 5, "ADJUST");

            inventoryService.change(flow);

            ArgumentCaptor<Inventory> invCaptor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryMapper).insert(invCaptor.capture());
            assertEquals(5, invCaptor.getValue().getQtyOnHand());
            assertEquals(5, invCaptor.getValue().getQtyAvailable());
            assertEquals(0, invCaptor.getValue().getQtyLocked());
            assertEquals(0, invCaptor.getValue().getQtyTransit());
            ArgumentCaptor<InventoryFlow> flowCaptor = ArgumentCaptor.forClass(InventoryFlow.class);
            verify(inventoryFlowMapper).insert(flowCaptor.capture());
            assertEquals(0, flowCaptor.getValue().getBeforeQty());
            assertEquals(5, flowCaptor.getValue().getAfterQty());
            verify(inventoryMapper, never()).updateById(any(Inventory.class));
        }

        @Test
        void changeUpdatesExistingRowAtomicallyAndWritesFlow() {
            // 原子 UPDATE 命中后同事务回读到的行(回读值即 after)
            when(inventoryMapper.updateAvailableDelta(1L, 2L, -3)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(1, 0, 0, 2));
            InventoryFlow flow = buildFlow(1L, 2L, -3, "ADJUST");

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
            when(inventoryMapper.updateAvailableDelta(1L, 2L, -5)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(row(1, 0, 0, 1));
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -5, "ADJUST")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
            verify(inventoryMapper, never()).updateById(any(Inventory.class));
            verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
        }

        @Test
        void changeRejectsNegativeQuantityWhenRowMissing() {
            // 无行 + 负数 = 负库存起步,拒绝建行
            when(inventoryMapper.updateAvailableDelta(1L, 2L, -5)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -5, "ADJUST")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
            verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
        }

        @Test
        void concurrentFirstCreateRetriesViaAtomicUpdate() {
            // 首建撞 uk_sku_wh:他方先建行 → 捕 DuplicateKeyException → 回退原子 UPDATE 走存量分支
            when(inventoryMapper.updateAvailableDelta(1L, 2L, 5)).thenReturn(0, 1);
            when(inventoryMapper.selectOne(any())).thenReturn(null, row(5, 0, 0, 5));
            doThrow(new DuplicateKeyException("uk_sku_wh")).when(inventoryMapper).insert(any(Inventory.class));
            InventoryFlow flow = buildFlow(1L, 2L, 5, "ADJUST");

            inventoryService.change(flow);

            verify(inventoryMapper, times(2)).updateAvailableDelta(1L, 2L, 5);
            verify(inventoryFlowMapper).insert(flow);
            assertEquals(0, flow.getBeforeQty());
            assertEquals(5, flow.getAfterQty());
        }

        @Test
        void rowCreatedBetweenUpdateAndLookupRetriesAtomicUpdate() {
            // 微秒级窗口:UPDATE 未命中后回查发现行已被他方首建且余额足够 → 重走原子 UPDATE
            when(inventoryMapper.updateAvailableDelta(1L, 2L, -3)).thenReturn(0, 1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(10, 0, 0, 10), row(7, 0, 0, 7));
            InventoryFlow flow = buildFlow(1L, 2L, -3, "ADJUST");

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
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 5, "ADJUST")));
            verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
        }
    }

    @Nested
    class InTransit {

        @Test
        void occupyUsesTransitDeltaAndCreatesTransitOnlyRow() {
            // 审核占在途:无行 → 首建 在途=q、在库/占用/可用 0
            when(inventoryMapper.updateTransitDelta(1L, 2L, 6)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);

            inventoryService.change(buildFlow(1L, 2L, 6, "IN_TRANSIT"));

            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
            verify(inventoryMapper).insert(captor.capture());
            assertEquals(6, captor.getValue().getQtyTransit());
            assertEquals(0, captor.getValue().getQtyOnHand());
            assertEquals(0, captor.getValue().getQtyAvailable());
            // 可用不动,流水前后相等属正常
            verify(inventoryFlowMapper).insert(any(InventoryFlow.class));
        }

        @Test
        void releaseUsesSameDeltaWithNegativeQuantity() {
            // 关闭释放在途:负数同走 updateTransitDelta
            when(inventoryMapper.updateTransitDelta(1L, 2L, -4)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(3, 0, 0, 3));

            inventoryService.change(buildFlow(1L, 2L, -4, "IN_TRANSIT"));

            verify(inventoryMapper).updateTransitDelta(1L, 2L, -4);
            verify(inventoryFlowMapper).insert(any(InventoryFlow.class));
        }

        @Test
        void releaseRejectsWhenRowMissing() {
            when(inventoryMapper.updateTransitDelta(1L, 2L, -4)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -4, "IN_TRANSIT")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }
    }

    @Nested
    class InPurchase {

        @Test
        void receiveUsesInboundDelta() {
            when(inventoryMapper.receiveInbound(1L, 2L, 6)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(9, 0, 0, 9));

            inventoryService.change(buildFlow(1L, 2L, 6, "IN_PURCHASE"));

            verify(inventoryMapper).receiveInbound(1L, 2L, 6);
            verify(inventoryMapper, never()).updateAvailableDelta(anyLong(), anyLong(), anyInt());
            verify(inventoryFlowMapper).insert(any(InventoryFlow.class));
        }

        @Test
        void rejectsWhenTransitInsufficient() {
            // 未审核占在途(或已超收):守卫拦截
            when(inventoryMapper.receiveInbound(1L, 2L, 6)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(row(5, 0, 4, 5));
            assertTrue(assertThrows(BusinessException.class,
                    () -> inventoryService.change(buildFlow(1L, 2L, 6, "IN_PURCHASE")))
                    .getMessage().contains("在途库存不足"));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }

        @Test
        void rejectsWhenRowMissing() {
            when(inventoryMapper.receiveInbound(1L, 2L, 6)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 6, "IN_PURCHASE")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }
    }

    @Nested
    class LockShip {

        @Test
        void lockUsesLockForShip() {
            when(inventoryMapper.lockForShip(1L, 2L, 4)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(6, 4, 0, 10));

            inventoryService.change(buildFlow(1L, 2L, 4, "LOCK_SHIP"));

            verify(inventoryMapper).lockForShip(1L, 2L, 4);
            verify(inventoryMapper, never()).updateAvailableDelta(anyLong(), anyLong(), anyInt());
            verify(inventoryFlowMapper).insert(any(InventoryFlow.class));
        }

        @Test
        void releaseUsesNegativeQuantity() {
            when(inventoryMapper.lockForShip(1L, 2L, -4)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(6, 0, 0, 6));

            inventoryService.change(buildFlow(1L, 2L, -4, "LOCK_SHIP"));

            verify(inventoryMapper).lockForShip(1L, 2L, -4);
        }

        @Test
        void rejectsWhenAvailableInsufficient() {
            when(inventoryMapper.lockForShip(1L, 2L, 4)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(row(3, 0, 0, 3));
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 4, "LOCK_SHIP")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }

        @Test
        void rejectsWhenRowMissing() {
            when(inventoryMapper.lockForShip(1L, 2L, 4)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 4, "LOCK_SHIP")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }
    }

    @Nested
    class OutShip {

        @Test
        void shipUsesLockedOutDelta() {
            when(inventoryMapper.shipLockedOut(1L, 2L, -4)).thenReturn(1);
            when(inventoryMapper.selectOne(any())).thenReturn(row(6, 0, 0, 6));

            inventoryService.change(buildFlow(1L, 2L, -4, "OUT_SHIP"));

            verify(inventoryMapper).shipLockedOut(1L, 2L, -4);
            verify(inventoryMapper, never()).updateAvailableDelta(anyLong(), anyLong(), anyInt());
            verify(inventoryFlowMapper).insert(any(InventoryFlow.class));
        }

        @Test
        void rejectsWhenLockedInsufficient() {
            // 未建单占用(或已释放)即发货:守卫拦截
            when(inventoryMapper.shipLockedOut(1L, 2L, -4)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(row(6, 1, 0, 6));
            assertTrue(assertThrows(BusinessException.class,
                    () -> inventoryService.change(buildFlow(1L, 2L, -4, "OUT_SHIP")))
                    .getMessage().contains("出库占用不足"));
        }

        @Test
        void rejectsWhenRowMissing() {
            when(inventoryMapper.shipLockedOut(1L, 2L, -4)).thenReturn(0);
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, -4, "OUT_SHIP")));
            verify(inventoryMapper, never()).insert(any(Inventory.class));
        }
    }

    @Test
    void rejectsUnknownFlowType() {
        // 封闭枚举:未知/缺类型拒,防脏流水绕过列语义
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 1, "MAGIC")));
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 1, null)));
        verifyNoInteractions(inventoryMapper, inventoryFlowMapper);
    }

    @Nested
    class Transfer {

        @Test
        void movesStockWithTwoLegsInOneCall() {
            // 两腿均命中存量行(updateAvailableDelta 按类型分发)
            when(inventoryMapper.updateAvailableDelta(1L, 2L, -5)).thenReturn(1);
            when(inventoryMapper.updateAvailableDelta(1L, 3L, 5)).thenReturn(1);

            inventoryService.transfer(1L, 2L, 3L, 5, "调拨", 9L);

            ArgumentCaptor<InventoryFlow> captor = ArgumentCaptor.forClass(InventoryFlow.class);
            verify(inventoryFlowMapper, times(2)).insert(captor.capture());
            InventoryFlow out = captor.getAllValues().get(0);
            assertEquals(2L, out.getWarehouseId());
            assertEquals(-5, out.getQuantity());
            assertEquals("TRANSFER_OUT", out.getFlowType());
            assertEquals("INVENTORY_TRANSFER", out.getBizType());
            assertEquals(9L, out.getCreatedBy());
            InventoryFlow in = captor.getAllValues().get(1);
            assertEquals(3L, in.getWarehouseId());
            assertEquals(5, in.getQuantity());
            assertEquals("TRANSFER_IN", in.getFlowType());
        }

        @Test
        void rejectsSameWarehouseOrNonPositiveQuantity() {
            assertThrows(BusinessException.class, () -> inventoryService.transfer(1L, 2L, 2L, 5, null, null));
            assertThrows(BusinessException.class, () -> inventoryService.transfer(1L, 2L, 3L, 0, null, null));
            assertThrows(BusinessException.class, () -> inventoryService.transfer(1L, null, 3L, 5, null, null));
            verifyNoInteractions(inventoryMapper, inventoryFlowMapper);
        }
    }

    @Test
    void changeRejectsZeroQuantity() {
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, 2L, 0, "ADJUST")));
        verifyNoMapperInteractions();
    }

    @Test
    void changeRejectsMissingSkuOrWarehouse() {
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(null, 2L, 1, "ADJUST")));
        assertThrows(BusinessException.class, () -> inventoryService.change(buildFlow(1L, null, 1, "ADJUST")));
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
    private InventoryFlow buildFlow(Long skuId, Long warehouseId, int quantity, String flowType) {
        return InventoryFlow.builder()
                .skuId(skuId)
                .warehouseId(warehouseId)
                .flowType(flowType)
                .quantity(quantity)
                .build();
    }

    private void verifyNoMapperInteractions() {
        verify(inventoryMapper, never()).insert(any(Inventory.class));
        verify(inventoryMapper, never()).updateById(any(Inventory.class));
        verify(inventoryMapper, never()).updateAvailableDelta(anyLong(), anyLong(), anyInt());
        verify(inventoryMapper, never()).updateTransitDelta(anyLong(), anyLong(), anyInt());
        verify(inventoryMapper, never()).receiveInbound(anyLong(), anyLong(), anyInt());
        verify(inventoryMapper, never()).lockForShip(anyLong(), anyLong(), anyInt());
        verify(inventoryMapper, never()).shipLockedOut(anyLong(), anyLong(), anyInt());
        verify(inventoryFlowMapper, never()).insert(any(InventoryFlow.class));
    }
}
