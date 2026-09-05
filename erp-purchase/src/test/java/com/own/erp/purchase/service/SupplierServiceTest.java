package com.own.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.purchase.entity.Supplier;
import com.own.erp.purchase.mapper.SupplierMapper;
import com.own.erp.purchase.request.query.SupplierQuery;
import com.own.erp.purchase.request.command.SupplierSaveRequest;
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
 * @Description : SupplierService 单测(AIR:mock Mapper,不依赖数据库)
 */
class SupplierServiceTest {

    private SupplierMapper supplierMapper;
    private PurchaseOrderService purchaseOrderService;
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        supplierMapper = mock(SupplierMapper.class);
        purchaseOrderService = mock(PurchaseOrderService.class);
        supplierService = new SupplierService(supplierMapper, purchaseOrderService);
    }

    @Test
    void saveMapsRequestAndInserts() {
        SupplierSaveRequest request = SupplierSaveRequest.builder().name("冒烟供应商").build();
        supplierService.save(request);
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierMapper).insert(captor.capture());
        assertEquals("冒烟供应商", captor.getValue().getName());
    }

    @Test
    void updateSetsIdFromPathAndDelegates() {
        supplierService.update(9L, SupplierSaveRequest.builder().build());
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierMapper).updateById(captor.capture());
        assertEquals(9L, captor.getValue().getId());
    }

    @Test
    void deleteRemovesSupplierWithoutPurchaseOrders() {
        Supplier exist = new Supplier();
        exist.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(exist);
        when(purchaseOrderService.countBySupplierId(1L)).thenReturn(0L);

        supplierService.delete(1L);
        verify(supplierMapper).deleteById(1L);
    }

    @Test
    void deleteRejectsWhenPurchaseOrderExists() {
        Supplier exist = new Supplier();
        exist.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(exist);
        when(purchaseOrderService.countBySupplierId(1L)).thenReturn(2L);

        assertTrue(assertThrows(BusinessException.class, () -> supplierService.delete(1L))
                .getMessage().contains("禁删除"));
        verify(supplierMapper, never()).deleteById(1L);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        when(supplierMapper.selectById(1L)).thenReturn(supplier);
        assertEquals(1L, supplierService.getById(1L).id());
        assertNull(supplierService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Supplier supplier = new Supplier();
        supplier.setId(2L);
        Page<Supplier> page = new Page<>(1, 10);
        page.setRecords(List.of(supplier));
        doReturn(page).when(supplierMapper).selectPage(any(), any());
        assertEquals(2L, supplierService.page(new SupplierQuery()).getRecords().get(0).id());
    }
}
