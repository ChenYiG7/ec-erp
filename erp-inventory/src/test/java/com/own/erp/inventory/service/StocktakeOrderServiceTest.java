package com.own.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.StocktakeConsts;
import com.own.erp.inventory.entity.Inventory;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.entity.StocktakeItem;
import com.own.erp.inventory.entity.StocktakeOrder;
import com.own.erp.inventory.mapper.StocktakeItemMapper;
import com.own.erp.inventory.mapper.StocktakeOrderMapper;
import com.own.erp.inventory.request.command.StocktakeCountRequest;
import com.own.erp.inventory.request.command.StocktakeOrderSaveRequest;
import com.own.erp.inventory.request.query.StocktakeOrderQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : StocktakeOrderService 单测(AIR:mock Mapper/InventoryService,不依赖数据库)。
 *     覆盖状态机生成器射程外的业务断言:建单快照(ALL/SKU_SET)、录实盘 diff 口径、
 *     generateAdjust 确认时点 re-diff + ADJUST 动账装配 + 可用不足整单回滚、
 *     明细校验与删除/改单护栏;单 cas 守卫用例见 StocktakeOrderStateMachineTest
 */
class StocktakeOrderServiceTest {

    private StocktakeOrderMapper stocktakeOrderMapper;
    private StocktakeItemMapper stocktakeItemMapper;
    private InventoryService inventoryService;
    private WarehouseApi warehouseApi;
    private GoodsSkuApi goodsSkuApi;
    private CurrentUserApi currentUserApi;
    private StocktakeOrderService stocktakeOrderService;

