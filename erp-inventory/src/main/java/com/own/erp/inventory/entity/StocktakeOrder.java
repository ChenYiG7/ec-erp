package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单(仓内作业;差异经 InventoryService.change 同事务写 flow,flow_type=ADJUST)(stocktake_order)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stocktake_order")
public class StocktakeOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号 ST+yyyyMMdd+seq,唯一 */
    private String stocktakeNo;

    /** 盘点仓ID(warehouse.id) */
    private Long warehouseId;

    /** 盘点范围:ALL全仓/SKU_SET选定SKU集(库位不做,只能按SKU集圈定) */
    private String scopeType;

    /** DRAFT草稿/COUNTING盘点中/PENDING_ADJUST待调整/ADJUSTED已调整/CLOSED已关闭/CANCELED已取消 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id) */
    private Long createdBy;

    /** 调整确认人(sys_user.id,生成调整时回填) */
    private Long confirmedBy;

    /** 调整确认时间(生成调整时回填) */
    private LocalDateTime confirmedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
