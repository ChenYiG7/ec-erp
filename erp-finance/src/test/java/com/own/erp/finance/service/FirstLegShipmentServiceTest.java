package com.own.erp.finance.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.GoodsQueryApi.SkuView;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.PurchaseQueryApi.SkuSupplierView;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.contract.WarehouseApi.WarehouseView;
import com.own.erp.finance.constant.FirstLegConsts;
import com.own.erp.finance.entity.FirstLegAlloc;
import com.own.erp.finance.entity.FirstLegBox;
import com.own.erp.finance.entity.FirstLegShipment;
import com.own.erp.finance.mapper.FirstLegAllocMapper;
import com.own.erp.finance.mapper.FirstLegBoxItemMapper;
import com.own.erp.finance.mapper.FirstLegBoxMapper;
import com.own.erp.finance.mapper.FirstLegQueryMapper;
import com.own.erp.finance.mapper.FirstLegShipmentMapper;
import com.own.erp.finance.request.command.FirstLegShipRequest;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest.BoxItemSave;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest.BoxSave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : FirstLegShipmentService 单测(#33,AIR:mock Mapper/契约,不依赖数据库):
 *     建单守卫链(流向 SELF→OVERSEAS/FBA/箱号重复/同箱SKU重复/SKU存在性/策略词表)、改删状态守卫、
 *     装箱空箱拦截、发货录运费三汇率路径(CNY/回溯/手填)+ 无报价拦截、
 *     分摊三策略(数量/重量/金额+采购价回退成本价)勾稽 Σ=freight_cny、全0降级按数量留 remark、
 *     部分缺基数拦截、尾差并入最大基数行;纯单 cas 四类守卫另见 FirstLegShipmentStateMachineTest。
 *     注:MP LambdaQueryWrapper 纯 Mockito 环境无元数据,本类只走 eq 路径不碰 .in()(docs/07 §10)
 */
class FirstLegShipmentServiceTest {

    private static final long FROM_WH = 100L;
    private static final long TO_WH = 200L;
    private static final Long USER = 1L;

    private FirstLegShipmentMapper shipmentMapper;
    private FirstLegBoxMapper boxMapper;
    private FirstLegBoxItemMapper boxItemMapper;
    private FirstLegAllocMapper allocMapper;
    private FirstLegQueryMapper queryMapper;
    private ExchangeRateService exchangeRateService;
    private WarehouseApi warehouseApi;
    private GoodsQueryApi goodsQueryApi;
    private PurchaseQueryApi purchaseQueryApi;
    private CurrentUserApi currentUserApi;
    private FirstLegShipmentService service;

    private long boxIdSeq = 500L;

    @BeforeEach
    void setUp() {
        shipmentMapper = mock(FirstLegShipmentMapper.class);
        boxMapper = mock(FirstLegBoxMapper.class);
        boxItemMapper = mock(FirstLegBoxItemMapper.class);
        allocMapper = mock(FirstLegAllocMapper.class);
        queryMapper = mock(FirstLegQueryMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        purchaseQueryApi = mock(PurchaseQueryApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        service = new FirstLegShipmentService(shipmentMapper, boxMapper, boxItemMapper, allocMapper, queryMapper,
                exchangeRateService, warehouseApi, goodsQueryApi, purchaseQueryApi, currentUserApi);

        when(shipmentMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            ((FirstLegShipment) inv.getArgument(0)).setId(9001L);
            return 1;
        }).when(shipmentMapper).insert(any(FirstLegShipment.class));
        doAnswer(inv -> {
            ((FirstLegBox) inv.getArgument(0)).setId(++boxIdSeq);
            return 1;
        }).when(boxMapper).insert(any(FirstLegBox.class));
        when(currentUserApi.currentUserId()).thenReturn(USER);
        givenWarehouse(FROM_WH, "SELF");
        givenWarehouse(TO_WH, "OVERSEAS");
    }

    // ============================ 造数辅助 ============================

    private void givenWarehouse(long id, String type) {
        when(warehouseApi.findWarehouseViewById(id)).thenReturn(WarehouseView.builder()
                .id(id).whName("WH" + id).whType(type).country(type.equals("SELF") ? "CN" : "US").status(1).build());
    }

    private static SkuView sku(long id, String code, Integer weightG, String costPrice) {
        return SkuView.builder().id(id).productId(1L).skuCode(code).costPrice(costPrice == null ? null : new BigDecimal(costPrice))
                .weightG(weightG).status(1).build();
    }

    private void givenSkus(SkuView... skus) {
        when(goodsQueryApi.findSkusByIds(anyCollection())).thenReturn(List.of(skus));
    }

    private static SkuSupplierView latest(long skuId, String lastPrice) {
        return SkuSupplierView.builder().skuId(skuId).supplierId(9L).supplierName("SUP")
                .lastPrice(lastPrice == null ? null : new BigDecimal(lastPrice)).lastPoNo("PO1")
                .lastPoAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
    }

    private static BoxItemSave item(long skuId, int qty) {
        return BoxItemSave.builder().skuId(skuId).quantity(qty).build();
    }

    private static BoxSave box(String no, BoxItemSave... items) {
        return BoxSave.builder().boxNo(no).items(List.of(items)).build();
    }

    private FirstLegShipmentSaveRequest request(String strategy, BoxSave... boxes) {
        return FirstLegShipmentSaveRequest.builder()
                .fromWarehouseId(FROM_WH).toWarehouseId(TO_WH)
                .allocateStrategy(strategy).boxes(boxes == null ? null : List.of(boxes))
                .build();
    }

    private static FirstLegShipment shipment(String status, String strategy, String freightCny) {
        return FirstLegShipment.builder().id(9001L).shipmentNo("FL202609110001")
                .fromWarehouseId(FROM_WH).toWarehouseId(TO_WH)
                .status(status).allocateStrategy(strategy)
                .freightAmount(freightCny == null ? null : new BigDecimal(freightCny))
                .currency("CNY").freightCny(freightCny == null ? null : new BigDecimal(freightCny))
                .exchangeRate(BigDecimal.ONE).build();
    }

    /** 桩:发货单下的箱与内件(boxItemMapper.selectList 按箱顺序连续返回各箱内件) */
    @SuppressWarnings("unchecked")
    private void givenPacking(List<FirstLegBox> boxes, List<List<com.own.erp.finance.entity.FirstLegBoxItem>> itemsPerBox) {
        when(boxMapper.selectList(any())).thenReturn(boxes);
        List<com.own.erp.finance.entity.FirstLegBoxItem>[] rest = itemsPerBox
                .subList(1, itemsPerBox.size()).toArray(List[]::new);
        when(boxItemMapper.selectList(any())).thenReturn(itemsPerBox.get(0), rest);
    }

    private static FirstLegBox boxEntity(long id, String no) {
        return FirstLegBox.builder().id(id).shipmentId(9001L).boxNo(no).build();
    }

    private static com.own.erp.finance.entity.FirstLegBoxItem boxItemEntity(long boxId, long skuId, int qty) {
        return com.own.erp.finance.entity.FirstLegBoxItem.builder()
                .id(boxId * 10 + skuId).boxId(boxId).skuId(skuId).quantity(qty).build();
    }

    private Map<Long, BigDecimal> allocateAndCapture(FirstLegShipment s) {
        when(shipmentMapper.casAllocate(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(s);
        service.allocate(9001L);
        ArgumentCaptor<FirstLegAlloc> captor = ArgumentCaptor.forClass(FirstLegAlloc.class);
        verify(allocMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        Map<Long, BigDecimal> amountBySku = new HashMap<>();
        for (FirstLegAlloc alloc : captor.getAllValues()) {
            amountBySku.put(alloc.getSkuId(), alloc.getAllocAmount());
        }
        return amountBySku;
    }

    // ============================ 建单/改单/删除守卫 ============================

    @Test
    void saveHappyWithBoxesDefaultsWeightStrategy() {
        givenSkus(sku(11L, "S11", 100, "10"), sku(12L, "S12", 200, "20"));
        Long id = service.save(request(null, box("B1", item(11L, 2)), box("B2", item(12L, 1))));
        assertEquals(9001L, id);

        ArgumentCaptor<FirstLegShipment> h = ArgumentCaptor.forClass(FirstLegShipment.class);
        verify(shipmentMapper).insert(h.capture());
        assertEquals(FirstLegConsts.STATUS_DRAFT, h.getValue().getStatus());
        assertEquals(FirstLegConsts.STRATEGY_WEIGHT, h.getValue().getAllocateStrategy());
        assertEquals("CNY", h.getValue().getCurrency());
        assertTrue(h.getValue().getShipmentNo().startsWith("FL"));
        verify(boxMapper, org.mockito.Mockito.times(2)).insert(any(FirstLegBox.class));
        verify(boxItemMapper, org.mockito.Mockito.times(2))
                .insert(any(com.own.erp.finance.entity.FirstLegBoxItem.class));
    }

    @Test
    void saveWithoutBoxesAllowedForDraftHeaderFirst() {
        Long id = service.save(request(FirstLegConsts.STRATEGY_QTY));
        assertEquals(9001L, id);
        verify(boxMapper, never()).insert(any(FirstLegBox.class));
    }

    @Test
    void saveRejectsNonSelfFromWarehouse() {
        givenWarehouse(FROM_WH, "OVERSEAS");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(request(FirstLegConsts.STRATEGY_QTY)));
        assertTrue(ex.getMessage().contains("国内自仓"));
        verify(shipmentMapper, never()).insert(any(FirstLegShipment.class));
    }

    @Test
    void saveRejectsSelfToWarehouse() {
        givenWarehouse(TO_WH, "SELF");
        assertThrows(BusinessException.class, () -> service.save(request(FirstLegConsts.STRATEGY_QTY)));
        verify(shipmentMapper, never()).insert(any(FirstLegShipment.class));
    }

    @Test
    void saveRejectsMissingWarehouse() {
        when(warehouseApi.findWarehouseViewById(FROM_WH)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.save(request(FirstLegConsts.STRATEGY_QTY)));
    }

    @Test
    void saveRejectsDuplicateBoxNo() {
        givenSkus(sku(11L, "S11", 100, null));
        FirstLegShipmentSaveRequest req = request(FirstLegConsts.STRATEGY_QTY,
                box("B1", item(11L, 1)), box("B1", item(11L, 1)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(req));
        assertTrue(ex.getMessage().contains("箱号在单内重复"));
    }

    @Test
    void saveRejectsDuplicateSkuInSameBox() {
        givenSkus(sku(11L, "S11", 100, null));
        FirstLegShipmentSaveRequest req = request(FirstLegConsts.STRATEGY_QTY,
                box("B1", item(11L, 1), item(11L, 2)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(req));
        assertTrue(ex.getMessage().contains("同一箱内同一 SKU 重复"));
    }

    @Test
    void saveRejectsUnknownSku() {
        givenSkus(sku(11L, "S11", 100, null));
        FirstLegShipmentSaveRequest req = request(FirstLegConsts.STRATEGY_QTY,
                box("B1", item(99L, 1)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(req));
        assertTrue(ex.getMessage().contains("SKU 不存在"));
    }

    @Test
    void saveRejectsInvalidStrategyWord() {
        assertThrows(BusinessException.class, () -> service.save(request("VOLUME")));
    }

    @Test
    void updateOnlyDraft() {
        when(shipmentMapper.selectByIdForUpdate(9001L)).thenReturn(shipment("BOXED", "QTY", null));
        assertThrows(BusinessException.class,
                () -> service.update(9001L, request(FirstLegConsts.STRATEGY_QTY)));
        verify(shipmentMapper, never()).updateById(any(FirstLegShipment.class));
    }

    @Test
    void updateDraftReplacesBoxes() {
        when(shipmentMapper.selectByIdForUpdate(9001L)).thenReturn(shipment("DRAFT", "QTY", null));
        givenSkus(sku(11L, "S11", 100, null));
        when(boxMapper.selectList(any())).thenReturn(List.of(boxEntity(501L, "OLD")));
        service.update(9001L, request(FirstLegConsts.STRATEGY_QTY, box("B1", item(11L, 3))));
        verify(shipmentMapper).updateById(any(FirstLegShipment.class));
        verify(boxItemMapper).delete(any());
        verify(boxMapper).delete(any());
        verify(boxMapper).insert(any(FirstLegBox.class));
    }

    @Test
    void deleteBlockedAfterShipped() {
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("SHIPPED", "QTY", "100"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(9001L));
        assertTrue(ex.getMessage().contains("禁止删除"));
        verify(shipmentMapper, never()).deleteById(9001L);
    }

    @Test
    void deleteDraftCascadesBoxes() {
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("DRAFT", "QTY", null));
        when(boxMapper.selectList(any())).thenReturn(List.of(boxEntity(501L, "B1")));
        service.delete(9001L);
        verify(boxItemMapper).delete(any());
        verify(boxMapper).delete(any());
        verify(shipmentMapper).deleteById(9001L);
    }

    // ============================ 装箱/发货 ============================

    @Test
    void boxCasMissRejected() {
        when(shipmentMapper.casBox(9001L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.box(9001L));
        assertTrue(ex.getMessage().startsWith("装箱失败"));
    }

    @Test
    void boxRejectsEmptyPacking() {
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        when(boxMapper.selectList(any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.box(9001L));
    }

    @Test
    void boxHappy() {
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        when(boxMapper.selectList(any())).thenReturn(List.of(boxEntity(501L, "B1")));
        when(boxItemMapper.selectList(any()))
                .thenReturn(List.of(boxItemEntity(501L, 11L, 2)));
        service.box(9001L);
        verify(shipmentMapper).casBox(9001L);
    }

    @Test
    void shipHappyCnyFreezesRateOne() {
        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("BOXED", "WEIGHT", null));
        FirstLegShipRequest req = FirstLegShipRequest.builder()
                .freightAmount(new BigDecimal("100")).build();
        service.ship(9001L, req);
        ArgumentCaptor<FirstLegShipment> c = ArgumentCaptor.forClass(FirstLegShipment.class);
        verify(shipmentMapper).updateById(c.capture());
        FirstLegShipment saved = c.getValue();
        assertEquals("SHIPPED", saved.getStatus());
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getExchangeRate()));
        assertEquals(0, new BigDecimal("100.0000").compareTo(saved.getFreightCny()));
        assertNotNull(saved.getShippedAt());
        verify(exchangeRateService, never()).resolveRate(any(), any());
    }

    @Test
    void shipForeignResolvesRateAndConverts() {
        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("BOXED", "WEIGHT", null));
        LocalDateTime shippedAt = LocalDateTime.of(2026, 9, 10, 8, 0);
        when(exchangeRateService.resolveRate(eq("USD"), eq(shippedAt))).thenReturn(new BigDecimal("7.2"));
        FirstLegShipRequest req = FirstLegShipRequest.builder()
                .freightAmount(new BigDecimal("100")).currency("USD").shippedAt(shippedAt).build();
        service.ship(9001L, req);
        ArgumentCaptor<FirstLegShipment> c = ArgumentCaptor.forClass(FirstLegShipment.class);
        verify(shipmentMapper).updateById(c.capture());
        assertEquals(0, new BigDecimal("720.0000").compareTo(c.getValue().getFreightCny()));
        assertEquals(0, new BigDecimal("7.2").compareTo(c.getValue().getExchangeRate()));
    }

    @Test
    void shipForeignWithoutQuoteBlocked() {
        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("BOXED", "WEIGHT", null));
        when(exchangeRateService.resolveRate(eq("USD"), any())).thenReturn(null);
        FirstLegShipRequest req = FirstLegShipRequest.builder()
                .freightAmount(new BigDecimal("100")).currency("USD").build();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.ship(9001L, req));
        assertTrue(ex.getMessage().contains("无汇率报价"));
        verify(shipmentMapper, never()).updateById(any(FirstLegShipment.class));
    }

    @Test
    void shipManualRateBypassesResolve() {
        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("BOXED", "WEIGHT", null));
        FirstLegShipRequest req = FirstLegShipRequest.builder()
                .freightAmount(new BigDecimal("100")).currency("EUR")
                .exchangeRate(new BigDecimal("7.85")).build();
        service.ship(9001L, req);
        verify(exchangeRateService, never()).resolveRate(any(), any());
        ArgumentCaptor<FirstLegShipment> c = ArgumentCaptor.forClass(FirstLegShipment.class);
        verify(shipmentMapper).updateById(c.capture());
        assertEquals(0, new BigDecimal("785.0000").compareTo(c.getValue().getFreightCny()));
    }

    // ============================ 分摊三策略 + 勾稽/降级/拦截 ============================

    @Test
    void allocateQtyAcrossTwoBoxesSumsFreight() {
        givenPacking(
                List.of(boxEntity(501L, "B1"), boxEntity(502L, "B2")),
                List.of(List.of(boxItemEntity(501L, 11L, 3)), List.of(boxItemEntity(502L, 12L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "QTY", "100"));
        assertEquals(2, amounts.size());
        assertEquals(0, new BigDecimal("75.0000").compareTo(amounts.get(11L)));
        assertEquals(0, new BigDecimal("25.0000").compareTo(amounts.get(12L)));
        assertEquals(0, new BigDecimal("100.0000").compareTo(sum(amounts)));
    }

    @Test
    void allocateWeightProportionalToGramBases() {
        givenSkus(sku(11L, "S11", 100, null), sku(12L, "S12", 300, null));
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "WEIGHT", "100"));
        assertEquals(0, new BigDecimal("25.0000").compareTo(amounts.get(11L)));
        assertEquals(0, new BigDecimal("75.0000").compareTo(amounts.get(12L)));
        assertEquals(0, new BigDecimal("100.0000").compareTo(sum(amounts)));
        assertAllocStrategy("WEIGHT");
    }

    @Test
    void allocateWeightAllZeroDowngradesToQtyWithRemark() {
        givenSkus(sku(11L, "S11", null, null), sku(12L, "S12", 0, null));
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "WEIGHT", "100"));
        assertEquals(0, new BigDecimal("50.0000").compareTo(amounts.get(11L)));
        assertEquals(0, new BigDecimal("50.0000").compareTo(amounts.get(12L)));
        assertAllocStrategy("QTY");
        ArgumentCaptor<FirstLegShipment> sc = ArgumentCaptor.forClass(FirstLegShipment.class);
        verify(shipmentMapper).updateById(sc.capture());
        assertTrue(sc.getValue().getAllocRemark().contains("降级按数量"));
    }

    @Test
    void allocateWeightPartialZeroBlocked() {
        givenSkus(sku(11L, "S11", 100, null), sku(12L, "S12", null, null));
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 1))));
        when(shipmentMapper.casAllocate(9001L)).thenReturn(1);
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment("SHIPPED", "WEIGHT", "100"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.allocate(9001L));
        assertTrue(ex.getMessage().contains("重量(weight_g)未维护"));
        verify(allocMapper, never()).insert(any(FirstLegAlloc.class));
        verify(shipmentMapper, never()).updateById(any(FirstLegShipment.class));
    }

    @Test
    void allocateAmountUsesLatestPurchasePrice() {
        givenSkus(sku(11L, "S11", null, null), sku(12L, "S12", null, null));
        when(purchaseQueryApi.findLatestSupplierBySkuIds(anyCollection()))
                .thenReturn(List.of(latest(11L, "10"), latest(12L, "30")));
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "AMOUNT", "100"));
        assertEquals(0, new BigDecimal("25.0000").compareTo(amounts.get(11L)));
        assertEquals(0, new BigDecimal("75.0000").compareTo(amounts.get(12L)));
        assertAllocStrategy("AMOUNT");
    }

    @Test
    void allocateAmountFallsBackToSkuCostPrice() {
        givenSkus(sku(11L, "S11", null, "10"), sku(12L, "S12", null, "30"));
        when(purchaseQueryApi.findLatestSupplierBySkuIds(anyCollection())).thenReturn(List.of());
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "AMOUNT", "100"));
        assertEquals(0, new BigDecimal("25.0000").compareTo(amounts.get(11L)));
        assertEquals(0, new BigDecimal("75.0000").compareTo(amounts.get(12L)));
    }

    @Test
    void allocateAmountAllZeroDowngradesToQty() {
        givenSkus(sku(11L, "S11", null, null), sku(12L, "S12", null, null));
        when(purchaseQueryApi.findLatestSupplierBySkuIds(anyCollection())).thenReturn(List.of());
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(boxItemEntity(501L, 11L, 1), boxItemEntity(501L, 12L, 3))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "AMOUNT", "100"));
        assertAllocStrategy("QTY");
        assertEquals(0, new BigDecimal("100.0000").compareTo(sum(amounts)));
    }

    @Test
    void allocateRoundingTailGoesToSmallestSkuOnTie() {
        givenPacking(
                List.of(boxEntity(501L, "B1")),
                List.of(List.of(
                        boxItemEntity(501L, 30L, 1),
                        boxItemEntity(501L, 20L, 1),
                        boxItemEntity(501L, 10L, 1))));
        Map<Long, BigDecimal> amounts = allocateAndCapture(shipment("SHIPPED", "QTY", "100"));
        // 100/3 = 33.3333(HALF_UP),0.0001 舍入残差并入最大基数行(并列最小 SKU=10)
        assertEquals(0, new BigDecimal("33.3333").compareTo(amounts.get(30L)));
        assertEquals(0, new BigDecimal("33.3333").compareTo(amounts.get(20L)));
        assertEquals(0, new BigDecimal("33.3334").compareTo(amounts.get(10L)), "尾差应并入最小SKU");
        assertEquals(0, new BigDecimal("100.0000").compareTo(sum(amounts)), "Σalloc 必须结构性等于运费");
    }

    @Test
    void allocateCasMissRejected() {
        when(shipmentMapper.casAllocate(9001L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.allocate(9001L));
        assertTrue(ex.getMessage().startsWith("分摊失败"));
        verify(allocMapper, never()).insert(any(FirstLegAlloc.class));
    }

    // ============================ 关闭/取消 ============================

    @Test
    void closeCasMissRejected() {
        when(shipmentMapper.casClose(9001L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.close(9001L));
        assertTrue(ex.getMessage().startsWith("关闭失败"));
    }

    @Test
    void closeHappy() {
        when(shipmentMapper.casClose(9001L)).thenReturn(1);
        service.close(9001L);
        verify(shipmentMapper).casClose(9001L);
    }

    @Test
    void cancelCasMissRejected() {
        when(shipmentMapper.casCancel(9001L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(9001L));
        assertTrue(ex.getMessage().startsWith("取消失败"));
    }

    @Test
    void cancelHappyFromBoxed() {
        when(shipmentMapper.casCancel(9001L)).thenReturn(1);
        service.cancel(9001L);
        verify(shipmentMapper).casCancel(9001L);
    }

    // ============================ 内部断言辅助 ============================

    private static BigDecimal sum(Map<Long, BigDecimal> amounts) {
        return amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 校验最近一批落库分摊行的实际策略 */
    private void assertAllocStrategy(String strategy) {
        ArgumentCaptor<FirstLegAlloc> c = ArgumentCaptor.forClass(FirstLegAlloc.class);
        verify(allocMapper, org.mockito.Mockito.atLeastOnce()).insert(c.capture());
        for (FirstLegAlloc alloc : c.getAllValues()) {
            assertEquals(strategy, alloc.getStrategy());
        }
    }
}
