package com.own.erp.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.aftersale.entity.AftersaleReturnItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : aftersale_return_item 表 Mapper(#12 退货明细):写侧仅 receive-return 复合事务内 insert(实收明细即动账凭证),
 *         无独立人工写入口;读侧 = 详情按售后单查 + 数量预校验按订单侧聚合(join 关联到售后单的 order_id)
 */
public interface AftersaleReturnItemMapper extends BaseMapper<AftersaleReturnItem> {

    /**
     * 某平台订单下全部历史退货明细行(跨该订单的所有售后单,join aftersale_order 取 order_id;
     * 行数=退货明细个位数,内存聚合 Σreturn_qty 做数量预校验,同 #11"逐单 eq 聚合规避 .in() 急切解析"思路)
     */
    @Select("SELECT r.id, r.aftersale_id, r.order_item_id, r.sku_id, r.return_qty "
            + "FROM aftersale_return_item r INNER JOIN aftersale_order a ON r.aftersale_id = a.id "
            + "WHERE a.order_id = #{orderId}")
    List<AftersaleReturnItem> listByOrderId(@Param("orderId") Long orderId);
}
