package com.own.erp.system.request.command;

import com.own.erp.system.entity.SysDept;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 部门写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     刻意不含 id/createdAt/updatedAt/deleted(服务端);parentId 不传按根节点(parent_id=0)处理
 */
@Builder
public record SysDeptSaveRequest(

        /** 父部门ID,根节点为 0(未传按根处理) */
        Long parentId,

        /** 部门名称 */
        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称不能超过 64 字")
        String deptName,

        /** 同级排序,小在前 */
        Integer sort,

        /** 1=启用 0=禁用 */
        Integer status
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);update 时 id 由 Service 从路径参数回填 */
    public SysDept toEntity() {
        SysDept dept = new SysDept();
        dept.setParentId(parentId);
        dept.setDeptName(deptName);
        dept.setSort(sort);
        dept.setStatus(status);
        return dept;
    }
}
