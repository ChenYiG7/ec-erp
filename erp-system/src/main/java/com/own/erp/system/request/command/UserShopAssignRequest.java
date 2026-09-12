package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 用户-店铺数据授权入参(#27①):全量重绑(传空列表 = 清空该用户授权,不可见任何店铺数据)
 */
public record UserShopAssignRequest(

        /** 允许空列表=清空全部授权;null 视为非法载荷 */
        @NotNull(message = "店铺ID列表不能为null")
        List<Long> shopIds) {
}
