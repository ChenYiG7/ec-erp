package com.own.erp.fulfill.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.GoodsQueryApi.SkuView;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.contract.WarehouseApi.WarehouseView;
import com.own.erp.fulfill.constant.FbaConsts;
import com.own.erp.fulfill.entity.FbaBox;
import com.own.erp.fulfill.entity.FbaBoxItem;
import com.own.erp.fulfill.entity.FbaShipment;
import com.own.erp.fulfill.entity.FbaShipmentDiff;
import com.own.erp.fulfill.entity.FbaShipmentItem;
import com.own.erp.fulfill.mapper.FbaBoxItemMapper;
import com.own.erp.fulfill.mapper.FbaBoxMapper;
import com.own.erp.fulfill.mapper.FbaQueryMapper;
import com.own.erp.fulfill.mapper.FbaShipmentDiffMapper;
import com.own.erp.fulfill.mapper.FbaShipmentItemMapper;
import com.own.erp.fulfill.mapper.FbaShipmentMapper;
import com.own.erp.fulfill.request.command.FbaReceiveRequest;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.BoxItemSave;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.BoxSave;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.PlanItemSave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FbaShipmentService 单测(fba-shipment,AIR:mock Mapper/契约,不依赖数据库):
 *     建单守卫链(仓型非SELF/计划行空/计划SKU重复/箱号重复/箱内计划外SKU)、改删状态守卫、
 *     装箱勾稽预检(空箱/缺SKU/数量不平)、SHIPPED 复合事务(勾稽失败拦截 + 逐SKU OUT_SHIP 动账断言:
 *     flowType/bizType/bizId/负数量)、收货登记(状态守卫/缺发出SKU拦截/diff 三态 SHORT/EXTRA/OK 生成/
 *     RECEIVING 重复登记覆盖先删后插)、超期取数口。
 *     纯单 cas 两类守卫另见 FbaShipmentStateMachineTest。
 *     注:MP LambdaQueryWrapper 纯 Mockito 环境无元数据,本类只走 eq/le 路径不碰 .in()(docs/07 §10)
 */
class FbaShipmentServiceTest {

    private static final long WH = 100L;
    private static final Long USER = 1L;

    private FbaShipmentMapper shipmentMapper;
    private FbaShipmentItemMapper planItemMapper;
    private FbaBoxMapper boxMapper;
    private FbaBoxItemMapper boxItemMapper;
    private FbaShipmentDiffMapper diffMapper;
    private FbaQueryMapper queryMapper;
    private WarehouseApi warehouseApi;
    private GoodsQueryApi goodsQueryApi;
    private InventoryChangeApi inventoryChangeApi;
    private CurrentUserApi currentUserApi;
    private FbaShipmentService service;

    private long boxIdSeq = 500L;

