package com.own.erp.inventory.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.mapper.InventoryFlowMapper;
import com.own.erp.inventory.request.query.InventoryFlowQuery;
import com.own.erp.inventory.response.InventoryFlowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存流水服务:inventory_flow 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)
 *     流水只增不改不删(写入唯一入口 InventoryService.change,与库存变更同事务),对外只读
 */
@Service
@RequiredArgsConstructor
public class InventoryFlowService {

    private final InventoryFlowMapper inventoryFlowMapper;

    /** 分页查询(过滤:SKU/仓库/流水类型;按时间倒序) */
    public Page<InventoryFlowResponse> page(InventoryFlowQuery query) {
        LambdaQueryWrapper<InventoryFlow> wrapper = new LambdaQueryWrapper<InventoryFlow>()
                .eq(query.getSkuId() != null, InventoryFlow::getSkuId, query.getSkuId())
                .eq(query.getWarehouseId() != null, InventoryFlow::getWarehouseId, query.getWarehouseId())
                .eq(StrUtil.isNotBlank(query.getFlowType()), InventoryFlow::getFlowType, query.getFlowType())
                .orderByDesc(InventoryFlow::getId);
        Page<InventoryFlow> result = inventoryFlowMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<InventoryFlowResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(InventoryFlowResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public InventoryFlowResponse getById(Long id) {
        InventoryFlow flow = inventoryFlowMapper.selectById(id);
        return flow == null ? null : InventoryFlowResponse.from(flow);
    }
}
