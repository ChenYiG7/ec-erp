package com.own.erp.report.service;

import com.own.erp.report.mapper.ReportQueryMapper;
import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.ReportDigest;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import com.own.erp.report.service.ReportDigestService.Period;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 经营简报服务单测(#23,AIR:mock Mapper,固定 Clock 可重复):
 *     三周期窗口语义(日报昨日单日/周报上周一至周日/月报上月自然月)、聚合文本(合计/动销/日均/TOP5/期末库存)、
 *     空数据降级(零销量/无快照写"暂无"不猜)、周期入参解析(容大小写/非法抛错)
 */
class ReportDigestServiceTest {

    /** 固定今天 = 2026-09-09(周三):三周期窗口断言的锚点 */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private ReportQueryMapper mapper;
    private ReportDigestService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ReportQueryMapper.class);
        service = new ReportDigestService(mapper, CLOCK);
        // 缺省数据面:有销量有快照,各用例按需覆盖
        when(mapper.selectSalesDaily(any(), any())).thenReturn(List.of(new SalesDailyRow(TODAY.minusDays(1), 30, 2)));
        when(mapper.selectSalesSku(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.selectLatestSnapshotDate()).thenReturn(TODAY.minusDays(1));
        when(mapper.selectSnapshot(any())).thenReturn(List.of());
    }

    @Test
    void dailyWindowIsYesterdaySingleDay() {
        ReportDigest digest = service.digest(Period.DAILY);

        verify(mapper).selectSalesDaily(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 8));
        assertEquals("DAILY", digest.period());
        assertEquals("REPORT_DAILY", digest.notifyType());
        assertEquals("经营日报 2026-09-08", digest.title());
        assertEquals(LocalDate.of(2026, 9, 8), digest.dateFrom());
        assertEquals(LocalDate.of(2026, 9, 8), digest.dateTo());
    }

    @Test
    void weeklyWindowIsLastMondayToSunday() {
        // 今天周三 09-09:上周一 = 09-07,上周日 = 09-13
        ReportDigest digest = service.digest(Period.WEEKLY);

        verify(mapper).selectSalesDaily(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));
        assertEquals("经营周报 2026-09-07~2026-09-13", digest.title());
        assertEquals("REPORT_WEEKLY", digest.notifyType());
    }

    @Test
    void monthlyWindowIsLastCalendarMonth() {
        ReportDigest digest = service.digest(Period.MONTHLY);

        verify(mapper).selectSalesDaily(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertEquals("经营月报 2026-08", digest.title());
        assertEquals("REPORT_MONTHLY", digest.notifyType());
    }

    @Test
    void contentAggregatesSalesTopSkuAndStock() {
        when(mapper.selectSalesDaily(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13))).thenReturn(List.of(
                new SalesDailyRow(LocalDate.of(2026, 9, 7), 100, 2),
                new SalesDailyRow(LocalDate.of(2026, 9, 8), 0, 0),
                new SalesDailyRow(LocalDate.of(2026, 9, 9), 50, 1)));
        when(mapper.selectSalesSku(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), 5)).thenReturn(List.of(
                new SalesSkuRow(1L, "SKU-A", "商品A", 80),
                new SalesSkuRow(2L, null, null, 40)));
        when(mapper.selectSnapshot(LocalDate.of(2026, 9, 8))).thenReturn(List.of(
                snapshot(300, 200), snapshot(156, 100)));

        ReportDigest digest = service.digest(Period.WEEKLY);

        // 合计 150/动销 2 天/日均 21(150/7 向下取整);TOP5 逐行,脏编码回落裸 ID
        String content = digest.content();
        assertTrue(content.contains("统计窗口:2026-09-07 ~ 2026-09-13"), content);
        assertTrue(content.contains("销量合计:150 件 | 动销天数:2 天 | 日均:21 件"), content);
        assertTrue(content.contains("1. SKU-A 商品A:80 件"), content);
        assertTrue(content.contains("2. SKU#2:40 件"), content);
        assertTrue(content.contains("期末库存:在库 456 件 | 可用 300 件(快照日 2026-09-08)"), content);
    }

    @Test
    void emptySalesAndMissingSnapshotDegradeWithoutGuessing() {
        when(mapper.selectSalesDaily(any(), any())).thenReturn(List.of());
        when(mapper.selectSalesSku(any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.selectLatestSnapshotDate()).thenReturn(null);

        ReportDigest digest = service.digest(Period.DAILY);

        String content = digest.content();
        assertTrue(content.contains("销量合计:0 件 | 动销天数:0 天 | 日均:0 件"), content);
        assertTrue(content.contains("销量 TOP5:无"), content);
        assertTrue(content.contains("期末库存:暂无快照"), content);
    }

    @Test
    void parseAcceptsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(Period.DAILY, Period.parse("daily"));
        assertEquals(Period.MONTHLY, Period.parse(" Monthly "));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Period.parse("yearly"));
        assertTrue(e.getMessage().contains("非法简报周期"));
    }

    private static InventorySnapshotRow snapshot(int qtyOnHand, int qtyAvailable) {
        return new InventorySnapshotRow(LocalDate.of(2026, 9, 8), 1L, "SKU-A", "商品A",
                1L, "主仓", qtyOnHand, 0, 0, qtyAvailable);
    }
}
