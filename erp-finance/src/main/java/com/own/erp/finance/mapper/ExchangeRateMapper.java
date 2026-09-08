package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.ExchangeRate;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : exchange_rate 表 Mapper(#19 汇率快照):写入侧仅维护入口(MANUAL 先行);
 *         读侧折算口径=quoted_at<=业务日的最近一条(回溯取数,禁取表内最新一条),
 *         自定义查询随利润核算切片落地,先 BaseMapper 通能力的 eq/orderBy 手拼
 */
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
