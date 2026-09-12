package com.own.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.PlatformFeeRate;
import com.own.erp.finance.mapper.PlatformFeeRateMapper;
import com.own.erp.finance.request.command.PlatformFeeRateSaveRequest;
import com.own.erp.finance.request.query.PlatformFeeRateQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : PlatformFeeRateService 单测(AIR:mock Mapper,不依赖数据库):
 *     写侧白名单/区间/查重校验、逻辑删除、估费回溯挑选(eq/orderBy 路径,绕开 §10 wrapper 急切解析坑)
 */
class PlatformFeeRateServiceTest {

    private PlatformFeeRateMapper platformFeeRateMapper;
    private PlatformFeeRateService platformFeeRateService;

    @BeforeEach
    void setUp() {
        platformFeeRateMapper = mock(PlatformFeeRateMapper.class);
        platformFeeRateService = new PlatformFeeRateService(platformFeeRateMapper);
    }

    private PlatformFeeRateSaveRequest commissionRequest(String feeType, String rate,
                                                          LocalDate from, LocalDate to) {
        return PlatformFeeRateSaveRequest.builder()
                .platform("AMAZON").feeType(feeType).rate(new BigDecimal(rate)).effFrom(from).effTo(to).build();
    }

    @Nested
    class Write {

        @Test
        void saveForcesManualSourceAndInserts() {
            when(platformFeeRateMapper.selectCount(any())).thenReturn(0L);
            PlatformFeeRateSaveRequest request =
                    commissionRequest("COMMISSION", "0.15", LocalDate.of(2026, 9, 1), null);

            platformFeeRateService.save(request);

            ArgumentCaptor<PlatformFeeRate> captor = ArgumentCaptor.forClass(PlatformFeeRate.class);
            verify(platformFeeRateMapper).insert(captor.capture());
            assertEquals("MANUAL", captor.getValue().getSource());
            assertEquals("AMAZON", captor.getValue().getPlatform());
        }

        @Test
        void saveRejectsNonCommissionFeeType() {
            // FBA 仓储类无费率不猜:白名单外费种拒绝落库
            BusinessException ex = assertThrows(BusinessException.class, () -> platformFeeRateService.save(
                    commissionRequest("FBA_FEE", "0.10", LocalDate.of(2026, 9, 1), null)));
            assertTrue(ex.getMessage().contains("COMMISSION"));
            verifyNoInteractions(platformFeeRateMapper);
        }

        @Test
        void saveRejectsDuplicateDimensionAndEffectiveDate() {
            when(platformFeeRateMapper.selectCount(any())).thenReturn(1L);
            assertThrows(BusinessException.class, () -> platformFeeRateService.save(
                    commissionRequest("COMMISSION", "0.15", LocalDate.of(2026, 9, 1), null)));
            verify(platformFeeRateMapper, never()).insert(any(PlatformFeeRate.class));
        }

        @Test
        void saveRejectsInvertedEffectiveInterval() {
            BusinessException ex = assertThrows(BusinessException.class, () -> platformFeeRateService.save(
                    commissionRequest("COMMISSION", "0.15",
                            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1))));
            assertTrue(ex.getMessage().contains("生效止"));
            verify(platformFeeRateMapper, never()).insert(any(PlatformFeeRate.class));
        }

        @Test
        void updateMissingRateThrows() {
            when(platformFeeRateMapper.selectById(9L)).thenReturn(null);
            assertThrows(BusinessException.class, () -> platformFeeRateService.update(9L,
                    commissionRequest("COMMISSION", "0.15", LocalDate.of(2026, 9, 1), null)));
            verify(platformFeeRateMapper, never()).updateById(any(PlatformFeeRate.class));
        }

        @Test
        void deleteIsLogicalByTableLogic() {
            platformFeeRateService.delete(1L);
            verify(platformFeeRateMapper).deleteById(1L);
        }
    }

    @Nested
    class ReadAndResolve {

        @Test
        void getByIdMapsAndNullWhenMissing() {
            PlatformFeeRate rate = new PlatformFeeRate();
            rate.setId(1L);
            when(platformFeeRateMapper.selectById(1L)).thenReturn(rate);
            assertEquals(1L, platformFeeRateService.getById(1L).id());
            assertNull(platformFeeRateService.getById(404L));
        }

        @Test
        void pageMapsRecordsToResponse() {
            PlatformFeeRate rate = new PlatformFeeRate();
            rate.setId(2L);
            Page<PlatformFeeRate> page = new Page<>(1, 10);
            page.setRecords(List.of(rate));
            doReturn(page).when(platformFeeRateMapper).selectPage(any(), any());
            assertEquals(2L, platformFeeRateService.page(new PlatformFeeRateQuery()).getRecords().get(0).id());
        }

        @Test
        void blankArgumentsYieldNullWithoutQuery() {
            assertNull(platformFeeRateService.resolveFeeRate(null, "COMMISSION", LocalDate.of(2026, 9, 1)));
            verifyNoInteractions(platformFeeRateMapper);
        }

        @Test
        void resolveReturnsPersistedRate() {
            PlatformFeeRate rate = PlatformFeeRate.builder().platform("AMAZON")
                    .feeType("COMMISSION").rate(new BigDecimal("0.150000")).build();
            when(platformFeeRateMapper.selectOne(any())).thenReturn(rate);
            assertEquals(0, new BigDecimal("0.150000").compareTo(
                    platformFeeRateService.resolveFeeRate("AMAZON", "COMMISSION", LocalDate.of(2026, 9, 1))));
        }

        @Test
        void pickLatestEffectiveVersionByBusinessDate() {
            PlatformFeeRate v1 = PlatformFeeRate.builder().rate(new BigDecimal("0.100000"))
                    .effFrom(LocalDate.of(2026, 1, 1)).build();
            PlatformFeeRate v2 = PlatformFeeRate.builder().rate(new BigDecimal("0.150000"))
                    .effFrom(LocalDate.of(2026, 6, 1)).build();
            // 9/1 回溯取 6/1 新版
            PlatformFeeRate picked = PlatformFeeRateService.pickFeeRate(List.of(v1, v2), LocalDate.of(2026, 9, 1));
            assertEquals(0, new BigDecimal("0.150000").compareTo(picked.getRate()));
            // 5/1 只能取 1/1 旧版
            picked = PlatformFeeRateService.pickFeeRate(List.of(v1, v2), LocalDate.of(2026, 5, 1));
            assertEquals(0, new BigDecimal("0.100000").compareTo(picked.getRate()));
        }

        @Test
        void pickRespectsEffToAndFutureVersions() {
            PlatformFeeRate expired = PlatformFeeRate.builder().rate(new BigDecimal("0.100000"))
                    .effFrom(LocalDate.of(2026, 1, 1)).effTo(LocalDate.of(2026, 3, 1)).build();
            PlatformFeeRate future = PlatformFeeRate.builder().rate(new BigDecimal("0.200000"))
                    .effFrom(LocalDate.of(2026, 12, 1)).build();
            // 4/1:旧版已止、新版未生 → 无费率(null,不猜)
            assertNull(PlatformFeeRateService.pickFeeRate(List.of(expired, future), LocalDate.of(2026, 4, 1)));
            // 3/1 含 eff_to 当日仍生效
            assertEquals(0, new BigDecimal("0.100000").compareTo(
                    PlatformFeeRateService.pickFeeRate(List.of(expired), LocalDate.of(2026, 3, 1)).getRate()));
            assertNull(PlatformFeeRateService.pickFeeRate(List.of(), LocalDate.of(2026, 4, 1)));
        }
    }
}
