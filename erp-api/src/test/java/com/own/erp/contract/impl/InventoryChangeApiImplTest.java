package com.own.erp.contract.impl;

import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : InventoryChangeApiImpl 单测:契约命令 → InventoryFlow 字段逐一映射,委托唯一入口 change(#10)
 */
class InventoryChangeApiImplTest {

    private InventoryService inventoryService;
    private InventoryChangeApi inventoryChangeApi;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        inventoryChangeApi = new InventoryChangeApiImpl(inventoryService);
    }

    @Test
    void mapsCommandToFlowAndDelegates() {
        when(inventoryService.change(any(InventoryFlow.class))).thenReturn(66L);

        Long flowId = inventoryChangeApi.change(InventoryChangeCommand.builder()
                .skuId(1001L)
                .warehouseId(2L)
                .quantity(50)
                .flowType(InventoryConsts.FLOW_TYPE_IN_PURCHASE)
                .bizType("PURCHASE_INBOUND")
                .bizId(1L)
                .remark("入库单:RK001")
                .createdBy(7L)
                .build());

        assertEquals(66L, flowId);
        ArgumentCaptor<InventoryFlow> captor = ArgumentCaptor.forClass(InventoryFlow.class);
        verify(inventoryService).change(captor.capture());
        InventoryFlow flow = captor.getValue();
        assertEquals(1001L, flow.getSkuId());
        assertEquals(2L, flow.getWarehouseId());
        assertEquals(50, flow.getQuantity());
        assertEquals(InventoryConsts.FLOW_TYPE_IN_PURCHASE, flow.getFlowType());
        assertEquals("PURCHASE_INBOUND", flow.getBizType());
        assertEquals(1L, flow.getBizId());
        assertEquals("入库单:RK001", flow.getRemark());
        assertEquals(7L, flow.getCreatedBy());
    }
}
