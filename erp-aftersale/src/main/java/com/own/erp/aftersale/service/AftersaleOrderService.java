package com.own.erp.aftersale.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.aftersale.constant.AftersaleConsts;
import com.own.erp.aftersale.entity.AftersaleOrder;
import com.own.erp.aftersale.entity.AftersaleReturnItem;
import com.own.erp.aftersale.mapper.AftersaleOrderMapper;
import com.own.erp.aftersale.mapper.AftersaleReturnItemMapper;
import com.own.erp.aftersale.request.command.AftersaleReturnItemRequest;
import com.own.erp.aftersale.request.command.AftersaleReturnReceiveRequest;
import com.own.erp.aftersale.request.query.AftersaleOrderQuery;
import com.own.erp.aftersale.response.AftersaleOrderResponse;
import com.own.erp.aftersale.response.AftersaleReturnItemResponse;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.platform.unified.UnifiedRefund;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 售后单服务:aftersale_order(+aftersale_return_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     系统写入表:数据由平台同步 upsert 落库,不开放人工 CRUD 写接口;人工侧仅状态机处理动作(docs/07 §1,entity 不出本层)
 *     同步落库唯一入口 saveUnifiedRefund(#12,2026-09-05 槽位收口):幂等靠表唯一键 uk_shop_platform_refund,禁旁路 insert;
 *     调用方 = 售后拉单 Job(随 #3 真凭证接线,AmazonClient.pullRefunds 现为占位)
 *     状态机(#12,2026-09-04 拍板,词表见 AftersaleConsts):条件更新即守卫(docs/07 §6.3,WHERE 即前置态校验),
 *     同一 UPDATE 原子回填处理结果 result;单步流转不加事务(#10 audit 先例)。
 *     收退件升级复合事务动作(2026-09-04 拍板,同 #10 confirm/#11 ship 先例):RETURNING → RETURN_RECEIVED
 *     同事务完成仓库/归属/数量校验 → 占位回填 warehouse_id → 逐行 IN_RETURN 动账 → 实收明细落库(动账凭证),
 *     任一步失败整体回滚,库存/流水/售后单状态强一致;退货明细 = 人工录入实收(可≠平台申明,对齐采购入库实收模式)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AftersaleOrderService {

    private final AftersaleOrderMapper aftersaleOrderMapper;
    private final AftersaleReturnItemMapper aftersaleReturnItemMapper;
    private final ShopOrderApi shopOrderApi;
    private final WarehouseApi warehouseApi;
    private final InventoryChangeApi inventoryChangeApi;

    /** 分页查询(默认按 id 倒序;shopId/status/type/orderId 精确过滤) */
    public Page<AftersaleOrderResponse> page(AftersaleOrderQuery query) {
        Page<AftersaleOrder> result = aftersaleOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<AftersaleOrder>()
                        .eq(query.getShopId() != null, AftersaleOrder::getShopId, query.getShopId())
                        .eq(StrUtil.isNotBlank(query.getStatus()), AftersaleOrder::getStatus, query.getStatus())
                        .eq(StrUtil.isNotBlank(query.getType()), AftersaleOrder::getType, query.getType())
                        .eq(query.getOrderId() != null, AftersaleOrder::getOrderId, query.getOrderId())
                        .orderByDesc(AftersaleOrder::getId));
        Page<AftersaleOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(AftersaleOrderResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response 随退货明细(#12:withReturnItems wither,分页不查子表);不存在返回 null */
    public AftersaleOrderResponse getById(Long id) {
        AftersaleOrder aftersaleOrder = aftersaleOrderMapper.selectById(id);
        if (aftersaleOrder == null) {
            return null;
        }
        List<AftersaleReturnItemResponse> items = aftersaleReturnItemMapper.selectList(
                        new LambdaQueryWrapper<AftersaleReturnItem>().eq(AftersaleReturnItem::getAftersaleId, id))
                .stream().map(AftersaleReturnItemResponse::from).toList();
        return AftersaleOrderResponse.from(aftersaleOrder).withReturnItems(items);
    }

    /**
     * 售后引用计数(#3 店铺删除校验,经 erp-contract 接口暴露):shopIds 在 aftersale_order 的行数。
     * 售后为系统写入表(平台同步,#12),有售后历史的店铺不可硬删
     */
    public long countByShopIds(Collection<Long> shopIds) {
        if (CollUtil.isEmpty(shopIds)) {
            return 0;
        }
        Long count = aftersaleOrderMapper.selectCount(new LambdaQueryWrapper<AftersaleOrder>()
                .in(AftersaleOrder::getShopId, shopIds));
        return count == null ? 0L : count;
    }

    /**
     * 平台售后同步落库唯一写入口(#12,2026-09-05 槽位收口,同 #4 saveUnifiedOrder 套路):
     * 幂等靠 uk(shop_id, platform_refund_id) upsert 冲突即更新(docs/07 铁律 5 禁先查后插,SQL 见 AftersaleOrderMapper.xml);
     * aftersale_no 取 platformRefundId(平台退款单ID,uk_aftersale_no 防御性兜底,撞键上抛由拉单侧记 pull_log)。
     * order_id 经 ShopOrderApi.findIdByPlatformOrderId 跨域翻译(铁律 2 契约通道,同 receiveReturn 用 findDeliveryView 先例)——
     * 售后单先于订单入库(订单还没拉到)时跳过返回 false 等下轮拉单窗口重拉,不抛异常断整批;
     * 状态映射拍板(#12):平台状态只做首插初始映射,人工状态机是处理主线——已存在单仅平台终态回传
     * (REJECTED/CANCELLED,AftersaleConsts 预留的平台撤单出口)条件推进未决态单,其余只刷金额/原因快照字段。
     * 平台申明明细 items 不落库(aftersale_return_item 是人工实收凭证非平台申明;表亦无 raw_json 列,
     * 翻译回溯需求随 #3 真凭证联调接线 pullRefunds 时按 add-table 流程评估)
     *
     * @return 是否落库(false = 关联订单未入库跳过)
     */
    public boolean saveUnifiedRefund(UnifiedRefund refund) {
        if (refund == null || refund.getShopId() == null || StrUtil.isBlank(refund.getPlatformRefundId())
                || StrUtil.isBlank(refund.getPlatformOrderId())) {
            throw new BusinessException(400, "售后落库入参不完整:shopId/platformRefundId/platformOrderId 必填");
        }
        if (refund.getType() == null || refund.getStatus() == null || StrUtil.isBlank(refund.getCurrency())) {
            throw new BusinessException(400, "售后字段缺失(类型/状态/币种必填),platformRefundId="
                    + refund.getPlatformRefundId());
        }
        Long orderId = shopOrderApi.findIdByPlatformOrderId(refund.getShopId(), refund.getPlatformOrderId());
        if (orderId == null) {
            log.warn("售后同步跳过:关联订单未入库,shopId={},platformOrderId={},platformRefundId={}",
                    refund.getShopId(), refund.getPlatformOrderId(), refund.getPlatformRefundId());
            return false;
        }
        aftersaleOrderMapper.upsert(toRefundEntity(refund, orderId));
        return true;
    }

    /** UnifiedRefund → AftersaleOrder 显式逐字段映射(禁反射拷贝,docs/07 §12);单条 upsert 原子,不加事务(#12 单步口径) */
    private AftersaleOrder toRefundEntity(UnifiedRefund refund, Long orderId) {
        return AftersaleOrder.builder()
                .aftersaleNo(refund.getPlatformRefundId())
                .shopId(refund.getShopId())
                .platformRefundId(refund.getPlatformRefundId())
                .orderId(orderId)
                .type(toRefundType(refund.getType()))
                .status(toRefundStatus(refund.getType(), refund.getStatus()))
                .refundAmount(refund.getRefundAmount())
                .currency(refund.getCurrency())
                .reason(buildReason(refund))
                .build();
    }

    /** 类型映射:枚举封闭穷举到词表(编译器保证不漏分支,词表漏配编译期即暴露) */
    private static String toRefundType(UnifiedRefund.RefundType type) {
        return switch (type) {
            case REFUND_ONLY -> AftersaleConsts.TYPE_REFUND_ONLY;
            case RETURN_REFUND -> AftersaleConsts.TYPE_RETURN_REFUND;
            case EXCHANGE -> AftersaleConsts.TYPE_EXCHANGE;
            case RESEND -> AftersaleConsts.TYPE_RESEND;
        };
    }

    /**
     * 平台状态 → 本系统状态机词表映射(#12 拍板):
     * APPLYING→PENDING 待人工处理;WAIT_RECEIVE(买家已寄回)→RETURNING 待收退件;
     * FINISHED 按类型分流——退款类平台已完成退款=REFUNDED(人工核实后 complete 收尾),
     * 换货/补发类无退款动作=COMPLETED 终态;REJECTED/CANCELLED 平台终态直落
     */
    private static String toRefundStatus(UnifiedRefund.RefundType type, UnifiedRefund.RefundStatus status) {
        return switch (status) {
            case APPLYING -> AftersaleConsts.STATUS_PENDING;
            case WAIT_RECEIVE -> AftersaleConsts.STATUS_RETURNING;
            case FINISHED -> switch (type) {
                case REFUND_ONLY, RETURN_REFUND -> AftersaleConsts.STATUS_REFUNDED;
                case EXCHANGE, RESEND -> AftersaleConsts.STATUS_COMPLETED;
            };
            case REJECTED -> AftersaleConsts.STATUS_REJECTED;
            case CANCELLED -> AftersaleConsts.STATUS_CANCELLED;
        };
    }

    /** 售后原因:reason 优先,缺失用 description 兜底(部分平台把原因放补充说明),截 512 对齐列宽 */
    private static String buildReason(UnifiedRefund refund) {
        String reason = StrUtil.blankToDefault(refund.getReason(), refund.getDescription());
        return StrUtil.isBlank(reason) ? null : StrUtil.sub(reason, 0, 512);
    }

    /**
     * 同意:PENDING → APPROVED(仅退款/补发类,可直接退款)/ RETURNING(退货退款/换货类,等买家寄回);result 选填。
     * type 不可变(同步落库),先读分流再 cas,竞态由 casStatus 守卫兜底;单条 UPDATE 自原子,不加事务(#10 audit 先例)
     */
    public void agree(Long id, String result) {
        AftersaleOrder aftersaleOrder = aftersaleOrderMapper.selectById(id);
        if (aftersaleOrder == null) {
            throw new BusinessException("售后单不存在:" + id);
        }
        String toStatus = switch (aftersaleOrder.getType()) {
            case AftersaleConsts.TYPE_REFUND_ONLY, AftersaleConsts.TYPE_RESEND -> AftersaleConsts.STATUS_APPROVED;
            case AftersaleConsts.TYPE_RETURN_REFUND, AftersaleConsts.TYPE_EXCHANGE -> AftersaleConsts.STATUS_RETURNING;
            default -> throw new BusinessException("售后类型无法识别:" + aftersaleOrder.getType());
        };
        if (aftersaleOrderMapper.casStatus(id, AftersaleConsts.STATUS_PENDING, toStatus, result) == 0) {
            throw new BusinessException("同意失败:售后单不存在或不是待处理状态");
        }
    }

    /** 拒绝:PENDING → REJECTED(终态);result 必填(拒绝原因留痕) */
    public void reject(Long id, String result) {
        if (StrUtil.isBlank(result)) {
            throw new BusinessException("拒绝必须填写处理结果");
        }
        if (aftersaleOrderMapper.casStatus(id, AftersaleConsts.STATUS_PENDING, AftersaleConsts.STATUS_REJECTED, result) == 0) {
            throw new BusinessException("拒绝失败:售后单不存在或不是待处理状态");
        }
    }

    /**
     * 收退件 + 退货入库(#12 复合事务动作,2026-09-04 拍板,同 #10 confirm/#11 ship 先例):
     * RETURNING → RETURN_RECEIVED,同事务完成——
     * ①校验:仓库存在(WarehouseApi 防幻影库存,同 #10/#11)+ 实收明细非空 + 逐行归属校验
     *   (复用 ShopOrderApi.findDeliveryView,仅 sku_id 已绑定行可退,sku_id 服务端按订单行回填不入参)
     *   + 数量预校验(该订单行历史退货累计 + 本次 ≤ 订单行数量;上限按订单行数量而非发货量,放宽不误拦,
     *   精确超退窗口人工低频接受,同 #11 并发超发窗口口径,TODO(#12) 随发货域数据完善后收紧);
     * ②条件更新占位 RETURN_RECEIVED 并回填 warehouse_id(AftersaleOrderMapper.receiveReturn,
     *   并发双收退件/重复收退件仅一个成功,失败随事务回滚);
     * ③逐行经 InventoryChangeApi 走 InventoryService.change 唯一入口写 flow(flow_type=IN_RETURN 数量为正,
     *   biz=AFTERSALE_ORDER/售后单ID,docs/07 铁律 4);
     * ④aftersale_return_item 实收明细落库(动账凭证,无独立人工写入口)。
     * 任一步失败整体回滚,库存/流水/售后单状态/明细强一致;换货补发的出库侧走发货单域,不在本动作射程
     */
    @Transactional(rollbackFor = Exception.class)
    public void receiveReturn(Long id, AftersaleReturnReceiveRequest request) {
        if (request.warehouseId() == null) {
            throw new BusinessException("收退件必须指定退货入库仓");
        }
        if (CollUtil.isEmpty(request.items())) {
            throw new BusinessException("收退件必须录入实收退货明细");
        }
        AftersaleOrder aftersaleOrder = aftersaleOrderMapper.selectById(id);
        if (aftersaleOrder == null) {
            throw new BusinessException("售后单不存在:" + id);
        }
        if (!warehouseApi.existsWarehouse(request.warehouseId())) {
            throw new BusinessException("退货仓库不存在:" + request.warehouseId());
        }
        ShopOrderApi.OrderDeliveryView view = shopOrderApi.findDeliveryView(aftersaleOrder.getOrderId());
        if (view == null) {
            throw new BusinessException("关联订单不存在:" + aftersaleOrder.getOrderId());
        }
        Map<Long, ShopOrderApi.OrderDeliveryView.Item> orderItems = new HashMap<>();
        view.items().forEach(item -> orderItems.put(item.orderItemId(), item));
        Set<Long> seenOrderItemIds = new HashSet<>();
        for (AftersaleReturnItemRequest line : request.items()) {
            if (line.returnQty() == null || line.returnQty() <= 0) {
                throw new BusinessException("退货数量必须为正数");
            }
            if (!seenOrderItemIds.add(line.orderItemId())) {
                throw new BusinessException("实收明细行重复:订单明细 " + line.orderItemId());
            }
            if (!orderItems.containsKey(line.orderItemId())) {
                throw new BusinessException("退货明细不属于该订单或 SKU 未绑定:" + line.orderItemId());
            }
        }
        Map<Long, Integer> returned = returnedQtyByOrderItem(aftersaleOrder.getOrderId());
        for (AftersaleReturnItemRequest line : request.items()) {
            int history = returned.getOrDefault(line.orderItemId(), 0);
            int cap = orderItems.get(line.orderItemId()).quantity();
            if (history + line.returnQty() > cap) {
                throw new BusinessException("退货数量超限:订单明细 " + line.orderItemId()
                        + " 累计已退 " + history + ",本次 " + line.returnQty() + ",订单行数量 " + cap);
            }
        }
        if (aftersaleOrderMapper.receiveReturn(id, request.warehouseId(), request.result()) == 0) {
            throw new BusinessException("收退件失败:售后单不是待收退件状态");
        }
        for (AftersaleReturnItemRequest line : request.items()) {
            Long skuId = orderItems.get(line.orderItemId()).skuId();
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(skuId)
                    .warehouseId(request.warehouseId())
                    .quantity(line.returnQty())
                    .flowType(InventoryConsts.FLOW_TYPE_IN_RETURN)
                    .bizType(AftersaleConsts.BIZ_TYPE_AFTERSALE_ORDER)
                    .bizId(id)
                    .remark("售后单:" + aftersaleOrder.getAftersaleNo())
                    .createdBy(null)
                    .build());
            aftersaleReturnItemMapper.insert(AftersaleReturnItem.builder()
                    .aftersaleId(id)
                    .orderItemId(line.orderItemId())
                    .skuId(skuId)
                    .returnQty(line.returnQty())
                    .build());
        }
    }

    /** 该平台订单全部历史退货明细按订单行聚合(跨售后单,同单重复收退件由 cas 占位挡住) */
    private Map<Long, Integer> returnedQtyByOrderItem(Long orderId) {
        Map<Long, Integer> returned = new HashMap<>();
        for (AftersaleReturnItem row : aftersaleReturnItemMapper.listByOrderId(orderId)) {
            returned.merge(row.getOrderItemId(), row.getReturnQty(), Integer::sum);
        }
        return returned;
    }

    /** 退款:APPROVED(仅退款/补发)或 RETURN_RECEIVED(退货类,强制已收退件)→ REFUNDED;result 选填 */
    public void refund(Long id, String result) {
        if (aftersaleOrderMapper.refundOrder(id, result) == 0) {
            throw new BusinessException("退款失败:售后单需先同意(退货类须已收退件)");
        }
    }

    /** 完成:REFUNDED → COMPLETED(终态收尾,如换货已补发/退款到账核实);result 选填 */
    public void complete(Long id, String result) {
        if (aftersaleOrderMapper.casStatus(id, AftersaleConsts.STATUS_REFUNDED,
                AftersaleConsts.STATUS_COMPLETED, result) == 0) {
            throw new BusinessException("完成失败:售后单不是已退款状态");
        }
    }
}
