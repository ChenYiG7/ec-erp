package com.own.erp.finance.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.finance.constant.FirstLegConsts;
import com.own.erp.finance.entity.FirstLegShipment;
import com.own.erp.finance.mapper.FirstLegAllocMapper;
import com.own.erp.finance.mapper.FirstLegBoxItemMapper;
import com.own.erp.finance.mapper.FirstLegBoxMapper;
import com.own.erp.finance.mapper.FirstLegQueryMapper;
import com.own.erp.finance.mapper.FirstLegShipmentMapper;
import com.own.erp.finance.service.ExchangeRateService;
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
 * @Date : 2026/9/11
 * @Description : FirstLegShipmentStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class FirstLegShipmentStateMachineTest {

    private static final Long ID = 1L;

    private FirstLegShipmentMapper firstLegShipmentMapper;
    private FirstLegBoxMapper firstLegBoxMapper;
    private FirstLegBoxItemMapper firstLegBoxItemMapper;
    private FirstLegAllocMapper firstLegAllocMapper;
    private FirstLegQueryMapper firstLegQueryMapper;
    private ExchangeRateService exchangeRateService;
    private WarehouseApi warehouseApi;
    private GoodsQueryApi goodsQueryApi;
    private PurchaseQueryApi purchaseQueryApi;
    private CurrentUserApi currentUserApi;
    private FirstLegShipmentService firstLegShipmentService;

    @BeforeEach
    void setUp() {
        firstLegShipmentMapper = mock(FirstLegShipmentMapper.class);
        firstLegBoxMapper = mock(FirstLegBoxMapper.class);
        firstLegBoxItemMapper = mock(FirstLegBoxItemMapper.class);
        firstLegAllocMapper = mock(FirstLegAllocMapper.class);
        firstLegQueryMapper = mock(FirstLegQueryMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        purchaseQueryApi = mock(PurchaseQueryApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        firstLegShipmentService = new FirstLegShipmentService(firstLegShipmentMapper, firstLegBoxMapper, firstLegBoxItemMapper, firstLegAllocMapper, firstLegQueryMapper, exchangeRateService, warehouseApi, goodsQueryApi, purchaseQueryApi, currentUserApi);
    }

    @Test
    void closeSucceedsOnCasHitAndFailsOnMiss() {
        when(firstLegShipmentMapper.casClose(ID)).thenReturn(1);
        firstLegShipmentService.close(ID);
        verify(firstLegShipmentMapper).casClose(ID);

        when(firstLegShipmentMapper.casClose(2L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> firstLegShipmentService.close(2L))
                .getMessage().contains("关闭失败"));
    }

    @Test
    void cancelSucceedsOnCasHitAndFailsOnMiss() {
        when(firstLegShipmentMapper.casCancel(ID)).thenReturn(1);
        firstLegShipmentService.cancel(ID);
        verify(firstLegShipmentMapper).casCancel(ID);

        when(firstLegShipmentMapper.casCancel(2L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> firstLegShipmentService.cancel(2L))
                .getMessage().contains("取消失败"));
    }
}
