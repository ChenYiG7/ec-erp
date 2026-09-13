package com.own.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.finance.entity.ProfitPeriodReport;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.ProfitPeriodQueryMapper;
import com.own.erp.finance.mapper.ProfitPeriodReportMapper;
import com.own.erp.finance.mapper.SettlementReportMapper;
import com.own.erp.finance.profit.SettlementFeeSum;
import com.own.erp.finance.request.query.ProfitPeriodReportQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : ProfitPeriodReportService 单测(AIR:mock 五依赖,不依赖数据库):
 *     只读映射、FAILED 暂存态不生成、校差三态(#32 拍板 2026-09-12:平 OK/不平 DIFF/缺汇率 RATE_MISSING,
 *     收入差=SALE−订单收入、容差 0.01、缺口计数进 remark 不静默归零)。
 *     XML upsertPeriod 语法正确性由 scripts/validate_profit_sql.py 真库验证(单测 mock 测不出)。
 */
class ProfitPeriodReportServiceTest {

    private ProfitPeriodReportMapper periodReportMapper;
    private ProfitPeriodQueryMapper periodQueryMapper;
    private SettlementReportMapper settlementReportMapper;
    private ExchangeRateService exchangeRateService;
    private ProfitQueryService profitQueryService;
    private ProfitPeriodReportService service;

    @BeforeEach
    void setUp() {
        periodReportMapper = mock(ProfitPeriodReportMapper.class);
        periodQueryMapper = mock(ProfitPeriodQueryMapper.class);
        settlementReportMapper = mock(SettlementReportMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        profitQueryService = mock(ProfitQueryService.class);
        service = new ProfitPeriodReportService(periodReportMapper, periodQueryMapper, settlementReportMapper,
                exchangeRateService, profitQueryService);
    }

    @Test
    void getByIdMapsAndNullWhenMissing() {
        ProfitPeriodReport row = new ProfitPeriodReport();
        row.setId(1L);
        when(periodReportMapper.selectById(1L)).thenReturn(row);
        assertEquals(1L, service.getById(1L).id());
        assertNull(service.getById(404L));
    }

    @Test
    void pageMapsRecords() {
        ProfitPeriodReport row = new ProfitPeriodReport();
        row.setId(2L);
        Page<ProfitPeriodReport> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        doReturn(page).when(periodReportMapper).selectPage(any(), any());
        assertEquals(2L, service.page(new ProfitPeriodReportQuery()).getRecords().get(0).id());
    }

    @Test
    void rebuildMissingReportThrows() {
        when(settlementReportMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rebuildForReport(99L));
        verify(periodQueryMapper, never()).sumSettlementFees(anyLong());
    }

    @Test
    void rebuildSkipsFailedReport() {
        when(settlementReportMapper.selectById(7L)).thenReturn(report("FAILED", "USD"));
        service.rebuildForReport(7L);
        // FAILED 暂存态不聚合不生成,待重拉转 PARSED 经 overwrite 路径补派生
        verifyNoInteractions(periodQueryMapper);
        verifyNoInteractions(profitQueryService);
        verifyNoInteractions(periodReportMapper);
    }

    /** 三态①平:SALE 700 vs 订单 700、佣金 -105 vs -105,零差零缺口 → OK,flag=0,remark=null */
    @Test
    void rebuildParsedBalancedStampsOkRow() {
        SettlementReport report = report("PARSED", "USD");
        when(settlementReportMapper.selectById(7L)).thenReturn(report);
        when(exchangeRateService.resolveRate("USD", report.getPeriodEnd())).thenReturn(BigDecimal.ONE);
        when(periodQueryMapper.sumSettlementFees(7L)).thenReturn(List.of(
                new SettlementFeeSum("SALE", new BigDecimal("700.00")),
                new SettlementFeeSum("COMMISSION", new BigDecimal("-105.00")),
                new SettlementFeeSum("TRANSFER", new BigDecimal("595.00"))));
        when(profitQueryService.summarize(any())).thenReturn(
                new OrderProfitSummary(1, new BigDecimal("700.00"), new BigDecimal("400.00"),
                        new BigDecimal("-105.00"), new BigDecimal("195.00"), 0, 0, 0, null));

        service.rebuildForReport(7L);

        ArgumentCaptor<ProfitPeriodReport> captor = ArgumentCaptor.forClass(ProfitPeriodReport.class);
        verify(periodReportMapper).upsertPeriod(captor.capture());
        ProfitPeriodReport row = captor.getValue();
        assertEquals(ProfitPeriodReportService.STATUS_OK, row.getStatus());
        assertEquals(0, row.getDiffFlag());
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getDiffIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(row.getDiffCommission()));
        assertNull(row.getDiffRemark());
        assertEquals(0, new BigDecimal("700.00").compareTo(row.getOrderIncome()));
        assertEquals(0, new BigDecimal("595.0000").compareTo(row.getSettleIncome()));
        assertEquals(0, new BigDecimal("-105.0000").compareTo(row.getSettleCommission()));
        assertNull(row.getFbaFee());       // 无 FBA 行:null 与 0 语义不同
        assertNull(row.getOtherFee());     // 无 REFUND/ADVERTISING/OTHER 行
        assertEquals(0, new BigDecimal("-105.0000").compareTo(row.getOrderCommission()));
        assertEquals(0, new BigDecimal("195.00").compareTo(row.getOrderProfit()));
        assertEquals(0, row.getRateMissing());
        assertEquals(0, BigDecimal.ONE.compareTo(row.getRateUsed()), "汇率快照必须落列(rate_used)");
        assertEquals(7L, row.getSettlementId());
    }

