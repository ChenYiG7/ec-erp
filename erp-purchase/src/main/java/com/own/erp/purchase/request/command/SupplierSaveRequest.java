package com.own.erp.purchase.request.command;

import com.own.erp.purchase.entity.Supplier;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 供应商写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     生成器已剔除 id/created_at/updated_at(服务端管理列);其余服务端管理列(如 merchant_id)按业务人工删减
 *     校验注解(@NotNull/@Size 等)随业务约束逐步补,Controller 侧 @Valid 已就位(docs/07 §7)
 */
@Builder
public record SupplierSaveRequest(

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
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public Supplier toEntity() {
        Supplier entity = new Supplier();
        entity.setName(name);
        entity.setContact(contact);
        entity.setPhone(phone);
        entity.setSettleType(settleType);
        entity.setRemark(remark);
        entity.setStatus(status);
        return entity;
    }
}
