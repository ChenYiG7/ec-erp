package com.own.erp.contract.impl;

import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : InventoryChangeApi 实现(接口模块方案的编排胶水,收口 erp-api,#10):
 *         业务域动库存经契约调用,此处仅做 契约命令 → InventoryFlow 实体 翻译并委托唯一入口
 *         InventoryService.change(docs/07 铁律 4);无自建事务,调用方自持事务时 change 以
 *         REQUIRED 加入同一事务——入库核销与库存流水同事务由调用方 @Transactional 保证
 */
@Component
@RequiredArgsConstructor
public class InventoryChangeApiImpl implements InventoryChangeApi {

    private final InventoryService inventoryService;

    @Override
    public Long change(InventoryChangeCommand command) {
        return inventoryService.change(InventoryFlow.builder()
                .skuId(command.skuId())
                .warehouseId(command.warehouseId())
                .quantity(command.quantity())
                .flowType(command.flowType())
                .bizType(command.bizType())
                .bizId(command.bizId())
                .remark(command.remark())
                .createdBy(command.createdBy())
                .build());
    }
}
