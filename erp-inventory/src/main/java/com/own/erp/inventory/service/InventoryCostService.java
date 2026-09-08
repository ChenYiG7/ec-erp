package com.own.erp.inventory.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.inventory.entity.InventoryCostState;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryCostStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU 移动加权成本账(#19③ 利润核算 V1,docs/02 §14 成本计价拍板"先移动加权,FIFO 批次核算更重放后"):
 *     进账矩阵(与 01_schema_init.sql/docs/03 §4 注释同步):
 *     IN_PURCHASE 进账(采购单价传入,缺价按当时加权价暂估禁猜价——首次无价记 0 并 warn,账本数量守恒金额低估);
 *     OUT_SHIP 结转(当时加权价,unit_cost 快照落流水=利润面出库成本事实源);
 *     IN_RETURN/ADJUST 按当时加权价进出(同价不动加权);IN_TRANSIT/LOCK_SHIP/TRANSFER_OUT/TRANSFER_IN 不进账(NULL)。
 *     并发:事务内 FOR UPDATE 锁 sku_cost_state 行串行化同 SKU 成本计算,锁序 inventory 行 → state 行单向无死锁;
 *     首建并发撞 uk_sku 捕 DuplicateKeyException 回退重读。加权价仅 IN_PURCHASE 重算,结转/调整不改价
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCostService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final int SCALE_AMOUNT = 2;
    private static final int SCALE_AVG = 8;

    private final InventoryCostStateMapper costStateMapper;

    /**
     * 动账成本推进(InventoryService.change 同事务内调用,禁独立外用):
     * 锁/建账本行 → 按 flow_type 分派语义 → 回填 flow.unitCost/costAmount 快照 → 更新账本结存。
     * 不进成本账类型直接返回(flow 成本列保持 NULL);账本结存为负(账实漂移)抛异常回滚
     */
    public void apply(InventoryFlow flow) {
        FlowCostOps ops = FlowCostOps.ofOrNull(flow.getFlowType());
        if (ops == null) {
            return;
        }
        InventoryCostState state = lockOrInit(flow.getSkuId());
        ops.apply(flow, state);
        if (state.getTotalQty() < 0) {
            throw new BusinessException("成本账本结存为负(SKU=" + flow.getSkuId() + " 结存=" + state.getTotalQty()
                    + "),账实漂移拒绝动账");
        }
        costStateMapper.updateById(state);
    }

    /** 锁账本行:无行首建(并发撞 uk_sku 回退重读,同 InventoryService.change 首建套路) */
    private InventoryCostState lockOrInit(Long skuId) {
        InventoryCostState state = costStateMapper.selectBySkuIdForUpdate(skuId);
        if (state != null) {
            return state;
        }
        try {
            InventoryCostState created = InventoryCostState.builder()
                    .skuId(skuId).totalQty(0).totalAmount(ZERO).avgCost(ZERO).build();
            costStateMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            InventoryCostState retry = costStateMapper.selectBySkuIdForUpdate(skuId);
            if (retry == null) {
                throw new BusinessException("成本账本初始化并发冲突,请重试:SKU=" + skuId);
            }
            return retry;
        }
    }

    /**
     * flow_type → 成本账语义(#19③)。null = 不进成本账(流水成本列留 NULL):
     * <pre>
     * 类型          unit_cost                cost_amount           账本(total_qty/total_amount)  avg_cost
     * IN_PURCHASE  传入价,缺价暂估当时加权   +q×unit_cost          +q / +costAmount              重算(仅此处)
     * OUT_SHIP     当时加权价               q×unit_cost(负)       +q(负) / +costAmount           不变
     * IN_RETURN    当时加权价               +q×unit_cost          +q / +costAmount               不变
     * ADJUST       当时加权价               q×unit_cost(带符号)   +q / +costAmount               不变
     * 其余四类型    NULL(不进账)           NULL                  不动                           不动
     * </pre>
     */
    private enum FlowCostOps {
        IN_PURCHASE {
            @Override
            void apply(InventoryFlow flow, InventoryCostState state) {
                BigDecimal avgCost = nvl(state.getAvgCost());
                BigDecimal unitCost = flow.getUnitCost() != null ? flow.getUnitCost()
                        : (avgCost.compareTo(ZERO) > 0 ? avgCost : ZERO);
                if (flow.getUnitCost() == null) {
                    log.warn("采购入库缺单价,按移动加权价暂估(CNY):skuId={} 暂估价={}", flow.getSkuId(), unitCost);
                }
                BigDecimal costAmount = costOf(unitCost, flow.getQuantity());
                state.setTotalQty(state.getTotalQty() + flow.getQuantity());
                state.setTotalAmount(state.getTotalAmount().add(costAmount));
                // 仅入库重算加权价(结存>0;清零态保留末次价,DDL 注释拍板)
                if (state.getTotalQty() > 0) {
                    state.setAvgCost(state.getTotalAmount().divide(BigDecimal.valueOf(state.getTotalQty()),
                            SCALE_AVG, RoundingMode.HALF_UP));
                }
                flow.setUnitCost(unitCost);
                flow.setCostAmount(costAmount);
            }
        },
        OUT_SHIP {
            @Override
            void apply(InventoryFlow flow, InventoryCostState state) {
                settleAtAvgCost(flow, state);
            }
        },
        IN_RETURN {
            @Override
            void apply(InventoryFlow flow, InventoryCostState state) {
                settleAtAvgCost(flow, state);
            }
        },
        ADJUST {
            @Override
            void apply(InventoryFlow flow, InventoryCostState state) {
                settleAtAvgCost(flow, state);
            }
        };

        abstract void apply(InventoryFlow flow, InventoryCostState state);

        /** 出库结转/退货回库/人工调整:按当时加权价进出,加权价不动(同价进出);static 供常量体直调 */
        private static void settleAtAvgCost(InventoryFlow flow, InventoryCostState state) {
            BigDecimal avgCost = nvl(state.getAvgCost());
            BigDecimal costAmount = costOf(avgCost, flow.getQuantity());
            state.setTotalQty(state.getTotalQty() + flow.getQuantity());
            state.setTotalAmount(state.getTotalAmount().add(costAmount));
            flow.setUnitCost(avgCost);
            flow.setCostAmount(costAmount);
        }

        static FlowCostOps ofOrNull(String flowType) {
            for (FlowCostOps ops : values()) {
                if (ops.name().equals(flowType)) {
                    return ops;
                }
            }
            return null;
        }

        /** 成本额=单价×数量(带符号,金额两位 HALF_UP 与 DDL DECIMAL(18,2) 对齐) */
        private static BigDecimal costOf(BigDecimal unitCost, int quantity) {
            return unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE_AMOUNT, RoundingMode.HALF_UP);
        }

        private static BigDecimal nvl(BigDecimal value) {
            return value == null ? ZERO : value;
        }
    }
}
