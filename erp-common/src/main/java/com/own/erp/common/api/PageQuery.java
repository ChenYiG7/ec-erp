package com.own.erp.common.api;

import lombok.Data;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 通用分页查询参数
 */
@Data
public class PageQuery {

    private static final int MAX_PAGE_SIZE = 500;

    private int pageNo = 1;
    private int pageSize = 20;

    public int offset() {
        return (Math.max(pageNo, 1) - 1) * pageSize();
    }

    public int pageSize() {
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }
}