    /** 三态②不平:收入差 +12.34 超容差、佣金差 -0.50 超容差 → DIFF,flag=1,remark 写差异项+跨期口径+缺口计数 */
    @Test
    void rebuildParsedBeyondToleranceStampsDiffRowWithRemark() {
        SettlementReport report = report("PARSED", "USD");
        when(settlementReportMapper.selectById(7L)).thenReturn(report);
        when(exchangeRateService.resolveRate("USD", report.getPeriodEnd())).thenReturn(BigDecimal.ONE);
        when(periodQueryMapper.sumSettlementFees(7L)).thenReturn(List.of(
                new SettlementFeeSum("SALE", new BigDecimal("712.34")),
                new SettlementFeeSum("COMMISSION", new BigDecimal("-105.00"))));
        when(profitQueryService.summarize(any())).thenReturn(
                new OrderProfitSummary(10, new BigDecimal("700.00"), new BigDecimal("400.00"),
                        new BigDecimal("-104.50"), new BigDecimal("195.00"), 2, 1, 3, null));

        service.rebuildForReport(7L);

        ArgumentCaptor<ProfitPeriodReport> captor = ArgumentCaptor.forClass(ProfitPeriodReport.class);
        verify(periodReportMapper).upsertPeriod(captor.capture());
        ProfitPeriodReport row = captor.getValue();
        assertEquals(ProfitPeriodReportService.STATUS_DIFF, row.getStatus());
        assertEquals(1, row.getDiffFlag());
        assertEquals(0, BigDecimal.ONE.compareTo(row.getRateUsed()), "汇率快照必须落列(rate_used)");
        assertEquals(0, new BigDecimal("12.3400").compareTo(row.getDiffIncome()));
        assertEquals(0, new BigDecimal("-0.5000").compareTo(row.getDiffCommission()));
        String remark = row.getDiffRemark();
        assertTrue(remark.contains("收入差=12.3400(SALE 712.3400-订单 700.00)"), remark);
        assertTrue(remark.contains("佣金差=-0.5000(结算 -105.0000-订单 -104.50)"), remark);
        assertTrue(remark.contains("跨期:结算按posted_at/订单按order_time"), remark);
        assertTrue(remark.contains("缺口:缺汇率2/未出库1/缺佣金3(行10)"), remark);
        assertTrue(remark.length() <= 500, "remark 超 VARCHAR(500)");
    }

