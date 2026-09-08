package com.own.erp.finance.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.ExchangeRate;
import com.own.erp.finance.mapper.ExchangeRateMapper;
import com.own.erp.finance.request.query.ExchangeRateQuery;
import com.own.erp.finance.response.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 汇率快照服务(#19③ 利润核算 V1 数据面):折算口径唯一收口 resolveRate——
 *         汇率是快照不是现值,一律按业务日回溯取 quoted_at<=业务日的最近一条报价,禁取表内最新一条
 *         (01_schema_init.sql 拍板);本位币 V1 固定 CNY 短路 =1 不查表;
 *         无报价返回 null 由调用方决定语义(利润面折 NULL 显"缺汇率",禁猜禁取 1)。
 *         写侧 V1 仅 MANUAL 手工维护(page/save),API 行情源接入随四期评估
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    /** 记账本位币(V1 固定拍板,扩多本位币随四期评估) */
    public static final String BASE_CURRENCY = "CNY";

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final ExchangeRateMapper exchangeRateMapper;

    /**
     * 业务日回溯取汇率(利润折算唯一口径):1 currency = rate CNY。
     * CNY 短路 1;否则取 quoted_at<=businessTime 的最近一条,无报价返回 null(禁猜)
     *
     * @param currency     原币种(ISO 4217)
     * @param businessTime 业务时间(下单日/记账日,回溯锚点)
     */
    public BigDecimal resolveRate(String currency, LocalDateTime businessTime) {
        if (StrUtil.isBlank(currency)) {
            throw new BusinessException("折算币种必填");
        }
        if (businessTime == null) {
            throw new BusinessException("折算业务时间必填(回溯锚点)");
        }
        if (BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return ONE;
        }
        ExchangeRate rate = exchangeRateMapper.selectOne(new LambdaQueryWrapper<ExchangeRate>()
                .eq(ExchangeRate::getCurrency, currency)
                .le(ExchangeRate::getQuotedAt, businessTime)
                .orderByDesc(ExchangeRate::getQuotedAt)
                .last("LIMIT 1"));
        return rate == null ? null : rate.getRate();
    }

    /** 分页查询(按报价时间倒序;过滤币种) */
    public Page<ExchangeRateResponse> page(ExchangeRateQuery query) {
        Page<ExchangeRate> result = exchangeRateMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<ExchangeRate>()
                        .eq(StrUtil.isNotBlank(query.getCurrency()), ExchangeRate::getCurrency, query.getCurrency())
                        .orderByDesc(ExchangeRate::getQuotedAt)
                        .orderByDesc(ExchangeRate::getId));
        Page<ExchangeRateResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ExchangeRateResponse::from).toList());
        return responsePage;
    }

    /** 手工录入快照(source 固定 MANUAL):币种/汇率/报价时间必填,汇率必须大于 0 */
    public Long save(ExchangeRate rate) {
        if (rate == null || StrUtil.isBlank(rate.getCurrency()) || rate.getRate() == null
                || rate.getQuotedAt() == null) {
            throw new BusinessException("汇率快照缺必填字段(currency/rate/quotedAt),拒绝静默落库");
        }
        if (rate.getRate().signum() <= 0) {
            throw new BusinessException("汇率必须大于0");
        }
        rate.setId(null);
        rate.setSource("MANUAL");
        exchangeRateMapper.insert(rate);
        return rate.getId();
    }
}
