package com.own.erp.finance.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.PurchaseQueryApi.PurchasePayableView;
import com.own.erp.finance.entity.PaymentAlloc;
import com.own.erp.finance.entity.PaymentRecord;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.PaymentAllocMapper;
import com.own.erp.finance.mapper.PaymentQueryMapper;
import com.own.erp.finance.mapper.PaymentRecordMapper;
import com.own.erp.finance.request.command.ManualPaymentRequest;
import com.own.erp.finance.request.command.PurchasePaymentRequest;
import com.own.erp.finance.response.PurchasePaidRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : PaymentRecordService 单测(#31,AIR:mock Mapper/契约,不依赖数据库):
 *     采购付款守卫链(存在性/DRAFT 拦截/同供应商/按单超额/Σ分摊≤流水额)、手工登记多币种缺汇率留 NULL、
 *     作废 cas、结算回款派生三态(新建/刷新/VOIDED 不复活)、已付聚合补零。
 *     注:MP LambdaQueryWrapper.in() 急切解析在纯 Mockito 不可测(docs/07 §10),
 *     sumPaidByPoIds 为 XML 聚合,mock 返回值即可;流水号撞号重试路径不在本类射程
 */
class PaymentRecordServiceTest {

    private PaymentRecordMapper paymentRecordMapper;
    private PaymentAllocMapper paymentAllocMapper;
    private PaymentQueryMapper paymentQueryMapper;
    private ExchangeRateService exchangeRateService;
    private PurchaseQueryApi purchaseQueryApi;
    private CurrentUserApi currentUserApi;
    private PaymentRecordService service;

    @BeforeEach
    void setUp() {
        paymentRecordMapper = mock(PaymentRecordMapper.class);
        paymentAllocMapper = mock(PaymentAllocMapper.class);
        paymentQueryMapper = mock(PaymentQueryMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        purchaseQueryApi = mock(PurchaseQueryApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        service = new PaymentRecordService(paymentRecordMapper, paymentAllocMapper, paymentQueryMapper,
                exchangeRateService, purchaseQueryApi, currentUserApi);
        when(paymentRecordMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            ((PaymentRecord) inv.getArgument(0)).setId(9001L);
            return 1;
        }).when(paymentRecordMapper).insert(any(PaymentRecord.class));
        when(currentUserApi.currentUserId()).thenReturn(1L);
    }

    private static PurchasePayableView po(Long id, String poNo, Long supplierId, String status, String total) {
        return PurchasePayableView.builder().id(id).poNo(poNo).supplierId(supplierId).status(status)
                .totalAmount(new BigDecimal(total)).build();
    }

    private static PurchasePaymentRequest.Alloc alloc(Long poId, String amount) {
        return PurchasePaymentRequest.Alloc.builder().poId(poId).amount(new BigDecimal(amount)).build();
    }

    private void givenPayables(PurchasePayableView... views) {
        when(purchaseQueryApi.findPurchasePayables(anyCollection())).thenReturn(List.of(views));
    }

    private void givenPaid(PurchasePaidRow... rows) {
        when(paymentQueryMapper.sumPaidByPoIds(anyCollection())).thenReturn(List.of(rows));
    }

    // ============================ 采购付款 ============================

    @Test
    void registerPurchasePaymentHappyOnePaymentMultiplePos() {
        givenPayables(po(1L, "PO1", 7L, "AUDITED", "100"), po(2L, "PO2", 7L, "RECEIVED", "100"));
        givenPaid();
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100"))
                .paidAt(LocalDateTime.of(2026, 9, 11, 10, 0))
                .method("银行转账")
                .allocs(List.of(alloc(1L, "60"), alloc(2L, "40")))
                .build();

        Long id = service.registerPurchasePayment(request);

        assertEquals(9001L, id);
        ArgumentCaptor<PaymentRecord> recordCaptor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(recordCaptor.capture());
        PaymentRecord saved = recordCaptor.getValue();
        assertEquals("EXPENSE", saved.getDirection());
        assertEquals("PURCHASE_PAYMENT", saved.getBizType());
        assertEquals("SUPPLIER", saved.getPartyType());
        assertEquals(7L, saved.getPartyId());
        assertEquals("CNY", saved.getCurrency());
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getExchangeRate()));
        assertEquals(0, new BigDecimal("100").compareTo(saved.getAmountCny()));
        assertEquals("NORMAL", saved.getStatus());
        assertEquals(1L, saved.getCreatedBy());
        ArgumentCaptor<PaymentAlloc> allocCaptor = ArgumentCaptor.forClass(PaymentAlloc.class);
        verify(paymentAllocMapper, org.mockito.Mockito.times(2)).insert(allocCaptor.capture());
        List<PaymentAlloc> savedAllocs = allocCaptor.getAllValues();
        assertEquals(9001L, savedAllocs.get(0).getPaymentId());
        assertEquals("PURCHASE", savedAllocs.get(0).getAllocBizType());
        assertEquals(1L, savedAllocs.get(0).getAllocBizId());
        assertEquals(0, new BigDecimal("60").compareTo(savedAllocs.get(0).getAmount()));
        assertEquals(2L, savedAllocs.get(1).getAllocBizId());
    }

    @Test
    void draftPurchaseOrderCannotBePaid() {
        givenPayables(po(1L, "PO1", 7L, "DRAFT", "100"));
        givenPaid();
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100")).allocs(List.of(alloc(1L, "100"))).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerPurchasePayment(request));
        assertTrue(ex.getMessage().contains("未审核"));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void missingPurchaseOrderRejected() {
        givenPayables();
        givenPaid();
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100")).allocs(List.of(alloc(99L, "100"))).build();

        assertThrows(BusinessException.class, () -> service.registerPurchasePayment(request));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void allocationExceedingPoTotalPaidPlusThisBlocked() {
        givenPayables(po(1L, "PO1", 7L, "AUDITED", "100"));
        givenPaid(PurchasePaidRow.builder().poId(1L).paidAmount(new BigDecimal("90")).build());
        // 已付 90 + 本次 20 > 总额 100
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("20")).allocs(List.of(alloc(1L, "20"))).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerPurchasePayment(request));
        assertTrue(ex.getMessage().contains("分摊超额"));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void allocSumGreaterThanPaymentBlocked() {
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("50")).allocs(List.of(alloc(1L, "60"))).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.registerPurchasePayment(request));
        assertTrue(ex.getMessage().contains("分摊合计超过付款金额"));
        verify(purchaseQueryApi, never()).findPurchasePayables(anyCollection());
    }

    @Test
    void allocationsAcrossDifferentSuppliersBlocked() {
        givenPayables(po(1L, "PO1", 7L, "AUDITED", "100"), po(2L, "PO2", 8L, "AUDITED", "100"));
        givenPaid();
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100")).allocs(List.of(alloc(1L, "50"), alloc(2L, "50"))).build();

        assertThrows(BusinessException.class, () -> service.registerPurchasePayment(request));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void duplicatePoInOnePaymentBlocked() {
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100")).allocs(List.of(alloc(1L, "50"), alloc(1L, "50"))).build();

        assertThrows(BusinessException.class, () -> service.registerPurchasePayment(request));
    }

    @Test
    void partialAllocationAllowedAsCreditBalance() {
        // 付款 100,只分摊 60(允许部分挂账)
        givenPayables(po(1L, "PO1", 7L, "AUDITED", "100"));
        givenPaid();
        PurchasePaymentRequest request = PurchasePaymentRequest.builder()
                .amount(new BigDecimal("100")).allocs(List.of(alloc(1L, "60"))).build();

        Long id = service.registerPurchasePayment(request);
        assertEquals(9001L, id);
        verify(paymentAllocMapper).insert(any(PaymentAlloc.class));
    }

    // ============================ 手工登记 ============================

    @Test
    void manualCnyFreezesRateOne() {
        when(exchangeRateService.resolveRate(any(), any())).thenReturn(BigDecimal.ONE);
        ManualPaymentRequest request = ManualPaymentRequest.builder()
                .direction("INCOME").partyType("OTHER").amount(new BigDecimal("88.88"))
                .currency("CNY").build();

        service.registerManual(request);

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(captor.capture());
        PaymentRecord saved = captor.getValue();
        assertEquals("MANUAL_ADJUST", saved.getBizType());
        assertEquals("INCOME", saved.getDirection());
        assertEquals("OTHER", saved.getPartyType());
        assertNull(saved.getPartyId());
        assertEquals(0, new BigDecimal("88.88").compareTo(saved.getAmountCny()));
    }

    @Test
    void manualForeignCurrencyWithoutRateLeavesCnyNull() {
        when(exchangeRateService.resolveRate(any(), any())).thenReturn(null);
        ManualPaymentRequest request = ManualPaymentRequest.builder()
                .direction("INCOME").partyType("PLATFORM").partyId(7L)
                .amount(new BigDecimal("100")).currency("USD").build();

        service.registerManual(request);

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(captor.capture());
        PaymentRecord saved = captor.getValue();
        assertEquals(7L, saved.getPartyId());
        assertEquals("USD", saved.getCurrency());
        assertNull(saved.getExchangeRate());
        assertNull(saved.getAmountCny());
    }

    @Test
    void manualBadDirectionOrPartyRejected() {
        ManualPaymentRequest badDirection = ManualPaymentRequest.builder()
                .direction("UP").partyType("OTHER").amount(new BigDecimal("1")).build();
        assertThrows(BusinessException.class, () -> service.registerManual(badDirection));

        ManualPaymentRequest missingParty = ManualPaymentRequest.builder()
                .direction("EXPENSE").partyType("SUPPLIER").amount(new BigDecimal("1")).build();
        assertThrows(BusinessException.class, () -> service.registerManual(missingParty));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    // ============================ 作废 ============================

    @Test
    void voidNormalRecordOkAndVoidedTwiceRejected() {
        when(paymentRecordMapper.casVoid(1L)).thenReturn(1);
        service.voidPayment(1L);
        verify(paymentRecordMapper).casVoid(1L);

        when(paymentRecordMapper.casVoid(2L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.voidPayment(2L));
        assertTrue(ex.getMessage().contains("作废失败"));
    }

    // ============================ 结算回款派生 ============================

    private SettlementReport report(Long id, String currency, String transfer) {
        return SettlementReport.builder().id(id).shopId(7L).settlementId("S-1").currency(currency)
                .transferAmount(new BigDecimal(transfer))
                .periodStart(LocalDateTime.of(2026, 8, 1, 0, 0))
                .periodEnd(LocalDateTime.of(2026, 8, 14, 0, 0)).build();
    }

    @Test
    void deriveReceiptCreatesIncomeRecordWhenAbsent() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(null);
        when(exchangeRateService.resolveRate(any(), any())).thenReturn(new BigDecimal("7.10000000"));

        service.deriveSettlementReceipt(report(5L, "USD", "100.00"), Instant.parse("2026-08-16T00:00:00Z"));

        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordMapper).insert(captor.capture());
        PaymentRecord saved = captor.getValue();
        assertEquals("INCOME", saved.getDirection());
        assertEquals("SETTLEMENT_RECEIPT", saved.getBizType());
        assertEquals("PLATFORM", saved.getPartyType());
        assertEquals(7L, saved.getPartyId());
        assertEquals("SETTLEMENT_REPORT", saved.getRefType());
        assertEquals(5L, saved.getRefId());
        assertEquals(0, new BigDecimal("710.0000").compareTo(saved.getAmountCny()));
    }

    @Test
    void deriveReceiptRefreshesNormalExistingInsteadOfInsert() {
        PaymentRecord existing = PaymentRecord.builder().id(300L).status("NORMAL")
                .refType("SETTLEMENT_REPORT").refId(5L).build();
        when(paymentRecordMapper.selectOne(any())).thenReturn(existing);
        when(exchangeRateService.resolveRate(any(), any())).thenReturn(BigDecimal.ONE);

        service.deriveSettlementReceipt(report(5L, "CNY", "120.00"), null);

        verify(paymentRecordMapper).updateById(existing);
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
        assertEquals(0, new BigDecimal("120.00").compareTo(existing.getAmount()));
    }

    @Test
    void deriveReceiptDoesNotResurrectManuallyVoidedRecord() {
        when(paymentRecordMapper.selectOne(any())).thenReturn(
                PaymentRecord.builder().id(300L).status("VOIDED").build());

        service.deriveSettlementReceipt(report(5L, "CNY", "120.00"), null);

        verify(paymentRecordMapper, never()).updateById(any(PaymentRecord.class));
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    @Test
    void deriveReceiptSkipsZeroTransfer() {
        service.deriveSettlementReceipt(report(5L, "USD", "0"), null);
        verify(paymentRecordMapper, never()).selectOne(any());
        verify(paymentRecordMapper, never()).insert(any(PaymentRecord.class));
    }

    // ============================ 已付聚合 ============================

    @Test
    void purchasePaidEmptyInputShortCircuits() {
        assertTrue(service.listPurchasePaid(List.of()).isEmpty());
        verify(paymentQueryMapper, never()).sumPaidByPoIds(anyCollection());
    }

    @Test
    void purchasePaidFillsMissingPoWithZero() {
        when(paymentQueryMapper.sumPaidByPoIds(anyCollection()))
                .thenReturn(List.of(PurchasePaidRow.builder().poId(1L).paidAmount(new BigDecimal("30")).build()));

        List<PurchasePaidRow> rows = service.listPurchasePaid(List.of(1L, 2L));

        assertEquals(2, rows.size());
        assertEquals(0, new BigDecimal("30").compareTo(rows.get(0).paidAmount()));
        assertEquals(2L, rows.get(1).poId());
        assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(1).paidAmount()));
    }
}
