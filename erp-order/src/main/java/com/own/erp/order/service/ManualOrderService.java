package com.own.erp.order.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.OrderReviewConsts;
import com.own.erp.contract.ShopQueryApi;
import com.own.erp.order.entity.ShopOrder;
import com.own.erp.order.entity.ShopOrderItem;
import com.own.erp.order.mapper.ShopOrderItemMapper;
import com.own.erp.order.mapper.ShopOrderMapper;
import com.own.erp.order.request.command.ManualOrderItemSaveRequest;
import com.own.erp.order.request.command.ManualOrderSaveRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 内销订单手工录单服务(#29 订单域补课,计划书 §2.4)。
 *
 *     「系统写入表对外只读」纪律与「内销需人工录入」冲突的解法(计划书 §一核心纪律冲突):
 *     - 不开放 shop_order 通用 CRUD,只开**独立新写口** ManualOrderService(与拉单写口
 *       saveUnifiedOrder 分离),Controller 不直连 Mapper(docs/07 §2.1);
 *     - 单号用**合成平台单号** MAN-{shopId}-{yyyyMMdd}-{4位seq} 占 uk(shop_id, platform_order_id),
 *       平台拉单天然撞不上;order_source=MANUAL 标识区分,拉单侧 upsert 命中 MANUAL 单即拒绝覆盖并告警;
 *     - 明细 sku_id **必绑**(内销单无平台映射翻译环节,只能选内部 SKU,经 GoodsSkuApi 校验存在);
 *     - 落库即 WAIT_SHIP,并按订单审核风控规则置 review_status(与平台单共用 OrderRiskEvaluator)。
 *     写入口纪律:平台/单号/状态/金额/审核态一律服务端派生,不收客户端值;
 *     更新仅限 MANUAL + WAIT_SHIP,且不触碰审核列与平台单号(审核结论不因改单丢失)
 */
@Slf4j
@Service
public class ManualOrderService {

    /** 内销合成单号前缀:MAN-{shopId}-{yyyyMMdd}-{4位seq} */
    private static final String MANUAL_NO_PREFIX = "MAN-";
    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 当日序号冲突重试上限(并发/异常场景兜底;正常单店单日录单量远小于此) */
    private static final int SEQ_RETRY_LIMIT = 20;

    /** 内销单初始状态:待发货(与平台拉单 WAIT_SHIP 同语义) */
    private static final String STATUS_WAIT_SHIP = "WAIT_SHIP";
    /** 履约渠道:卖家自履约(手工单默认自履约,可产生系统发货单) */
    private static final String CHANNEL_SELF_FULFILL = "SELF_FULFILL";
    /** 币种缺省:本币 */
    private static final String DEFAULT_CURRENCY = "CNY";

    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderItemMapper shopOrderItemMapper;
    private final OrderRiskEvaluator orderRiskEvaluator;
    /** 契约注入一律 @Lazy 断构造环(docs/07 §2.2);实现收口 erp-api */
    private final ShopQueryApi shopQueryApi;
    private final GoodsSkuApi goodsSkuApi;

    public ManualOrderService(ShopOrderMapper shopOrderMapper,
                              ShopOrderItemMapper shopOrderItemMapper,
                              OrderRiskEvaluator orderRiskEvaluator,
                              @Lazy ShopQueryApi shopQueryApi,
                              @Lazy GoodsSkuApi goodsSkuApi) {
        this.shopOrderMapper = shopOrderMapper;
        this.shopOrderItemMapper = shopOrderItemMapper;
        this.orderRiskEvaluator = orderRiskEvaluator;
        this.shopQueryApi = shopQueryApi;
        this.goodsSkuApi = goodsSkuApi;
    }

    /**
     * 手工录单:校验店铺与 SKU → 装配主表(服务端派生单号/平台/状态/金额/审核态)→ 落库 + 明细。
     * 合成单号 uk 冲突(并发)重试下一序号,超限报错由人工重试。
     *
     * @return 内销订单主表ID(shop_order.id)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ManualOrderSaveRequest request) {
        ShopQueryApi.ShopView shop = shopQueryApi.getShop(request.shopId());
        if (shop == null) {
            throw new BusinessException("店铺不存在:" + request.shopId());
        }
        List<ManualOrderItemSaveRequest> items = request.items();
        requireSkusExist(items);

        ShopOrder entity = buildEntity(shop, request, sumAmount(items));
        String prefix = MANUAL_NO_PREFIX + request.shopId() + "-"
                + LocalDate.now(PullConsts.ZONE).format(DATE_PART) + "-";
        for (int attempt = 0; attempt < SEQ_RETRY_LIMIT; attempt++) {
            long seq = nextSeq(request.shopId(), prefix) + attempt;
            entity.setPlatformOrderId(prefix + String.format("%04d", seq));
            try {
                shopOrderMapper.insert(entity);
                insertItems(entity.getId(), items, entity.getCurrency());
                log.info("内销订单录入成功 orderId={} no={}", entity.getId(), entity.getPlatformOrderId());
                return entity.getId();
            } catch (DuplicateKeyException e) {
                // 合成单号撞 uk(并发录单):换下一序号重试,不动明细
                log.warn("内销合成单号冲突,换序号重试 shop={} no={}", request.shopId(), entity.getPlatformOrderId());
            }
        }
        throw new BusinessException("内销单号生成失败(当日序号冲突),请重试");
    }

    /**
     * 修改内销单:仅 MANUAL + WAIT_SHIP 可改;明细整体替换并重算金额。
     * 不触碰 platform_order_id / order_status / order_source / review_* —— 审核结论与单号稳定
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ManualOrderSaveRequest request) {
        ShopOrder exist = shopOrderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("订单不存在:" + id);
        }
        if (!OrderReviewConsts.SOURCE_MANUAL.equals(exist.getOrderSource())) {
            throw new BusinessException("非内销订单,禁止手工修改:" + id);
        }
        if (!STATUS_WAIT_SHIP.equals(exist.getOrderStatus())) {
            throw new BusinessException("仅待发货状态可修改,当前:" + exist.getOrderStatus());
        }
        if (!exist.getShopId().equals(request.shopId())) {
            throw new BusinessException("不允许变更关联店铺,请删单重建:" + id);
        }
        ShopQueryApi.ShopView shop = shopQueryApi.getShop(request.shopId());
        if (shop == null) {
            throw new BusinessException("店铺不存在:" + request.shopId());
        }
        List<ManualOrderItemSaveRequest> items = request.items();
        requireSkusExist(items);

        ShopOrder update = buildEntity(shop, request, sumAmount(items));
        update.setId(id);
        shopOrderMapper.updateById(update);
        shopOrderItemMapper.delete(new LambdaQueryWrapper<ShopOrderItem>().eq(ShopOrderItem::getOrderId, id));
        insertItems(id, items, update.getCurrency());
    }

    /** 装配主表(写前管理列回填):单号调用方回填,其余服务端派生 */
    private ShopOrder buildEntity(ShopQueryApi.ShopView shop, ManualOrderSaveRequest request, BigDecimal totalAmount) {
        String riskFlag = orderRiskEvaluator.evaluate(request.buyerNote(), request.receiverName(),
                request.receiverPhone(), request.receiverCountry(), request.receiverCity(),
                request.receiverAddress(), request.receiverZip());
        return ShopOrder.builder()
                .shopId(request.shopId())
                .platform(shop.platform())
                .orderStatus(STATUS_WAIT_SHIP)
                .fulfillmentChannel(CHANNEL_SELF_FULFILL)
                .orderTime(LocalDateTime.now(PullConsts.ZONE))
                .buyerNote(StrUtil.trimToNull(request.buyerNote()))
                .receiverName(request.receiverName())
                .receiverPhone(request.receiverPhone())
                .receiverCountry(request.receiverCountry())
                .receiverState(StrUtil.trimToNull(request.receiverState()))
                .receiverCity(request.receiverCity())
                .receiverAddress(request.receiverAddress())
                .receiverZip(request.receiverZip())
                .currency(StrUtil.blankToDefault(StrUtil.trimToNull(request.currency()), DEFAULT_CURRENCY))
                .exchangeRate(request.exchangeRate() == null ? BigDecimal.ONE : request.exchangeRate())
                .orderAmount(totalAmount)
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .orderSource(OrderReviewConsts.SOURCE_MANUAL)
                .reviewStatus(riskFlag == null ? OrderReviewConsts.REVIEW_NONE : OrderReviewConsts.REVIEW_PENDING)
                .riskFlag(riskFlag)
                .build();
    }

    /** 明细落库:sku_id 已校验存在;小计服务端计算,币种跟随订单头(库 NOT NULL);平台侧列留 NULL(内销单无平台映射) */
    private void insertItems(Long orderId, List<ManualOrderItemSaveRequest> items, String currency) {
        for (ManualOrderItemSaveRequest item : items) {
            shopOrderItemMapper.insert(ShopOrderItem.builder()
                    .orderId(orderId)
                    .skuId(item.skuId())
                    .productName(StrUtil.trimToNull(item.productName()))
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .itemAmount(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .currency(currency)
                    .build());
        }
    }

    /** 明细 SKU 存在性校验(内销单 sku_id 必绑,铁律 5 映射纪律的对偶:此处无映射可查,直接查 SKU 正本) */
    private void requireSkusExist(List<ManualOrderItemSaveRequest> items) {
        for (ManualOrderItemSaveRequest item : items) {
            if (!goodsSkuApi.existsSku(item.skuId())) {
                throw new BusinessException("SKU 不存在:" + item.skuId());
            }
        }
    }

    /** 订单金额 = Σ(单价×数量)(服务端计算,金额计算必测项 docs/07 §10) */
    private BigDecimal sumAmount(List<ManualOrderItemSaveRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ManualOrderItemSaveRequest item : items) {
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return total;
    }

    /** 当日序号 = 同店同日前缀已有内销单数 + 1(uk 冲突时由调用方换号重试) */
    private long nextSeq(Long shopId, String prefix) {
        Long count = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getShopId, shopId)
                .likeRight(ShopOrder::getPlatformOrderId, prefix));
        return (count == null ? 0L : count) + 1L;
    }
}