    @BeforeEach
    void setUp() {
        shipmentMapper = mock(FbaShipmentMapper.class);
        planItemMapper = mock(FbaShipmentItemMapper.class);
        boxMapper = mock(FbaBoxMapper.class);
        boxItemMapper = mock(FbaBoxItemMapper.class);
        diffMapper = mock(FbaShipmentDiffMapper.class);
        queryMapper = mock(FbaQueryMapper.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        inventoryChangeApi = mock(InventoryChangeApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        service = new FbaShipmentService(shipmentMapper, planItemMapper, boxMapper, boxItemMapper,
                diffMapper, queryMapper, warehouseApi, goodsQueryApi, inventoryChangeApi, currentUserApi);

        when(shipmentMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            ((FbaShipment) inv.getArgument(0)).setId(9001L);
            return 1;
        }).when(shipmentMapper).insert(any(FbaShipment.class));
        doAnswer(inv -> {
            ((FbaBox) inv.getArgument(0)).setId(++boxIdSeq);
            return 1;
        }).when(boxMapper).insert(any(FbaBox.class));
        when(currentUserApi.currentUserId()).thenReturn(USER);
        when(warehouseApi.findWarehouseViewById(WH)).thenReturn(WarehouseView.builder()
                .id(WH).whName("国内仓").whType("SELF").country("CN").status(1).build());
    }

    // ============================ 造数辅助 ============================

    private static SkuView sku(long id, String code) {
        return SkuView.builder().id(id).productId(1L).skuCode(code).status(1).build();
    }

    private void givenSkus(SkuView... skus) {
        when(goodsQueryApi.findSkusByIds(anyCollection())).thenReturn(List.of(skus));
    }

    private static PlanItemSave plan(long skuId, int qty) {
        return PlanItemSave.builder().skuId(skuId).planQty(qty).build();
    }

    private static BoxSave box(String no, BoxItemSave... items) {
        return BoxSave.builder().boxNo(no).items(List.of(items)).build();
    }

    private static BoxItemSave item(long skuId, int qty) {
        return BoxItemSave.builder().skuId(skuId).quantity(qty).build();
    }

    private static FbaShipmentSaveRequest request(List<PlanItemSave> planItems, List<BoxSave> boxes) {
        return FbaShipmentSaveRequest.builder()
                .shopId(1L).marketplace("US").warehouseId(WH)
                .planItems(planItems).boxes(boxes)
                .build();
    }

    private void givenShipment(FbaShipment shipment) {
        when(shipmentMapper.selectById(9001L)).thenReturn(shipment);
    }

    private FbaShipment shipmentOf(String status) {
        return FbaShipment.builder().id(9001L).shipmentNo("FB202609120001").shopId(1L)
                .marketplace("US").warehouseId(WH).status(status).createdBy(USER).build();
    }

    private void givenPlan(long skuId, int qty) {
        when(planItemMapper.selectList(any())).thenReturn(List.of(
                FbaShipmentItem.builder().id(1L).shipmentId(9001L).skuId(skuId).planQty(qty).build()));
    }

    /** 两个 SKU 的计划(planItemMapper.selectList 两次调用返回同一份) */
    private void givenPlanTwoSkus() {
        when(planItemMapper.selectList(any())).thenReturn(List.of(
                FbaShipmentItem.builder().id(1L).shipmentId(9001L).skuId(10L).planQty(3).build(),
                FbaShipmentItem.builder().id(2L).shipmentId(9001L).skuId(11L).planQty(2).build()));
    }

    /** 单箱两 SKU:mock 的 boxItemMapper.selectList(any()) 对每箱都返回同一份全量列表,
     *  多箱会让 listItemsOfBoxes 重复聚合(docs/07 §10 同源环境限制),造数统一走单箱 */
    private void givenBoxesTwoSkus() {
        FbaBox b1 = FbaBox.builder().id(501L).shipmentId(9001L).boxNo("A1").build();
        when(boxMapper.selectList(any())).thenReturn(List.of(b1));
        when(boxItemMapper.selectList(any())).thenReturn(List.of(
                FbaBoxItem.builder().id(1L).boxId(501L).skuId(10L).quantity(3).build(),
                FbaBoxItem.builder().id(2L).boxId(501L).skuId(11L).quantity(2).build()));
    }

    // ============================ 建单/改单/删除 ============================

    @Test
    void saveRejectsNonSelfWarehouse() {
        when(warehouseApi.findWarehouseViewById(WH)).thenReturn(WarehouseView.builder()
                .id(WH).whName("FBA仓").whType("FBA").country("US").status(1).build());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.save(request(List.of(plan(10L, 1)), List.of())));
        assertTrue(e.getMessage().contains("必须是国内自仓"));
    }

