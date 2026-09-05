package com.own.erp.order.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.order.entity.ShopOrder;
import com.own.erp.order.entity.ShopOrderItem;
import com.own.erp.order.mapper.ShopOrderItemMapper;
import com.own.erp.order.mapper.ShopOrderMapper;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderItemResponse;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.platform.unified.UnifiedOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 平台订单服务:shop_order 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     订单为系统写入表(拉单落库),不开放人工 CRUD 写接口;唯一写入口 saveUnifiedOrder(#4):
 *     幂等靠 UNIQUE(shop_id, platform_order_id) ON DUPLICATE KEY UPDATE(docs/07 铁律 5,禁先查后插),
 *     已有订单按本次报文整体推进状态与金额;状态只允许拉单同步与本系统操作两条路径产生,禁止旁路 update
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopOrderService {

    private final ShopOrderMapper shopOrderMapper;
    private final ShopOrderItemMapper shopOrderItemMapper;

    /** 分页查询(按下单时间倒序;过滤:店铺/平台/状态) */
    public Page<ShopOrderResponse> page(ShopOrderQuery query) {
        LambdaQueryWrapper<ShopOrder> wrapper = new LambdaQueryWrapper<ShopOrder>()
                .eq(query.getShopId() != null, ShopOrder::getShopId, query.getShopId())
                .eq(StrUtil.isNotBlank(query.getPlatform()), ShopOrder::getPlatform, query.getPlatform())
                .eq(StrUtil.isNotBlank(query.getOrderStatus()), ShopOrder::getOrderStatus, query.getOrderStatus())
                .orderByDesc(ShopOrder::getOrderTime)
                .orderByDesc(ShopOrder::getId);
        Page<ShopOrder> result = shopOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<ShopOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ShopOrderResponse::from).toList());
        return responsePage;
    }

    /**
     * 详情,出参 Response 并随单带明细(items);不存在返回 null。列表接口不带明细,只有详情带回。
     * Response 为 record(docs/07 §1),携带明细走 wither 副本,不回退可变模型
     */
    public ShopOrderResponse getById(Long id) {
        ShopOrder shopOrder = shopOrderMapper.selectById(id);
        if (shopOrder == null) {
            return null;
        }
        List<ShopOrderItem> items = shopOrderItemMapper.selectList(
                new LambdaQueryWrapper<ShopOrderItem>().eq(ShopOrderItem::getOrderId, id));
        return ShopOrderResponse.from(shopOrder)
                .withItems(items.stream().map(ShopOrderItemResponse::from).toList());
    }

    /**
     * 订单明细引用计数(#5 SKU 删除校验,经 erp-contract 接口暴露):skuIds 在 shop_order_item 的行数。
     * sku_id 未绑定时落 NULL(不在计数内);订单为系统写入表,历史引用只能靠"先停用后处理"人工决策
     */
    public long countItemRefsBySkuIds(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return 0;
        }
        Long count = shopOrderItemMapper.selectCount(new LambdaQueryWrapper<ShopOrderItem>()
                .in(ShopOrderItem::getSkuId, skuIds));
        return count == null ? 0L : count;
    }

    /**
     * 订单引用计数(#3 店铺删除校验,经 erp-contract 接口暴露):shopIds 在 shop_order 的行数。
     * 订单为系统写入表,有订单历史的店铺只能停用,不可硬删
     */
    public long countByShopIds(Collection<Long> shopIds) {
        if (CollUtil.isEmpty(shopIds)) {
            return 0;
        }
        Long count = shopOrderMapper.selectCount(new LambdaQueryWrapper<ShopOrder>()
                .in(ShopOrder::getShopId, shopIds));
        return count == null ? 0L : count;
    }

    /**
     * 订单状态条件推进(#11 发货发足场景 WAIT_SHIP→SHIPPED,经 erp-contract ShopOrderApi 暴露):
     * WHERE order_status = fromStatus 条件更新即状态机守卫,并发下仅一方命中;
     * "本系统操作"路径的唯一出口(docs/04:状态只允许拉单同步与本系统操作两条路径产生)
     *
     * @return 是否命中(命中=推进成功)
     */
    public boolean casOrderStatus(Long orderId, String fromStatus, String toStatus) {
        if (orderId == null) {
            return false;
        }
        return shopOrderMapper.casOrderStatus(orderId, fromStatus, toStatus) > 0;
    }

    /**
     * 平台订单ID反查内部订单ID(#12 售后同步落库,经 erp-contract ShopOrderApi 暴露):
     * 按 uk(shop_id, platform_order_id) 等值反查,订单未入库返回 null(售后单跳过等下轮窗口重拉);
     * LIMIT 1 兜防御(selectOne 多行会抛)
     */
    public Long findIdByPlatformOrderId(Long shopId, String platformOrderId) {
        if (shopId == null || StrUtil.isBlank(platformOrderId)) {
            return null;
        }
        ShopOrder row = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getShopId, shopId)
                .eq(ShopOrder::getPlatformOrderId, platformOrderId)
                .last("LIMIT 1"));
        return row == null ? null : row.getId();
    }

    /**
     * 拉单落库唯一写入口(#4):UnifiedOrder → shop_order/shop_order_item。
     * 幂等流程:upsert 主表(uk 冲突即更新)→ 按 uk 反查 id → 明细先删后插(状态回传可能改行,
     * 删插同事务保证一致)。sku_id 由调用方(erp-api 编排)按 shop_product_sku 映射传入,
     * 未绑定的 seller_sku 保持 NULL,订单照常入库(docs/07 核心流程)。
     *
     * @param skuIdBySellerSku seller_sku → 内部 sku_id(仅含已绑定行),null 视为无映射
     * @return 订单主表ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveUnifiedOrder(Long shopId, UnifiedOrder order, Map<String, Long> skuIdBySellerSku) {
        // 幂等键与必填列(库 NOT NULL)前置校验:脏数据整单拒绝,由拉单侧记 pull_log 失败重试
        if (shopId == null || order == null || StrUtil.isBlank(order.getPlatformOrderId())) {
            throw new BusinessException(400, "订单落库入参不完整:shopId/platformOrderId 必填");
        }
        if (order.getPlatform() == null || order.getStatus() == null || order.getOrderTime() == null
                || StrUtil.isBlank(order.getCurrency())) {
            throw new BusinessException(400, "订单字段缺失(平台/状态/下单时间/币种必填),platformOrderId="
                    + order.getPlatformOrderId());
        }
        shopOrderMapper.upsert(toOrderEntity(shopId, order));
        ShopOrder saved = shopOrderMapper.selectOne(new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getShopId, shopId)
                .eq(ShopOrder::getPlatformOrderId, order.getPlatformOrderId())
                .last("LIMIT 1"));
        if (saved == null) {
            // 理论不可达(upsert 后必存在),防御异常并发删单,避免明细挂空 orderId
            throw new BusinessException(500, "订单落库后按唯一键反查失败,platformOrderId=" + order.getPlatformOrderId());
        }
        replaceItems(saved.getId(), order, skuIdBySellerSku == null ? Map.of() : skuIdBySellerSku);
        return saved.getId();
    }

    /** UnifiedOrder → ShopOrder 显式逐字段映射(禁反射拷贝,docs/07 §12);null 金额按库默认语义归零 */
    private ShopOrder toOrderEntity(Long shopId, UnifiedOrder order) {
        ShopOrder entity = new ShopOrder();
        entity.setShopId(shopId);
        entity.setPlatform(order.getPlatform().name());
        entity.setPlatformOrderId(order.getPlatformOrderId());
        entity.setOrderStatus(order.getStatus().name());
        entity.setFulfillmentChannel(order.getFulfillmentChannel() == null
                ? UnifiedOrder.FulfillmentChannel.SELF_FULFILL.name() : order.getFulfillmentChannel().name());
        entity.setOrderTime(toDateTime(order.getOrderTime(), order.getPlatformOrderId()));
        entity.setPaidTime(order.getPayTime() == null ? null : toDateTime(order.getPayTime(), order.getPlatformOrderId()));
        entity.setBuyerNote(order.getBuyerMessage());
        entity.setReceiverName(order.getReceiverName());
        entity.setReceiverPhone(order.getReceiverPhone());
        entity.setReceiverCountry(order.getReceiverCountry());
        entity.setReceiverState(order.getReceiverState());
        entity.setReceiverCity(order.getReceiverCity());
        entity.setReceiverAddress(order.getReceiverAddress());
        entity.setReceiverZip(order.getReceiverZip());
        entity.setCurrency(order.getCurrency());
        // 汇率:国内订单固定 1;跨境汇率由 adapter 翻译,缺失落 1 并记日志(财务勾稽前须补汇率)
        entity.setExchangeRate(order.getExchangeRate() == null ? BigDecimal.ONE : order.getExchangeRate());
        if (order.getExchangeRate() == null && !order.getPlatform().isDomestic()) {
            log.warn("跨境订单缺汇率,按1落库待财务修正,platformOrderId={}", order.getPlatformOrderId());
        }
        entity.setOrderAmount(defaultZero(order.getTotalAmount()));
        entity.setShippingFee(defaultZero(order.getPostageAmount()));
        entity.setDiscountAmount(defaultZero(order.getDiscountAmount()));
        // raw_json 必存(docs/07 §8):翻译出错可回溯重放
        entity.setRawJson(order.getRawJson());
        return entity;
    }

    /** 明细先删后插:平台状态回传可能改行数量/金额,删插同事务保证与主表一致 */
    private void replaceItems(Long orderId, UnifiedOrder order, Map<String, Long> skuIdBySellerSku) {
        shopOrderItemMapper.delete(new LambdaQueryWrapper<ShopOrderItem>().eq(ShopOrderItem::getOrderId, orderId));
        if (CollUtil.isEmpty(order.getItems())) {
            return;
        }
        for (UnifiedOrder.Item item : order.getItems()) {
            if (item.getQuantity() == null) {
                // 库 NOT NULL,静默补 0 会污染后续对账,宁可整单失败重试
                throw new BusinessException(400, "订单明细数量缺失,platformOrderId=" + order.getPlatformOrderId());
            }
            ShopOrderItem row = new ShopOrderItem();
            row.setOrderId(orderId);
            row.setPlatformOrderItemId(item.getPlatformOrderItemId());
            row.setSkuId(skuIdBySellerSku.get(item.getSellerSku()));
            row.setPlatformSku(item.getSellerSku());
            row.setProductName(item.getTitle());
            row.setQuantity(item.getQuantity());
            row.setUnitPrice(defaultZero(item.getUnitPrice()));
            // 小计=单价×数量(金额计算必测项,docs/07 §10);UPPER 币种跟随订单头
            row.setItemAmount(defaultZero(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())));
            row.setCurrency(order.getCurrency());
            shopOrderItemMapper.insert(row);
        }
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 平台 Instant → 库内 DATETIME(会话时区 Asia/Shanghai,与 ShopService 同源,见 PullConsts.ZONE 注释) */
    private static LocalDateTime toDateTime(Instant instant, String platformOrderId) {
        return LocalDateTime.ofInstant(instant, PullConsts.ZONE);
    }
}
