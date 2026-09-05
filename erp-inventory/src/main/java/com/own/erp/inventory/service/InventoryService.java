package com.own.erp.inventory.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.inventory.entity.Inventory;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryFlowMapper;
import com.own.erp.inventory.mapper.InventoryMapper;
import com.own.erp.inventory.request.query.InventoryQuery;
import com.own.erp.inventory.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存服务:inventory 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     数量列唯一改动路径 change()(docs/03 §4 / docs/07 铁律 4):同事务更新 inventory 并写 inventory_flow,
 *     禁止任何旁路 update;对外只提供只读查询,写入口只有 change()
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryFlowMapper inventoryFlowMapper;

    /** 分页查询(过滤:SKU/仓库) */
    public Page<InventoryResponse> page(InventoryQuery query) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(query.getSkuId() != null, Inventory::getSkuId, query.getSkuId())
                .eq(query.getWarehouseId() != null, Inventory::getWarehouseId, query.getWarehouseId())
                .orderByDesc(Inventory::getId);
        Page<Inventory> result = inventoryMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<InventoryResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(InventoryResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public InventoryResponse getById(Long id) {
        Inventory inventory = inventoryMapper.selectById(id);
        return inventory == null ? null : InventoryResponse.from(inventory);
    }

    /**
     * 库存引用计数(#5 SKU 删除校验,经 erp-contract 接口暴露):skuIds 在 inventory 的行数。
     * 行由 change() 自动建,有行即发生过库存业务——删 SKU 前须人工先清库存
     */
    public long countBySkuIds(Collection<Long> skuIds) {
        if (CollUtil.isEmpty(skuIds)) {
            return 0;
        }
        Long count = inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>()
                .in(Inventory::getSkuId, skuIds));
        return count == null ? 0L : count;
    }

    /**
     * 仓库引用计数(#7 仓库删除校验,经 erp-contract WarehouseApi 暴露):warehouse_id 在 inventory 的行数。
     * 行由 change() 自动建,有行即发生过库存业务——删仓库前须先迁走/清掉库存
     */
    public long countByWarehouseId(Long warehouseId) {
        if (warehouseId == null) {
            return 0;
        }
        Long count = inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseId, warehouseId));
        return count == null ? 0L : count;
    }

    /**
     * 库存变更唯一入口(docs/07 铁律 4):同事务更新 inventory 并写 inventory_flow。
     * quantity 正负数(IN_PURCHASE/IN_RETURN 为正,OUT_SHIP/TRANSFER_OUT 为负,ADJUST 皆可);
     * before/after 记 qty_available 变更轨迹;行不存在自动建行(其余数量列 0 起步);
     * after < 0 拒绝(可用库存不可为负)。
     * 并发(docs/07 §1 ① 正确性锁落 DB,禁 check-then-act 读改写丢更新):存量行走一条原子 UPDATE
     * (余额条件在 WHERE,行锁至事务提交,affected=0 回查区分行不存在/余额不足);
     * 首建并发撞 uk_sku_wh 捕 DuplicateKeyException 回退原子 UPDATE 重试一轮
     * (不用 INSERT IGNORE:会把非重复键错误一并吞成 warning);不加应用层锁/version 列。
     *
     * @return 流水ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long change(InventoryFlow flow) {
        if (flow.getSkuId() == null || flow.getWarehouseId() == null) {
            throw new BusinessException("SKU与仓库必填");
        }
        if (flow.getQuantity() == null || flow.getQuantity() == 0) {
            throw new BusinessException("库存变动数量不能为0");
        }
        // TODO(#7): 按 flow_type 差异化维护 qty_locked/qty_transit(发货占用/取消释放/采购在途入库)随 #4 订单域补齐,
        //           原子 UPDATE 与首建两个分支都要覆盖;TRANSFER_OUT/TRANSFER_IN 跨仓由上层组合两次 change() 或另设 transfer()
        for (int attempt = 0; ; attempt++) {
            // 存量行:原子 UPDATE(算术与负库存校验都下 SQL,行锁串行化同行并发变更)
            if (inventoryMapper.updateAvailableDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity()) == 1) {
                // 同事务回读取 after(行已被本事务 X 锁锁定至提交,读到即稳定值)
                return recordFlow(flow, loadRow(flow));
            }
            Inventory row = loadRow(flow);
            if (row != null) {
                if (row.getQtyAvailable() + flow.getQuantity() < 0) {
                    throw new BusinessException("可用库存不足:当前" + row.getQtyAvailable() + ",变动" + flow.getQuantity());
                }
                // 微秒级窗口:行在 UPDATE 未命中与回查之间被他方首建且余额足够,重走原子 UPDATE
                ensureRetryable(attempt);
                continue;
            }
            if (flow.getQuantity() < 0) {
                throw new BusinessException("可用库存不足:当前0,变动" + flow.getQuantity());
            }
            // 新行一次成型(builder,docs/07 §1 分级⑤):qty_on_hand 起步 0 + 首笔,其余数量列 0 起步
            Inventory created = Inventory.builder()
                    .skuId(flow.getSkuId())
                    .warehouseId(flow.getWarehouseId())
                    .qtyOnHand(flow.getQuantity())
                    .qtyLocked(0)
                    .qtyTransit(0)
                    .qtyAvailable(flow.getQuantity())
                    .build();
            try {
                inventoryMapper.insert(created);
            } catch (DuplicateKeyException e) {
                // 并发首建撞 uk_sku_wh:他方已建行,回退原子 UPDATE 走存量分支
                ensureRetryable(attempt);
                continue;
            }
            return recordFlow(flow, null);
        }
    }

    /** 写流水:before/after 记 qty_available 轨迹(row=null 为首建行,after 即首笔数量);返回流水ID */
    private Long recordFlow(InventoryFlow flow, Inventory row) {
        int after = row == null ? flow.getQuantity() : row.getQtyAvailable();
        flow.setBeforeQty(after - flow.getQuantity());
        flow.setAfterQty(after);
        inventoryFlowMapper.insert(flow);
        return flow.getId();
    }

    /** 回查 (sku, warehouse) 行:区分原子 UPDATE 未命中是"行不存在"还是"余额不足" */
    private Inventory loadRow(InventoryFlow flow) {
        return inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSkuId, flow.getSkuId())
                .eq(Inventory::getWarehouseId, flow.getWarehouseId()));
    }

    /** 重试上限:一轮重试足够覆盖并发首建窗口,两轮仍冲突按业务冲突上抛(极端竞争,调用方可重试) */
    private void ensureRetryable(int attempt) {
        if (attempt >= 1) {
            throw new BusinessException("库存变更并发冲突,请重试");
        }
    }
}
