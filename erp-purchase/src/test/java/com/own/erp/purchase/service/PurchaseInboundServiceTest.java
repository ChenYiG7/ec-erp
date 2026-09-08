package com.own.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.purchase.constant.PurchaseConsts;
import com.own.erp.purchase.entity.PurchaseInbound;
import com.own.erp.purchase.entity.PurchaseInboundItem;
import com.own.erp.purchase.entity.PurchaseOrder;
import com.own.erp.purchase.entity.PurchaseOrderItem;
import com.own.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.own.erp.purchase.mapper.PurchaseInboundMapper;
import com.own.erp.purchase.request.command.PurchaseInboundItemSaveRequest;
import com.own.erp.purchase.request.command.PurchaseInboundSaveRequest;
import com.own.erp.purchase.request.query.PurchaseInboundQuery;
import com.own.erp.purchase.response.PurchaseInboundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : PurchaseInboundService 单测(AIR:mock Mapper/同域服务/契约接口,不依赖数据库)。
 *     必测面(docs/07 §10):库存 change 必经(核销链路)、状态机守卫、超收拒绝(#10)
 */
class PurchaseInboundServiceTest {

    private PurchaseInboundMapper purchaseInboundMapper;
    private PurchaseInboundItemMapper purchaseInboundItemMapper;
    private PurchaseOrderService purchaseOrderService;
    private InventoryChangeApi inventoryChangeApi;
    private CurrentUserApi currentUserApi;
    private PurchaseInboundService purchaseInboundService;

    /** 可收货采购单 88(仓库 2)与其明细 501(SKU 1001,采购 100 已收 0) */
    private static final Long PO_ID = 88L;

    @BeforeEach
    void setUp() {
        purchaseInboundMapper = mock(PurchaseInboundMapper.class);
        purchaseInboundItemMapper = mock(PurchaseInboundItemMapper.class);
        purchaseOrderService = mock(PurchaseOrderService.class);
        inventoryChangeApi = mock(InventoryChangeApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        purchaseInboundService = new PurchaseInboundService(purchaseInboundMapper, purchaseInboundItemMapper,
                purchaseOrderService, inventoryChangeApi, currentUserApi);
        // createdBy 服务端按 SecurityContext 回填(CurrentUserApi,#10 遗留收口)
        when(currentUserApi.currentUserId()).thenReturn(9L);
    }

    private PurchaseOrder receivableOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(PO_ID);
        order.setWarehouseId(2L);
        order.setStatus(PurchaseConsts.PO_AUDITED);
        return order;
    }

    private PurchaseOrderItem poItem() {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(501L);
        item.setSkuId(1001L);
        item.setQuantity(100);
        item.setArrivedQty(0);
        return item;
    }

    private PurchaseInboundSaveRequest validRequest() {
        return PurchaseInboundSaveRequest.builder()
                .inboundNo("RK001")
                .poId(PO_ID)
                .items(List.of(PurchaseInboundItemSaveRequest.builder().poItemId(501L).inboundQty(50).build()))
                .build();
    }

    private PurchaseInbound pendingInbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("RK001");
        inbound.setPoId(PO_ID);
        inbound.setWarehouseId(2L);
        inbound.setStatus(PurchaseConsts.INBOUND_PENDING);
        inbound.setCreatedBy(7L);
        return inbound;
    }

    @Nested
    class Save {

        @Test
        void forcesPendingAndWarehouseFromPoThenInsertsLines() {
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));

            purchaseInboundService.save(validRequest());

            ArgumentCaptor<PurchaseInbound> inboundCaptor = ArgumentCaptor.forClass(PurchaseInbound.class);
            verify(purchaseInboundMapper).insert(inboundCaptor.capture());
            assertEquals(PurchaseConsts.INBOUND_PENDING, inboundCaptor.getValue().getStatus());
            // 入库仓锁采购单收货仓,不收客户端值
            assertEquals(2L, inboundCaptor.getValue().getWarehouseId());
            // createdBy 服务端按 SecurityContext 回填,不收客户端值
            assertEquals(9L, inboundCaptor.getValue().getCreatedBy());

            ArgumentCaptor<PurchaseInboundItem> lineCaptor = ArgumentCaptor.forClass(PurchaseInboundItem.class);
            verify(purchaseInboundItemMapper).insert(lineCaptor.capture());
            // skuId 服务端按采购明细回填
            assertEquals(1001L, lineCaptor.getValue().getSkuId());
            assertEquals(501L, lineCaptor.getValue().getPoItemId());
            assertEquals(50, lineCaptor.getValue().getInboundQty());
        }

        @Test
        void rejectsWhenPoNotReceivable() {
            when(purchaseOrderService.requireReceivable(PO_ID))
                    .thenThrow(new BusinessException("采购单当前不可入库"));
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.save(validRequest()))
                    .getMessage().contains("不可入库"));
            verify(purchaseInboundMapper, never()).insert(any(PurchaseInbound.class));
        }

        @Test
        void rejectsForeignPoItem() {
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));
            PurchaseInboundSaveRequest request = PurchaseInboundSaveRequest.builder()
                    .inboundNo("RK001").poId(PO_ID)
                    .items(List.of(PurchaseInboundItemSaveRequest.builder().poItemId(999L).inboundQty(1).build()))
                    .build();
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.save(request))
                    .getMessage().contains("不属于该采购单"));
        }

        @Test
        void rejectsOverRemaining() {
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));
            PurchaseInboundSaveRequest request = PurchaseInboundSaveRequest.builder()
                    .inboundNo("RK001").poId(PO_ID)
                    .items(List.of(PurchaseInboundItemSaveRequest.builder().poItemId(501L).inboundQty(101).build()))
                    .build();
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.save(request))
                    .getMessage().contains("超出剩余未收量"));
        }

        @Test
        void rejectsDuplicatePoItemLines() {
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));
            PurchaseInboundSaveRequest request = PurchaseInboundSaveRequest.builder()
                    .inboundNo("RK001").poId(PO_ID)
                    .items(List.of(
                            PurchaseInboundItemSaveRequest.builder().poItemId(501L).inboundQty(10).build(),
                            PurchaseInboundItemSaveRequest.builder().poItemId(501L).inboundQty(20).build()))
                    .build();
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.save(request))
                    .getMessage().contains("重复"));
        }

        @Test
        void translatesDuplicateInboundNoToFriendlyError() {
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));
            when(purchaseInboundMapper.insert(any(PurchaseInbound.class)))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_inbound_no"));
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.save(validRequest()))
                    .getMessage().contains("入库单号已存在"));
        }
    }

    @Nested
    class Update {

        @Test
        void replacesLinesForPending() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(receivableOrder());
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem()));

            purchaseInboundService.update(1L, validRequest());

            verify(purchaseInboundMapper).updateById(any(PurchaseInbound.class));
            verify(purchaseInboundItemMapper).delete(any());
            verify(purchaseInboundItemMapper).insert(any(PurchaseInboundItem.class));
        }

        @Test
        void rejectsNonPending() {
            PurchaseInbound received = pendingInbound();
            received.setStatus(PurchaseConsts.INBOUND_RECEIVED);
            when(purchaseInboundMapper.selectById(1L)).thenReturn(received);
            assertTrue(assertThrows(BusinessException.class,
                    () -> purchaseInboundService.update(1L, validRequest())).getMessage().contains("仅待入库状态可修改"));
        }

        @Test
        void rejectsPoChange() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            // 归属采购单与在库单不一致即拒:入库单不允许换绑采购单
            PurchaseOrder other = receivableOrder();
            other.setId(99L);
            when(purchaseOrderService.requireReceivable(PO_ID)).thenReturn(other);
            assertTrue(assertThrows(BusinessException.class,
                    () -> purchaseInboundService.update(1L, validRequest())).getMessage().contains("不允许变更关联采购单"));
        }
    }

    @Nested
    class Confirm {

        @Test
        void movesInventoryViaContractAndDelegatesReceive() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            when(purchaseInboundMapper.casStatus(1L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_RECEIVED))
                    .thenReturn(1);
            PurchaseInboundItem line = PurchaseInboundItem.builder()
                    .poItemId(501L).skuId(1001L).inboundQty(50).build();
            when(purchaseInboundItemMapper.selectList(any())).thenReturn(List.of(line));
            // 采购行单价随命令传递进移动加权成本账(#19③)
            PurchaseOrderItem poItem = poItem();
            poItem.setPurchasePrice(new java.math.BigDecimal("10.50"));
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem));

            purchaseInboundService.confirm(1L);

            ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
            verify(inventoryChangeApi).change(captor.capture());
            InventoryChangeCommand command = captor.getValue();
            assertEquals(1001L, command.skuId());
            assertEquals(2L, command.warehouseId());
            assertEquals(50, command.quantity());
            assertEquals(InventoryConsts.FLOW_TYPE_IN_PURCHASE, command.flowType());
            assertEquals(PurchaseConsts.BIZ_TYPE_PURCHASE_INBOUND, command.bizType());
            assertEquals(1L, command.bizId());
            assertEquals(7L, command.createdBy());
            assertTrue(command.remark().contains("RK001"));
            assertEquals(new java.math.BigDecimal("10.50"), command.unitCost());

            verify(purchaseOrderService).receiveQuantities(eq(PO_ID), argThat(lines -> lines.size() == 1
                    && lines.get(0).poItemId().equals(501L) && lines.get(0).quantity().equals(50)));
        }

        @Test
        void rejectsWhenCasMiss() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            when(purchaseInboundMapper.casStatus(1L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_RECEIVED))
                    .thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.confirm(1L))
                    .getMessage().contains("确认失败"));
            verify(inventoryChangeApi, never()).change(any());
        }

        @Test
        void rejectsEmptyLines() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            when(purchaseInboundMapper.casStatus(1L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_RECEIVED))
                    .thenReturn(1);
            when(purchaseInboundItemMapper.selectList(any())).thenReturn(List.of());
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.confirm(1L))
                    .getMessage().contains("无明细"));
            verify(inventoryChangeApi, never()).change(any());
        }

        @Test
        void oneChangePerLine() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            when(purchaseInboundMapper.casStatus(1L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_RECEIVED))
                    .thenReturn(1);
            when(purchaseInboundItemMapper.selectList(any())).thenReturn(List.of(
                    PurchaseInboundItem.builder().poItemId(501L).skuId(1001L).inboundQty(50).build(),
                    PurchaseInboundItem.builder().poItemId(502L).skuId(1002L).inboundQty(30).build()));
            PurchaseOrderItem poItem = poItem();
            PurchaseOrderItem otherItem = poItem();
            otherItem.setId(502L);
            otherItem.setSkuId(1002L);
            when(purchaseOrderService.listItemEntities(PO_ID)).thenReturn(List.of(poItem, otherItem));

            purchaseInboundService.confirm(1L);

            verify(inventoryChangeApi, times(2)).change(any());
        }
    }

    @Nested
    class CancelAndDelete {

        @Test
        void cancelSucceedsOnCasHitAndFailsOnMiss() {
            when(purchaseInboundMapper.casStatus(1L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_CANCELLED))
                    .thenReturn(1);
            purchaseInboundService.cancel(1L);

            when(purchaseInboundMapper.casStatus(2L, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_CANCELLED))
                    .thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.cancel(2L))
                    .getMessage().contains("取消失败"));
        }

        @Test
        void rejectsDeleteWhenReceived() {
            PurchaseInbound received = pendingInbound();
            received.setStatus(PurchaseConsts.INBOUND_RECEIVED);
            when(purchaseInboundMapper.selectById(1L)).thenReturn(received);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseInboundService.delete(1L))
                    .getMessage().contains("禁止删除"));
            verify(purchaseInboundMapper, never()).deleteById(anyLong());
        }

        @Test
        void removesPendingWithLines() {
            when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
            purchaseInboundService.delete(1L);
            verify(purchaseInboundItemMapper).delete(any());
            verify(purchaseInboundMapper).deleteById(1L);
        }
    }

    @Test
    void getByIdCarriesItemsAndReturnsNullWhenMissing() {
        when(purchaseInboundMapper.selectById(1L)).thenReturn(pendingInbound());
        when(purchaseInboundItemMapper.selectList(any())).thenReturn(List.of(
                PurchaseInboundItem.builder().poItemId(501L).skuId(1001L).inboundQty(50).build()));
        assertEquals(1, purchaseInboundService.getById(1L).items().size());
        assertNull(purchaseInboundService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponseWithoutItems() {
        Page<PurchaseInbound> page = new Page<>(1, 10);
        page.setRecords(List.of(pendingInbound()));
        doReturn(page).when(purchaseInboundMapper).selectPage(any(), any());
        Page<PurchaseInboundResponse> result = purchaseInboundService.page(new PurchaseInboundQuery());
        assertEquals("RK001", result.getRecords().get(0).inboundNo());
        assertNull(result.getRecords().get(0).items());
    }
}
