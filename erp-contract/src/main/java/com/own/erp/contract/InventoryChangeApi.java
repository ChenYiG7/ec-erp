package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 库存变更契约(写侧,接口模块方案,#10 起):业务域动库存必须经 InventoryService.change 唯一入口
 *         (docs/07 铁律 4),但模块间禁横向依赖(铁律 2)——故契约收口本模块,实现 InventoryChangeApiImpl
 *         委托 erp-inventory InventoryService.change;调用方自持事务时 change 以 REQUIRED 加入同一事务,
 *         满足"入库核销与流水同事务"。消费方:erp-purchase 入库核销(#10)、后续 erp-fulfill(#11)/erp-aftersale(#12)
 */
public interface InventoryChangeApi {

    /**
     * 库存变更唯一入口透传:同事务更新 inventory + 写 inventory_flow(唯一入口即 InventoryService.change)。
     * 校验(skuId/warehouseId 必填、quantity 非 0)与并发控制(原子 UPDATE/首建重试)见 erp-inventory。
     *
     * @return 流水ID(inventory_flow.id)
     */
    Long change(InventoryChangeCommand command);
}
