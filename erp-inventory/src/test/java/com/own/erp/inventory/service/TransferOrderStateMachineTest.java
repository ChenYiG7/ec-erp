package com.own.erp.inventory.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.TransferConsts;
import com.own.erp.inventory.entity.TransferOrder;
import com.own.erp.inventory.mapper.TransferOrderItemMapper;
import com.own.erp.inventory.mapper.TransferOrderMapper;
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
 * @Description : TransferOrderStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class TransferOrderStateMachineTest {

    private static final Long ID = 1L;

    private TransferOrderMapper transferOrderMapper;
    private TransferOrderItemMapper transferOrderItemMapper;
    private InventoryService inventoryService;
    private WarehouseApi warehouseApi;
    private GoodsSkuApi goodsSkuApi;
    private CurrentUserApi currentUserApi;
    private TransferOrderService transferOrderService;

    @BeforeEach
    void setUp() {
        transferOrderMapper = mock(TransferOrderMapper.class);
        transferOrderItemMapper = mock(TransferOrderItemMapper.class);
        inventoryService = mock(InventoryService.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        transferOrderService = new TransferOrderService(transferOrderMapper, transferOrderItemMapper, inventoryService, warehouseApi, goodsSkuApi, currentUserApi);
    }

    private TransferOrder order(String status) {
        return TransferOrder.builder().id(ID).transferNo("TR001").fromWarehouseId(2L).toWarehouseId(3L).status(status).build();
    }

    @Test
    void cancelSucceedsOnCasHitAndFailsOnMiss() {
        when(transferOrderMapper.selectById(ID)).thenReturn(order(TransferConsts.STATUS_DRAFT));
        when(transferOrderMapper.casStatus(ID,
                TransferConsts.STATUS_DRAFT,
                TransferConsts.STATUS_CANCELED)).thenReturn(1);
        transferOrderService.cancel(ID);
        verify(transferOrderMapper).casStatus(ID,
                TransferConsts.STATUS_DRAFT,
                TransferConsts.STATUS_CANCELED);

        when(transferOrderMapper.selectById(404L)).thenReturn(null);
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.cancel(404L))
                .getMessage().contains("调拨单不存在"));

        when(transferOrderMapper.casStatus(ID,
                TransferConsts.STATUS_DRAFT,
                TransferConsts.STATUS_CANCELED)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.cancel(ID))
                .getMessage().contains("取消失败"));
    }

    // TODO(#30) 人工补齐(生成器不覆盖):confirm(复合事务:逐行 InventoryService.transfer 两腿动账、调出仓可用不足整单回滚、重复确认被拦)不在生成射程,用例见 TransferOrderServiceTest;V1 无在途账,在途模式(OUT 占用→到货 IN)留 TODO#30 拍板
}
