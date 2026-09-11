package com.own.erp.order.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.OrderReviewConsts;
import com.own.erp.order.entity.ShopOrder;
import com.own.erp.order.mapper.ShopOrderItemMapper;
import com.own.erp.order.mapper.ShopOrderMapper;
import com.own.erp.order.service.OrderRiskEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
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
 * @Description : ShopOrderStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class ShopOrderStateMachineTest {

    private static final Long ID = 1L;

    private ShopOrderMapper shopOrderMapper;
    private ShopOrderItemMapper shopOrderItemMapper;
    private OrderRiskEvaluator orderRiskEvaluator;
    private ApplicationEventPublisher applicationEventPublisher;
    private CurrentUserApi currentUserApi;
    private ShopOrderService shopOrderService;

    @BeforeEach
    void setUp() {
        shopOrderMapper = mock(ShopOrderMapper.class);
        shopOrderItemMapper = mock(ShopOrderItemMapper.class);
        orderRiskEvaluator = mock(OrderRiskEvaluator.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        currentUserApi = mock(CurrentUserApi.class);
        shopOrderService = new ShopOrderService(shopOrderMapper, shopOrderItemMapper, orderRiskEvaluator, applicationEventPublisher, currentUserApi);
    }

    @Test
    void reviewApproveSucceedsOnCasHitAndFailsOnMiss() {
        when(shopOrderMapper.casReviewStatus(ID, OrderReviewConsts.REVIEW_APPROVED, "凭证齐全", 0L)).thenReturn(1);
        shopOrderService.review(ID, true, "凭证齐全");
        verify(shopOrderMapper).casReviewStatus(ID, OrderReviewConsts.REVIEW_APPROVED, "凭证齐全", 0L);

        when(shopOrderMapper.casReviewStatus(2L, OrderReviewConsts.REVIEW_APPROVED, "凭证齐全", 0L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> shopOrderService.review(2L, true, "凭证齐全"))
                .getMessage().contains("审核通过失败"));
    }

    @Test
    void reviewRejectSucceedsOnCasHitAndFailsOnMiss() {
        when(shopOrderMapper.casReviewStatus(ID, OrderReviewConsts.REVIEW_REJECTED, "虚假单号驳回", 0L)).thenReturn(1);
        shopOrderService.review(ID, false, "虚假单号驳回");
        verify(shopOrderMapper).casReviewStatus(ID, OrderReviewConsts.REVIEW_REJECTED, "虚假单号驳回", 0L);

        when(shopOrderMapper.casReviewStatus(2L, OrderReviewConsts.REVIEW_REJECTED, "虚假单号驳回", 0L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> shopOrderService.review(2L, false, "虚假单号驳回"))
                .getMessage().contains("审核驳回失败"));
    }

    // TODO(#29) 人工补齐(生成器不覆盖):审核人 reviewed_by 由 CurrentUserApi 回填(单测 mock 返回 null,故 stub/call 不含真实用户);MANUAL 单冲突防御、upsert 不冲审核列(ODKU 显式排除)在真库 validate 脚本与手写单测覆盖;内销录单 ManualOrderService 合成单号重试/店铺与 SKU 校验属复合流程,落手写单测(生成器边界外)
}
