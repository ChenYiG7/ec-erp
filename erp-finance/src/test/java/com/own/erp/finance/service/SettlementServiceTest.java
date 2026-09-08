package com.own.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.SettlementDetail;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.SettlementDetailMapper;
import com.own.erp.finance.mapper.SettlementReportMapper;
import com.own.erp.platform.unified.UnifiedSettlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SettlementService 单测(AIR:mock Mapper,不依赖数据库):
 *     幂等三态(首拉入库/已入库勾稽平跳过/FAILED 重拉覆盖)/勾稽拍板(Σ明细 vs 报告头总额,
 *     不平仍入库 FAILED 留痕)/派生统计(费用小计=Σ负项绝对值、回款净额=ΣTRANSFER)/必填校验拒静默。
 *     注:明细 reportId 在生产路径由 MP insert 后主键回填,mock 环境为 null,此处断言业务字段
 */
class SettlementServiceTest {

    private static final Long SHOP_ID = 7L;

    private SettlementReportMapper reportMapper;
    private SettlementDetailMapper detailMapper;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(SettlementReportMapper.class);
        detailMapper = mock(SettlementDetailMapper.class);
        service = new SettlementService(reportMapper, detailMapper);
    }

    /** 勾稽平样板:SALE 29.99 + COMMISSION -4.50 + TRANSFER 100.00 → Σ=125.49=报告头 */
    private static UnifiedSettlement balanced() {
        return UnifiedSettlement.builder()
                .settlementId("4049-4845-94")
                .shopId(SHOP_ID)
                .currency("USD")
                .totalAmount(new BigDecimal("125.49"))
                .periodStart(Instant.parse("2026-08-01T00:00:00Z"))
                .periodEnd(Instant.parse("2026-08-14T00:00:00Z"))
                .lines(List.of(
                        line("Order", UnifiedSettlement.FeeType.SALE, "29.99"),
                        line("Order", UnifiedSettlement.FeeType.COMMISSION, "-4.50"),
                        line("Transfer", UnifiedSettlement.FeeType.TRANSFER, "100.00")))
                .build();
    }

    private static UnifiedSettlement.Line line(String transactionType, UnifiedSettlement.FeeType feeType, String amount) {
        return UnifiedSettlement.Line.builder()
                .orderId("111-222")
                .orderItemId("C1")
                .sku("SKU-A")
                .transactionType(transactionType)
                .feeType(feeType)
                .amount(new BigDecimal(amount))
                .postedAt(Instant.parse("2026-08-03T10:00:00Z"))
                .quantity(1)
                .build();
    }

    @Test
    void firstSaveInsertsReportWithDerivedTotalsAndDetails() {
        when(reportMapper.selectOne(any())).thenReturn(null);

        boolean saved = service.saveUnifiedSettlement(SHOP_ID, balanced());

        assertTrue(saved);
        ArgumentCaptor<SettlementReport> reportCaptor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        SettlementReport report = reportCaptor.getValue();
        assertEquals(SHOP_ID, report.getShopId());
        assertEquals("4049-4845-94", report.getSettlementId());
        assertEquals("USD", report.getCurrency());
        assertEquals(0, new BigDecimal("125.49").compareTo(report.getTotalAmount()));
        // 派生统计:费用小计=Σ负项绝对值;回款净额=ΣTRANSFER
        assertEquals(0, new BigDecimal("4.50").compareTo(report.getFeeAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(report.getTransferAmount()));
        assertEquals(SettlementService.STATUS_PARSED, report.getStatus());
        verify(detailMapper, times(3)).insert(any(SettlementDetail.class));
    }

    @Test
    void reconciliationMismatchStoresAsFailedForInvestigation() {
        when(reportMapper.selectOne(any())).thenReturn(null);
        UnifiedSettlement mismatched = balanced();
        mismatched.setTotalAmount(new BigDecimal("999.99"));

        boolean saved = service.saveUnifiedSettlement(SHOP_ID, mismatched);

        // 勾稽不平仍入库留痕(费用事实是资产,差异排查禁静默截断),status=FAILED 可重拉覆盖
        assertTrue(saved);
        ArgumentCaptor<SettlementReport> captor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(SettlementService.STATUS_FAILED, captor.getValue().getStatus());
        verify(detailMapper, times(3)).insert(any(SettlementDetail.class));
    }

    @Test
    void parsedExistingIsSkippedIdempotently() {
        when(reportMapper.selectOne(any())).thenReturn(SettlementReport.builder()
                .id(5L).shopId(SHOP_ID).settlementId("4049-4845-94")
                .status(SettlementService.STATUS_PARSED).build());

        boolean saved = service.saveUnifiedSettlement(SHOP_ID, balanced());

        assertFalse(saved);
        verify(reportMapper, never()).insert(any(SettlementReport.class));
        verify(reportMapper, never()).updateById(any(SettlementReport.class));
        verify(detailMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(detailMapper, never()).insert(any(SettlementDetail.class));
    }

    @Test
    void failedExistingIsOverwrittenWithDetailsReplacedOnRepull() {
        when(reportMapper.selectOne(any())).thenReturn(SettlementReport.builder()
                .id(5L).shopId(SHOP_ID).settlementId("4049-4845-94")
                .status(SettlementService.STATUS_FAILED).build());

        boolean saved = service.saveUnifiedSettlement(SHOP_ID, balanced());

        assertTrue(saved);
        ArgumentCaptor<SettlementReport> captor = ArgumentCaptor.forClass(SettlementReport.class);
        verify(reportMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(SettlementService.STATUS_PARSED, captor.getValue().getStatus());
        verify(detailMapper).delete(any(LambdaQueryWrapper.class));
        verify(detailMapper, times(3)).insert(any(SettlementDetail.class));
    }

    @Test
    void rejectsMissingRequiredFieldsWithoutTouchingMappers() {
        UnifiedSettlement noId = balanced();
        noId.setSettlementId(" ");

        assertThrows(BusinessException.class, () -> service.saveUnifiedSettlement(SHOP_ID, noId));
        assertThrows(BusinessException.class, () -> service.saveUnifiedSettlement(SHOP_ID, null));
        UnifiedSettlement nullLines = balanced();
        nullLines.setLines(null);
        assertThrows(BusinessException.class, () -> service.saveUnifiedSettlement(SHOP_ID, nullLines));

        verify(reportMapper, never()).insert(any(SettlementReport.class));
        verify(detailMapper, never()).insert(any(SettlementDetail.class));
    }
}