    @Test
    void saveRejectsEmptyPlanOrDuplicatePlanSku() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.save(request(List.of(), List.of())));
        assertTrue(empty.getMessage().contains("计划行不能为空"));
        BusinessException dup = assertThrows(BusinessException.class,
                () -> service.save(request(List.of(plan(10L, 1), plan(10L, 2)), List.of())));
        assertTrue(dup.getMessage().contains("计划行 SKU 重复"));
    }

    @Test
    void saveRejectsBoxSkuOutsidePlan() {
        givenSkus(sku(10L, "A"), sku(11L, "B"));
        BusinessException e = assertThrows(BusinessException.class, () -> service.save(
                request(List.of(plan(10L, 3)), List.of(box("A1", item(11L, 1))))));
        assertTrue(e.getMessage().contains("计划外 SKU"));
        verify(shipmentMapper, never()).insert(any(FbaShipment.class));
    }

    @Test
    void savePersistsPlanAndBoxesWithGeneratedNo() {
        givenSkus(sku(10L, "A"));
        Long id = service.save(request(List.of(plan(10L, 3)), List.of(box("A1", item(10L, 3)))));
        assertEquals(9001L, id);
        ArgumentCaptor<FbaShipment> captor = ArgumentCaptor.forClass(FbaShipment.class);
        verify(shipmentMapper).insert(captor.capture());
        assertTrue(captor.getValue().getShipmentNo().startsWith("FB"));
        assertEquals(FbaConsts.STATUS_DRAFT, captor.getValue().getStatus());
        verify(planItemMapper).insert(any(FbaShipmentItem.class));
        verify(boxMapper).insert(any(FbaBox.class));
        verify(boxItemMapper).insert(any(FbaBoxItem.class));
    }

    @Test
    void updateAndDeleteGuardByStatus() {
        when(shipmentMapper.selectByIdForUpdate(9001L)).thenReturn(shipmentOf(FbaConsts.STATUS_BOXED));
        givenSkus(sku(10L, "A"));
        BusinessException upd = assertThrows(BusinessException.class,
                () -> service.update(9001L, request(List.of(plan(10L, 1)), List.of())));
        assertTrue(upd.getMessage().contains("仅草稿状态可修改"));

        givenShipment(shipmentOf(FbaConsts.STATUS_SHIPPED));
        BusinessException del = assertThrows(BusinessException.class, () -> service.delete(9001L));
        assertTrue(del.getMessage().contains("禁止删除"));
        verify(shipmentMapper, never()).deleteById(9001L);
    }

    // ============================ 装箱(勾稽预检) ============================

    @Test
    void boxRejectsEmptyBoxesAndReconcileMismatch() {
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        givenShipment(shipmentOf(FbaConsts.STATUS_DRAFT));
        when(boxMapper.selectList(any())).thenReturn(List.of());
        BusinessException empty = assertThrows(BusinessException.class, () -> service.box(9001L));
        assertTrue(empty.getMessage().contains("至少需要 1 个箱"));

        givenShipment(shipmentOf(FbaConsts.STATUS_DRAFT));
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        givenPlanTwoSkus();
        // 只装了 SKU 10,缺 SKU 11
        FbaBox b1 = FbaBox.builder().id(501L).shipmentId(9001L).boxNo("A1").build();
        when(boxMapper.selectList(any())).thenReturn(List.of(b1));
        when(boxItemMapper.selectList(any())).thenReturn(List.of(
                FbaBoxItem.builder().id(1L).boxId(501L).skuId(10L).quantity(3).build()));
        BusinessException missing = assertThrows(BusinessException.class, () -> service.box(9001L));
        assertTrue(missing.getMessage().contains("未装箱"));

        // 数量不平:计划 3/装箱 2
        givenShipment(shipmentOf(FbaConsts.STATUS_DRAFT));
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        FbaBox b2 = FbaBox.builder().id(502L).shipmentId(9001L).boxNo("A2").build();
        when(boxMapper.selectList(any())).thenReturn(List.of(b2));
        when(boxItemMapper.selectList(any())).thenReturn(List.of(
                FbaBoxItem.builder().id(1L).boxId(502L).skuId(10L).quantity(2).build(),
                FbaBoxItem.builder().id(2L).boxId(502L).skuId(11L).quantity(2).build()));
        BusinessException mismatch = assertThrows(BusinessException.class, () -> service.box(9001L));
        assertTrue(mismatch.getMessage().contains("数量与计划不平"));
    }

    @Test
    void boxSucceedsOnCasHitAndReconcileOk() {
        when(shipmentMapper.casBox(9001L)).thenReturn(1);
        givenPlanTwoSkus();
        givenBoxesTwoSkus();
        service.box(9001L);
        verify(shipmentMapper).casBox(9001L);
    }

    // ============================ 确认发出(复合事务) ============================

    @Test
    void shipRejectsOnCasMissAndReconcileMismatch() {
        when(shipmentMapper.casShip(9001L)).thenReturn(0);
        BusinessException miss = assertThrows(BusinessException.class, () -> service.ship(9001L));
        assertTrue(miss.getMessage().contains("发货失败"));

        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        givenShipment(shipmentOf(FbaConsts.STATUS_BOXED));
        givenPlanTwoSkus();
        // 只装了 SKU 10 → 勾稽缺 SKU 11,拦截且不动账
        FbaBox b1 = FbaBox.builder().id(501L).shipmentId(9001L).boxNo("A1").build();
        when(boxMapper.selectList(any())).thenReturn(List.of(b1));
        when(boxItemMapper.selectList(any())).thenReturn(List.of(
                FbaBoxItem.builder().id(1L).boxId(501L).skuId(10L).quantity(3).build()));
        BusinessException e = assertThrows(BusinessException.class, () -> service.ship(9001L));
        assertTrue(e.getMessage().contains("未装箱"));
        verify(inventoryChangeApi, never()).change(any());
    }

    @Test
    void shipWritesOutShipPerSkuWithFbaBizType() {
        when(shipmentMapper.casShip(9001L)).thenReturn(1);
        FbaShipment shipment = shipmentOf(FbaConsts.STATUS_BOXED);
        givenShipment(shipment);
        givenPlanTwoSkus();
        givenBoxesTwoSkus();

        service.ship(9001L);

        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi, times(2)).change(captor.capture());
        List<InventoryChangeCommand> commands = captor.getAllValues();
        assertEquals(10L, commands.get(0).skuId());
        assertEquals(-3, commands.get(0).quantity());
        assertEquals(11L, commands.get(1).skuId());
        assertEquals(-2, commands.get(1).quantity());
        for (InventoryChangeCommand command : commands) {
            assertEquals(WH, command.warehouseId());
            assertEquals(InventoryConsts.FLOW_TYPE_OUT_SHIP, command.flowType());
            assertEquals(FbaConsts.BIZ_TYPE_FBA_SHIPMENT, command.bizType());
            assertEquals(9001L, command.bizId());
        }
        ArgumentCaptor<FbaShipment> markCaptor = ArgumentCaptor.forClass(FbaShipment.class);
        verify(shipmentMapper).updateById(markCaptor.capture());
        assertEquals(FbaConsts.STATUS_SHIPPED, markCaptor.getValue().getStatus());
        assertTrue(markCaptor.getValue().getShippedAt() != null);
    }

    // ============================ 收货登记(diff 三态) ============================

    @Test
    void receiveRejectsBadStatusAndMissingShippedSku() {
        // DRAFT 状态:casReceive 脱靶 + 状态不符
        when(shipmentMapper.casReceive(9001L)).thenReturn(0);
        givenShipment(shipmentOf(FbaConsts.STATUS_DRAFT));
        BusinessException bad = assertThrows(BusinessException.class, () -> service.registerReceive(9001L,
                FbaReceiveRequest.builder().items(List.of()).build()));
        assertTrue(bad.getMessage().contains("仅已发出/收货登记中状态可登记"));

        // SHIPPED:发出 SKU 10/11,登记只给 10 → 缺 11 拦截
        when(shipmentMapper.casReceive(9001L)).thenReturn(1);
        givenShipment(shipmentOf(FbaConsts.STATUS_SHIPPED));
        givenBoxesTwoSkus();
        givenSkus(sku(10L, "A"), sku(11L, "B"));
        BusinessException missing = assertThrows(BusinessException.class, () -> service.registerReceive(9001L,
                FbaReceiveRequest.builder().items(List.of(
                        FbaReceiveRequest.ReceiveItem.builder().skuId(10L).receivedQty(3).build())).build()));
        assertTrue(missing.getMessage().contains("未全部登记"));
        verify(diffMapper, never()).delete(any());
    }

    @Test
    void receiveGeneratesDiffThreeTypes() {
        when(shipmentMapper.casReceive(9001L)).thenReturn(1);
        givenShipment(shipmentOf(FbaConsts.STATUS_SHIPPED));
        givenBoxesTwoSkus();
        givenSkus(sku(9L, "Z"), sku(10L, "A"), sku(11L, "B"), sku(12L, "C"));
        service.registerReceive(9001L, FbaReceiveRequest.builder().items(List.of(
                FbaReceiveRequest.ReceiveItem.builder().skuId(10L).receivedQty(2).build(),
                FbaReceiveRequest.ReceiveItem.builder().skuId(11L).receivedQty(3).build(),
                FbaReceiveRequest.ReceiveItem.builder().skuId(12L).receivedQty(7).build())).build());

        verify(diffMapper).delete(any());
        ArgumentCaptor<FbaShipmentDiff> captor = ArgumentCaptor.forClass(FbaShipmentDiff.class);
        verify(diffMapper, times(3)).insert(captor.capture());
        List<FbaShipmentDiff> rows = captor.getAllValues();
        // sku 10: 发出3 收2 → SHORT;sku 11: 发出2 收3 → EXTRA;sku 12: 发出0 收7 → EXTRA
        assertEquals(FbaConsts.DIFF_SHORT, rows.get(0).getDiffType());
        assertEquals(3, rows.get(0).getShippedQty());
        assertEquals(2, rows.get(0).getReceivedQty());
        assertEquals(FbaConsts.DIFF_EXTRA, rows.get(1).getDiffType());
        assertEquals(FbaConsts.DIFF_EXTRA, rows.get(2).getDiffType());
        ArgumentCaptor<FbaShipment> markCaptor = ArgumentCaptor.forClass(FbaShipment.class);
        verify(shipmentMapper).updateById(markCaptor.capture());
        // 状态推进由 casReceive 完成,mark 仅回填最近登记时间
        assertTrue(markCaptor.getValue().getReceivedAt() != null);
    }

    @Test
    void receiveIsIdempotentFromReceivingStatus() {
        // cas 脱靶但状态已是 RECEIVING → 放行覆盖(先删后插)
        when(shipmentMapper.casReceive(9001L)).thenReturn(0);
        givenShipment(shipmentOf(FbaConsts.STATUS_RECEIVING));
        givenBoxesTwoSkus();
        givenSkus(sku(10L, "A"), sku(11L, "B"));
        service.registerReceive(9001L, FbaReceiveRequest.builder().items(List.of(
                FbaReceiveRequest.ReceiveItem.builder().skuId(10L).receivedQty(3).build(),
                FbaReceiveRequest.ReceiveItem.builder().skuId(11L).receivedQty(2).build())).build());
        verify(diffMapper).delete(any());
        verify(diffMapper, times(2)).insert(any(FbaShipmentDiff.class));
    }

    @Test
    void receiveRejectsDuplicateSkuRow() {
        when(shipmentMapper.casReceive(9001L)).thenReturn(1);
        givenShipment(shipmentOf(FbaConsts.STATUS_SHIPPED));
        givenBoxesTwoSkus();
        BusinessException e = assertThrows(BusinessException.class, () -> service.registerReceive(9001L,
                FbaReceiveRequest.builder().items(List.of(
                        FbaReceiveRequest.ReceiveItem.builder().skuId(10L).receivedQty(1).build(),
                        FbaReceiveRequest.ReceiveItem.builder().skuId(10L).receivedQty(2).build())).build()));
        assertTrue(e.getMessage().contains("SKU 重复"));
    }

    // ============================ Job 取数口 ============================

    @Test
    void listOverdueForReceiveConcatsShippedAndReceiving() {
        FbaShipment shipped = shipmentOf(FbaConsts.STATUS_SHIPPED);
        FbaShipment receiving = shipmentOf(FbaConsts.STATUS_RECEIVING);
        when(shipmentMapper.selectList(any())).thenReturn(List.of(shipped), List.of(receiving));
        List<FbaShipment> result = service.listOverdueForReceive(LocalDateTime.now());
        assertEquals(2, result.size());
    }
}
