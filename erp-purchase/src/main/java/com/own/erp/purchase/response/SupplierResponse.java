package com.own.erp.purchase.response;

import com.own.erp.purchase.entity.Supplier;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 供应商对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record SupplierResponse(

        /** 主键 */
        Long id,

        /** 供应商名称 */
        String name,

        /** 联系人 */
        String contact,

        /** 联系电话 */
        String phone,

        /** 结算方式,走 sys_dict(预付/月结等) */
        String settleType,

        /** 备注 */
        String remark,

        /** 1启用0禁用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static SupplierResponse from(Supplier entity) {
        return SupplierResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .contact(entity.getContact())
                .phone(entity.getPhone())
                .settleType(entity.getSettleType())
                .remark(entity.getRemark())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
