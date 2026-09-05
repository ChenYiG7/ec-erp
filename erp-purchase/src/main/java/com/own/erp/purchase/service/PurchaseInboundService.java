package com.own.erp.purchase.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.purchase.constant.PurchaseConsts;
import com.own.erp.purchase.entity.PurchaseInbound;
import com.own.erp.purchase.entity.PurchaseInboundItem;
import com.own.erp.purchase.entity.PurchaseOrder;
import com.own.erp.purchase.entity.PurchaseOrderItem;
import com.own.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.own.erp.purchase.mapper.PurchaseInboundMapper;
import com.own.erp.purchase.request.command.PurchaseInboundItemSaveRequest;
import com.own.erp.purchase.request.command.PurchaseInboundSaveRequest;
import com.own.erp.purchase.request.query.PurchaseInboundQuery;
import com.own.erp.purchase.response.PurchaseInboundItemResponse;
import com.own.erp.purchase.response.PurchaseInboundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购入库单服务:purchase_inbound(+purchase_inbound_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     入库单状态机(#10):PENDING(可改/删/取消)→ confirm → RECEIVED(核销完成,禁删改)/ CANCELLED(终态);
 *     confirm = 本系统唯一动库存的入库路径:同事务内 ①状态占位(条件更新防并发双确认)→
 *     ②逐行经 InventoryChangeApi 走 InventoryService.change 唯一入口写 flow(flow_type=IN_PURCHASE,docs/07 铁律 4)→
 *     ③PurchaseOrderService.receiveQuantities 回写 arrived_qty 并推进采购单状态;
 *     任一步失败整体回滚,库存/流水/核销/状态四者强一致
 */
@Service
@RequiredArgsConstructor
public class PurchaseInboundService {

    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final InventoryChangeApi inventoryChangeApi;

    /** 分页查询(默认按 id 倒序;过滤条件在 PurchaseInboundQuery 加字段后在此补 Wrapper 条件);列表不带明细 */
    public Page<PurchaseInboundResponse> page(PurchaseInboundQuery query) {
        Page<PurchaseInbound> result = purchaseInboundMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<PurchaseInbound>().orderByDesc(PurchaseInbound::getId));
        Page<PurchaseInboundResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(PurchaseInboundResponse::from).toList());
        return responsePage;
    }

    /** 详情带明细(withItems wither 副本);不存在返回 null */
    public PurchaseInboundResponse getById(Long id) {
        PurchaseInbound purchaseInbound = purchaseInboundMapper.selectById(id);
        if (purchaseInbound == null) {
            return null;
        }
        List<PurchaseInboundItemResponse> items = purchaseInboundItemMapper.selectList(
                        new LambdaQueryWrapper<PurchaseInboundItem>()
                                .eq(PurchaseInboundItem::getInboundId, id)
                                .orderByAsc(PurchaseInboundItem::getId))
                .stream().map(PurchaseInboundItemResponse::from).toList();
        return PurchaseInboundResponse.from(purchaseInbound).withItems(items);
    }

    /**
     * 创建入库单(待入库):校验采购单可收货 + 明细行合法(po_item 归属/剩余量/不重复)→
     * PENDING + 入库仓取采购单收货仓(服务端管理列)→ 落主表与明细(同事务)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(PurchaseInboundSaveRequest request) {
        PurchaseOrder purchaseOrder = purchaseOrderService.requireReceivable(request.poId());
        Map<Long, PurchaseOrderItem> poItemById = poItemMap(purchaseOrder.getId());
        List<PurchaseInboundItem> lines = assembleLines(request.items(), poItemById);
        PurchaseInbound purchaseInbound = request.toEntity();
        // 写前回填服务端管理列(setter 白名单):状态固定待入库,入库仓锁采购单收货仓
        purchaseInbound.setStatus(PurchaseConsts.INBOUND_PENDING);
        purchaseInbound.setWarehouseId(purchaseOrder.getWarehouseId());
        try {
            purchaseInboundMapper.insert(purchaseInbound);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("入库单号已存在:" + request.inboundNo());
        }
        for (PurchaseInboundItem line : lines) {
            line.setInboundId(purchaseInbound.getId());
            purchaseInboundItemMapper.insert(line);
        }
        return purchaseInbound.getId();
    }

    /**
     * 更新:仅 PENDING 可改(待入库单调整明细);明细整体替换。已有数量约束在确认时原子兜底,
     * 此处按当前剩余量预校验
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseInboundSaveRequest request) {
        PurchaseInbound exist = purchaseInboundMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("入库单不存在:" + id);
        }
        if (!PurchaseConsts.INBOUND_PENDING.equals(exist.getStatus())) {
            throw new BusinessException("仅待入库状态可修改,当前:" + exist.getStatus());
        }
        PurchaseOrder purchaseOrder = purchaseOrderService.requireReceivable(request.poId());
        if (!purchaseOrder.getId().equals(exist.getPoId())) {
            throw new BusinessException("入库单不允许变更关联采购单,请删除重建:" + id);
        }
        List<PurchaseInboundItem> lines = assembleLines(request.items(), poItemMap(purchaseOrder.getId()));
        PurchaseInbound purchaseInbound = request.toEntity();
        purchaseInbound.setId(id);
        try {
            purchaseInboundMapper.updateById(purchaseInbound);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("入库单号已存在:" + request.inboundNo());
        }
        purchaseInboundItemMapper.delete(new LambdaQueryWrapper<PurchaseInboundItem>()
                .eq(PurchaseInboundItem::getInboundId, id));
        for (PurchaseInboundItem line : lines) {
            line.setInboundId(id);
            purchaseInboundItemMapper.insert(line);
        }
    }

    /**
     * 确认入库(核销,#10 核心):PENDING → RECEIVED,同事务完成库存动账与采购核销。
     * ①条件更新占位 RECEIVED(并发双确认/重复确认仅一个成功,affected=0 拒;失败由事务整体回滚);
     * ②逐行库存变更(唯一入口 InventoryService.change,flow_type=IN_PURCHASE,biz 指向本入库单);
     * ③receiveQuantities 原子累加 arrived_qty 并推进采购单状态(超收/单被关闭在此原子兜底);
     * 流水操作人记入库单创建人(确认人维度待前端接 SecurityContext 后补)
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        PurchaseInbound inbound = purchaseInboundMapper.selectById(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在:" + id);
        }
        if (purchaseInboundMapper.casStatus(id, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_RECEIVED) == 0) {
            throw new BusinessException("确认失败:入库单不存在或不是待入库状态");
        }
        List<PurchaseInboundItem> lines = purchaseInboundItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseInboundItem>().eq(PurchaseInboundItem::getInboundId, id));
        if (CollUtil.isEmpty(lines)) {
            throw new BusinessException("入库单无明细,禁止确认:" + id);
        }
        for (PurchaseInboundItem line : lines) {
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(line.getSkuId())
                    .warehouseId(inbound.getWarehouseId())
                    .quantity(line.getInboundQty())
                    .flowType(InventoryConsts.FLOW_TYPE_IN_PURCHASE)
                    .bizType(PurchaseConsts.BIZ_TYPE_PURCHASE_INBOUND)
                    .bizId(id)
                    .remark("入库单:" + inbound.getInboundNo())
                    .createdBy(inbound.getCreatedBy())
                    .build());
        }
        purchaseOrderService.receiveQuantities(inbound.getPoId(),
                lines.stream().map(line -> new PurchaseOrderService.ReceiveLine(line.getPoItemId(), line.getInboundQty()))
                        .toList());
    }

    /** 取消:仅 PENDING → CANCELLED(终态);已入库走删除保护,不可取消 */
    public void cancel(Long id) {
        if (purchaseInboundMapper.casStatus(id, PurchaseConsts.INBOUND_PENDING, PurchaseConsts.INBOUND_CANCELLED) == 0) {
            throw new BusinessException("取消失败:入库单不存在或不是待入库状态");
        }
    }

    /** 删除:RECEIVED 禁删(库存已动账,删单致账实无法追溯);PENDING/CANCELLED 连明细同事务删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseInbound exist = purchaseInboundMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (PurchaseConsts.INBOUND_RECEIVED.equals(exist.getStatus())) {
            throw new BusinessException("已入库单据禁止删除(库存已动账):" + id);
        }
        purchaseInboundItemMapper.delete(new LambdaQueryWrapper<PurchaseInboundItem>()
                .eq(PurchaseInboundItem::getInboundId, id));
        purchaseInboundMapper.deleteById(id);
    }

    /** 校验并装配入库明细行:po_item 归属校验 + 剩余量预校验(确认时原子兜底)+ sku 冗余回填 + 行去重 */
    private List<PurchaseInboundItem> assembleLines(List<PurchaseInboundItemSaveRequest> requests,
                                                    Map<Long, PurchaseOrderItem> poItemById) {
        if (CollUtil.isEmpty(requests)) {
            throw new BusinessException("入库明细不能为空");
        }
        HashSet<Long> seenPoItemIds = new HashSet<>();
        List<PurchaseInboundItem> lines = requests.stream().map(request -> {
            PurchaseOrderItem poItem = poItemById.get(request.poItemId());
            if (poItem == null) {
                throw new BusinessException("入库明细不属于该采购单:poItemId=" + request.poItemId());
            }
            if (!seenPoItemIds.add(request.poItemId())) {
                throw new BusinessException("同一采购明细在入库单内重复,请合并为一行:poItemId=" + request.poItemId());
            }
            int remaining = nvl(poItem.getQuantity()) - nvl(poItem.getArrivedQty());
            if (request.inboundQty() > remaining) {
                throw new BusinessException("入库数量超出剩余未收量:poItemId=" + request.poItemId()
                        + ",剩余" + remaining);
            }
            // builder 纯构造装配(docs/07 §1 分级⑤);skuId 从采购明细回填,不收客户端值
            return PurchaseInboundItem.builder()
                    .poItemId(poItem.getId())
                    .skuId(poItem.getSkuId())
                    .inboundQty(request.inboundQty())
                    .build();
        }).toList();
        return lines;
    }

    /** 采购明细按 id 索引(建单/改单共用) */
    private Map<Long, PurchaseOrderItem> poItemMap(Long poId) {
        Map<Long, PurchaseOrderItem> byId = new HashMap<>();
        for (PurchaseOrderItem item : purchaseOrderService.listItemEntities(poId)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
