package com.own.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryFlowMapper;
import com.own.erp.inventory.request.query.InventoryFlowQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : InventoryFlowService 单测(AIR:mock Mapper,不依赖数据库);写入路径在 InventoryServiceTest 覆盖
 */
class InventoryFlowServiceTest {

    private InventoryFlowMapper inventoryFlowMapper;
    private InventoryFlowService inventoryFlowService;

    @BeforeEach
    void setUp() {
        inventoryFlowMapper = mock(InventoryFlowMapper.class);
        inventoryFlowService = new InventoryFlowService(inventoryFlowMapper);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        InventoryFlow flow = new InventoryFlow();
        flow.setId(1L);
        when(inventoryFlowMapper.selectById(1L)).thenReturn(flow);
        assertEquals(1L, inventoryFlowService.getById(1L).id());
        assertNull(inventoryFlowService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        InventoryFlow flow = new InventoryFlow();
        flow.setId(2L);
        Page<InventoryFlow> page = new Page<>(1, 10);
        page.setRecords(List.of(flow));
        doReturn(page).when(inventoryFlowMapper).selectPage(any(), any());
        assertEquals(2L, inventoryFlowService.page(new InventoryFlowQuery()).getRecords().get(0).id());
    }
}