    /** 三态③缺汇率:rateUsed=null → RATE_MISSING,CNY 列全 NULL 禁猜,缺口计数留 remark 不静默 */
    @Test
    void rebuildParsedMissingRateStampsRateMissingRow() {
        SettlementReport report = report("PARSED", "USD");
        when(settlementReportMapper.selectById(7L)).thenReturn(report);
        when(exchangeRateService.resolveRate("USD", report.getPeriodEnd())).thenReturn(null);
        when(periodQueryMapper.sumSettlementFees(7L)).thenReturn(List.of(
                new SettlementFeeSum("SALE", new BigDecimal("700.00"))));
        when(profitQueryService.summarize(any())).thenReturn(
                new OrderProfitSummary(10, new BigDecimal("700.00"), new BigDecimal("400.00"),
                        new BigDecimal("-105.00"), new BigDecimal("195.00"), 2, 1, 3, null));

        service.rebuildForReport(7L);

        ArgumentCaptor<ProfitPeriodReport> captor = ArgumentCaptor.forClass(ProfitPeriodReport.class);
        verify(periodReportMapper).upsertPeriod(captor.capture());
        ProfitPeriodReport row = captor.getValue();
        assertEquals(ProfitPeriodReportService.STATUS_RATE_MISSING, row.getStatus());
        assertEquals(1, row.getRateMissing());
        assertNull(row.getRateUsed());
        assertNull(row.getOrderIncome());
        assertNull(row.getSettleIncome());
        assertNull(row.getSettleCommission());
        assertNull(row.getOrderCommission());
        assertNull(row.getOrderProfit());
        assertNull(row.getDiffIncome());
        assertNull(row.getDiffCommission());
        assertEquals(0, row.getDiffFlag());
        assertEquals("缺口:缺汇率2/未出库1/缺佣金3(行10)", row.getDiffRemark());
        // 身份列不受缺汇率影响
        assertEquals(1L, row.getShopId());
        assertEquals(7L, row.getSettlementId());
        assertEquals("USD", row.getCurrency());
    }

    /** 容差边界:差=0.01(结算列精度)不超容差 → OK 防尾差误报(同 RefundReconciliationService 语义) */
    @Test
    void rebuildParsedAtToleranceEdgeStaysOk() {
        SettlementReport report = report("PARSED", "USD");
        when(settlementReportMapper.selectById(7L)).thenReturn(report);
        when(exchangeRateService.resolveRate("USD", report.getPeriodEnd())).thenReturn(BigDecimal.ONE);
        when(periodQueryMapper.sumSettlementFees(7L)).thenReturn(List.of(
                new SettlementFeeSum("SALE", new BigDecimal("700.01"))));
        when(profitQueryService.summarize(any())).thenReturn(
                new OrderProfitSummary(1, new BigDecimal("700.00"), new BigDecimal("400.00"),
                        new BigDecimal("-105.00"), new BigDecimal("195.00"), 0, 0, 0, null));

        service.rebuildForReport(7L);

        ArgumentCaptor<ProfitPeriodReport> captor = ArgumentCaptor.forClass(ProfitPeriodReport.class);
        verify(periodReportMapper).upsertPeriod(captor.capture());
        ProfitPeriodReport row = captor.getValue();
        assertEquals(ProfitPeriodReportService.STATUS_OK, row.getStatus());
        assertEquals(0, row.getDiffFlag());
        assertEquals(0, new BigDecimal("0.0100").compareTo(row.getDiffIncome()));
        // 佣金差不可比(结算无 COMMISSION 行)不硬算
        assertNull(row.getDiffCommission());
        // 差在容差内且缺口计数全零 → remark 空(禁拼空段)
        assertNull(row.getDiffRemark());
    }

    private SettlementReport report(String status, String currency) {
        return SettlementReport.builder()
                .id(7L).shopId(1L).settlementId("S-1").status(status).currency(currency)
                .periodStart(LocalDateTime.of(2026, 9, 1, 0, 0))
                .periodEnd(LocalDateTime.of(2026, 9, 15, 0, 0))
                .build();
    }
}
