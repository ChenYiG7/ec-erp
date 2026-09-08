package com.own.erp.inventory.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.inventory.entity.InventoryCostState;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryCostStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : InventoryCostService 单测(AIR:mock costStateMapper,不依赖数据库;#19③ 移动加权核心必测)。
 *     必测面:入库加权重算/缺价暂估(禁猜价)/出库结转当时加权价(快照回填流水)/退货·调整同价不动加权/
 *     不进账四类型零交互/账本结存不足拒绝/首建并发 DuplicateKey 回退
 */
class InventoryCostServiceTest {

    private InventoryCostStateMapper costStateMapper;
    private InventoryCostService costService;

    /** 账本:结存数量/金额/加权价 */
    private InventoryCostState state(int qty, String amount, String avg) {
        return InventoryCostState.builder()
                .skuId(1L).totalQty(qty)
                .totalAmount(amount == null ? BigDecimal.ZERO : new BigDecimal(amount))
                .avgCost(avg == null ? BigDecimal.ZERO : new BigDecimal(avg))
                .build();
    }

    private InventoryFlow flow(String type, int qty) {
        return InventoryFlow.builder().skuId(1L).warehouseId(2L).quantity(qty).flowType(type).build();
    }

    @BeforeEach
    void setUp() {
        costStateMapper = mock(InventoryCostStateMapper.class);
        costService = new InventoryCostService(costStateMapper);
    }

    @Nested
    class InPurchase {

        @Test
        void recomputeWeightedAvgAndSnapshotFlow() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(100, "1000.00", "10.00000000"));

            InventoryFlow f = flow("IN_PURCHASE", 100);
            f.setUnitCost(new BigDecimal("20"));
            costService.apply(f);

            // 加权 = (1000+2000)/200 = 15;流水快照回填
            assertEquals(new BigDecimal("15.00000000"), stateAfter().getAvgCost());
            assertEquals(200, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("3000.00").compareTo(stateAfter().getTotalAmount()));
            assertEquals(new BigDecimal("20"), f.getUnitCost());
            assertEquals(0, new BigDecimal("2000.00").compareTo(f.getCostAmount()));
        }

        @Test
        void missingPriceFallsBackToAvgCost() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(100, "1000.00", "10.00000000"));

            InventoryFlow f = flow("IN_PURCHASE", 10);
            costService.apply(f);

            // 缺价禁猜价:按当时加权价暂估,加权价不变
            assertEquals(0, new BigDecimal("10.00000000").compareTo(stateAfter().getAvgCost()));
            assertEquals(110, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("1100.00").compareTo(stateAfter().getTotalAmount()));
            assertEquals(0, new BigDecimal("10").compareTo(f.getUnitCost()));
        }

        @Test
        void firstInboundWithoutPriceRecordsZero() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(0, "0.00", "0.00000000"));

            InventoryFlow f = flow("IN_PURCHASE", 30);
            costService.apply(f);

            // 首次无价记 0(warn 已日志):数量守恒金额低估,账本不漂移
            assertEquals(30, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("0.00").compareTo(stateAfter().getTotalAmount()));
            assertEquals(0, BigDecimal.ZERO.compareTo(stateAfter().getAvgCost()));
            assertEquals(0, BigDecimal.ZERO.compareTo(f.getUnitCost()));
        }

        @Test
        void zeroBalanceKeepsLastAvgForEstimate() {
            // 结存清零保留末次价:下次缺价入库按末次价暂估(DDL 拍板)
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(0, "0.00", "12.50000000"));

            InventoryFlow f = flow("IN_PURCHASE", 8);
            costService.apply(f);

            assertEquals(0, new BigDecimal("12.5").compareTo(f.getUnitCost()));
            assertEquals(0, new BigDecimal("100.00").compareTo(f.getCostAmount()));
        }
    }

    @Nested
    class OutShip {

        @Test
        void settlesAtCurrentAvgAndUpdatesLedger() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(200, "3000.00", "15.00000000"));

            InventoryFlow f = flow("OUT_SHIP", -50);
            costService.apply(f);

            // 结转价=当时加权 15,金额带符号负数;加权价不动
            assertEquals(0, new BigDecimal("15").compareTo(f.getUnitCost()));
            assertEquals(0, new BigDecimal("-750.00").compareTo(f.getCostAmount()));
            assertEquals(150, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("2250.00").compareTo(stateAfter().getTotalAmount()));
            assertEquals(0, new BigDecimal("15.00000000").compareTo(stateAfter().getAvgCost()));
        }

        @Test
        void rejectsWhenLedgerInsufficient() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(10, "150.00", "15.00000000"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> costService.apply(flow("OUT_SHIP", -50)));
            assertTrue(ex.getMessage().contains("账本结存为负"));
            verify(costStateMapper, never()).updateById(any(InventoryCostState.class));
        }
    }

    @Nested
    class ReturnAndAdjust {

        @Test
        void inReturnSettlesAtAvgWithoutRepricing() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(150, "2250.00", "15.00000000"));

            InventoryFlow f = flow("IN_RETURN", 5);
            costService.apply(f);

            assertEquals(0, new BigDecimal("15").compareTo(f.getUnitCost()));
            assertEquals(0, new BigDecimal("75.00").compareTo(f.getCostAmount()));
            assertEquals(155, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("2325.00").compareTo(stateAfter().getTotalAmount()));
            assertEquals(0, new BigDecimal("15.00000000").compareTo(stateAfter().getAvgCost()));
        }

        @Test
        void adjustNegativeSettlesSignedAmount() {
            when(costStateMapper.selectBySkuIdForUpdate(1L))
                    .thenReturn(state(155, "2325.00", "15.00000000"));

            InventoryFlow f = flow("ADJUST", -3);
            costService.apply(f);

            assertEquals(0, new BigDecimal("-45.00").compareTo(f.getCostAmount()));
            assertEquals(152, stateAfter().getTotalQty());
            assertEquals(0, new BigDecimal("2280.00").compareTo(stateAfter().getTotalAmount()));
        }
    }

    @Nested
    class NonCostTypes {

        @Test
        void transitLockAndTransferLegsBypassLedger() {
            for (String type : new String[]{"IN_TRANSIT", "LOCK_SHIP", "TRANSFER_OUT", "TRANSFER_IN"}) {
                InventoryFlow f = flow(type, 10);
                costService.apply(f);
                assertNull(f.getUnitCost(), type);
                assertNull(f.getCostAmount(), type);
            }
            verify(costStateMapper, never()).selectBySkuIdForUpdate(any());
            verify(costStateMapper, never()).updateById(any(InventoryCostState.class));
        }
    }

    @Nested
    class StateInit {

        @Test
        void firstTouchCreatesZeroRow() {
            when(costStateMapper.selectBySkuIdForUpdate(1L)).thenReturn(null);

            InventoryFlow f = flow("IN_PURCHASE", 10);
            f.setUnitCost(new BigDecimal("7"));
            costService.apply(f);

            verify(costStateMapper).insert(any(InventoryCostState.class));
            assertEquals(10, stateAfter().getTotalQty());
        }

        @Test
        void duplicateKeyOnInitFallsBackToReload() {
            InventoryCostState existing = state(100, "1000.00", "10.00000000");
            when(costStateMapper.selectBySkuIdForUpdate(1L)).thenReturn(null).thenReturn(existing);
            when(costStateMapper.insert(any(InventoryCostState.class)))
                    .thenThrow(new DuplicateKeyException("uk_sku", null));

            InventoryFlow f = flow("IN_PURCHASE", 10);
            f.setUnitCost(new BigDecimal("20"));
            costService.apply(f);

            // 回退读到并发他方已建行,在其基础上推进
            assertEquals(110, existing.getTotalQty());
            verify(costStateMapper, times(2)).selectBySkuIdForUpdate(1L);
        }
    }

    /** apply 后经 updateById 持久化的 state 实体(账本推进断言出口) */
    private InventoryCostState stateAfter() {
        ArgumentCaptor<InventoryCostState> captor = ArgumentCaptor.forClass(InventoryCostState.class);
        verify(costStateMapper, atLeastOnce()).updateById(captor.capture());
        return captor.getValue();
    }
}
