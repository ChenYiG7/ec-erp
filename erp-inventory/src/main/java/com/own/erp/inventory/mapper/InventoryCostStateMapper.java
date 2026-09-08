package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.InventoryCostState;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU 移动加权成本账 Mapper(#19③):FOR UPDATE 锁行先例同 DeliveryOrderMapper.selectByIdForUpdate,
 *     出库结转持锁至事务提交串行化同 SKU 成本计算(docs/07 §1 ① 正确性锁落 DB)
 */
public interface InventoryCostStateMapper extends BaseMapper<InventoryCostState> {

    /**
     * 锁定成本账行(事务内调用,行锁至提交):无行返回 null,由服务层首建(DuplicateKey 回退重读)
     */
    @Select("SELECT * FROM sku_cost_state WHERE sku_id = #{skuId} FOR UPDATE")
    InventoryCostState selectBySkuIdForUpdate(@Param("skuId") Long skuId);
}
