package com.own.erp.job;

import com.own.erp.ai.service.SplitAdviceService;
import com.own.erp.order.event.OrderReviewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026-9-12
 * @Description : 订单审核通过→拆单建议桥接监听(#29 余量「自动拆单建议」,2026-09-12 方案 A 拍板):
 *         消费 erp-order OrderReviewedEvent(铁律 2:erp-order/erp-ai 互不依赖,erp-api 编排桥接),
 *         映射为 erp-ai SplitAdviceService 中立入参产出纯规则拆单建议;建议属旁路,
 *         失败只记日志不回滚审核主流程;监听在发布线程同步执行(审核低频,开销可忽略)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSplitAdviceListener {

    private final SplitAdviceService splitAdviceService;

    @EventListener
    public void onOrderReviewed(OrderReviewedEvent event) {
        try {
            splitAdviceService.advise(new SplitAdviceService.AdviceInput(
                    event.orderId(), event.shopId(), event.platformOrderId(),
                    event.items().stream()
                            .map(item -> new SplitAdviceService.AdviceInput.Item(item.skuId(), item.quantity()))
                            .toList()));
        } catch (Exception e) {
            log.error("拆单建议产出异常 orderId={}", event.orderId(), e);
        }
    }
}
