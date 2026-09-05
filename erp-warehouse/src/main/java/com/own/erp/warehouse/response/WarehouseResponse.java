package com.own.erp.warehouse.response;

import com.own.erp.warehouse.entity.Warehouse;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 仓库对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record WarehouseResponse(

        /** 主键 */
        Long id,

        /** 仓库名称 */
        String whName,

        /** SELF自仓/FBA/OVERSEAS海外仓/VIRTUAL虚拟仓 */
        String whType,

        /** 国家(ISO 3166) */
        String country,

        /** 仓库地址 */
        String address,

        /** 1启用0禁用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static WarehouseResponse from(Warehouse entity) {
        return WarehouseResponse.builder()
                .id(entity.getId())
                .whName(entity.getWhName())
                .whType(entity.getWhType())
                .country(entity.getCountry())
                .address(entity.getAddress())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
