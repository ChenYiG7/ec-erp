package com.own.erp.inventory.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.StocktakeConsts;
import com.own.erp.inventory.entity.StocktakeOrder;
import com.own.erp.inventory.mapper.StocktakeItemMapper;
import com.own.erp.inventory.mapper.StocktakeOrderMapper;
import com.own.erp.inventory.service.InventoryService;
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
 * @Description : StocktakeOrderStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class StocktakeOrderStateMachineTest {

    private static final Long ID = 1L;

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
        stocktakeOrderService = new StocktakeOrderService(stocktakeOrderMapper, stocktakeItemMapper, inventoryService, warehouseApi, goodsSkuApi, currentUserApi);
    }

    private StocktakeOrder order(String status) {
        return StocktakeOrder.builder().id(ID).stocktakeNo("ST001").warehouseId(9L).status(status).build();
    }

    @Test
    void startSucceedsOnCasHitAndFailsOnMiss() {
        when(stocktakeOrderMapper.casStatus(ID,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_COUNTING)).thenReturn(1);
        stocktakeOrderService.start(ID);
        verify(stocktakeOrderMapper).casStatus(ID,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_COUNTING);

        when(stocktakeOrderMapper.casStatus(2L,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_COUNTING)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.start(2L))
                .getMessage().contains("开始盘点失败"));
    }

    @Test
    void submitSucceedsOnCasHitAndFailsOnMiss() {
        when(stocktakeOrderMapper.casStatus(ID,
                StocktakeConsts.STATUS_COUNTING,
                StocktakeConsts.STATUS_PENDING_ADJUST)).thenReturn(1);
        stocktakeOrderService.submit(ID);
        verify(stocktakeOrderMapper).casStatus(ID,
                StocktakeConsts.STATUS_COUNTING,
                StocktakeConsts.STATUS_PENDING_ADJUST);

        when(stocktakeOrderMapper.casStatus(2L,
                StocktakeConsts.STATUS_COUNTING,
                StocktakeConsts.STATUS_PENDING_ADJUST)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.submit(2L))
                .getMessage().contains("提交盘点失败"));
    }

    @Test
    void closeSucceedsOnCasHitAndFailsOnMiss() {
        when(stocktakeOrderMapper.casStatus(ID,
                StocktakeConsts.STATUS_ADJUSTED,
                StocktakeConsts.STATUS_CLOSED)).thenReturn(1);
        stocktakeOrderService.close(ID);
        verify(stocktakeOrderMapper).casStatus(ID,
                StocktakeConsts.STATUS_ADJUSTED,
                StocktakeConsts.STATUS_CLOSED);

        when(stocktakeOrderMapper.casStatus(2L,
                StocktakeConsts.STATUS_ADJUSTED,
                StocktakeConsts.STATUS_CLOSED)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.close(2L))
                .getMessage().contains("关闭失败"));
    }

    @Test
    void cancelSucceedsOnCasHitAndFailsOnMiss() {
        when(stocktakeOrderMapper.selectById(ID)).thenReturn(order(StocktakeConsts.STATUS_DRAFT));
        when(stocktakeOrderMapper.casStatus(ID,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_CANCELED)).thenReturn(1);
        stocktakeOrderService.cancel(ID);
        verify(stocktakeOrderMapper).casStatus(ID,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_CANCELED);

        when(stocktakeOrderMapper.selectById(404L)).thenReturn(null);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.cancel(404L))
                .getMessage().contains("盘点单不存在"));

        when(stocktakeOrderMapper.casStatus(ID,
                StocktakeConsts.STATUS_DRAFT,
                StocktakeConsts.STATUS_CANCELED)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> stocktakeOrderService.cancel(ID))
                .getMessage().contains("取消失败"));
    }

    // TODO(#30) 人工补齐(生成器不覆盖):generateAdjust(复合事务:确认时点 re-diff + 逐行 ADJUST 动账 + 可用不足整单回滚)与 recordCounts/submit 明细校验不在生成射程,用例见 StocktakeOrderServiceTest;前端录实盘行编辑定制组件见 TODO#30
}
