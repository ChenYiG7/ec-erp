package com.own.erp.fulfill.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.fulfill.constant.FbaConsts;
import com.own.erp.fulfill.entity.FbaShipment;
import com.own.erp.fulfill.mapper.FbaBoxItemMapper;
import com.own.erp.fulfill.mapper.FbaBoxMapper;
import com.own.erp.fulfill.mapper.FbaQueryMapper;
import com.own.erp.fulfill.mapper.FbaShipmentDiffMapper;
import com.own.erp.fulfill.mapper.FbaShipmentItemMapper;
import com.own.erp.fulfill.mapper.FbaShipmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FbaShipmentStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class FbaShipmentStateMachineTest {

    private static final Long ID = 1L;

    private FbaShipmentMapper fbaShipmentMapper;
    private FbaShipmentItemMapper fbaShipmentItemMapper;
    private FbaBoxMapper fbaBoxMapper;
    private FbaBoxItemMapper fbaBoxItemMapper;
    private FbaShipmentDiffMapper fbaShipmentDiffMapper;
    private FbaQueryMapper fbaQueryMapper;
    private WarehouseApi warehouseApi;
    private GoodsQueryApi goodsQueryApi;
    private InventoryChangeApi inventoryChangeApi;
    private CurrentUserApi currentUserApi;
    private FbaShipmentService fbaShipmentService;

    @BeforeEach
    void setUp() {
        fbaShipmentMapper = mock(FbaShipmentMapper.class);
        fbaShipmentItemMapper = mock(FbaShipmentItemMapper.class);
        fbaBoxMapper = mock(FbaBoxMapper.class);
        fbaBoxItemMapper = mock(FbaBoxItemMapper.class);
        fbaShipmentDiffMapper = mock(FbaShipmentDiffMapper.class);
        fbaQueryMapper = mock(FbaQueryMapper.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        inventoryChangeApi = mock(InventoryChangeApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        fbaShipmentService = new FbaShipmentService(fbaShipmentMapper, fbaShipmentItemMapper, fbaBoxMapper, fbaBoxItemMapper, fbaShipmentDiffMapper, fbaQueryMapper, warehouseApi, goodsQueryApi, inventoryChangeApi, currentUserApi);
    }

    @Test
    void closeSucceedsOnCasHitAndFailsOnMiss() {
        when(fbaShipmentMapper.casClose(ID)).thenReturn(1);
        fbaShipmentService.close(ID);
        verify(fbaShipmentMapper).casClose(ID);

        when(fbaShipmentMapper.casClose(2L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> fbaShipmentService.close(2L))
                .getMessage().contains("关闭失败"));
    }

    @Test
    void cancelSucceedsOnCasHitAndFailsOnMiss() {
        when(fbaShipmentMapper.casCancel(ID)).thenReturn(1);
        fbaShipmentService.cancel(ID);
        verify(fbaShipmentMapper).casCancel(ID);

        when(fbaShipmentMapper.casCancel(2L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> fbaShipmentService.cancel(2L))
                .getMessage().contains("取消失败"));
    }
}
