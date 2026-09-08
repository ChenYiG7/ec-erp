package com.own.erp.finance.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.SettlementDetail;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.SettlementDetailMapper;
import com.own.erp.finance.mapper.SettlementReportMapper;
import com.own.erp.platform.unified.UnifiedSettlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 结算报告落库(#19 财务/结算域):adapter 翻译产物 UnifiedSettlement → settlement_report 正本
 *         + settlement_detail 明细,域唯一写入口。
 *         - 幂等拍板:uk_shop_settlement(平台 SettlementId)——已入库且勾稽平(PARSED)直接跳过不重放;
 *           已入库但 FAILED(上次勾稽不平/解析残缺)可重拉覆盖:正本逐列更新 + 明细先删后插
 *           (同 #4 拉单明细纪律;明细行级无唯一键,幂等收口报告级);
 *         - 勾稽拍板(01_schema_init.sql settlement_report 注释):Σ明细金额 = 报告头 total-amount
 *           (compareTo 零差)才 PARSED,否则仍入库留痕 status=FAILED(费用事实是资产,差异留痕排查,
 *           禁静默截断;真凭证联调时结合报告原文核实差异来源);
 *         - shopId 以会话为准覆盖(setShopId 口径同 UnifiedOrder/UnifiedProduct,防平台报文内脏值);
 *         - 编排接线随真凭证联调拍板(V1 无定时 Job:结算报告 14 天一份,频率不支持高频拉取)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** 勾稽平(Σ明细=报告头总额) */
    public static final String STATUS_PARSED = "PARSED";
    /** 勾稽不平/解析残缺(可重拉覆盖) */
    public static final String STATUS_FAILED = "FAILED";

    private final SettlementReportMapper settlementReportMapper;
    private final SettlementDetailMapper settlementDetailMapper;

    /**
     * 结算报告落库(事务:正本 upsert + 明细先删后插同事务)。
     *
     * @return true=本次新入库/覆盖重拉;false=已入库且勾稽平,幂等跳过
     */
    @Transactional
    public boolean saveUnifiedSettlement(Long shopId, UnifiedSettlement settlement) {
        validate(shopId, settlement);
        BigDecimal sum = sumOf(settlement);
        String status = settlement.getTotalAmount().compareTo(sum) == 0 ? STATUS_PARSED : STATUS_FAILED;
        if (STATUS_FAILED.equals(status)) {
            log.warn("结算报告勾稽不平,FAILED 留痕 settlement={} 报告头={} Σ明细={}",
                    settlement.getSettlementId(), settlement.getTotalAmount(), sum);
        }
        SettlementReport existing = settlementReportMapper.selectOne(new LambdaQueryWrapper<SettlementReport>()
                .eq(SettlementReport::getShopId, shopId)
                .eq(SettlementReport::getSettlementId, settlement.getSettlementId()));
        if (existing != null) {
            if (STATUS_PARSED.equals(existing.getStatus())) {
                log.info("结算报告已入库勾稽平,幂等跳过 settlement={}", settlement.getSettlementId());
                return false;
            }
            overwrite(existing, settlement, sum, status);
            return true;
        }
        SettlementReport report = SettlementReport.builder()
                .shopId(shopId)
                .settlementId(settlement.getSettlementId())
                .periodStart(toLocal(settlement.getPeriodStart()))
                .periodEnd(toLocal(settlement.getPeriodEnd()))
                .currency(settlement.getCurrency())
                .totalAmount(settlement.getTotalAmount())
                .feeAmount(feeSum(settlement))
                .transferAmount(transferSum(settlement))
                .status(status)
                .build();
        settlementReportMapper.insert(report);
        insertDetails(report.getId(), shopId, settlement);
        log.info("结算报告入库 settlement={} 明细={} status={}",
                settlement.getSettlementId(), settlement.getLines().size(), status);
        return true;
    }

    /** FAILED 重拉覆盖:正本逐列更新 + 明细先删后插(FAILED 行是可修正的暂存态,不重复建行) */
    private void overwrite(SettlementReport existing, UnifiedSettlement settlement, BigDecimal sum, String status) {
        existing.setPeriodStart(toLocal(settlement.getPeriodStart()));
        existing.setPeriodEnd(toLocal(settlement.getPeriodEnd()));
        existing.setCurrency(settlement.getCurrency());
        existing.setTotalAmount(settlement.getTotalAmount());
        existing.setFeeAmount(feeSum(settlement));
        existing.setTransferAmount(transferSum(settlement));
        existing.setStatus(status);
        settlementReportMapper.updateById(existing);
        settlementDetailMapper.delete(new LambdaQueryWrapper<SettlementDetail>()
                .eq(SettlementDetail::getReportId, existing.getId()));
        insertDetails(existing.getId(), existing.getShopId(), settlement);
        log.info("结算报告 FAILED 重拉覆盖 settlement={} 明细={} status={}",
                settlement.getSettlementId(), settlement.getLines().size(), status);
    }

    private void insertDetails(Long reportId, Long shopId, UnifiedSettlement settlement) {
        for (UnifiedSettlement.Line line : settlement.getLines()) {
            settlementDetailMapper.insert(SettlementDetail.builder()
                    .reportId(reportId)
                    .shopId(shopId)
                    .orderId(line.getOrderId())
                    .orderItemId(line.getOrderItemId())
                    .sku(line.getSku())
                    .feeType(line.getFeeType().name())
                    .amount(line.getAmount())
                    .postedAt(toLocal(line.getPostedAt()))
                    .build());
        }
    }

    /** 勾稽:Σ明细金额(空明细按 0,禁 NPE) */
    private BigDecimal sumOf(UnifiedSettlement settlement) {
        return settlement.getLines().stream()
                .map(UnifiedSettlement.Line::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 费用小计=Σ负项明细绝对值(派生统计,费用事实在明细) */
    private BigDecimal feeSum(UnifiedSettlement settlement) {
        return settlement.getLines().stream()
                .map(UnifiedSettlement.Line::getAmount)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 回款净额=Σ TRANSFER 明细(预留金随真凭证实测校准,拍板见 DDL 注释) */
    private BigDecimal transferSum(UnifiedSettlement settlement) {
        return settlement.getLines().stream()
                .filter(line -> UnifiedSettlement.FeeType.TRANSFER == line.getFeeType())
                .map(UnifiedSettlement.Line::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validate(Long shopId, UnifiedSettlement settlement) {
        if (shopId == null) {
            throw new BusinessException("结算报告落库缺 shopId");
        }
        if (settlement == null || StrUtil.isBlank(settlement.getSettlementId())
                || StrUtil.isBlank(settlement.getCurrency())
                || settlement.getTotalAmount() == null
                || settlement.getPeriodStart() == null || settlement.getPeriodEnd() == null) {
            throw new BusinessException("结算报告缺必填字段(settlementId/currency/totalAmount/period),拒绝静默落库");
        }
        if (settlement.getLines() == null) {
            throw new BusinessException("结算报告明细列表缺失(null),拒绝静默落库");
        }
    }

    /** Instant → LocalDateTime(与订单时间同口径 PullConsts.ZONE,周期/记账时间可对齐订单面) */
    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, PullConsts.ZONE);
    }
}
