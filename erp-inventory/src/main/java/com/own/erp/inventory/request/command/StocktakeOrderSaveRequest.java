package com.own.erp.inventory.request.command;

import com.own.erp.inventory.entity.StocktakeOrder;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     record+@Builder(模型可变性分级 docs/07 §1);toEntity 用 entity builder 链一次成型(纯构造位,docs/07 §1 分级①)
 *     已剔除服务端管理列(status 固定 DRAFT、created_by 按 SecurityContext、confirmed_* 生成调整时回填);
 *     scopeType=SKU_SET 时 skuIds 必填且去重,ALL 时忽略
 */
@Builder
public record StocktakeOrderSaveRequest(

        /** 盘点单号 ST+yyyyMMdd+seq,唯一 */
        String stocktakeNo,

        /** 盘点仓ID(warehouse.id) */
        Long warehouseId,

        /** 盘点范围:ALL全仓(按该仓现有库存行快照)/SKU_SET选定SKU集 */
        String scopeType,

        /** 备注 */
        String remark,

        /** 盘点 SKU 集(scopeType=SKU_SET 时必填;无库存行的 SKU 快照账面按 0) */
        List<Long> skuIds

) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);服务端管理列不透传(status/createdBy/confirmed* 由 Service 回填) */
    public StocktakeOrder toEntity() {
        return StocktakeOrder.builder()
                .stocktakeNo(stocktakeNo)
                .warehouseId(warehouseId)
                .scopeType(scopeType)
                .remark(remark)
                .build();
    }
}
