package com.own.erp.contract;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 查询契约通用分页结果(#6 三期 AI 地基):查询类契约(OrderQueryApi/InventoryQueryApi/
 *         GoodsQueryApi/AftersaleQueryApi)共用,不引 MyBatis-Plus 类型(契约模型纪律,docs/07 §2.2);
 *         list 不为 null(空页为空列表),total 为满足条件的总行数(非本页行数)
 */
public record QueryPage<T>(List<T> list, long total) {

    public static <T> QueryPage<T> of(List<T> list, long total) {
        return new QueryPage<>(list, total);
    }
}