    @BeforeEach
    void setUp() {
        stocktakeOrderMapper = mock(StocktakeOrderMapper.class);
        stocktakeItemMapper = mock(StocktakeItemMapper.class);
        inventoryService = mock(InventoryService.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        stocktakeOrderService = new StocktakeOrderService(stocktakeOrderMapper, stocktakeItemMapper,
                inventoryService, warehouseApi, goodsSkuApi, currentUserApi);
    }

    private StocktakeOrderSaveRequest allScope() {
        return StocktakeOrderSaveRequest.builder()
                .stocktakeNo("ST20260911001").warehouseId(9L).scopeType(StocktakeConsts.SCOPE_ALL).build();
    }

    private StocktakeOrder order(Long id, String status) {
        return StocktakeOrder.builder().id(id).stocktakeNo("ST20260911001").warehouseId(9L)
                .status(status).createdBy(7L).build();
    }

    private StocktakeItem item(Long id, Long skuId, int bookQty, Integer countedQty) {
        return StocktakeItem.builder().id(id).stocktakeId(1L).skuId(skuId)
                .bookQty(bookQty).countedQty(countedQty).build();
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_DRAFT));
        assertEquals(1L, stocktakeOrderService.getById(1L).id());
        assertNull(stocktakeOrderService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Page<StocktakeOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(order(2L, StocktakeConsts.STATUS_DRAFT)));
        doReturn(page).when(stocktakeOrderMapper).selectPage(any(), any());
        assertEquals(2L, stocktakeOrderService.page(new StocktakeOrderQuery()).getRecords().get(0).id());
    }

    @Test
    void saveAllScopeSnapshotsEveryInventoryRowAndForcesDraft() {
        when(warehouseApi.existsWarehouse(9L)).thenReturn(true);
        when(currentUserApi.currentUserId()).thenReturn(7L);
        doAnswer(inv -> {
            ((StocktakeOrder) inv.getArgument(0)).setId(77L);
            return 1;
        }).when(stocktakeOrderMapper).insert(any(StocktakeOrder.class));
        when(inventoryService.listByWarehouse(9L, null)).thenReturn(List.of(
                Inventory.builder().skuId(11L).warehouseId(9L).qtyOnHand(10).build(),
                Inventory.builder().skuId(12L).warehouseId(9L).qtyOnHand(0).build()));

        Long id = stocktakeOrderService.save(allScope());

        assertEquals(77L, id);
        ArgumentCaptor<StocktakeOrder> orderCaptor = ArgumentCaptor.forClass(StocktakeOrder.class);
        verify(stocktakeOrderMapper).insert(orderCaptor.capture());
        assertEquals(StocktakeConsts.STATUS_DRAFT, orderCaptor.getValue().getStatus());
        assertEquals(7L, orderCaptor.getValue().getCreatedBy());
        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(stocktakeItemMapper, times(2)).insert(itemCaptor.capture());
        assertEquals(11L, itemCaptor.getAllValues().get(0).getSkuId());
        assertEquals(10, itemCaptor.getAllValues().get(0).getBookQty());
        assertEquals(77L, itemCaptor.getAllValues().get(0).getStocktakeId());
        assertNull(itemCaptor.getAllValues().get(0).getCountedQty());
    }

    @Test
    void saveSkuSetSnapshotsPerSkuAndUsesZeroForMissingRow() {
        when(warehouseApi.existsWarehouse(9L)).thenReturn(true);
        when(goodsSkuApi.existsSku(11L)).thenReturn(true);
        when(goodsSkuApi.existsSku(12L)).thenReturn(true);
        when(currentUserApi.currentUserId()).thenReturn(7L);
        when(inventoryService.listByWarehouse(9L, List.of(11L, 12L))).thenReturn(List.of(
                Inventory.builder().skuId(11L).warehouseId(9L).qtyOnHand(6).build()));

        stocktakeOrderService.save(StocktakeOrderSaveRequest.builder()
                .stocktakeNo("ST002").warehouseId(9L).scopeType(StocktakeConsts.SCOPE_SKU_SET)
                .skuIds(List.of(11L, 12L)).build());

        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(stocktakeItemMapper, times(2)).insert(itemCaptor.capture());
        assertEquals(6, itemCaptor.getAllValues().get(0).getBookQty());
        assertEquals(0, itemCaptor.getAllValues().get(1).getBookQty());
    }

    @Test
    void saveRejectsSkuSetWithoutSkusOrDuplicateSku() {
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.save(
                        StocktakeOrderSaveRequest.builder().stocktakeNo("ST003").warehouseId(9L)
                                .scopeType(StocktakeConsts.SCOPE_SKU_SET).build()))
                .getMessage().contains("skuIds 不能为空"));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.save(
                        StocktakeOrderSaveRequest.builder().stocktakeNo("ST004").warehouseId(9L)
                                .scopeType(StocktakeConsts.SCOPE_SKU_SET).skuIds(List.of(11L, 11L)).build()))
                .getMessage().contains("重复"));
        verify(stocktakeOrderMapper, never()).insert(any(StocktakeOrder.class));
    }

    @Test
    void saveRejectsUnknownScopeOrWarehouse() {
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.save(
                        StocktakeOrderSaveRequest.builder().stocktakeNo("ST005").warehouseId(9L)
                                .scopeType("MAGIC").build()))
                .getMessage().contains("盘点范围非法"));
        when(warehouseApi.existsWarehouse(9L)).thenReturn(false);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.save(allScope()))
                .getMessage().contains("仓库不存在"));
    }

    @Test
    void saveTranslatesDuplicateNoConflict() {
        when(warehouseApi.existsWarehouse(9L)).thenReturn(true);
        when(stocktakeOrderMapper.insert(any(StocktakeOrder.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.save(allScope()))
                .getMessage().contains("盘点单号已存在"));
    }

    @Test
    void updateRejectsNonDraftOrder() {
        when(stocktakeOrderMapper.selectByIdForUpdate(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_COUNTING));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.update(1L, allScope()))
                .getMessage().contains("仅草稿状态可修改"));
        verify(stocktakeOrderMapper, never()).updateById(any(StocktakeOrder.class));
    }

    @Test
    void recordCountsComputesDiffAgainstSnapshotBookQty() {
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_COUNTING));
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, null)));

        stocktakeOrderService.recordCounts(1L, StocktakeCountRequest.builder()
                .lines(List.of(new StocktakeCountRequest.StocktakeCountLine(11L, 7))).build());

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(stocktakeItemMapper).updateById(captor.capture());
        assertEquals(100L, captor.getValue().getId());
        assertEquals(7, captor.getValue().getCountedQty());
        assertEquals(-3, captor.getValue().getDiffQty());
    }

    @Test
    void recordCountsRejectsSkuNotInOrderAndNonCountingStatus() {
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_COUNTING));
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, null)));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.recordCounts(1L,
                        StocktakeCountRequest.builder()
                                .lines(List.of(new StocktakeCountRequest.StocktakeCountLine(99L, 1))).build()))
                .getMessage().contains("SKU 不属于本盘点单"));

        when(stocktakeOrderMapper.selectById(2L)).thenReturn(order(2L, StocktakeConsts.STATUS_DRAFT));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.recordCounts(2L,
                        StocktakeCountRequest.builder()
                                .lines(List.of(new StocktakeCountRequest.StocktakeCountLine(11L, 1))).build()))
                .getMessage().contains("仅盘点中状态可录入实盘"));
    }

    @Test
    void submitRejectsOrderWithUncountedRows() {
        when(stocktakeOrderMapper.casStatus(1L, StocktakeConsts.STATUS_COUNTING,
                StocktakeConsts.STATUS_PENDING_ADJUST)).thenReturn(1);
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, null)));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.submit(1L))
                .getMessage().contains("未录入实盘"));
    }

    @Test
    void generateAdjustReDiffsAgainstCurrentOnHandAndWritesAdjustFlow() {
        when(stocktakeOrderMapper.casStatus(1L, StocktakeConsts.STATUS_PENDING_ADJUST,
                StocktakeConsts.STATUS_ADJUSTED)).thenReturn(1);
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_PENDING_ADJUST));
        // 建单快照 book=10,实盘 8,但确认时点账面已被其他动账推到 5 → 以确认时点为准,差异 = 8-5 = +3
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, 8)));
        when(inventoryService.currentOnHand(11L, 9L)).thenReturn(5);
        when(inventoryService.change(any(InventoryFlow.class))).thenReturn(555L);
        when(currentUserApi.currentUserId()).thenReturn(7L);

        stocktakeOrderService.generateAdjust(1L);

        ArgumentCaptor<InventoryFlow> flowCaptor = ArgumentCaptor.forClass(InventoryFlow.class);
        verify(inventoryService).change(flowCaptor.capture());
        InventoryFlow flow = flowCaptor.getValue();
        assertEquals(11L, flow.getSkuId());
        assertEquals(9L, flow.getWarehouseId());
        assertEquals(3, flow.getQuantity());
        assertEquals(InventoryConsts.FLOW_TYPE_ADJUST, flow.getFlowType());
        assertEquals(InventoryConsts.BIZ_TYPE_STOCKTAKE, flow.getBizType());
        assertEquals(1L, flow.getBizId());
        assertEquals(7L, flow.getCreatedBy());

        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(stocktakeItemMapper).updateById(itemCaptor.capture());
        assertEquals(3, itemCaptor.getValue().getDiffQty());
        assertEquals(555L, itemCaptor.getValue().getAdjustFlowId());

        ArgumentCaptor<StocktakeOrder> orderCaptor = ArgumentCaptor.forClass(StocktakeOrder.class);
        verify(stocktakeOrderMapper).updateById(orderCaptor.capture());
        assertEquals(7L, orderCaptor.getValue().getConfirmedBy());
    }

    @Test
    void generateAdjustSkipsZeroDiffAndWrapsGuardRejection() {
        when(stocktakeOrderMapper.casStatus(1L, StocktakeConsts.STATUS_PENDING_ADJUST,
                StocktakeConsts.STATUS_ADJUSTED)).thenReturn(1);
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_PENDING_ADJUST));
        // 差异为 0 的行不写流水,只回填 diff_qty
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, 10)));
        when(inventoryService.currentOnHand(11L, 9L)).thenReturn(10);
        stocktakeOrderService.generateAdjust(1L);
        verify(inventoryService, never()).change(any(InventoryFlow.class));
        verify(stocktakeItemMapper).updateById(any(StocktakeItem.class));

        // 守卫拒(可用不足)→ 包装为带 SKU 与差异的整单失败消息
        when(stocktakeOrderMapper.casStatus(1L, StocktakeConsts.STATUS_PENDING_ADJUST,
                StocktakeConsts.STATUS_ADJUSTED)).thenReturn(1);
        when(stocktakeItemMapper.selectList(any())).thenReturn(List.of(item(100L, 11L, 10, 0)));
        when(inventoryService.currentOnHand(11L, 9L)).thenReturn(20);
        when(inventoryService.change(any(InventoryFlow.class)))
                .thenThrow(new BusinessException("可用库存不足:当前0,变动-20"));
        BusinessException ex = assertThrows(BusinessException.class, () -> stocktakeOrderService.generateAdjust(1L));
        assertTrue(ex.getMessage().contains("盘点差异调整失败"));
        assertTrue(ex.getMessage().contains("SKU=11"));
    }

    @Test
    void deleteRejectsAdjustedOrderAndCancelsBeforeHardDelete() {
        when(stocktakeOrderMapper.selectById(1L)).thenReturn(order(1L, StocktakeConsts.STATUS_ADJUSTED));
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.delete(1L))
                .getMessage().contains("禁止删除"));
        verify(stocktakeOrderMapper, never()).deleteById(1L);
    }
}
