package com.own.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.purchase.entity.PurchaseOrderItem;
import com.own.erp.purchase.response.SkuSupplierRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : purchase_order_item 表 Mapper:子表通用 CRUD 走 BaseMapper,
 *         入库核销回写用原子累加(docs/07 §1 ① 正确性锁落 DB,禁 selectOne→算术→updateById)
 */
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {

    /**
     * 已入库数量原子累加(#10 入库核销,docs/07 §1 ①):
     * 防超收条件 `arrived_qty + Δ <= quantity` 进 WHERE,行锁串行化同明细并发核销;
     * affected=0 = 明细不存在或超收(含并发窗口他单先收满),由调用方统一按业务冲突报错
     */
    @Update("UPDATE purchase_order_item SET arrived_qty = arrived_qty + #{quantity} "
            + "WHERE id = #{poItemId} AND arrived_qty + #{quantity} <= quantity")
    int increaseArrivedQty(@Param("poItemId") Long poItemId, @Param("quantity") Integer quantity);

    /**
     * SKU→最新供应商映射(#17 采购建议取数,XML 在 resources/mapper/):
     * 每 SKU 取最近一笔非 DRAFT 采购单的明细行(窗口函数 ROW_NUMBER,草稿未定案不算历史);
     * 自定义联查禁 Wrapper .in() 急切解析坑(docs/07 §10),skuIds 走 XML foreach
     */
    List<SkuSupplierRow> findLatestSupplierRows(@Param("skuIds") Collection<Long> skuIds);
}
