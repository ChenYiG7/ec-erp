package com.own.erp.purchase.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.purchase.constant.PurchaseConsts;
import com.own.erp.purchase.entity.PurchaseInbound;
import com.own.erp.purchase.entity.PurchaseOrder;
import com.own.erp.purchase.entity.PurchaseOrderItem;
import com.own.erp.purchase.entity.Supplier;
import com.own.erp.purchase.mapper.PurchaseInboundMapper;
import com.own.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.own.erp.purchase.mapper.PurchaseOrderMapper;
import com.own.erp.purchase.mapper.SupplierMapper;
import com.own.erp.purchase.request.command.PurchaseOrderItemSaveRequest;
import com.own.erp.purchase.request.command.PurchaseOrderSaveRequest;
import com.own.erp.purchase.request.query.PurchaseOrderQuery;
import com.own.erp.purchase.response.PurchaseOrderItemResponse;
import com.own.erp.purchase.response.PurchaseOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购单服务:purchase_order(+purchase_order_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     状态机(#10):DRAFT(可改/删)→ audit → AUDITED(可入库/关闭)→ (PARTIAL_RECEIVED)→ RECEIVED → CLOSED;
 *     流转一律走条件更新(WHERE 即守卫,docs/07 §6.3 禁先查后改),禁旁路 update 状态列;
 *     totalAmount 服务端按 Σ(数量×单价) 计算,与明细强一致;跨域存在性校验走 erp-contract(禁横向依赖,铁律 2)
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final SupplierMapper supplierMapper;
    private final GoodsSkuApi goodsSkuApi;
    private final WarehouseApi warehouseApi;

    /** 入库核销回写行:po_item_id + 本次入库数量(confirm 逐行调用,同事务) */
    public record ReceiveLine(Long poItemId, Integer quantity) {
    }

    /**
     * 采购明细引用计数(#5 SKU 删除校验,经 erp-contract 接口暴露):skuIds 在 purchase_order_item 的行数。
     * 子表仅 entity+mapper,计数由主表域收口(同域内 Service 管 Mapper,docs/07 §2.1)
     */
    public long countItemRefsBySkuIds(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return 0;
        }
        Long count = purchaseOrderItemMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderItem>()
                .in(PurchaseOrderItem::getSkuId, skuIds));
        return count == null ? 0L : count;
    }

    /** 供应商采购笔数(#10 供应商删除校验):>0 即已发生业务,禁删供应商 */
    public long countBySupplierId(Long supplierId) {
        Long count = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getSupplierId, supplierId));
        return count == null ? 0L : count;
    }

    /** 仓库采购单引用数(#7 仓库删除校验,经 erp-contract WarehouseApi 暴露):>0 即有采购业务,禁删仓库 */
    public long countByWarehouseId(Long warehouseId) {
        if (warehouseId == null) {
            return 0;
        }
        Long count = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getWarehouseId, warehouseId));
        return count == null ? 0L : count;
    }

    /** 分页查询(默认按 id 倒序;过滤条件在 PurchaseOrderQuery 加字段后在此补 Wrapper 条件);列表不带明细 */
    public Page<PurchaseOrderResponse> page(PurchaseOrderQuery query) {
        Page<PurchaseOrder> result = purchaseOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<PurchaseOrder>().orderByDesc(PurchaseOrder::getId));
        Page<PurchaseOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(PurchaseOrderResponse::from).toList());
        return responsePage;
    }

    /** 详情带明细(withItems wither 副本);不存在返回 null */
    public PurchaseOrderResponse getById(Long id) {
        PurchaseOrder purchaseOrder = purchaseOrderMapper.selectById(id);
        if (purchaseOrder == null) {
            return null;
        }
        List<PurchaseOrderItemResponse> items = purchaseOrderItemMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrderItem>()
                                .eq(PurchaseOrderItem::getPoId, id)
                                .orderByAsc(PurchaseOrderItem::getId))
                .stream().map(PurchaseOrderItemResponse::from).toList();
        return PurchaseOrderResponse.from(purchaseOrder).withItems(items);
    }

    /** 新增:引用校验 → DRAFT + Σ金额 落主表 → 落明细(同事务);单号撞 uk 友好报错 */
    @Transactional(rollbackFor = Exception.class)
    public Long save(PurchaseOrderSaveRequest request) {
        validateRefs(request);
        PurchaseOrder purchaseOrder = request.toEntity();
        // 写前回填服务端管理列(setter 白名单:status 固定草稿,金额按明细汇总)
        purchaseOrder.setStatus(PurchaseConsts.PO_DRAFT);
        purchaseOrder.setTotalAmount(sumTotal(request.items()));
        try {
            purchaseOrderMapper.insert(purchaseOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("采购单号已存在:" + request.poNo());
        }
        insertItems(purchaseOrder.getId(), request.items());
        return purchaseOrder.getId();
    }

    /**
     * 更新:仅 DRAFT 可改(审核后单据禁改,改单走删除重建);明细整体替换并重算金额。
     * 注意这里是有业务规则的多表操作,与生成骨架的"大而全 update"不同——更新语义按状态收窄(docs/07 §6.3)
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseOrderSaveRequest request) {
        PurchaseOrder exist = purchaseOrderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("采购单不存在:" + id);
        }
        if (!PurchaseConsts.PO_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可修改,当前:" + exist.getStatus());
        }
        validateRefs(request);
        PurchaseOrder purchaseOrder = request.toEntity();
        purchaseOrder.setId(id);
        purchaseOrder.setTotalAmount(sumTotal(request.items()));
        try {
            purchaseOrderMapper.updateById(purchaseOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("采购单号已存在:" + request.poNo());
        }
        purchaseOrderItemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getPoId, id));
        insertItems(id, request.items());
    }

    /** 审核:DRAFT → AUDITED(管理权限在 Controller @PreAuthorize);非草稿/单不存在即拒 */
    public void audit(Long id) {
        if (purchaseOrderMapper.casStatus(id, PurchaseConsts.PO_DRAFT, PurchaseConsts.PO_AUDITED) == 0) {
            throw new BusinessException("审核失败:采购单不存在或不是草稿状态");
        }
    }

    /** 关闭:AUDITED/PARTIAL_RECEIVED/RECEIVED → CLOSED(剩余量作废);草稿单走删除 */
    public void close(Long id) {
        if (purchaseOrderMapper.closeOrder(id) == 0) {
            throw new BusinessException("关闭失败:采购单不存在或已是终态(草稿/已关闭)");
        }
    }

    /** 删除:仅 DRAFT 且无入库记录(防御:入库仅审核后可建,此处兜底人工改数场景);明细同事务删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseOrder exist = purchaseOrderMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (!PurchaseConsts.PO_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可删除,当前:" + exist.getStatus() + ",请走关闭流程");
        }
        Long inboundCount = purchaseInboundMapper.selectCount(new LambdaQueryWrapper<PurchaseInbound>()
                .eq(PurchaseInbound::getPoId, id));
        if (inboundCount != null && inboundCount > 0) {
            throw new BusinessException("采购单已有入库记录,禁删除");
        }
        purchaseOrderItemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getPoId, id));
        purchaseOrderMapper.deleteById(id);
    }

    /**
     * 取可收货采购单(#10 入库建单校验,同模块 PurchaseInboundService 调用):
     * 单不存在或状态不在 {AUDITED, PARTIAL_RECEIVED} 即拒(草稿未审核/已收齐/已关闭/终态不可再入库)
     */
    public PurchaseOrder requireReceivable(Long poId) {
        PurchaseOrder purchaseOrder = purchaseOrderMapper.selectById(poId);
        if (purchaseOrder == null) {
            throw new BusinessException("采购单不存在:" + poId);
        }
        String status = purchaseOrder.getStatus();
        if (!PurchaseConsts.PO_AUDITED.equals(status) && !PurchaseConsts.PO_PARTIAL_RECEIVED.equals(status)) {
            throw new BusinessException("采购单当前不可入库,状态:" + status);
        }
        return purchaseOrder;
    }

    /** 采购明细实体列表(同模块服务间取数,entity 不出本模块;按 id 升序稳定排序) */
    public List<PurchaseOrderItem> listItemEntities(Long poId) {
        return purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getPoId, poId)
                .orderByAsc(PurchaseOrderItem::getId));
    }

    /**
     * 入库核销回写(#10,由 PurchaseInboundService.confirm 在同事务内调用):
     * ①逐行原子累加 arrived_qty(防超收条件进 WHERE,并发多入库单核销同一采购单时行锁串行化);
     * ②回读明细算推进状态:全部收齐 → RECEIVED,任一已收 → PARTIAL_RECEIVED;
     * ③条件更新推进(与 close 并发互斥,单被关闭则本事务整体回滚)。
     * REQUIRED 语义:confirm 自持事务则加入同事务;防直调脱离事务(逐条自提交破原子性)自挂注解兜底
     */
    @Transactional(rollbackFor = Exception.class)
    public void receiveQuantities(Long poId, List<ReceiveLine> lines) {
        for (ReceiveLine line : lines) {
            if (purchaseOrderItemMapper.increaseArrivedQty(line.poItemId(), line.quantity()) == 0) {
                throw new BusinessException("入库数量超出采购明细剩余量或明细不存在:poItemId=" + line.poItemId());
            }
        }
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getPoId, poId));
        boolean allReceived = !items.isEmpty() && items.stream()
                .allMatch(item -> nvl(item.getArrivedQty()) >= nvl(item.getQuantity()));
        String nextStatus = allReceived ? PurchaseConsts.PO_RECEIVED : PurchaseConsts.PO_PARTIAL_RECEIVED;
        if (purchaseOrderMapper.advanceOnReceive(poId, nextStatus) == 0) {
            throw new BusinessException("采购单状态已变化(被关闭),入库核销失败:poId=" + poId);
        }
    }

    /** 落库前引用与明细校验(建单/改单共用;@Valid 兜 HTTP 侧,此处兜直接调用侧) */
    private void validateRefs(PurchaseOrderSaveRequest request) {
        if (CollUtil.isEmpty(request.items())) {
            throw new BusinessException("采购明细不能为空");
        }
        if (request.items().stream().anyMatch(item -> item.quantity() == null || item.quantity() <= 0)) {
            throw new BusinessException("采购数量必须大于0");
        }
        List<Long> skuIds = request.items().stream().map(PurchaseOrderItemSaveRequest::skuId).toList();
        if (new HashSet<>(skuIds).size() != skuIds.size()) {
            throw new BusinessException("同一 SKU 在采购单内重复,请合并为一行");
        }
        Supplier supplier = supplierMapper.selectById(request.supplierId());
        if (supplier == null) {
            throw new BusinessException("供应商不存在:" + request.supplierId());
        }
        if (!warehouseApi.existsWarehouse(request.warehouseId())) {
            throw new BusinessException("仓库不存在:" + request.warehouseId());
        }
        for (Long skuId : skuIds) {
            if (!goodsSkuApi.existsSku(skuId)) {
                throw new BusinessException("SKU 不存在:" + skuId);
            }
        }
    }

    /** 落明细行(builder 纯构造装配,docs/07 §1 分级⑤):arrivedQty 服务端管理列 0 起步 */
    private void insertItems(Long poId, List<PurchaseOrderItemSaveRequest> items) {
        for (PurchaseOrderItemSaveRequest item : items) {
            purchaseOrderItemMapper.insert(PurchaseOrderItem.builder()
                    .poId(poId)
                    .skuId(item.skuId())
                    .quantity(item.quantity())
                    .arrivedQty(0)
                    .purchasePrice(item.purchasePrice() == null ? BigDecimal.ZERO : item.purchasePrice())
                    .build());
        }
    }

    /** 金额汇总红线:Σ(数量×单价),单价格按 0 计(赠品);BigDecimal 全程,禁 double(docs/07 §1) */
    private BigDecimal sumTotal(List<PurchaseOrderItemSaveRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItemSaveRequest item : items) {
            BigDecimal price = item.purchasePrice() == null ? BigDecimal.ZERO : item.purchasePrice();
            total = total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
        }
        return total;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
