package com.own.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.order.entity.ShopOrder;
import org.apache.ibatis.annotations.Param;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 平台订单 Mapper:通用 CRUD 走 BaseMapper;upsert 为拉单幂等专用(docs/07 §5),
 *         casOrderStatus 为发货推进专用(#11),SQL 均见 mapper/ShopOrderMapper.xml
 */
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

    /** uk(shop_id, platform_order_id) 冲突即整体更新订单主表,返回受影响行数(1 插入 / 2 更新) */
    int upsert(ShopOrder order);

    /** 订单状态条件推进(#11):WHERE order_status = fromStatus 即状态机守卫,返回受影响行数 */
    int casOrderStatus(@Param("orderId") Long orderId,
                       @Param("fromStatus") String fromStatus,
                       @Param("toStatus") String toStatus);
}
