package com.own.erp.finance.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.PlatformFeeRate;
import com.own.erp.finance.mapper.PlatformFeeRateMapper;
import com.own.erp.finance.request.command.PlatformFeeRateSaveRequest;
import com.own.erp.finance.request.query.PlatformFeeRateQuery;
import com.own.erp.finance.response.PlatformFeeRateResponse;
import com.own.erp.platform.unified.UnifiedSettlement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 平台费率表服务(#19 预估费用模型):platform_fee_rate 域整域收口,Controller 不直连 Mapper
 *     (docs/07 §2.1)。V1 边界——①费种白名单仅 COMMISSION(有明确费率才估算,FBA 仓储类无费率不猜,
 *     新增可估费种随真凭证拍板扩白名单);②维度仅平台×费种(marketplace/category_path 列预留,
 *     写侧不开放,resolve 只取全站点/全类目行);③source 服务端固定 MANUAL(CRAWLED 随抓取源评估)。
 *     消费侧唯一入口 resolveFeeRate:按下单日回溯取生效行最新一版,无费率返回 null(禁猜,docs/03 §6.4)。
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest,出参 response/XxxResponse。
 */
@Service
@RequiredArgsConstructor
public class PlatformFeeRateService {

    /** 估费白名单(V1 仅佣金;扩值必须有明确费率来源,禁拍脑袋加) */
    private static final List<String> ESTIMATABLE_FEE_TYPES = List.of(UnifiedSettlement.FeeType.COMMISSION.name());

    /** 写侧来源固定手工维护(CRAWLED 抓取源预留,随行情/费率抓取能力评估) */
    private static final String SOURCE_MANUAL = "MANUAL";

    private final PlatformFeeRateMapper platformFeeRateMapper;

    /** 分页查询(平台/费种过滤;同维按生效起倒序,id 倒序兜底) */
    public Page<PlatformFeeRateResponse> page(PlatformFeeRateQuery query) {
        Page<PlatformFeeRate> result = platformFeeRateMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<PlatformFeeRate>()
                        .eq(StrUtil.isNotBlank(query.getPlatform()), PlatformFeeRate::getPlatform, query.getPlatform())
                        .eq(StrUtil.isNotBlank(query.getFeeType()), PlatformFeeRate::getFeeType, query.getFeeType())
                        .orderByDesc(PlatformFeeRate::getEffFrom)
                        .orderByDesc(PlatformFeeRate::getId));
        Page<PlatformFeeRateResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(PlatformFeeRateResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public PlatformFeeRateResponse getById(Long id) {
        PlatformFeeRate platformFeeRate = platformFeeRateMapper.selectById(id);
        return platformFeeRate == null ? null : PlatformFeeRateResponse.from(platformFeeRate);
    }

    /** 新增,返回自增ID;费种白名单/生效区间/同维同生效日重复校验,source 固定 MANUAL */
    public Long save(PlatformFeeRateSaveRequest request) {
        validate(request);
        PlatformFeeRate platformFeeRate = request.toEntity();
        platformFeeRate.setSource(SOURCE_MANUAL);
        ensureNotDuplicate(platformFeeRate, null);
        platformFeeRateMapper.insert(platformFeeRate);
        return platformFeeRate.getId();
    }

    /** 更新(MP 忽略 null 可部分更新;id 只认路径参数);记录不存在 404,source 不接受入参修改 */
    public void update(Long id, PlatformFeeRateSaveRequest request) {
        PlatformFeeRate existing = platformFeeRateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("费率不存在或已删除");
        }
        validate(request);
        PlatformFeeRate platformFeeRate = request.toEntity();
        platformFeeRate.setId(id);
        ensureNotDuplicate(platformFeeRate, id);
        platformFeeRateMapper.updateById(platformFeeRate);
    }

    /** 删除(逻辑删除:@TableLogic 置 deleted=id,删后同维度同生效日可重建,docs/07 §6.4) */
    public void delete(Long id) {
        platformFeeRateMapper.deleteById(id);
    }

    /**
     * 估费唯一消费入口:平台×费种在 businessDate 当日生效的费率(V1 仅取全站点/全类目行),
     * 同维多版本取 eff_from 不晚于业务日的最新一条(回溯口径同汇率快照);无生效行返回 null(无费率不估算,禁猜)
     */
    public BigDecimal resolveFeeRate(String platform, String feeType, LocalDate businessDate) {
        if (StrUtil.isBlank(platform) || StrUtil.isBlank(feeType) || businessDate == null) {
            return null;
        }
        PlatformFeeRate hit = platformFeeRateMapper.selectOne(new LambdaQueryWrapper<PlatformFeeRate>()
                .eq(PlatformFeeRate::getPlatform, platform)
                .eq(PlatformFeeRate::getFeeType, feeType)
                .isNull(PlatformFeeRate::getMarketplace)
                .isNull(PlatformFeeRate::getCategoryPath)
                .le(PlatformFeeRate::getEffFrom, businessDate)
                .and(w -> w.isNull(PlatformFeeRate::getEffTo).or().ge(PlatformFeeRate::getEffTo, businessDate))
                .orderByDesc(PlatformFeeRate::getEffFrom)
                .last("LIMIT 1"));
        return hit == null ? null : hit.getRate();
    }

    /**
     * 批量取某费种全部全站点/全类目费率行(费率表量级小),供利润装配一次性载入内存挑选,
     * 禁逐行 resolveFeeRate 造成 N+1(docs/07 §5);按平台分组后用 {@link #pickFeeRate} 回溯
     */
    public List<PlatformFeeRate> listGlobalRates(String feeType) {
        return platformFeeRateMapper.selectList(new LambdaQueryWrapper<PlatformFeeRate>()
                .eq(PlatformFeeRate::getFeeType, feeType)
                .isNull(PlatformFeeRate::getMarketplace)
                .isNull(PlatformFeeRate::getCategoryPath));
    }

    /**
     * 同维候选费率中挑 businessDate 当日生效(eff_from≤日期且 eff_to 空或≥日期)的最新一版;
     * 无生效行返回 null(无费率不估算)。纯内存计算,与 resolveFeeRate SQL 口径一致
     */
    public static PlatformFeeRate pickFeeRate(List<PlatformFeeRate> candidates, LocalDate businessDate) {
        if (candidates == null || candidates.isEmpty() || businessDate == null) {
            return null;
        }
        PlatformFeeRate picked = null;
        for (PlatformFeeRate candidate : candidates) {
            if (candidate.getEffFrom() == null || candidate.getEffFrom().isAfter(businessDate)) {
                continue;
            }
            if (candidate.getEffTo() != null && candidate.getEffTo().isBefore(businessDate)) {
                continue;
            }
            if (picked == null || candidate.getEffFrom().isAfter(picked.getEffFrom())) {
                picked = candidate;
            }
        }
        return picked;
    }

    /** 写侧校验:费种白名单 + 生效区间(eff_to 空=长期,非空不得早于 eff_from) */
    private void validate(PlatformFeeRateSaveRequest request) {
        if (!ESTIMATABLE_FEE_TYPES.contains(request.feeType())) {
            throw new BusinessException("V1 仅支持 COMMISSION 费率估算,费种白名单外不建费率(无费率不猜)");
        }
        if (request.effTo() != null && request.effTo().isBefore(request.effFrom())) {
            throw new BusinessException("生效止不得早于生效起");
        }
    }

    /**
     * 同维同生效日查重:uk_fee_dim 含 marketplace/category_path 两个 NULL 列,
     * MySQL 唯一索引对 NULL 不去重(多行 NULL 可并存),故应用层补显式查重(同 #31 资金流水纪律)。
     * V1 写侧维度恒为全站点/全类目,查重键=platform+feeType+effFrom;更新时排除自身
     */
    private void ensureNotDuplicate(PlatformFeeRate candidate, Long excludeId) {
        Long count = platformFeeRateMapper.selectCount(new LambdaQueryWrapper<PlatformFeeRate>()
                .eq(PlatformFeeRate::getPlatform, candidate.getPlatform())
                .eq(PlatformFeeRate::getFeeType, candidate.getFeeType())
                .isNull(PlatformFeeRate::getMarketplace)
                .isNull(PlatformFeeRate::getCategoryPath)
                .eq(PlatformFeeRate::getEffFrom, candidate.getEffFrom())
                .ne(excludeId != null, PlatformFeeRate::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("同平台同费种同生效日的全站点费率已存在,禁止重复维护");
        }
    }
}
