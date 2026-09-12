package com.own.erp.inventory.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.TransferConsts;
import com.own.erp.inventory.entity.TransferOrder;
import com.own.erp.inventory.entity.TransferOrderItem;
import com.own.erp.inventory.mapper.TransferOrderItemMapper;
import com.own.erp.inventory.mapper.TransferOrderMapper;
import com.own.erp.inventory.request.command.TransferOrderItemSaveRequest;
import com.own.erp.inventory.request.command.TransferOrderSaveRequest;
import com.own.erp.inventory.request.query.TransferOrderQuery;
import com.own.erp.inventory.response.TransferOrderItemResponse;
import com.own.erp.inventory.response.TransferOrderResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单服务:transfer_order(+transfer_order_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     状态机(仓内作业,docs/plans/warehouse-ops.md §2.2):DRAFT(可改/删/取消)→ confirm → CONFIRMED(V1 确认即达:
 *     两腿已动账,禁改禁删禁取消);CANCELED 旁路。流转一律条件更新(WHERE 即守卫,CONFIRMED 幂等靠 affected=0 拦)。
 *     CONFIRM 复合事务:占位条件更新(并发双确认/重复确认仅一个成功,失败随事务回滚)→ 逐行调
 *     InventoryService.transfer() 原语(同事务 TRANSFER_OUT 负 + TRANSFER_IN 正,biz_type=TRANSFER_ORDER 收口,
 *     biz_id=调拨单 id);任一腿可用不足整体回滚。V1 无在途账(确认即达);在途模式(OUT 占用→到货 IN)留 TODO#30 拍板。
 *     跨域存在性校验走 erp-contract(禁横向依赖,铁律 2)
 */
@Service
public class TransferOrderService {

    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final InventoryService inventoryService;
    private final WarehouseApi warehouseApi;
    private final GoodsSkuApi goodsSkuApi;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public TransferOrderService(TransferOrderMapper transferOrderMapper,
                                TransferOrderItemMapper transferOrderItemMapper,
                                InventoryService inventoryService,
                                @Lazy WarehouseApi warehouseApi,
                                @Lazy GoodsSkuApi goodsSkuApi,
                                @Lazy CurrentUserApi currentUserApi) {
        this.transferOrderMapper = transferOrderMapper;
        this.transferOrderItemMapper = transferOrderItemMapper;
        this.inventoryService = inventoryService;
        this.warehouseApi = warehouseApi;
        this.goodsSkuApi = goodsSkuApi;
        this.currentUserApi = currentUserApi;
    }

    /** 分页查询(按 id 倒序;过滤:调拨单号模糊/调出仓/调入仓/状态);列表不带明细 */
    public Page<TransferOrderResponse> page(TransferOrderQuery query) {
        Page<TransferOrder> result = transferOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<TransferOrder>()
                        .like(StrUtil.isNotBlank(query.getTransferNo()), TransferOrder::getTransferNo, query.getTransferNo())
                        .eq(query.getFromWarehouseId() != null, TransferOrder::getFromWarehouseId, query.getFromWarehouseId())
                        .eq(query.getToWarehouseId() != null, TransferOrder::getToWarehouseId, query.getToWarehouseId())
                        .eq(StrUtil.isNotBlank(query.getStatus()), TransferOrder::getStatus, query.getStatus())
                        .orderByDesc(TransferOrder::getId));
        Page<TransferOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(TransferOrderResponse::from).toList());
        return responsePage;
    }

    /** 详情带明细(withItems wither 副本);不存在返回 null */
    public TransferOrderResponse getById(Long id) {
        TransferOrder transferOrder = transferOrderMapper.selectById(id);
        if (transferOrder == null) {
            return null;
        }
        List<TransferOrderItemResponse> items = listItems(id).stream().map(TransferOrderItemResponse::from).toList();
        return TransferOrderResponse.from(transferOrder).withItems(items);
    }

    /** 建单(DRAFT):引用校验 → 落主表(状态固定 DRAFT)→ 落明细(同事务);单号撞 uk 友好报错 */
    @Transactional(rollbackFor = Exception.class)
    public Long save(TransferOrderSaveRequest request) {
        validateRefs(request);
        TransferOrder transferOrder = request.toEntity();
        // 写前回填服务端管理列(setter 白名单):状态固定草稿,createdBy 按 SecurityContext
        transferOrder.setStatus(TransferConsts.STATUS_DRAFT);
        transferOrder.setCreatedBy(currentUserApi.currentUserId());
        try {
            transferOrderMapper.insert(transferOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("调拨单号已存在:" + request.transferNo());
        }
        insertItems(transferOrder.getId(), request.items());
        return transferOrder.getId();
    }

    /**
     * 更新:仅 DRAFT 可改(确认后单据禁改,改单走取消重建);明细整体替换。
     * 行锁读(selectByIdForUpdate)串行化与 confirm 的竞态(禁 check-then-act 串状态,docs/07 §6.3)
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TransferOrderSaveRequest request) {
        TransferOrder exist = transferOrderMapper.selectByIdForUpdate(id);
        if (exist == null) {
            throw new BusinessException("调拨单不存在:" + id);
        }
        if (!TransferConsts.STATUS_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可修改,当前:" + exist.getStatus());
        }
        validateRefs(request);
        TransferOrder transferOrder = request.toEntity();
        transferOrder.setId(id);
        try {
            transferOrderMapper.updateById(transferOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("调拨单号已存在:" + request.transferNo());
        }
        transferOrderItemMapper.delete(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        insertItems(id, request.items());
    }

    /**
     * 确认调拨(V1 确认即达,复合事务核心):DRAFT → CONFIRMED 条件更新占位(并发双确认/重复确认仅一个成功,
     * affected=0 拒;失败由事务整体回滚)→ 逐行调 InventoryService.transfer() 原语
     * (同事务两腿 TRANSFER_OUT 负/TRANSFER_IN 正,biz_type=TRANSFER_ORDER,biz_id=本单);任一腿可用不足整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        if (transferOrderMapper.casStatus(id, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CONFIRMED) == 0) {
            throw new BusinessException("确认失败:调拨单不存在或不是草稿状态");
        }
        TransferOrder order = transferOrderMapper.selectById(id);
        List<TransferOrderItem> items = listItems(id);
        if (CollUtil.isEmpty(items)) {
            throw new BusinessException("调拨单无明细,禁止确认:" + id);
        }
        for (TransferOrderItem item : items) {
            try {
                inventoryService.transfer(item.getSkuId(), order.getFromWarehouseId(), order.getToWarehouseId(),
                        item.getQuantity(), "调拨单:" + order.getTransferNo(), order.getCreatedBy(), id);
            } catch (BusinessException e) {
                throw new BusinessException("调拨失败(SKU=" + item.getSkuId() + "):" + e.getMessage());
            }
        }
    }

    /** 取消:仅 DRAFT → CANCELED 条件更新占位(并发取消/取消与确认竞态仅一个成功);已确认库存已动账走反向调拨 */
    public void cancel(Long id) {
        TransferOrder exist = transferOrderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("调拨单不存在:" + id);
        }
        if (!TransferConsts.STATUS_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("取消失败:仅草稿状态可取消,当前:" + exist.getStatus());
        }
        if (transferOrderMapper.casStatus(id, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CANCELED) == 0) {
            throw new BusinessException("取消失败:调拨单状态已变化,请刷新重试:" + id);
        }
    }

    /** 删除:CONFIRMED 禁删(库存已动账,删单致账实无法追溯);DRAFT/CANCELED 连明细同事务硬删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TransferOrder exist = transferOrderMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (TransferConsts.STATUS_CONFIRMED.equals(exist.getStatus())) {
            throw new BusinessException("已确认调拨单禁止删除(库存已动账):" + id);
        }
        transferOrderItemMapper.delete(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        transferOrderMapper.deleteById(id);
    }

    /**
     * 仓库调拨单引用数(#30 余量,仓库删除校验经 erp-contract WarehouseApi 暴露):调出/调入任一腿引用即计数。
     * 调出仓 ≠ 调入仓(validateRefs 拦),单行不会双计;已确认单库存已动账,草稿单是业务凭证,均禁删仓
     */
    public long countByWarehouseId(Long warehouseId) {
        if (warehouseId == null) {
            return 0;
        }
        Long count = transferOrderMapper.selectCount(new LambdaQueryWrapper<TransferOrder>()
                .and(w -> w.eq(TransferOrder::getFromWarehouseId, warehouseId)
                        .or()
                        .eq(TransferOrder::getToWarehouseId, warehouseId)));
        return count == null ? 0L : count;
    }

    /** 明细实体列表(同模块内取数,entity 不出本模块;按 id 升序稳定排序) */
    private List<TransferOrderItem> listItems(Long transferId) {
        return transferOrderItemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, transferId)
                .orderByAsc(TransferOrderItem::getId));
    }

    /** 落明细行(builder 纯构造装配,docs/07 §1 分级⑤) */
    private void insertItems(Long transferId, List<TransferOrderItemSaveRequest> items) {
        for (TransferOrderItemSaveRequest item : items) {
            transferOrderItemMapper.insert(TransferOrderItem.builder()
                    .transferId(transferId)
                    .skuId(item.skuId())
                    .quantity(item.quantity())
                    .build());
        }
    }

    /** 落库前引用与明细校验(建单/改单共用;@Valid 兜 HTTP 侧,此处兜直接调用侧) */
    private void validateRefs(TransferOrderSaveRequest request) {
        if (StrUtil.isBlank(request.transferNo())) {
            throw new BusinessException("调拨单号必填");
        }
        if (request.fromWarehouseId() == null || request.toWarehouseId() == null) {
            throw new BusinessException("调出仓与调入仓必填");
        }
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new BusinessException("调出仓与调入仓不能相同");
        }
        if (CollUtil.isEmpty(request.items())) {
            throw new BusinessException("调拨明细不能为空");
        }
        if (request.items().stream().anyMatch(item -> item.quantity() == null || item.quantity() <= 0)) {
            throw new BusinessException("调拨数量必须大于0");
        }
        List<Long> skuIds = request.items().stream().map(TransferOrderItemSaveRequest::skuId).toList();
        if (new HashSet<>(skuIds).size() != skuIds.size()) {
            throw new BusinessException("同一 SKU 在调拨单内重复,请合并为一行");
        }
        if (!warehouseApi.existsWarehouse(request.fromWarehouseId())) {
            throw new BusinessException("调出仓不存在:" + request.fromWarehouseId());
        }
        if (!warehouseApi.existsWarehouse(request.toWarehouseId())) {
            throw new BusinessException("调入仓不存在:" + request.toWarehouseId());
        }
        for (Long skuId : skuIds) {
            if (!goodsSkuApi.existsSku(skuId)) {
                throw new BusinessException("SKU 不存在:" + skuId);
            }
        }
    }
}
