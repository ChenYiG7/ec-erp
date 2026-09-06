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
 *     禁止任何旁路 update;对外只提供只读查询,写入口只有 change() 与跨仓组合 transfer()。
 *     change 按 flow_type 差异化列语义(#7 2026-09-06,FlowOps 矩阵):采购审核占在途/入库核销(#10)、
 *     发货建单占用/出库核销占用(#11),占用与在途全部落原子 UPDATE 守卫
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    /** 调拨两腿流水关联业务类型(transfer 上层组合专用;调拨单据域立项后由其常量收口) */
    private static final String BIZ_TYPE_INVENTORY_TRANSFER = "INVENTORY_TRANSFER";

    /** 全零行:行不存在时守卫提示的取值基准(仅供文案,不入库) */
    private static final Inventory ZERO_ROW = Inventory.builder()
            .qtyOnHand(0).qtyLocked(0).qtyTransit(0).qtyAvailable(0).build();

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
     * quantity 正负数,列语义随 flow_type(见 {@link FlowOps} 矩阵,2026-09-06 #7 差异化落地);
     * before/after 记 qty_available 变更轨迹(IN_TRANSIT/OUT_SHIP 不动可用,前后相等属正常);
     * 行不存在自动建行(仅限该类型允许的首建形态);守卫不足拒绝(可用不可为负/在途不可透支/出库必须已占用)。
     * 并发(docs/07 §1 ① 正确性锁落 DB,禁 check-then-act 读改写丢更新):存量行按类型走一条原子 UPDATE
     * (守卫条件在 WHERE,行锁至事务提交,affected=0 回查区分行不存在/守卫不足);
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
        FlowOps ops = FlowOps.of(flow.getFlowType());
        for (int attempt = 0; ; attempt++) {
            // 存量行:按类型原子 UPDATE(算术与守卫都下 SQL,行锁串行化同行并发变更)
            if (ops.update(inventoryMapper, flow) == 1) {
                // 同事务回读取 after(行已被本事务 X 锁锁定至提交,读到即稳定值)
                return recordFlow(flow, loadRow(flow));
            }
            Inventory row = loadRow(flow);
            if (row == null) {
                // 新行一次成型(builder,docs/07 §1 分级⑤);类型不允许无行动账(null)即守卫不足拒绝
                Inventory created = ops.newRow(flow);
                if (created == null) {
                    throw new BusinessException(ops.insufficientMsg(ZERO_ROW, flow.getQuantity()));
                }
                try {
                    inventoryMapper.insert(created);
                } catch (DuplicateKeyException e) {
                    // 并发首建撞 uk_sku_wh:他方已建行,回退原子 UPDATE 走存量分支
                    ensureRetryable(attempt);
                    continue;
                }
                return recordFlow(flow, null);
            }
            if (!ops.sufficient(row, flow.getQuantity())) {
                throw new BusinessException(ops.insufficientMsg(row, flow.getQuantity()));
            }
            // 微秒级窗口:行在 UPDATE 未命中与回查之间被他方首建且守卫足够,重走原子 UPDATE
            ensureRetryable(attempt);
        }
    }

    /**
     * 跨仓调拨组合(#7 收口"TRANSFER 由上层组合或另设 transfer"的拍板:组合收口本方法):
     * 同事务 TRANSFER_OUT(源仓负数)+ TRANSFER_IN(目标仓正数),两腿各写一条流水
     * (biz_type=INVENTORY_TRANSFER),任一腿可用不足整体回滚。
     * 目前无调拨单业务域与人工入口,直调预留(调拨单据/审批/前端页面随需求另立项)
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long skuId, Long fromWarehouseId, Long toWarehouseId, int quantity,
                         String remark, Long createdBy) {
        if (quantity <= 0) {
            throw new BusinessException("调拨数量必须大于0");
        }
        if (fromWarehouseId == null || toWarehouseId == null || fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessException("调拨源仓与目标仓必填且不能相同");
        }
        change(InventoryFlow.builder()
                .skuId(skuId).warehouseId(fromWarehouseId).quantity(-quantity)
                .flowType(FlowOps.TRANSFER_OUT.name()).bizType(BIZ_TYPE_INVENTORY_TRANSFER)
                .remark(remark).createdBy(createdBy).build());
        change(InventoryFlow.builder()
                .skuId(skuId).warehouseId(toWarehouseId).quantity(quantity)
                .flowType(FlowOps.TRANSFER_IN.name()).bizType(BIZ_TYPE_INVENTORY_TRANSFER)
                .remark(remark).createdBy(createdBy).build());
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

    /**
     * flow_type → 库存列语义(#7 差异化)。枚举名即 flow_type 字面量(本模块不依赖 erp-contract,
     * 词表三方同步:本枚举 ↔ InventoryConsts ↔ 01_schema_init.sql/docs/03 §4 DDL 注释):
     * <pre>
     * 类型           列变化(Δ=quantity,正负随调用方)                守卫
     * IN_TRANSIT    在途±Δ(采购审核占/关闭释放,仅 qty_transit)      无(释放量调用方按未到货给值)
     * IN_PURCHASE   在途-Δ 在库+Δ 可用+Δ(入库核销)                  在途 >= Δ
     * IN_RETURN     在库+Δ 可用+Δ(售后退货入库)                     可用+Δ >= 0
     * ADJUST        在库+Δ 可用+Δ(人工调整,可正可负)               可用+Δ >= 0
     * TRANSFER_OUT  在库+Δ 可用+Δ(Δ<0,transfer 源腿)               可用+Δ >= 0
     * TRANSFER_IN   在库+Δ 可用+Δ(Δ>0,transfer 目标腿)             可用+Δ >= 0
     * LOCK_SHIP     占用+Δ 可用-Δ(发货建单占/取消释放)              可用-Δ >= 0
     * OUT_SHIP      在库+Δ 占用+Δ(Δ<0,出库核销占用,可用不变)       在库+Δ>=0 且 占用+Δ>=0
     * </pre>
     * 不变量 qty_available = qty_on_hand - qty_locked 对全部类型成立;
     * 无行自动建行仅限:通用形正数(在库=Δ、可用=Δ)与 IN_TRANSIT 正数(在途=Δ、其余 0);
     * 占用/出库/核销类型都需要既有存量行,null 即按守卫不足拒绝。
     * 未知类型拒绝(封闭枚举,防脏流水绕过列语义)
     */
    private enum FlowOps {
        IN_TRANSIT {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.updateTransitDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return true; // 无守卫;存量行 UPDATE 未命中仅发生在并发首建窗口
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "在途库存不足:当前" + nvl(row.getQtyTransit()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                // 审核占在途(+Δ)允许建行;释放(-Δ)无行即脏数据,拒绝
                if (flow.getQuantity() < 0) {
                    return null;
                }
                return Inventory.builder()
                        .skuId(flow.getSkuId()).warehouseId(flow.getWarehouseId())
                        .qtyOnHand(0).qtyLocked(0).qtyTransit(flow.getQuantity()).qtyAvailable(0)
                        .build();
            }
        },
        IN_PURCHASE {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.receiveInbound(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyTransit()) >= quantity;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "在途库存不足(采购单未审核或已超收):当前" + nvl(row.getQtyTransit()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return null; // 核销在途需既有存量行(审核占在途时已建)
            }
        },
        IN_RETURN {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.updateAvailableDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyAvailable()) + quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "可用库存不足:当前" + nvl(row.getQtyAvailable()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return simpleNewRow(flow);
            }
        },
        ADJUST {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.updateAvailableDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyAvailable()) + quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "可用库存不足:当前" + nvl(row.getQtyAvailable()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return simpleNewRow(flow);
            }
        },
        TRANSFER_OUT {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.updateAvailableDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyAvailable()) + quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "可用库存不足:当前" + nvl(row.getQtyAvailable()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return simpleNewRow(flow);
            }
        },
        TRANSFER_IN {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.updateAvailableDelta(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyAvailable()) + quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "可用库存不足:当前" + nvl(row.getQtyAvailable()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return simpleNewRow(flow);
            }
        },
        LOCK_SHIP {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.lockForShip(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyAvailable()) - quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "可用库存不足:当前" + nvl(row.getQtyAvailable()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return null; // 占用需既有可用量,无行即不足
            }
        },
        OUT_SHIP {
            @Override
            int update(InventoryMapper mapper, InventoryFlow flow) {
                return mapper.shipLockedOut(flow.getSkuId(), flow.getWarehouseId(), flow.getQuantity());
            }

            @Override
            boolean sufficient(Inventory row, int quantity) {
                return nvl(row.getQtyOnHand()) + quantity >= 0 && nvl(row.getQtyLocked()) + quantity >= 0;
            }

            @Override
            String insufficientMsg(Inventory row, int quantity) {
                return "出库占用不足(未建单占用或已释放):在库" + nvl(row.getQtyOnHand())
                        + "/占用" + nvl(row.getQtyLocked()) + ",变动" + quantity;
            }

            @Override
            Inventory newRow(InventoryFlow flow) {
                return null; // 出库核销占用需既有存量行
            }
        };

        abstract int update(InventoryMapper mapper, InventoryFlow flow);

        abstract boolean sufficient(Inventory row, int quantity);

        abstract String insufficientMsg(Inventory row, int quantity);

        /** 无行时的首建形态,返回 null = 该类型不允许无行动账(调用方按守卫不足拒绝) */
        abstract Inventory newRow(InventoryFlow flow);

        static FlowOps of(String flowType) {
            for (FlowOps ops : values()) {
                if (ops.name().equals(flowType)) {
                    return ops;
                }
            }
            throw new BusinessException("未知库存流水类型:" + flowType);
        }

        /** 通用形首建:正数在库=Δ、可用=Δ(占用/在途 0 起步);负数不允许无行起步 */
        private static Inventory simpleNewRow(InventoryFlow flow) {
            if (flow.getQuantity() < 0) {
                return null;
            }
            return Inventory.builder()
                    .skuId(flow.getSkuId()).warehouseId(flow.getWarehouseId())
                    .qtyOnHand(flow.getQuantity()).qtyLocked(0).qtyTransit(0).qtyAvailable(flow.getQuantity())
                    .build();
        }

        private static int nvl(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
