package com.own.erp.system.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserQuery extends PageQuery {

    /** 用户名模糊匹配(可选) */
    private String username;

    /** 状态:1=启用 0=禁用(可选) */
    private Integer status;
}
