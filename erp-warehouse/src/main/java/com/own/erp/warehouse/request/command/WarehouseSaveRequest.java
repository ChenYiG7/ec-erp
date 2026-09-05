package com.own.erp.warehouse.request.command;

import com.own.erp.warehouse.entity.Warehouse;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 仓库写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     生成器已剔除 id/created_at/updated_at(服务端管理列);其余服务端管理列(如 merchant_id)按业务人工删减
 *     校验注解(@NotNull/@Size 等)随业务约束逐步补,Controller 侧 @Valid 已就位(docs/07 §7)
 */
@Builder
public record WarehouseSaveRequest(

        /** 仓库名称 */
        String whName,

        /** SELF自仓/FBA/OVERSEAS海外仓/VIRTUAL虚拟仓 */
        String whType,

        /** 国家(ISO 3166) */
        String country,

        /** 仓库地址 */
        String address,

        /** 1启用0禁用 */
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public Warehouse toEntity() {
        Warehouse entity = new Warehouse();
        entity.setWhName(whName);
        entity.setWhType(whType);
        entity.setCountry(country);
        entity.setAddress(address);
        entity.setStatus(status);
        return entity;
    }
}
