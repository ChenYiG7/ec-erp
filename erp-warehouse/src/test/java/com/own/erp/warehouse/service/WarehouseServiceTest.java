package com.own.erp.warehouse.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.warehouse.entity.Warehouse;
import com.own.erp.warehouse.mapper.WarehouseMapper;
import com.own.erp.warehouse.request.query.WarehouseQuery;
import com.own.erp.warehouse.request.command.WarehouseSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : WarehouseService 单测(AIR:mock Mapper 与契约接口,不依赖数据库);
 *     #7 收口:删除前库存/采购引用拦截经 WarehouseApi 计数
 */
class WarehouseServiceTest {

    private WarehouseMapper warehouseMapper;
    private WarehouseApi warehouseApi;
    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseMapper = mock(WarehouseMapper.class);
        warehouseApi = mock(WarehouseApi.class);
        warehouseService = new WarehouseService(warehouseMapper, warehouseApi);
    }

    @Test
    void saveMapsRequestAndInserts() {
        WarehouseSaveRequest request = WarehouseSaveRequest.builder().build();
        warehouseService.save(request);
        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseMapper).insert(captor.capture());
    }

    @Test
    void updateSetsIdFromPathAndDelegates() {
        warehouseService.update(9L, WarehouseSaveRequest.builder().build());
        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseMapper).updateById(captor.capture());
        assertEquals(9L, captor.getValue().getId());
    }

    @Test
    void deleteRejectedWhenReferencedByInventoryOrPurchase() {
        when(warehouseApi.countWarehouseRefs(1L)).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> warehouseService.delete(1L));

        assertTrue(e.getMessage().contains("禁删"));
        verify(warehouseMapper, never()).deleteById(1L);
    }

    @Test
    void deleteDelegatesToMapperWhenUnreferenced() {
        when(warehouseApi.countWarehouseRefs(1L)).thenReturn(0L);

        warehouseService.delete(1L);

        verify(warehouseMapper).deleteById(1L);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        when(warehouseMapper.selectById(1L)).thenReturn(warehouse);
        assertEquals(1L, warehouseService.getById(1L).id());
        assertNull(warehouseService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        Page<Warehouse> page = new Page<>(1, 10);
        page.setRecords(List.of(warehouse));
        doReturn(page).when(warehouseMapper).selectPage(any(), any());
        assertEquals(2L, warehouseService.page(new WarehouseQuery()).getRecords().get(0).id());
    }
}
