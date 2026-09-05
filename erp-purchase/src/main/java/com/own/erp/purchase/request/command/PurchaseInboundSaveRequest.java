package com.own.erp.purchase.request.command;

import com.own.erp.purchase.entity.PurchaseInbound;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购入库单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     #10 核销改造:status 服务端管理(创建固定 PENDING)、warehouseId 取采购单收货仓,二者从入参剔除;
 *     入库明细整单提交,更新时整体替换
 */
@Builder
public record PurchaseInboundSaveRequest(

        /** 入库单号,唯一 */
        @NotBlank
        @Size(max = 64)
        String inboundNo,

        /** 采购单ID(purchase_order.id),须为已审核未收齐状态 */
        @NotNull
        Long poId,

        /** 备注(数量差异说明等) */
        @Size(max = 255)
        String remark,

        /** 创建人(sys_user.id),前端工程接线 SecurityContext 前暂由调用方传 */
        Long createdBy,

        /** 入库明细(创建/更新整单提交) */
        @NotEmpty
        @Valid
        List<PurchaseInboundItemSaveRequest> items
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);status/warehouseId 由 Service 服务端回填,不入映射 */
    public PurchaseInbound toEntity() {
        return PurchaseInbound.builder()
                .inboundNo(inboundNo)
                .poId(poId)
                .remark(remark)
                .createdBy(createdBy)
                .build();
    }
}
