package com.own.erp.system.service;

import com.own.erp.system.mapper.SysUserShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SysUserShopService 单测(#27①,AIR:mock Mapper 不依赖数据库):
 *     授权集查询委托、全量重绑先删后插顺序、入参去重、null 传入只删不插
 */
class SysUserShopServiceTest {

    private SysUserShopMapper userShopMapper;
    private SysUserShopService userShopService;

    @BeforeEach
    void setUp() {
        userShopMapper = mock(SysUserShopMapper.class);
        userShopService = new SysUserShopService(userShopMapper);
    }

    @Test
    void listShopIdsDelegatesToMapper() {
        when(userShopMapper.selectShopIdsByUserId(9L)).thenReturn(List.of(2L, 5L));
        assertEquals(List.of(2L, 5L), userShopService.listShopIdsByUserId(9L));
    }

    @Test
    void assignRebindsDeleteFirstThenInsert() {
        userShopService.assignShopsToUser(9L, List.of(2L, 5L));

        InOrder inOrder = inOrder(userShopMapper);
        inOrder.verify(userShopMapper).deleteByUserId(9L);
        inOrder.verify(userShopMapper).insert(9L, 2L);
        inOrder.verify(userShopMapper).insert(9L, 5L);
    }

    @Test
    void assignDeduplicatesShopIds() {
        userShopService.assignShopsToUser(9L, List.of(2L, 2L, 5L));

        verify(userShopMapper).deleteByUserId(9L);
        verify(userShopMapper).insert(9L, 2L);
        verify(userShopMapper).insert(9L, 5L);
        verifyNoMoreInteractions(userShopMapper);
    }

    @Test
    void assignWithNullClearsAllBindings() {
        userShopService.assignShopsToUser(9L, null);

        verify(userShopMapper).deleteByUserId(9L);
        verify(userShopMapper, never()).insert(anyLong(), anyLong());
    }
}
